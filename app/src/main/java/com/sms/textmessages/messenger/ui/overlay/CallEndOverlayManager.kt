package com.sms.textmessages.messenger.ui.overlay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sms.textmessages.messenger.MainActivity
import com.sms.textmessages.messenger.data.db.AppDatabase
import com.sms.textmessages.messenger.receiver.CallEndType
import com.sms.textmessages.messenger.service.CallOverlayForegroundService
import com.sms.textmessages.messenger.ads.AdCache
import com.sms.textmessages.messenger.ads.AdPlacement
import com.sms.textmessages.messenger.ui.home.SmsRepository
import com.sms.textmessages.messenger.ui.home.getContactName
import com.sms.textmessages.messenger.utils.CallEndMetrics
import com.sms.textmessages.messenger.utils.OverlayPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Truecaller-style host: during-call bubble keeps a visible overlay (and process
// priority) for the call duration; IDLE morphs the same window into the
// call-end card. Cold show() remains for misses when no during-call window.
object CallEndOverlayManager {

    private const val TAG = "CALLEND_DEBUG"
    private const val NUMBER_UNAVAILABLE_MESSAGE = "Number not available"

    private val ownerScope = CoroutineScope(Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var hostContext: Context? = null

    private var closeSystemDialogsReceiver: BroadcastReceiver? = null
    private var registeredReceiverContext: Context? = null

    private var overlayMode = mutableStateOf(OverlayMode.NONE)
    private var duringPhase = mutableStateOf(DuringCallPhase.RINGING)
    private var displayNameState = mutableStateOf("Unknown")
    private var phoneState = mutableStateOf<String?>(null)
    private var callEndTypeState = mutableStateOf(CallEndType.MISSED)
    private var durationState = mutableStateOf(0L)
    private var unreadCountState = mutableStateOf<Int?>(null)

    // Dedupe key for CallLog fallback / double triggers.
    private var lastHandledCallKey: String? = null

    private enum class OverlayMode { NONE, DURING_CALL, CALL_END }

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore = ViewModelStore()

        fun start() {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }

    fun isShowing(): Boolean = overlayMode.value != OverlayMode.NONE

    fun isShowingDuringCall(): Boolean = overlayMode.value == OverlayMode.DURING_CALL

    fun wasCallHandled(key: String): Boolean = lastHandledCallKey == key

    fun markCallHandled(key: String) {
        lastHandledCallKey = key
    }

    // Truecaller path: attach bubble as soon as call activity is known.
    fun showDuringCall(
        context: Context,
        phoneNumber: String?,
        phase: DuringCallPhase
    ) {
        val appContext = context.applicationContext
        if (!OverlayPermission.canDrawOverlays(appContext)) {
            Log.w(TAG, "showDuringCall: aborted - no overlay permission")
            CallEndMetrics.recordMiss(appContext, "during_no_overlay_perm")
            return
        }

        val contactLookup = phoneNumber?.let { getContactName(appContext, it) }
        val displayName = contactLookup?.takeIf { it.isNotBlank() && it != phoneNumber }
            ?: phoneNumber
            ?: "Unknown"

        phoneState.value = phoneNumber
        displayNameState.value = displayName
        duringPhase.value = phase

        if (overlayMode.value == OverlayMode.DURING_CALL && overlayView != null) {
            Log.d(TAG, "showDuringCall: already showing - updating phase=$phase number=${describeNumber(phoneNumber)}")
            // Re-check cache: keep existing banner if fresh, otherwise start a new load.
            AdCache.ensure(AdPlacement.CALL_END_BANNER, appContext)
            return
        }

        if (overlayMode.value == OverlayMode.CALL_END) {
            Log.d(TAG, "showDuringCall: call-end already up - skip during-call")
            return
        }

        teardownWindowOnly()
        // ensure(): if CALL_END_BANNER already loaded and under 30m TTL → keep it;
        // otherwise load a new one (non-blocking) while the call is still active.
        AdCache.ensure(AdPlacement.CALL_END_BANNER, appContext)
        attachWindow(appContext, duringCall = true)
        overlayMode.value = OverlayMode.DURING_CALL
        CallEndMetrics.recordDuringCallShown(appContext)
        CallOverlayForegroundService.start(appContext)
        Log.d(TAG, "showDuringCall: phase=$phase displayName=$displayName")
    }

    // Morph existing during-call window into full call-end UI, or cold-show.
    fun show(context: Context, callType: CallEndType, phoneNumber: String?, durationMs: Long) {
        val appContext = context.applicationContext
        val numberDesc = describeNumber(phoneNumber)
        Log.d(TAG, "show(): mode=${overlayMode.value} callType=$callType phone=$numberDesc durationMs=$durationMs")

        val key = "${phoneNumber.orEmpty()}|$callType|$durationMs|${System.currentTimeMillis() / 5000}"
        // Coarse dedupe window handled by callers for CallLog; still set key.
        markCallHandled("${phoneNumber.orEmpty()}|${callType.name}|${System.currentTimeMillis() / 10_000}")

        if (!OverlayPermission.canDrawOverlays(appContext)) {
            Log.w(TAG, "show(): no overlay - notification fallback")
            CallEndMetrics.recordMiss(appContext, "call_end_no_overlay_perm")
            CallEndNotifier.notifyCallEnded(appContext, callType, phoneNumber, durationMs)
            return
        }

        val contactLookup = phoneNumber?.let { getContactName(appContext, it) }
        val displayName = contactLookup?.takeIf { it.isNotBlank() }
            ?: phoneNumber
            ?: "Unknown"

        phoneState.value = phoneNumber
        displayNameState.value = displayName
        callEndTypeState.value = callType
        durationState.value = durationMs

        if (overlayMode.value == OverlayMode.DURING_CALL && overlayView != null) {
            Log.d(TAG, "show(): morphing during-call → call-end")
            AdCache.ensure(AdPlacement.CALL_END_BANNER, appContext)
            ownerScope.launch {
                unreadCountState.value = AppDatabase.getDatabase(appContext).threadDao().getUnreadThreadCount()
            }
            // Upgrade window to full-screen focusable call-end params.
            upgradeToCallEndWindow(appContext)
            overlayMode.value = OverlayMode.CALL_END
            CallEndMetrics.recordMorph(appContext)
            registerCloseSystemDialogsReceiver(appContext)
            return
        }

        teardown()
        AdCache.ensure(AdPlacement.CALL_END_BANNER, appContext)
        ownerScope.launch {
            unreadCountState.value = AppDatabase.getDatabase(appContext).threadDao().getUnreadThreadCount()
        }
        attachWindow(appContext, duringCall = false)
        overlayMode.value = OverlayMode.CALL_END
        CallEndMetrics.recordShow(appContext)
        CallOverlayForegroundService.start(appContext)
        registerCloseSystemDialogsReceiver(appContext)
    }

    fun dismissDuringCallOnly() {
        if (overlayMode.value == OverlayMode.DURING_CALL) {
            Log.d(TAG, "dismissDuringCallOnly: user closed bubble - call continues")
            teardown()
        }
    }

    private fun attachWindow(context: Context, duringCall: Boolean) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        hostContext = context

        val owner = OverlayLifecycleOwner().also { it.start() }
        lifecycleOwner = owner

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            if (!duringCall) {
                isFocusableInTouchMode = true
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        dismiss()
                        true
                    } else false
                }
            }
            setContent {
                when (overlayMode.value) {
                    OverlayMode.DURING_CALL -> DuringCallOverlayCard(
                        displayName = displayNameState.value,
                        phoneNumber = phoneState.value,
                        phase = duringPhase.value,
                        onDismiss = { dismissDuringCallOnly() }
                    )
                    OverlayMode.CALL_END -> CallEndOverlayCard(
                        displayName = displayNameState.value,
                        phoneNumber = phoneState.value,
                        callType = callEndTypeState.value,
                        durationMs = durationState.value,
                        bannerAd = AdCache.bannerState(AdPlacement.CALL_END_BANNER).value,
                        unreadCount = unreadCountState.value,
                        onMessage = { openChat(context, phoneState.value) },
                        onCallBack = { callBack(context, phoneState.value) },
                        onSave = { saveContact(context, phoneState.value) },
                        onBlock = { blockNumber(context, phoneState.value) },
                        onOpenApp = { openApp(context) },
                        onDismiss = { dismiss() },
                        onCopyNumber = { copyNumber(context, phoneState.value) },
                        onViewContact = { viewContact(context, phoneState.value) },
                        onReportSpam = { reportSpam(context, phoneState.value) }
                    )
                    OverlayMode.NONE -> {}
                }
            }
        }

        val params = if (duringCall) duringCallParams() else callEndParams()
        try {
            wm.addView(composeView, params)
            overlayView = composeView
            if (!duringCall) composeView.requestFocus()
            Log.d(TAG, "attachWindow: duringCall=$duringCall ok")
        } catch (e: Exception) {
            Log.e(TAG, "attachWindow failed: ${e.message}", e)
            CallEndMetrics.recordMiss(context, "addView_failed")
            teardown()
            if (!duringCall) {
                CallEndNotifier.notifyCallEnded(
                    context,
                    callEndTypeState.value,
                    phoneState.value,
                    durationState.value
                )
            }
        }
    }

    private fun upgradeToCallEndWindow(context: Context) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        try {
            wm.updateViewLayout(view, callEndParams())
            view.isFocusableInTouchMode = true
            view.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    true
                } else false
            }
            view.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "upgradeToCallEndWindow failed - recreating: ${e.message}", e)
            teardownWindowOnly()
            attachWindow(context, duringCall = false)
            overlayMode.value = OverlayMode.CALL_END
        }
    }

    private fun duringCallParams(): WindowManager.LayoutParams {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 48
        }
    }

    private fun callEndParams(): WindowManager.LayoutParams {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun describeNumber(number: String?): String = number?.ifEmpty { "empty" } ?: "null"

    private fun registerCloseSystemDialogsReceiver(context: Context) {
        if (closeSystemDialogsReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (overlayMode.value == OverlayMode.CALL_END) dismiss()
            }
        }
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            closeSystemDialogsReceiver = receiver
            registeredReceiverContext = context
        } catch (e: Exception) {
            Log.e(TAG, "registerCloseSystemDialogsReceiver failed: ${e.message}", e)
        }
    }

    private fun unregisterCloseSystemDialogsReceiver() {
        val receiver = closeSystemDialogsReceiver
        val ctx = registeredReceiverContext
        if (receiver == null || ctx == null) return
        try {
            ctx.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        closeSystemDialogsReceiver = null
        registeredReceiverContext = null
    }

    private fun openChat(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                putExtra("open_chat_sender", phoneNumber)
                putExtra("open_chat_autofocus", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        dismiss()
    }

    private fun callBack(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        val hasCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        try {
            context.startActivity(
                Intent(action).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (_: SecurityException) {
            context.startActivity(
                Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
        dismiss()
    }

    private fun saveContact(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(
            Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        dismiss()
    }

    private fun blockNumber(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        ownerScope.launch { SmsRepository.blockThread(context, phoneNumber) }
        dismiss()
    }

    private fun copyNumber(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", phoneNumber))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun viewContact(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        val lookupUri = getContactLookupUri(context, phoneNumber)
        if (lookupUri == null) {
            Toast.makeText(context, "Contact not found", Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(
            Intent(Intent.ACTION_VIEW, lookupUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        dismiss()
    }

    private fun reportSpam(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        ownerScope.launch { SmsRepository.blockThread(context, phoneNumber) }
        Toast.makeText(context, "Reported and blocked", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun getContactLookupUri(context: Context, phoneNumber: String): Uri? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                if (idIndex == -1 || lookupKeyIndex == -1) return null
                ContactsContract.Contacts.getLookupUri(cursor.getLong(idIndex), cursor.getString(lookupKeyIndex))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getContactLookupUri failed: ${e.message}", e)
            null
        }
    }

    private fun openApp(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        dismiss()
    }

    private fun dismiss() {
        Log.d(TAG, "dismiss()")
        teardown()
    }

    private fun teardownWindowOnly() {
        unregisterCloseSystemDialogsReceiver()
        val wm = windowManager
        val view = overlayView
        if (wm != null && view != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        windowManager = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        overlayMode.value = OverlayMode.NONE
    }

    private fun teardown() {
        val ctx = hostContext
        teardownWindowOnly()
        hostContext = null
        // Keep CALL_END_BANNER cached for the next call — only detach from the
        // overlay view tree. release() forced a full reload + shimmer every time.
        AdCache.detachBanner(AdPlacement.CALL_END_BANNER)
        if (ctx != null) CallOverlayForegroundService.stop(ctx)
    }

    // ---- External helpers for CallEndActivity ----

    fun openChatFromExternal(context: Context, phoneNumber: String?) =
        openChat(context, phoneNumber)

    fun callBackFromExternal(context: Context, phoneNumber: String?) =
        callBack(context, phoneNumber)

    fun saveContactFromExternal(context: Context, phoneNumber: String?) =
        saveContact(context, phoneNumber)

    fun blockFromExternal(context: Context, phoneNumber: String?) =
        blockNumber(context, phoneNumber)

    fun openAppFromExternal(context: Context) = openApp(context)

    fun copyNumberFromExternal(context: Context, phoneNumber: String?) =
        copyNumber(context, phoneNumber)

    fun viewContactFromExternal(context: Context, phoneNumber: String?) =
        viewContact(context, phoneNumber)

    fun reportSpamFromExternal(context: Context, phoneNumber: String?) =
        reportSpam(context, phoneNumber)
}

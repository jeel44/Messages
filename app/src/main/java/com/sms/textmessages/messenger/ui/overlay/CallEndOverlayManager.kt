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
import com.sms.textmessages.messenger.ui.ads.CallEndAdManager
import com.sms.textmessages.messenger.ui.home.SmsRepository
import com.sms.textmessages.messenger.ui.home.getContactName
import com.sms.textmessages.messenger.utils.OverlayPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Hosts the post-call overlay's show()/dismiss() logic. Called directly from
// CallStateListener.onReceive() - no Service involved at all, matching
// Calldorado's actual architecture (confirmed via their public integration
// guide: a plain manifest PHONE_STATE receiver, no service). Adding a
// SYSTEM_ALERT_WINDOW overlay only needs that permission at call time, not a
// running component to host it from - the addView() call below happens
// synchronously inside the receiver's execution context, riding the OS's
// short post-broadcast priority window rather than trying to guarantee the
// process stays alive afterward.
//
// This used to be routed through OverlayHostService, a persistent foreground
// service both this and CategoryOverlayService attached to, kept alive
// indefinitely via startForeground() from App.onCreate()/BootCompletedReceiver
// so it would already exist by the time a PHONE_STATE broadcast fired
// (PHONE_STATE isn't itself exempt from Android 12+'s can't-start-a-
// foreground-service-from-a-background-broadcast restriction, so
// CallStateListener could never start it fresh). That fixed the immediate
// symptom (the process dying mid-overlay) but did so by keeping the whole
// app process running all the time, which isn't what a call-end overlay
// actually needs and isn't how Calldorado does it - reverted in favor of
// this direct-draw approach. See CALLEND_DEBUG logs for how often the
// process is still alive by the time this runs vs. reclaimed first; a miss
// here is an accepted platform limitation (see the note in show() below),
// not something this migration tries to work around.
object CallEndOverlayManager {

    private const val TAG = "CALLEND_DEBUG"

    private val ownerScope = CoroutineScope(Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    // Dynamically registered (never manifest-declared - ACTION_CLOSE_SYSTEM_DIALOGS
    // is restricted to dynamic receivers since Android 12) while the overlay is
    // showing, so Home/recents/notification-shade pulldown dismisses it instead
    // of leaving it floating with no Activity lifecycle to catch that itself.
    // registeredReceiverContext is kept alongside the receiver purely so
    // teardown() (called from several dismiss paths with no context param of
    // its own) has something to unregister with.
    private var closeSystemDialogsReceiver: BroadcastReceiver? = null
    private var registeredReceiverContext: Context? = null

    // Minimal stand-in for the Lifecycle/SavedStateRegistry/ViewModelStore an
    // Activity or Service would otherwise provide for free - needed because
    // ComposeView requires all three of ViewTreeLifecycleOwner,
    // ViewTreeSavedStateRegistryOwner and ViewTreeViewModelStoreOwner to be
    // set, and there is deliberately no Service here to supply them. A fresh
    // instance is created per show() and moved straight to DESTROYED in
    // teardown() rather than reused - a Lifecycle can't leave DESTROYED once
    // it gets there, so reuse across show/dismiss cycles isn't an option.
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

    // context here is CallStateListener's receiver context (applicationContext
    // by the time it reaches this call) - see CallStateListener.handleState().
    // Known limitation, logged rather than worked around: if the OS reclaims
    // this process before addView() below runs, there is no recovery and no
    // overlay shows. That's expected on some OEMs under aggressive process
    // killing and is an accepted tradeoff of not running a keep-alive
    // service, not a bug - do not add WorkManager/AlarmManager/JobScheduler
    // polling to chase it.
    fun show(context: Context, callType: CallEndType, phoneNumber: String?, durationMs: Long) {

        val canDraw = OverlayPermission.canDrawOverlays(context)
        val numberDesc = describeNumber(phoneNumber)
        Log.d(TAG, "show(): canDrawOverlays()=$canDraw callType=$callType phoneNumber=$numberDesc durationMs=$durationMs")

        if (!canDraw) {
            Log.w(TAG, "show(): aborted - overlay permission not granted")
            return
        }

        teardown()
        showOverlay(context, callType, phoneNumber, durationMs)
    }

    private fun describeNumber(number: String?): String = number?.ifEmpty { "empty" } ?: "null"

    private fun showOverlay(context: Context, callType: CallEndType, phoneNumber: String?, durationMs: Long) {

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val owner = OverlayLifecycleOwner().also { it.start() }
        lifecycleOwner = owner

        val contactLookupResult = phoneNumber?.let { getContactName(context, it) }
        val resolvedContactName = contactLookupResult?.takeIf { it.isNotBlank() }
        val displayName = resolvedContactName ?: phoneNumber ?: "Unknown"

        Log.d(
            TAG,
            "showOverlay: rawPhoneNumber=${describeNumber(phoneNumber)} " +
                "getContactName()=${if (contactLookupResult == null) "not queried (number null)" else contactLookupResult.ifEmpty { "empty (no contact match)" }} " +
                "displayName=$displayName"
        )

        CallEndAdManager.loadNative(context)

        val unreadCountState = mutableStateOf<Int?>(null)
        ownerScope.launch {
            unreadCountState.value = AppDatabase.getDatabase(context).threadDao().getUnreadThreadCount()
        }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            // Disposal is tied to this view leaving the window (there's no
            // longer a Service/Activity lifecycle to tie it to instead) -
            // teardown() below both removes the view and destroys the owner.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            // The window is now focusable (FLAG_NOT_FOCUSABLE removed below) so
            // it can actually receive the back key - touch dispatch already
            // worked without this, but key/focus delivery is a separate path
            // that FLAG_NOT_FOCUSABLE blocks entirely. Compose's own
            // BackHandler can't help here: there's no OnBackPressedDispatcher
            // on a raw WindowManager-added window, so the key has to be
            // intercepted directly.
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    Log.d(TAG, "onKey: KEYCODE_BACK intercepted - dismissing overlay via back-key path")
                    dismiss()
                    true
                } else {
                    false
                }
            }
            setContent {
                CallEndOverlayCard(
                    displayName = displayName,
                    phoneNumber = phoneNumber,
                    callType = callType,
                    durationMs = durationMs,
                    nativeAd = CallEndAdManager.nativeAdState.value,
                    unreadCount = unreadCountState.value,
                    onMessage = { openChat(context, phoneNumber) },
                    onCallBack = { callBack(context, phoneNumber) },
                    onSave = { saveContact(context, phoneNumber) },
                    onBlock = { blockNumber(context, phoneNumber) },
                    onOpenApp = { openApp(context) },
                    onDismiss = { dismiss() },
                    onCopyNumber = { copyNumber(context, phoneNumber) },
                    onViewContact = { viewContact(context, phoneNumber) },
                    onReportSpam = { reportSpam(context, phoneNumber) }
                )
            }
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        // No FLAG_NOT_FOCUSABLE: that flag blocks all key event delivery to
        // this window (not just touch), which is why the physical back button
        // used to pass through to whatever was behind the overlay instead of
        // reaching it. This overlay only ever shows post-call (see
        // CallStateListener), never while a call is actually in progress, so
        // becoming focusable here doesn't compete with the in-call UI.
        //
        // FLAG_LAYOUT_NO_LIMITS + FLAG_LAYOUT_INSET_DECOR let this window
        // extend under the status bar and navigation bar instead of being
        // inset to the normal content area - without them MATCH_PARENT still
        // stops short of the real screen edges, leaving whatever's behind
        // visible in those gaps. FLAG_NOT_TOUCH_MODAL keeps this window from
        // swallowing touches meant for other overlay windows this app may
        // show elsewhere.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        Log.d(
            TAG,
            "addView: width=${params.width} height=${params.height} " +
                "(MATCH_PARENT=${WindowManager.LayoutParams.MATCH_PARENT}) type=${params.type} flags=${params.flags}"
        )

        try {
            wm.addView(composeView, params)
            overlayView = composeView
            Log.d(TAG, "addView: succeeded - overlay is now showing, no exception thrown")
            val focusRequested = composeView.requestFocus()
            Log.d(TAG, "addView: requestFocus() called - focusRequested=$focusRequested")
            registerCloseSystemDialogsReceiver(context)
        } catch (e: Exception) {
            Log.e(TAG, "addView: failed to add overlay view - ${e.javaClass.name}: ${e.message}", e)
            teardown()
        }
    }

    // Fires on Home press, recent-apps switch, and notification-shade
    // pulldown alike - all three mean the user is navigating away from the
    // call-end context, so dismissing in every case is correct, not just
    // Home specifically.
    private fun registerCloseSystemDialogsReceiver(context: Context) {

        if (closeSystemDialogsReceiver != null) {
            Log.d(TAG, "registerCloseSystemDialogsReceiver: already registered - skipping duplicate registration")
            return
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                Log.d(
                    TAG,
                    "CloseSystemDialogsReceiver.onReceive: ACTION_CLOSE_SYSTEM_DIALOGS fired " +
                        "(reason=${intent.getStringExtra("reason")}) - dismissing overlay"
                )
                dismiss()
            }
        }

        try {
            val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            closeSystemDialogsReceiver = receiver
            registeredReceiverContext = context
            Log.d(TAG, "registerCloseSystemDialogsReceiver: registered for ACTION_CLOSE_SYSTEM_DIALOGS")
        } catch (e: Exception) {
            Log.e(TAG, "registerCloseSystemDialogsReceiver: failed to register - ${e.javaClass.name}: ${e.message}", e)
        }
    }

    // Idempotent by construction: nulls both fields once handled, so a
    // second call (e.g. dismiss() reached again via another path before a
    // fresh show()) sees null and no-ops instead of double-unregistering.
    private fun unregisterCloseSystemDialogsReceiver() {

        val receiver = closeSystemDialogsReceiver
        val ctx = registeredReceiverContext

        if (receiver == null || ctx == null) {
            Log.d(TAG, "unregisterCloseSystemDialogsReceiver: nothing registered - skipping")
            return
        }

        try {
            ctx.unregisterReceiver(receiver)
            Log.d(TAG, "unregisterCloseSystemDialogsReceiver: unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "unregisterCloseSystemDialogsReceiver: failed to unregister - ${e.javaClass.name}: ${e.message}", e)
        }

        closeSystemDialogsReceiver = null
        registeredReceiverContext = null
    }

    // Missing number is the common case, not the exception - CALLEND_DEBUG
    // logs confirm incoming_number is blank on API 29+ without READ_CALL_LOG
    // (see CallStateListener's class doc). Every action below checks for it
    // up front and bails with a toast rather than silently no-op'ing or
    // crashing on a null Uri/tel: string.
    private const val NUMBER_UNAVAILABLE_MESSAGE = "Number not available"

    private fun openChat(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Log.d(TAG, "openChat() called (Message action) - number unavailable, toast + no-op")
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "openChat() called (Message action) - number=$phoneNumber - launches MainActivity")
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_chat_sender", phoneNumber)
            putExtra("open_chat_autofocus", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
        dismiss()
    }

    // ACTION_CALL when CALL_PHONE is actually granted (it's declared in the
    // manifest but never requested at runtime anywhere in the app today, so
    // in practice it's usually NOT granted) - ACTION_DIAL otherwise, same
    // pattern ContactInfoScreen/ChatScreen use for their "Call" buttons. The
    // try/catch is a second safety net in case the permission is revoked
    // between the check and the call.
    private fun callBack(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Log.d(TAG, "callBack() called (Call back action) - number unavailable, toast + no-op")
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL

        Log.d(TAG, "callBack() called (Call back action) - number=$phoneNumber hasCallPermission=$hasCallPermission - launching $action")

        try {
            context.startActivity(
                Intent(action).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "callBack(): $action denied at call time (${e.message}) - falling back to ACTION_DIAL")
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
            Log.d(TAG, "saveContact() called (Save action) - number unavailable, toast + no-op")
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "saveContact() called (Save action) - number=$phoneNumber - launches Contacts insert screen")
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        dismiss()
    }

    // Same SmsRepository.blockThread() the Archived/Blocked screen's own
    // unblock path writes through (PreferenceManager.blockNumber +
    // ThreadDao.setBlockedForNumber) - BlockedNumbersScreen's list is a Room
    // Flow keyed off that same column, so a number blocked here shows up
    // there immediately, no separate table needed. Calling
    // PreferenceManager.blockNumber() directly instead (skipping blockThread)
    // would actually be a regression: BlockedNumbersScreen reads
    // ThreadDao.getBlockedThreadsFlow(), not the preference set directly, so
    // a number would silently vanish from that screen's list.
    private fun blockNumber(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Log.d(TAG, "blockNumber() called (Block action) - number unavailable, toast + no-op")
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "blockNumber() called (Block action) - number=$phoneNumber - blocking then dismissing")
        ownerScope.launch {
            SmsRepository.blockThread(context, phoneNumber)
        }
        dismiss()
    }

    private fun copyNumber(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Log.d(TAG, "copyNumber() called (kebab: Copy number) - number unavailable, toast + no-op")
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "copyNumber() called (kebab: Copy number) - number=$phoneNumber")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", phoneNumber))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun viewContact(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            Log.d(TAG, "viewContact() called (kebab: View contact) - number unavailable, toast + no-op")
            Toast.makeText(context, NUMBER_UNAVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }

        val lookupUri = getContactLookupUri(context, phoneNumber)
        if (lookupUri == null) {
            Log.d(TAG, "viewContact() called (kebab: View contact) - number=$phoneNumber - no matching contact, toast + no-op")
            Toast.makeText(context, "Contact not found", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "viewContact() called (kebab: View contact) - number=$phoneNumber lookupUri=$lookupUri - launching ACTION_VIEW")
        context.startActivity(
            Intent(Intent.ACTION_VIEW, lookupUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        dismiss()
    }

    // Placeholder only - no backend/network call exists yet for spam
    // reporting. Logs the report so the tap is verifiable in logcat, and
    // still gives feedback via toast even without a resolved number (this
    // item has no visibility gate tied to phoneNumber, unlike Copy/View
    // above, so it's expected to work with a null number too).
    private fun reportSpam(context: Context, phoneNumber: String?) {
        Log.d(TAG, "reportSpam() called (kebab: Report as spam) - number=${describeNumber(phoneNumber)} - reported (log-only, no backend yet)")
        Toast.makeText(context, "Reported, thanks", Toast.LENGTH_SHORT).show()
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
            Log.e(TAG, "getContactLookupUri: query failed - ${e.javaClass.name}: ${e.message}", e)
            null
        }
    }

    private fun openApp(context: Context) {
        Log.d(TAG, "openApp() called (engagement banner tap) - launches MainActivity")
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
        dismiss()
    }

    private fun dismiss() {
        Log.d(TAG, "dismiss() called - removing overlay")
        teardown()
    }

    private fun teardown() {

        unregisterCloseSystemDialogsReceiver()

        val wm = windowManager
        val view = overlayView

        if (wm != null && view != null) {
            try {
                wm.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "teardown: failed to remove overlay view - ${e.javaClass.name}: ${e.message}", e)
            }
        }
        overlayView = null
        windowManager = null

        lifecycleOwner?.destroy()
        lifecycleOwner = null

        CallEndAdManager.destroy()
    }
}

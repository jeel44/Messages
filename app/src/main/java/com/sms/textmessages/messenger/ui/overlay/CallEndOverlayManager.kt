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
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sms.textmessages.messenger.MainActivity
import com.sms.textmessages.messenger.data.db.AppDatabase
import com.sms.textmessages.messenger.receiver.CallEndType
import com.sms.textmessages.messenger.service.OverlayHostService
import com.sms.textmessages.messenger.ui.ads.CallEndAdManager
import com.sms.textmessages.messenger.ui.home.SmsRepository
import com.sms.textmessages.messenger.ui.home.getContactName
import com.sms.textmessages.messenger.utils.OverlayPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Hosts the post-call overlay's show()/dismiss() logic, driven by
// OverlayHostService - the one persistent foreground service both this and
// CategoryOverlayManager attach to. OverlayHostService.onStartCommand()
// calls show() below, passing itself as the Context/LifecycleOwner/
// SavedStateRegistryOwner/ViewModelStoreOwner the ComposeView needs, so
// there's no manual lifecycle to drive by hand here anymore (that used to
// be OverlayLifecycleOwner, a stand-in needed only because the old
// architecture had no Service to provide those for free).
//
// This previously called WindowManager.addView() directly from
// CallStateListener.onReceive() with no Service involved at all, because
// starting a foreground Service from that broadcast was itself rejected:
// android.intent.action.PHONE_STATE is not on the small allowlist of
// broadcasts exempt from Android 12+'s "can't start a foreground service
// from a background broadcast receiver" restriction. That's still true, but
// no longer matters - OverlayHostService is never started fresh from
// CallStateListener's receiver. It's already running (started from a
// foreground context), so CallStateListener only ever redelivers into an
// existing, already-foregrounded service instance, which isn't subject to
// that restriction. See OverlayHostService.showCallEndOverlay().
object CallEndOverlayManager {

    private const val TAG = "CALLEND_DEBUG"

    private val ownerScope = CoroutineScope(Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    // Dynamically registered (never manifest-declared - ACTION_CLOSE_SYSTEM_DIALOGS
    // is restricted to dynamic receivers since Android 12) while the overlay is
    // showing, so Home/recents/notification-shade pulldown dismisses it instead
    // of leaving it floating with no Activity lifecycle to catch that itself.
    // registeredReceiverContext is kept alongside the receiver purely so
    // teardown() (called from several dismiss paths with no context param of
    // its own) has something to unregister with.
    private var closeSystemDialogsReceiver: BroadcastReceiver? = null
    private var registeredReceiverContext: Context? = null

    fun show(service: OverlayHostService, callType: CallEndType, phoneNumber: String?, durationMs: Long) {

        val canDraw = OverlayPermission.canDrawOverlays(service)
        val numberDesc = describeNumber(phoneNumber)
        Log.d(TAG, "show(): canDrawOverlays()=$canDraw callType=$callType phoneNumber=$numberDesc durationMs=$durationMs")

        if (!canDraw) {
            Log.w(TAG, "show(): aborted - overlay permission not granted")
            return
        }

        teardown()
        showOverlay(service, callType, phoneNumber, durationMs)
    }

    private fun describeNumber(number: String?): String = number?.ifEmpty { "empty" } ?: "null"

    private fun showOverlay(service: OverlayHostService, callType: CallEndType, phoneNumber: String?, durationMs: Long) {

        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val contactLookupResult = phoneNumber?.let { getContactName(service, it) }
        val resolvedContactName = contactLookupResult?.takeIf { it.isNotBlank() }
        val displayName = resolvedContactName ?: phoneNumber ?: "Unknown"

        Log.d(
            TAG,
            "showOverlay: rawPhoneNumber=${describeNumber(phoneNumber)} " +
                "getContactName()=${if (contactLookupResult == null) "not queried (number null)" else contactLookupResult.ifEmpty { "empty (no contact match)" }} " +
                "displayName=$displayName"
        )

        CallEndAdManager.loadNative(service)

        val unreadCountState = mutableStateOf<Int?>(null)
        ownerScope.launch {
            unreadCountState.value = AppDatabase.getDatabase(service).threadDao().getUnreadThreadCount()
        }

        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(service)
            setViewTreeViewModelStoreOwner(service)
            // The service (and thus its lifecycle) outlives any single overlay
            // show/dismiss cycle, unlike an Activity/Fragment tree - so disposal
            // has to be tied to this view leaving the window, not to the owner
            // ever reaching ON_DESTROY (it won't, until the service itself dies).
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
                    onMessage = { openChat(service, phoneNumber) },
                    onCallBack = { callBack(service, phoneNumber) },
                    onSave = { saveContact(service, phoneNumber) },
                    onBlock = { blockNumber(service, phoneNumber) },
                    onOpenApp = { openApp(service) },
                    onDismiss = { dismiss() },
                    onCopyNumber = { copyNumber(service, phoneNumber) },
                    onViewContact = { viewContact(service, phoneNumber) },
                    onReportSpam = { reportSpam(service, phoneNumber) }
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
            registerCloseSystemDialogsReceiver(service)
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

        CallEndAdManager.destroy()
    }
}

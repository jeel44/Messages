package com.sms.textmessages.messenger.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch

// Full-screen post-call card, shown by CallStateListener the moment a call
// ends. Same WindowManager-attached-ComposeView shape as CategoryOverlayService
// (manual Lifecycle/SavedStateRegistry/ViewModelStore owners, since a bare
// Service provides none of the three ComposeView needs) but MATCH_PARENT
// instead of a top banner, since this replaces the whole screen rather than
// popping a notification-style card over it.
class CallEndOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val dismissHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (!OverlayPermission.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val callTypeName = intent?.getStringExtra(EXTRA_CALL_TYPE)
        val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER)
        val durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, 0L) ?: 0L

        val callType = callTypeName?.let { name ->
            runCatching { CallEndType.valueOf(name) }.getOrNull()
        }

        if (callType == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        removeOverlay()
        showOverlay(callType, phoneNumber, durationMs)

        return START_NOT_STICKY
    }

    private fun showOverlay(callType: CallEndType, phoneNumber: String?, durationMs: Long) {

        val resolvedContactName = phoneNumber?.let { getContactName(this, it) }?.takeIf { it.isNotBlank() }
        val displayName = resolvedContactName ?: phoneNumber ?: "Unknown"

        CallEndAdManager.loadNative(this)

        val unreadCountState = mutableStateOf<Int?>(null)
        lifecycleScope.launch {
            unreadCountState.value = unreadMessageCount()
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@CallEndOverlayService)
            setViewTreeSavedStateRegistryOwner(this@CallEndOverlayService)
            setViewTreeViewModelStoreOwner(this@CallEndOverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CallEndOverlayCard(
                    displayName = displayName,
                    phoneNumber = phoneNumber,
                    callType = callType,
                    durationMs = durationMs,
                    nativeAd = CallEndAdManager.nativeAdState.value,
                    unreadCount = unreadCountState.value,
                    onMessage = { openChat(phoneNumber) },
                    onCallBack = { callBack(phoneNumber) },
                    onSave = { saveContact(phoneNumber) },
                    onBlock = { blockNumber(phoneNumber) },
                    onOpenApp = { openApp() },
                    onDismiss = { dismiss() }
                )
            }
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(composeView, params)
            overlayView = composeView
        } catch (e: Exception) {
            Log.e("CALL_END_OVERLAY", "Failed to add overlay view", e)
            stopSelf()
            return
        }

        dismissRunnable = Runnable { dismiss() }.also {
            dismissHandler.postDelayed(it, AUTO_DISMISS_MS)
        }
    }

    // Same archived=0/blocked=0/isRead=0 count InboxUI's own thread list is
    // built from (ThreadDao.getThreadsFlow), so this always matches what's
    // shown bolded there - see ThreadDao.getUnreadThreadCount().
    private suspend fun unreadMessageCount(): Int {
        return AppDatabase.getDatabase(this).threadDao().getUnreadThreadCount()
    }

    private fun openChat(phoneNumber: String?) {
        if (phoneNumber != null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("open_chat_sender", phoneNumber)
                putExtra("open_chat_autofocus", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }
        dismiss()
    }

    // ACTION_DIAL (not ACTION_CALL) - same pattern ContactInfoScreen/ChatScreen
    // already use for their "Call" buttons. Opens the dialer pre-filled rather
    // than placing the call directly, so it needs no CALL_PHONE runtime grant.
    private fun callBack(phoneNumber: String?) {
        if (phoneNumber != null) {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
        dismiss()
    }

    private fun saveContact(phoneNumber: String?) {
        if (phoneNumber != null) {
            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
        dismiss()
    }

    // Same SmsRepository.blockThread() the Archived/Blocked screen's own
    // unblock path writes through (PreferenceManager.blockNumber +
    // ThreadDao.setBlockedForNumber) - BlockedNumbersScreen's list is a Room
    // Flow keyed off that same column, so a number blocked here shows up
    // there immediately, no separate table needed.
    private fun blockNumber(phoneNumber: String?) {
        if (phoneNumber != null) {
            lifecycleScope.launch {
                SmsRepository.blockThread(this@CallEndOverlayService, phoneNumber)
            }
        }
        dismiss()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        dismiss()
    }

    private fun dismiss() {
        removeOverlay()
        stopSelf()
    }

    private fun removeOverlay() {
        dismissRunnable?.let { dismissHandler.removeCallbacks(it) }
        dismissRunnable = null
        try {
            overlayView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e("CALL_END_OVERLAY", "Failed to remove overlay view", e)
        }
        overlayView = null
    }

    override fun onDestroy() {
        removeOverlay()
        CallEndAdManager.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_DURATION_MS = "extra_duration_ms"

        // Longer than CategoryOverlayService's 5s banner auto-dismiss - this
        // is a full-screen card the user needs a moment to actually read and
        // act on, not a transient notification popup.
        private const val AUTO_DISMISS_MS = 8000L

        fun start(context: Context, callType: CallEndType, phoneNumber: String?, durationMs: Long) {
            if (!OverlayPermission.canDrawOverlays(context)) return
            val intent = Intent(context, CallEndOverlayService::class.java).apply {
                putExtra(EXTRA_CALL_TYPE, callType.name)
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            context.startService(intent)
        }
    }
}

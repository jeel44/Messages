package com.sms.textmessages.messenger.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sms.textmessages.messenger.MainActivity
import com.sms.textmessages.messenger.receiver.CopyOtpReceiver
import com.sms.textmessages.messenger.receiver.NotificationActionReceiver
import com.sms.textmessages.messenger.receiver.NotificationCategory
import com.sms.textmessages.messenger.ui.home.getContactName
import com.sms.textmessages.messenger.utils.OverlayPermission

// Custom WindowManager-attached popup that is the only visible alert for a
// classified SMS - no system notification is posted. See the 5-category
// design in CategoryOverlayCard.
//
// Its own plain (never foregrounded) LifecycleService, started via
// context.startService() from SmsReceiver. SMS_DELIVER is exempt from
// Android 12+'s background-FGS-start restriction, and this never needs FGS
// promotion anyway since it only draws a SYSTEM_ALERT_WINDOW overlay - a
// running Service is only needed here to provide the Lifecycle/
// SavedStateRegistry/ViewModelStore owners ComposeView requires, and to
// outlive the single onReceive() call long enough to show/auto-dismiss the
// card. Independent of the after-call Activities (CallEndPopupActivity /
// CallEndFullscreenActivity) — those never use WindowManager overlays.
class CategoryOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val dismissHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    // TEMPORARY DEBUG LOGGING - diagnosing the fast-dismiss bug (overlay
    // disappearing after ~1-2s instead of the coded AUTO_DISMISS_MS=5000).
    // Set when addView() succeeds, cleared on every teardown path, so every
    // dismiss()/removeOverlay() log line can report actual elapsed time
    // instead of relying on logcat wall-clock diffing across processes.
    private var shownAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: ts=${System.currentTimeMillis()}")
        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (!OverlayPermission.canDrawOverlays(this)) {
            Log.w(TAG, "onStartCommand: aborted - overlay permission not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        val categoryName = intent?.getStringExtra(EXTRA_CATEGORY)
        val sender = intent?.getStringExtra(EXTRA_SENDER)
        val body = intent?.getStringExtra(EXTRA_BODY) ?: ""
        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1) ?: -1

        if (categoryName == null || sender == null) {
            Log.w(TAG, "onStartCommand: missing EXTRA_CATEGORY/EXTRA_SENDER - stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        val category = runCatching { NotificationCategory.valueOf(categoryName) }
            .getOrDefault(NotificationCategory.SERVICE_DEFAULT)

        Log.d(TAG, "onStartCommand: ts=${System.currentTimeMillis()} category=$category sender=$sender notificationId=$notificationId")

        removeOverlay(reason = "onStartCommand-replacing-previous")
        showOverlay(category, sender, body, notificationId)

        return START_NOT_STICKY
    }

    private fun showOverlay(category: NotificationCategory, sender: String, body: String, notificationId: Int) {

        val displayName = getContactName(this, sender)

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@CategoryOverlayService)
            setViewTreeSavedStateRegistryOwner(this@CategoryOverlayService)
            setViewTreeViewModelStoreOwner(this@CategoryOverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CategoryOverlayCard(
                    category = category,
                    senderName = displayName,
                    messageBody = body,
                    onAction1 = { runAction1(category, sender, body, notificationId) },
                    onAction2 = { runAction2(category, sender, notificationId) },
                    onDismiss = { dismiss(notificationId, cancelSystemNotification = false, reason = "composable-onDismiss") }
                )
            }
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val displayMetrics = resources.displayMetrics
        val margin = (16 * displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            displayMetrics.widthPixels - (margin * 2),
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (24 * displayMetrics.density).toInt()
        }

        try {
            windowManager.addView(composeView, params)
            overlayView = composeView
            shownAtMs = System.currentTimeMillis()
            Log.d(TAG, "showOverlay: ts=$shownAtMs addView succeeded - overlay is now showing (notificationId=$notificationId)")
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay: failed to add overlay view - ${e.javaClass.name}: ${e.message}", e)
            stopSelf()
            return
        }

        dismissRunnable = Runnable { dismiss(notificationId, cancelSystemNotification = false, reason = "auto-dismiss-timer") }.also {
            dismissHandler.postDelayed(it, AUTO_DISMISS_MS)
            Log.d(TAG, "showOverlay: ts=${System.currentTimeMillis()} auto-dismiss timer scheduled, fires in ${AUTO_DISMISS_MS}ms at target ts=${shownAtMs + AUTO_DISMISS_MS}")
        }
    }

    private fun runAction1(category: NotificationCategory, sender: String, body: String, notificationId: Int) {
        when (category) {
            NotificationCategory.PERSONAL -> openChat(sender, autoFocus = true)
            NotificationCategory.OTP -> copyOtp(body, notificationId)
            NotificationCategory.OFFER,
            NotificationCategory.TRANSACTION_DEBIT,
            NotificationCategory.TRANSACTION_CREDIT,
            NotificationCategory.SERVICE_DEFAULT -> openChat(sender, autoFocus = false)
        }
        dismiss(notificationId, cancelSystemNotification = category == NotificationCategory.OTP, reason = "action1:$category")
    }

    private fun runAction2(category: NotificationCategory, sender: String, notificationId: Int) {
        when (category) {
            NotificationCategory.PERSONAL -> markAsRead(sender, notificationId)
            NotificationCategory.OFFER -> muteSender(sender, notificationId)
            else -> Unit
        }
        dismiss(notificationId, cancelSystemNotification = true, reason = "action2:$category")
    }

    private fun openChat(sender: String, autoFocus: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_chat_sender", sender)
            putExtra("open_chat_autofocus", autoFocus)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun copyOtp(body: String, notificationId: Int) {
        sendBroadcast(Intent(this, CopyOtpReceiver::class.java).apply {
            action = CopyOtpReceiver.ACTION_COPY_OTP
            putExtra(CopyOtpReceiver.EXTRA_MESSAGE_BODY, body)
            putExtra(CopyOtpReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        })
    }

    private fun markAsRead(sender: String, notificationId: Int) {
        sendBroadcast(Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            putExtra(NotificationActionReceiver.EXTRA_SENDER, sender)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        })
    }

    private fun muteSender(sender: String, notificationId: Int) {
        sendBroadcast(Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MUTE_SENDER
            putExtra(NotificationActionReceiver.EXTRA_SENDER, sender)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        })
    }

    private fun dismiss(notificationId: Int, cancelSystemNotification: Boolean, reason: String) {
        val now = System.currentTimeMillis()
        val elapsedSinceShowMs = if (shownAtMs != 0L) now - shownAtMs else -1
        Log.d(
            TAG,
            "dismiss(): ts=$now reason=$reason notificationId=$notificationId " +
                "cancelSystemNotification=$cancelSystemNotification elapsedSinceShowMs=$elapsedSinceShowMs " +
                "(coded AUTO_DISMISS_MS=$AUTO_DISMISS_MS)"
        )
        if (cancelSystemNotification && notificationId != -1) {
            sendBroadcast(Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DISMISS
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            })
        }
        removeOverlay(reason = reason)
        stopSelf()
    }

    private fun removeOverlay(reason: String) {
        val hadPendingTimer = dismissRunnable != null
        Log.d(TAG, "removeOverlay: ts=${System.currentTimeMillis()} reason=$reason hadPendingDismissTimer=$hadPendingTimer")
        dismissRunnable?.let { dismissHandler.removeCallbacks(it) }
        dismissRunnable = null
        try {
            overlayView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "removeOverlay: failed to remove overlay view - ${e.javaClass.name}: ${e.message}", e)
        }
        overlayView = null
        shownAtMs = 0L
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: ts=${System.currentTimeMillis()}")
        removeOverlay(reason = "onDestroy")
        super.onDestroy()
    }

    companion object {

        private const val TAG = "CATEGORY_OVERLAY"

        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val AUTO_DISMISS_MS = 10000L

        fun start(context: Context, category: NotificationCategory, sender: String, body: String, notificationId: Int) {
            if (!OverlayPermission.canDrawOverlays(context)) {
                Log.w(TAG, "start: aborted - overlay permission not granted")
                return
            }
            val intent = Intent(context, CategoryOverlayService::class.java).apply {
                putExtra(EXTRA_CATEGORY, category.name)
                putExtra(EXTRA_SENDER, sender)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            context.startService(intent)
        }
    }
}

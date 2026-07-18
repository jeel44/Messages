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
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sms.textmessages.messenger.MainActivity
import com.sms.textmessages.messenger.receiver.CopyOtpReceiver
import com.sms.textmessages.messenger.receiver.NotificationActionReceiver
import com.sms.textmessages.messenger.receiver.NotificationCategory
import com.sms.textmessages.messenger.service.OverlayHostService
import com.sms.textmessages.messenger.ui.home.getContactName
import com.sms.textmessages.messenger.utils.OverlayPermission

// Custom WindowManager-attached popup that is the only visible alert for a
// classified SMS - no system notification is posted. See the 5-category
// design in CategoryOverlayCard.
//
// This used to be CategoryOverlayService, its own plain (never foregrounded)
// LifecycleService started via context.startService() from SmsReceiver.
// SMS_DELIVER is exempt from Android 12+'s background-FGS-start restriction,
// so that start call itself never failed - but the service still wasn't
// protected from Android 8+'s background-service execution limits once
// SmsReceiver's own exemption window closed, so the OS could (and, per the
// "early-dismiss bug" this replaces, did) reclaim the process shortly after
// the overlay was shown. Restructured to the same shape as
// CallEndOverlayManager: a plain object whose show()/dismiss() logic runs
// against OverlayHostService, the one persistent foreground service both
// overlay types now attach to, so the overlay's process is protected for as
// long as the overlay (and the app) needs it to be.
object CategoryOverlayManager {

    private const val TAG = "CATEGORY_OVERLAY"

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private val dismissHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    fun show(service: OverlayHostService, category: NotificationCategory, sender: String, body: String, notificationId: Int) {

        if (!OverlayPermission.canDrawOverlays(service)) {
            Log.w(TAG, "show(): aborted - overlay permission not granted")
            return
        }

        Log.d(TAG, "show(): category=$category sender=$sender notificationId=$notificationId")

        removeOverlay()
        showOverlay(service, category, sender, body, notificationId)
    }

    private fun showOverlay(
        service: OverlayHostService,
        category: NotificationCategory,
        sender: String,
        body: String,
        notificationId: Int
    ) {

        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val displayName = getContactName(service, sender)

        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(service)
            setViewTreeViewModelStoreOwner(service)
            // service outlives any single overlay show/dismiss cycle, so
            // disposal is tied to this view leaving the window rather than to
            // the owner's lifecycle ever reaching ON_DESTROY.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                CategoryOverlayCard(
                    category = category,
                    senderName = displayName,
                    messageBody = body,
                    onAction1 = { runAction1(service, category, sender, body, notificationId) },
                    onAction2 = { runAction2(service, category, sender, notificationId) },
                    onDismiss = { dismiss(service, notificationId, cancelSystemNotification = false) }
                )
            }
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val displayMetrics = service.resources.displayMetrics
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
            wm.addView(composeView, params)
            overlayView = composeView
            Log.d(TAG, "showOverlay: addView succeeded - overlay is now showing")
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay: failed to add overlay view - ${e.javaClass.name}: ${e.message}", e)
            removeOverlay()
            return
        }

        dismissRunnable = Runnable { dismiss(service, notificationId, cancelSystemNotification = false) }.also {
            dismissHandler.postDelayed(it, AUTO_DISMISS_MS)
        }
    }

    private fun runAction1(service: OverlayHostService, category: NotificationCategory, sender: String, body: String, notificationId: Int) {
        when (category) {
            NotificationCategory.PERSONAL -> openChat(service, sender, autoFocus = true)
            NotificationCategory.OTP -> copyOtp(service, body, notificationId)
            NotificationCategory.OFFER,
            NotificationCategory.TRANSACTION_DEBIT,
            NotificationCategory.TRANSACTION_CREDIT,
            NotificationCategory.SERVICE_DEFAULT -> openChat(service, sender, autoFocus = false)
        }
        dismiss(service, notificationId, cancelSystemNotification = category == NotificationCategory.OTP)
    }

    private fun runAction2(service: OverlayHostService, category: NotificationCategory, sender: String, notificationId: Int) {
        when (category) {
            NotificationCategory.PERSONAL -> markAsRead(service, sender, notificationId)
            NotificationCategory.OFFER -> muteSender(service, sender, notificationId)
            else -> Unit
        }
        dismiss(service, notificationId, cancelSystemNotification = true)
    }

    private fun openChat(service: OverlayHostService, sender: String, autoFocus: Boolean) {
        val intent = Intent(service, MainActivity::class.java).apply {
            putExtra("open_chat_sender", sender)
            putExtra("open_chat_autofocus", autoFocus)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        service.startActivity(intent)
    }

    private fun copyOtp(service: OverlayHostService, body: String, notificationId: Int) {
        service.sendBroadcast(Intent(service, CopyOtpReceiver::class.java).apply {
            action = CopyOtpReceiver.ACTION_COPY_OTP
            putExtra(CopyOtpReceiver.EXTRA_MESSAGE_BODY, body)
            putExtra(CopyOtpReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        })
    }

    private fun markAsRead(service: OverlayHostService, sender: String, notificationId: Int) {
        service.sendBroadcast(Intent(service, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            putExtra(NotificationActionReceiver.EXTRA_SENDER, sender)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        })
    }

    private fun muteSender(service: OverlayHostService, sender: String, notificationId: Int) {
        service.sendBroadcast(Intent(service, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MUTE_SENDER
            putExtra(NotificationActionReceiver.EXTRA_SENDER, sender)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        })
    }

    private fun dismiss(service: OverlayHostService, notificationId: Int, cancelSystemNotification: Boolean) {
        Log.d(TAG, "dismiss(): notificationId=$notificationId cancelSystemNotification=$cancelSystemNotification")
        if (cancelSystemNotification && notificationId != -1) {
            service.sendBroadcast(Intent(service, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DISMISS
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            })
        }
        removeOverlay()
    }

    private fun removeOverlay() {
        dismissRunnable?.let { dismissHandler.removeCallbacks(it) }
        dismissRunnable = null
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "removeOverlay: failed to remove overlay view - ${e.javaClass.name}: ${e.message}", e)
        }
        overlayView = null
        windowManager = null
    }

    private const val AUTO_DISMISS_MS = 5000L
}

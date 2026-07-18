package com.sms.textmessages.messenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.receiver.CallEndType
import com.sms.textmessages.messenger.receiver.NotificationCategory
import com.sms.textmessages.messenger.ui.overlay.CallEndOverlayManager
import com.sms.textmessages.messenger.ui.overlay.CategoryOverlayManager

// The one persistent, foregrounded, process-keeping-alive service both
// overlay types (post-call and SMS-category) attach to.
//
// This replaces the old reactive model, where each overlay only came alive
// when its own broadcast fired: a BroadcastReceiver.onReceive() returning
// with no other active component does not protect the process, and Android
// can (and, per CALLEND_DEBUG logs, did) kill it mid-overlay - "PROCESS
// ENDED" was logged less than a second after a successful addView(),
// stranding the overlay on screen until a stray back-press cleaned it up.
// A genuinely-foreground Service is what the OOM killer respects, so this
// service is started once from a foreground context (App.onCreate() on a
// normal user launch, or BootCompletedReceiver after a reboot) and then just
// stays up. CallStateListener/SmsReceiver route into it via onStartCommand()
// instead of ever trying to start something new themselves - by the time
// either of those broadcasts fires, this service (and the lifecycle owner it
// provides both overlay managers) already exists.
class OverlayHostService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        Log.d(TAG, "OverlayHostService.onCreate: starting foreground")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "OverlayHostService.onCreate: foreground started - process is now protected from the OOM killer")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {

            ACTION_SHOW_CALL_END -> {

                val callType = intent.getStringExtra(EXTRA_CALL_TYPE)
                    ?.let { runCatching { CallEndType.valueOf(it) }.getOrNull() }
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
                val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)

                Log.d(
                    TAG,
                    "OverlayHostService.onStartCommand: ACTION_SHOW_CALL_END callType=$callType " +
                        "phoneNumber=${phoneNumber ?: "null"} durationMs=$durationMs"
                )

                if (callType != null) {
                    CallEndOverlayManager.show(this, callType, phoneNumber, durationMs)
                } else {
                    Log.w(TAG, "OverlayHostService.onStartCommand: missing/invalid EXTRA_CALL_TYPE - ignoring")
                }
            }

            ACTION_SHOW_CATEGORY -> {

                val category = intent.getStringExtra(EXTRA_CATEGORY)
                    ?.let { runCatching { NotificationCategory.valueOf(it) }.getOrNull() }
                val sender = intent.getStringExtra(EXTRA_SENDER)
                val body = intent.getStringExtra(EXTRA_BODY) ?: ""
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

                Log.d(
                    TAG,
                    "OverlayHostService.onStartCommand: ACTION_SHOW_CATEGORY category=$category " +
                        "sender=$sender notificationId=$notificationId"
                )

                if (category != null && sender != null) {
                    CategoryOverlayManager.show(this, category, sender, body, notificationId)
                } else {
                    Log.w(TAG, "OverlayHostService.onStartCommand: missing EXTRA_CATEGORY/EXTRA_SENDER - ignoring")
                }
            }

            else -> {
                Log.d(TAG, "OverlayHostService.onStartCommand: no action (plain start/redelivery) - service is running")
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Background activity", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Keeps Messages running so call-end and message alerts can appear"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Messages is running")
            .setSmallIcon(R.drawable.ic_notif_message)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {

        private const val TAG = "CALLEND_DEBUG"

        private const val CHANNEL_ID = "overlay_host_service"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_SHOW_CALL_END = "com.sms.textmessages.messenger.action.SHOW_CALL_END_OVERLAY"
        private const val ACTION_SHOW_CATEGORY = "com.sms.textmessages.messenger.action.SHOW_CATEGORY_OVERLAY"

        private const val EXTRA_CALL_TYPE = "extra_call_type"
        private const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        private const val EXTRA_DURATION_MS = "extra_duration_ms"

        private const val EXTRA_CATEGORY = "extra_category"
        private const val EXTRA_SENDER = "extra_sender"
        private const val EXTRA_BODY = "extra_body"
        private const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        // Starts (or, if already running, harmlessly redelivers into) the
        // service. Safe to call unconditionally from a foreground context -
        // App.onCreate() during a normal user launch, or BootCompletedReceiver
        // right after boot (itself an exempted broadcast). Still wrapped
        // defensively: if this is ever somehow reached from a genuinely
        // backgrounded, non-exempted process state, the platform throws
        // rather than the app crashing outright.
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, OverlayHostService::class.java))
                Log.d(TAG, "OverlayHostService.start: startForegroundService() called")
            } catch (e: Exception) {
                Log.e(TAG, "OverlayHostService.start: failed - ${e.javaClass.name}: ${e.message}", e)
            }
        }

        // Routes a call-end overlay request into the service. Unlike
        // CallStateListener calling startForegroundService() directly on a
        // not-yet-running service (rejected: PHONE_STATE isn't on the FGS-start
        // exemption allowlist), this targets a service that is already running
        // and already foregrounded, so it only redelivers onStartCommand() to
        // the existing instance - no new foreground promotion is requested.
        fun showCallEndOverlay(context: Context, callType: CallEndType, phoneNumber: String?, durationMs: Long) {
            try {
                val intent = Intent(context, OverlayHostService::class.java).apply {
                    action = ACTION_SHOW_CALL_END
                    putExtra(EXTRA_CALL_TYPE, callType.name)
                    putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                    putExtra(EXTRA_DURATION_MS, durationMs)
                }
                ContextCompat.startForegroundService(context, intent)
                Log.d(TAG, "OverlayHostService.showCallEndOverlay: routed callType=$callType")
            } catch (e: Exception) {
                Log.e(TAG, "OverlayHostService.showCallEndOverlay: failed - ${e.javaClass.name}: ${e.message}", e)
            }
        }

        fun showCategoryOverlay(
            context: Context,
            category: NotificationCategory,
            sender: String,
            body: String,
            notificationId: Int
        ) {
            try {
                val intent = Intent(context, OverlayHostService::class.java).apply {
                    action = ACTION_SHOW_CATEGORY
                    putExtra(EXTRA_CATEGORY, category.name)
                    putExtra(EXTRA_SENDER, sender)
                    putExtra(EXTRA_BODY, body)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
                ContextCompat.startForegroundService(context, intent)
                Log.d(TAG, "OverlayHostService.showCategoryOverlay: routed category=$category sender=$sender")
            } catch (e: Exception) {
                Log.e(TAG, "OverlayHostService.showCategoryOverlay: failed - ${e.javaClass.name}: ${e.message}", e)
            }
        }
    }
}

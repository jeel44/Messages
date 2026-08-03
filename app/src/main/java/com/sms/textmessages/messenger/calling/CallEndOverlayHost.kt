package com.sms.textmessages.messenger.calling

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.sms.textmessages.messenger.ads.AdCache
import com.sms.textmessages.messenger.ads.AdPlacement
import com.sms.textmessages.messenger.ui.overlay.CallEndOverlayCard
import com.sms.textmessages.messenger.ui.theme.MessagesTheme
import com.sms.textmessages.messenger.utils.OverlayPermission
import com.sms.textmessages.messenger.utils.PreferenceManager

/**
 * After-call presentation:
 * 1) Prefer [CallEndPopupActivity] / [CallEndFullscreenActivity] (no blink).
 * 2) If MIUI/Android silently drops the background Activity start, fall back to
 *    a WindowManager overlay after a short grace period — never show both.
 */
object CallEndOverlayHost {

    private const val TAG = "CALLEND_DEBUG"
    private const val ACTIVITY_GRACE_MS = 450L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var hostContext: Context? = null
    private var currentEvent: CallEndEvent? = null
    private var pendingFullscreen = false

    /** Generation bumped on each launch; Activity must match to cancel overlay. */
    @Volatile
    private var launchGeneration = 0L

    /** Set by Activity.onCreate when it actually started. */
    @Volatile
    private var activityShownGeneration = 0L

    private var fallbackRunnable: Runnable? = null

    private val displayNameState = mutableStateOf("Unknown")
    private val phoneState = mutableStateOf<String?>(null)
    private val callTypeState = mutableStateOf(com.sms.textmessages.messenger.receiver.CallEndType.MISSED)
    private val durationState = mutableStateOf(0L)

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry
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

    fun isShowing(): Boolean = overlayView != null

    /**
     * Preferred path: start Activity immediately. Overlay only if Activity
     * never reaches onCreate (background start denied).
     */
    fun openActivityWithOverlayFallback(context: Context, event: CallEndEvent, fullscreen: Boolean) {
        val app = context.applicationContext
        val gen = SystemClock.elapsedRealtime()
        launchGeneration = gen
        activityShownGeneration = 0L
        currentEvent = event
        pendingFullscreen = fullscreen
        displayNameState.value = event.displayName
        phoneState.value = event.number
        callTypeState.value = event.type
        durationState.value = event.durationMs

        cancelFallback()
        // Truecaller hang-up: drop during-call bubble before after-call UI.
        DuringCallOverlayHost.dismiss()
        // Never leave a stale after-call overlay from a previous call.
        if (overlayView != null) {
            Log.d(TAG, "OverlayHost: dismissing stale overlay before new launch")
            dismissOverlayOnly()
        }

        Log.d(
            TAG,
            "OverlayHost: Activity-first gen=$gen fullscreen=$fullscreen " +
                "canDraw=${OverlayPermission.canDrawOverlays(app)} type=${event.type} number=${event.number}"
        )

        openActivity(app, event, fullscreen)

        if (!OverlayPermission.canDrawOverlays(app)) {
            Log.w(TAG, "OverlayHost: no overlay permission — Activity-only (may be blocked on MIUI)")
            return
        }

        val fallback = Runnable {
            if (activityShownGeneration == gen) {
                Log.d(TAG, "OverlayHost: Activity shown gen=$gen — skip overlay fallback")
                return@Runnable
            }
            Log.w(
                TAG,
                "OverlayHost: Activity did not start within ${ACTIVITY_GRACE_MS}ms " +
                    "(likely MIUI background block) — showing overlay fallback gen=$gen"
            )
            showOverlay(app, event)
        }
        fallbackRunnable = fallback
        mainHandler.postDelayed(fallback, ACTIVITY_GRACE_MS)
    }

    /** Called from Activity.onCreate when the Activity actually appeared. */
    fun onActivityShown() {
        val gen = launchGeneration
        activityShownGeneration = gen
        Log.d(TAG, "OverlayHost.onActivityShown gen=$gen — cancel overlay fallback")
        cancelFallback()
        if (overlayView != null) {
            dismissOverlayOnly()
        }
    }

    fun dismiss() {
        Log.d(TAG, "OverlayHost.dismiss")
        cancelFallback()
        dismissOverlayOnly()
        currentEvent = null
    }

    private fun cancelFallback() {
        fallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        fallbackRunnable = null
    }

    private fun dismissOverlayOnly() {
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
        hostContext = null
        AdCache.detachBanner(AdPlacement.CALL_END_BANNER)
    }

    private fun openActivity(context: Context, event: CallEndEvent, fullscreen: Boolean) {
        val target = if (fullscreen) {
            CallEndFullscreenActivity::class.java
        } else {
            CallEndPopupActivity::class.java
        }
        val intent = Intent(context, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CallEndContract.EXTRA_EVENT, event)
        }
        try {
            context.startActivity(intent)
            Log.d(TAG, "OverlayHost: startActivity(${target.simpleName}) requested")
        } catch (e: Exception) {
            Log.w(TAG, "OverlayHost: startActivity failed: ${e.message}")
        }
    }

    private fun showOverlay(context: Context, event: CallEndEvent) {
        if (overlayView != null) {
            Log.d(TAG, "OverlayHost: overlay already showing")
            return
        }
        // Race: Activity may have won just before this runs.
        if (activityShownGeneration == launchGeneration && launchGeneration != 0L) {
            Log.d(TAG, "OverlayHost: Activity already shown — abort overlay")
            return
        }

        val adsAllowed = !PreferenceManager.isPremiumSubscribed(context)
        AdCache.ensure(AdPlacement.CALL_END_BANNER, context)

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
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, keyEvent ->
                if (keyCode == KeyEvent.KEYCODE_BACK && keyEvent.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    true
                } else false
            }
            setContent {
                MessagesTheme {
                    CallEndOverlayCard(
                        displayName = displayNameState.value,
                        phoneNumber = phoneState.value,
                        callType = callTypeState.value,
                        durationMs = durationState.value,
                        bannerAd = if (adsAllowed) {
                            AdCache.bannerState(AdPlacement.CALL_END_BANNER).value
                        } else null,
                        showAdSlot = adsAllowed,
                        unreadCount = null,
                        onMessage = {
                            CallEndActions.openChat(context, phoneState.value)
                            dismiss()
                        },
                        onCallBack = {
                            CallEndActions.callBack(context, phoneState.value)
                            dismiss()
                        },
                        onSave = {
                            CallEndActions.saveContact(context, phoneState.value)
                            dismiss()
                        },
                        onBlock = {
                            CallEndActions.blockNumber(context, phoneState.value)
                            dismiss()
                        },
                        onOpenApp = {
                            CallEndActions.openApp(context)
                            dismiss()
                        },
                        onDismiss = { dismiss() },
                        onCopyNumber = { CallEndActions.copyNumber(context, phoneState.value) },
                        onViewContact = {
                            CallEndActions.viewContact(context, phoneState.value)
                            dismiss()
                        },
                        onReportSpam = {
                            CallEndActions.reportSpam(context, phoneState.value)
                            dismiss()
                        }
                    )
                }
            }
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            wm.addView(composeView, params)
            overlayView = composeView
            composeView.requestFocus()
            Log.d(TAG, "OverlayHost: addView OK (fallback) for ${event.type} ${event.number}")
        } catch (e: Exception) {
            Log.e(TAG, "OverlayHost: addView failed: ${e.message}", e)
            teardownOwnerOnly()
        }
    }

    private fun teardownOwnerOnly() {
        overlayView = null
        windowManager = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        hostContext = null
    }
}

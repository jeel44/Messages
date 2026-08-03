package com.sms.textmessages.messenger.calling

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
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
import com.sms.textmessages.messenger.service.CallOverlayForegroundService
import com.sms.textmessages.messenger.ui.home.getContactName
import com.sms.textmessages.messenger.ui.overlay.DuringCallOverlayCard
import com.sms.textmessages.messenger.ui.overlay.DuringCallPhase
import com.sms.textmessages.messenger.ui.theme.MessagesTheme
import com.sms.textmessages.messenger.utils.CallEndMetrics
import com.sms.textmessages.messenger.utils.OverlayPermission

/**
 * Truecaller-style during-call bubble. Shown on RINGING/OFFHOOK via WindowManager.
 * Dismissed on hang-up before the after-call Activity launches — never morphs
 * into the call-end card (that is always an Activity).
 */
object DuringCallOverlayHost {

    private const val TAG = "CALLEND_DEBUG"

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var hostContext: Context? = null

    private val displayNameState = mutableStateOf("Unknown")
    private val phoneState = mutableStateOf<String?>(null)
    private val phaseState = mutableStateOf(DuringCallPhase.RINGING)

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

    fun show(context: Context, phoneNumber: String?, phase: DuringCallPhase) {
        val app = context.applicationContext
        if (!OverlayPermission.canDrawOverlays(app)) {
            Log.w(TAG, "DuringCall: aborted - no overlay permission")
            CallEndMetrics.recordMiss(app, "during_no_overlay_perm")
            return
        }

        val contactLookup = phoneNumber?.let { getContactName(app, it) }
        val displayName = contactLookup?.takeIf { it.isNotBlank() && it != phoneNumber }
            ?: phoneNumber
            ?: "Unknown"

        phoneState.value = phoneNumber
        displayNameState.value = displayName
        phaseState.value = phase

        // Warm after-call banner while the user is still on the call.
        AdCache.ensure(AdPlacement.CALL_END_BANNER, app)

        if (overlayView != null) {
            Log.d(TAG, "DuringCall: update phase=$phase number=${phoneNumber ?: "null"}")
            return
        }

        attach(app)
        CallEndMetrics.recordDuringCallShown(app)
        CallOverlayForegroundService.start(app)
        Log.d(TAG, "DuringCall: shown phase=$phase displayName=$displayName")
    }

    fun dismiss() {
        if (overlayView == null && hostContext == null) return
        Log.d(TAG, "DuringCall: dismiss")
        val ctx = hostContext
        teardown()
        ctx?.let { CallOverlayForegroundService.stop(it) }
    }

    private fun attach(context: Context) {
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
            setContent {
                MessagesTheme {
                    DuringCallOverlayCard(
                        displayName = displayNameState.value,
                        phoneNumber = phoneState.value,
                        phase = phaseState.value,
                        onDismiss = { dismiss() }
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

        try {
            wm.addView(composeView, params)
            overlayView = composeView
        } catch (e: Exception) {
            Log.e(TAG, "DuringCall: addView failed: ${e.message}", e)
            CallEndMetrics.recordMiss(context, "during_addView_failed")
            teardown()
        }
    }

    private fun teardown() {
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
    }
}

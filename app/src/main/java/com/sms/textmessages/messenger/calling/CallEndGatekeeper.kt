package com.sms.textmessages.messenger.calling

import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import com.sms.textmessages.messenger.ads.RemoteConfigManager
import com.sms.textmessages.messenger.ui.home.getContactName
import com.sms.textmessages.messenger.utils.CallEndMetrics
import com.sms.textmessages.messenger.utils.CallSessionStore
import com.sms.textmessages.messenger.utils.PreferenceManager

/**
 * Single launcher for the after-call screen. All entry points feed here.
 * Dismisses any during-call bubble first, then starts Popup/Fullscreen Activity
 * (with overlay fallback only if the Activity start is blocked).
 */
object CallEndGatekeeper {

    private const val TAG = "CALLEND_DEBUG"

    @Volatile
    private var lastLaunchElapsedMs = 0L

    @Volatile
    private var lastHandledKey: String? = null

    fun wasCallHandled(key: String): Boolean = lastHandledKey == key

    fun markCallHandled(key: String) {
        lastHandledKey = key
    }

    /** Test seam to reset cooldown between unit tests. */
    internal fun resetForTests() {
        lastLaunchElapsedMs = 0L
        lastHandledKey = null
    }

    fun onCallEnded(context: Context, event: CallEndEvent) {
        val app = context.applicationContext
        Log.d(
            TAG,
            "Gatekeeper.onCallEnded ENTER source=${event.source} type=${event.type} " +
                "number=${describeNumber(event.number)} durationMs=${event.durationMs} " +
                "wasBlocked=${event.wasBlocked} disconnectCause=${event.disconnectCause}"
        )

        val enriched = enrich(app, event)
        Log.d(
            TAG,
            "Gatekeeper.enrich → number=${describeNumber(enriched.number)} " +
                "displayName=${enriched.displayName} wasBlocked=${enriched.wasBlocked}"
        )

        val callEndEnabled = PreferenceManager.isCallEndEnabled(app)
        val rcEnabled = RemoteConfigManager.afterCallScreenEnabled()
        val onboarding = isOnboardingBlocking(app)
        val firstLaunch = PreferenceManager.isFirstLaunch(app)
        val landscape = isLandscape(app)
        val locked = isKeyguardLocked(app)
        val now = SystemClock.elapsedRealtime()
        val dedupe = enriched.dedupeKey()
        val already = wasCallHandled(dedupe)

        Log.d(
            TAG,
            "Gatekeeper.checks callEndEnabled=$callEndEnabled rcEnabled=$rcEnabled " +
                "firstLaunch=$firstLaunch onboardingBlocking=$onboarding " +
                "landscape=$landscape keyguardLocked=$locked " +
                "cooldownActive=${CallEndEligibility.isCooldownActive(lastLaunchElapsedMs, now)} " +
                "alreadyHandled=$already dedupe=$dedupe " +
                "lastLaunchElapsed=$lastLaunchElapsedMs now=$now " +
                "metrics=${CallEndMetrics.summary(app)}"
        )

        val bail = CallEndEligibility.isEligible(
            callEndEnabled = callEndEnabled,
            remoteConfigEnabled = rcEnabled,
            wasBlocked = enriched.wasBlocked,
            number = enriched.number,
            onboardingInProgress = onboarding,
            lastLaunchElapsedMs = lastLaunchElapsedMs,
            nowElapsedMs = now,
            alreadyHandled = already,
            isLandscape = landscape
        )
        if (bail != null) {
            Log.w(TAG, "Gatekeeper BAIL reason=$bail")
            // Cooldown is intentional dedupe — not a "miss" for OEM prompts.
            if (bail != "cooldown" && bail != "dedupe") {
                CallEndMetrics.recordMiss(app, "gatekeeper_$bail")
            }
            return
        }

        CallEndMetrics.recordAttempt(app)
        markCallHandled(dedupe)
        lastLaunchElapsedMs = SystemClock.elapsedRealtime()

        // Hang-up sequence: drop during-call popup before after-call Activity.
        DuringCallOverlayHost.dismiss()

        val fullscreen = CallEndEligibility.shouldUseFullscreen(
            isKeyguardLocked = locked,
            type = enriched.type
        )
        Log.d(
            TAG,
            "Gatekeeper LAUNCH type=${enriched.type} number=${describeNumber(enriched.number)} " +
                "durationMs=${enriched.durationMs} source=${enriched.source} fullscreen=$fullscreen"
        )

        try {
            CallEndActivityLauncher.launch(app, enriched, fullscreen)
            CallEndMetrics.recordShow(app)
            Log.d(TAG, "Gatekeeper LAUNCH requested OK metrics=${CallEndMetrics.summary(app)}")
        } catch (e: Exception) {
            Log.e(TAG, "Gatekeeper launch FAILED: ${e.message}", e)
            CallEndMetrics.recordMiss(app, "gatekeeper_show_threw")
        }
    }

    /**
     * Only block while the first-run wizard is truly unfinished.
     * Do NOT use bare [PreferenceManager.isFirstLaunch] — that defaults to true
     * when the key is missing and permanently kills call-end for active users.
     */
    private fun isOnboardingBlocking(context: Context): Boolean {
        val lang = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("app_lang", null)
        val hasLanguage = !lang.isNullOrBlank()
        val firstLaunch = PreferenceManager.isFirstLaunch(context)
        // If the user already picked a language, call-end is allowed even if the
        // legacy first_launch flag was never written.
        if (hasLanguage) {
            if (firstLaunch) {
                Log.d(TAG, "Gatekeeper: app_lang set but first_launch still true — treating onboarding done")
                PreferenceManager.setFirstLaunchDone(context)
            }
            return false
        }
        return firstLaunch
    }

    private fun enrich(context: Context, event: CallEndEvent): CallEndEvent {
        val session = CallSessionStore.load(context)
        Log.d(
            TAG,
            "Gatekeeper.session sawRinging=${session?.sawRinging} sawOffhook=${session?.sawOffhook} " +
                "captured=${describeNumber(session?.capturedNumber)} wasBlocked=${session?.wasBlocked}"
        )
        val number = event.number?.takeIf { it.isNotBlank() }
            ?: session?.capturedNumber?.takeIf { it.isNotBlank() }
        val wasBlocked = event.wasBlocked || session?.wasBlocked == true
        val displayName = when {
            event.displayName.isNotBlank() &&
                event.displayName != "Unknown" &&
                event.displayName != number -> event.displayName
            number != null -> {
                getContactName(context, number)
                    ?.takeIf { it.isNotBlank() }
                    ?: number
            }
            else -> event.displayName.ifBlank { "Unknown" }
        }
        return event.copy(
            number = number,
            displayName = displayName,
            wasBlocked = wasBlocked
        )
    }

    private fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun isKeyguardLocked(context: Context): Boolean {
        val kg = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return kg?.isKeyguardLocked == true
    }

    private fun describeNumber(number: String?): String = when {
        number == null -> "null"
        number.isEmpty() -> "empty"
        else -> number
    }
}

object CallEndContract {
    const val EXTRA_EVENT = "call_end_event"
}

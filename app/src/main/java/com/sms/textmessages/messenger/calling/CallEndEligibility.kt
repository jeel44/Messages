package com.sms.textmessages.messenger.calling

import com.sms.textmessages.messenger.receiver.CallEndType

/**
 * Pure eligibility / variant helpers for [CallEndGatekeeper].
 * Kept free of Android framework types where possible so unit tests can cover
 * acceptance criteria without Robolectric.
 */
object CallEndEligibility {

    const val COOLDOWN_MS = 30_000L

    fun isValidNumber(number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        return number.count { it.isDigit() } >= 3
    }

    fun isCooldownActive(lastLaunchElapsedMs: Long, nowElapsedMs: Long): Boolean {
        if (lastLaunchElapsedMs <= 0L) return false
        return (nowElapsedMs - lastLaunchElapsedMs) < COOLDOWN_MS
    }

    /** Locked or missed → fullscreen; otherwise popup. */
    fun shouldUseFullscreen(isKeyguardLocked: Boolean, type: CallEndType): Boolean =
        isKeyguardLocked || type == CallEndType.MISSED

    fun isEligible(
        callEndEnabled: Boolean,
        remoteConfigEnabled: Boolean,
        wasBlocked: Boolean,
        number: String?,
        onboardingInProgress: Boolean,
        lastLaunchElapsedMs: Long,
        nowElapsedMs: Long,
        alreadyHandled: Boolean,
        isLandscape: Boolean
    ): String? {
        if (!callEndEnabled) return "user_pref_disabled"
        if (!remoteConfigEnabled) return "rc_disabled"
        if (wasBlocked) return "was_blocked"
        if (!isValidNumber(number)) return "invalid_number"
        if (onboardingInProgress) return "onboarding"
        if (isCooldownActive(lastLaunchElapsedMs, nowElapsedMs)) return "cooldown"
        if (alreadyHandled) return "dedupe"
        if (isLandscape) return "landscape"
        return null
    }
}

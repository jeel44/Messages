package com.sms.textmessages.messenger.calling

import com.sms.textmessages.messenger.receiver.CallEndType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CallEndEligibilityTest {

    @Before
    fun setUp() {
        CallEndGatekeeper.resetForTests()
    }

    @Test
    fun validNumberRequiresAtLeastThreeDigits() {
        assertFalse(CallEndEligibility.isValidNumber(null))
        assertFalse(CallEndEligibility.isValidNumber(""))
        assertFalse(CallEndEligibility.isValidNumber("ab"))
        assertFalse(CallEndEligibility.isValidNumber("12"))
        assertTrue(CallEndEligibility.isValidNumber("123"))
        assertTrue(CallEndEligibility.isValidNumber("+91 98765 43210"))
    }

    @Test
    fun cooldownBlocksRapidSecondLaunch() {
        assertFalse(CallEndEligibility.isCooldownActive(0L, 1_000L))
        assertTrue(CallEndEligibility.isCooldownActive(1_000L, 1_000L + 5_000L))
        assertFalse(
            CallEndEligibility.isCooldownActive(
                1_000L,
                1_000L + CallEndEligibility.COOLDOWN_MS
            )
        )
    }

    @Test
    fun fullscreenWhenLockedOrMissed() {
        assertTrue(CallEndEligibility.shouldUseFullscreen(true, CallEndType.INCOMING))
        assertTrue(CallEndEligibility.shouldUseFullscreen(false, CallEndType.MISSED))
        assertTrue(CallEndEligibility.shouldUseFullscreen(true, CallEndType.MISSED))
        assertFalse(CallEndEligibility.shouldUseFullscreen(false, CallEndType.INCOMING))
        assertFalse(CallEndEligibility.shouldUseFullscreen(false, CallEndType.OUTGOING))
    }

    @Test
    fun eligibilityReturnsBailReason() {
        assertEquals(
            "user_pref_disabled",
            CallEndEligibility.isEligible(
                callEndEnabled = false,
                remoteConfigEnabled = true,
                wasBlocked = false,
                number = "9998887777",
                onboardingInProgress = false,
                lastLaunchElapsedMs = 0L,
                nowElapsedMs = 10_000L,
                alreadyHandled = false,
                isLandscape = false
            )
        )
        assertEquals(
            "was_blocked",
            CallEndEligibility.isEligible(
                callEndEnabled = true,
                remoteConfigEnabled = true,
                wasBlocked = true,
                number = "9998887777",
                onboardingInProgress = false,
                lastLaunchElapsedMs = 0L,
                nowElapsedMs = 10_000L,
                alreadyHandled = false,
                isLandscape = false
            )
        )
        assertEquals(
            "cooldown",
            CallEndEligibility.isEligible(
                callEndEnabled = true,
                remoteConfigEnabled = true,
                wasBlocked = false,
                number = "9998887777",
                onboardingInProgress = false,
                lastLaunchElapsedMs = 1_000L,
                nowElapsedMs = 5_000L,
                alreadyHandled = false,
                isLandscape = false
            )
        )
        assertEquals(
            "landscape",
            CallEndEligibility.isEligible(
                callEndEnabled = true,
                remoteConfigEnabled = true,
                wasBlocked = false,
                number = "9998887777",
                onboardingInProgress = false,
                lastLaunchElapsedMs = 0L,
                nowElapsedMs = 10_000L,
                alreadyHandled = false,
                isLandscape = true
            )
        )
        assertNull(
            CallEndEligibility.isEligible(
                callEndEnabled = true,
                remoteConfigEnabled = true,
                wasBlocked = false,
                number = "9998887777",
                onboardingInProgress = false,
                lastLaunchElapsedMs = 0L,
                nowElapsedMs = 10_000L,
                alreadyHandled = false,
                isLandscape = false
            )
        )
    }

    @Test
    fun callEndEventDedupeKeyStableWithinWindow() {
        val event = CallEndEvent(
            number = "9998887777",
            displayName = "Test",
            type = CallEndType.INCOMING,
            durationMs = 5_000L,
            timestampMs = 1_700_000_000_000L
        )
        val sameWindow = event.copy(timestampMs = event.timestampMs + 500)
        assertEquals(event.dedupeKey(), sameWindow.dedupeKey())
    }
}

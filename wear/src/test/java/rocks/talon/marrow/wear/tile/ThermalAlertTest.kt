package rocks.talon.marrow.wear.tile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ThermalAlert.shouldAlert].
 *
 * Every decision branch is covered: threshold, streak count, and cooldown.
 * No Android context required — pure JVM.
 */
class ThermalAlertTest {

    /** Arbitrary "now" so we can reason about offsets cleanly. */
    private val nowMs = 1_000_000L

    /** A time far enough in the past that the 5-min cooldown has elapsed. */
    private val longAgo = nowMs - ThermalAlert.COOLDOWN_MS - 1L

    /** A time recent enough that the 5-min cooldown has NOT elapsed. */
    private val recent  = nowMs - ThermalAlert.COOLDOWN_MS + 60_000L // 4 min ago

    // ── Temperature threshold ────────────────────────────────────────────────

    @Test
    fun `no alert when temperature is below threshold`() {
        assertFalse(
            ThermalAlert.shouldAlert(
                tempC        = ThermalAlert.THRESHOLD_C - 0.1f,
                hotStreakCount = ThermalAlert.STREAK_TRIGGER,
                lastAlertMs  = longAgo,
                nowMs        = nowMs,
            )
        )
    }

    @Test
    fun `no alert when temperature is unavailable (negative sentinel)`() {
        assertFalse(
            ThermalAlert.shouldAlert(
                tempC        = -1f,
                hotStreakCount = 100,
                lastAlertMs  = longAgo,
                nowMs        = nowMs,
            )
        )
    }

    // ── Streak requirement ───────────────────────────────────────────────────

    @Test
    fun `no alert on first hot reading (streak not yet met)`() {
        // hotStreakCount = 0 → updatedStreak = 1 < STREAK_TRIGGER
        assertFalse(
            ThermalAlert.shouldAlert(
                tempC        = ThermalAlert.THRESHOLD_C,
                hotStreakCount = 0,
                lastAlertMs  = longAgo,
                nowMs        = nowMs,
            )
        )
    }

    @Test
    fun `no alert on second consecutive hot reading (streak not yet met)`() {
        // hotStreakCount = 1 → updatedStreak = 2 < STREAK_TRIGGER (3)
        assertFalse(
            ThermalAlert.shouldAlert(
                tempC        = ThermalAlert.THRESHOLD_C + 5f,
                hotStreakCount = 1,
                lastAlertMs  = longAgo,
                nowMs        = nowMs,
            )
        )
    }

    @Test
    fun `alert fires on third consecutive hot reading when cooldown elapsed`() {
        // hotStreakCount = 2 → updatedStreak = 3 == STREAK_TRIGGER; cooldown OK
        assertTrue(
            ThermalAlert.shouldAlert(
                tempC        = ThermalAlert.THRESHOLD_C,
                hotStreakCount = ThermalAlert.STREAK_TRIGGER - 1,
                lastAlertMs  = longAgo,
                nowMs        = nowMs,
            )
        )
    }

    @Test
    fun `alert fires even when streak is well above trigger`() {
        // Session-persistent streak (service restarted mid-session edge case
        // where callers didn't reset after a prior alert)
        assertTrue(
            ThermalAlert.shouldAlert(
                tempC        = 95f,
                hotStreakCount = 20,
                lastAlertMs  = longAgo,
                nowMs        = nowMs,
            )
        )
    }

    // ── Cooldown ────────────────────────────────────────────────────────────

    @Test
    fun `no alert when cooldown has not yet elapsed`() {
        // Streak is met but the previous alert was too recent.
        assertFalse(
            ThermalAlert.shouldAlert(
                tempC        = ThermalAlert.THRESHOLD_C,
                hotStreakCount = ThermalAlert.STREAK_TRIGGER - 1,
                lastAlertMs  = recent,
                nowMs        = nowMs,
            )
        )
    }

    @Test
    fun `alert fires immediately after cooldown elapses`() {
        // Exactly at the cooldown boundary.
        val exactlyAtCooldown = nowMs - ThermalAlert.COOLDOWN_MS
        assertTrue(
            ThermalAlert.shouldAlert(
                tempC        = ThermalAlert.THRESHOLD_C,
                hotStreakCount = ThermalAlert.STREAK_TRIGGER - 1,
                lastAlertMs  = exactlyAtCooldown,
                nowMs        = nowMs,
            )
        )
    }

    @Test
    fun `alert fires when no previous alert has ever been sent (lastAlertMs = 0)`() {
        // First ever alert — lastAlertMs = 0, which is always > COOLDOWN_MS ago.
        assertTrue(
            ThermalAlert.shouldAlert(
                tempC        = ThermalAlert.THRESHOLD_C + 10f,
                hotStreakCount = ThermalAlert.STREAK_TRIGGER - 1,
                lastAlertMs  = 0L,
                nowMs        = nowMs,
            )
        )
    }
}

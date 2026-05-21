package rocks.talon.marrow.wear.tile

/**
 * Pure decision logic for the tile's sustained-heat notification.
 *
 * Extracted from [StatsTileService] so it can be unit-tested without an
 * Android device — all inputs are plain primitives, there are no side effects.
 *
 * Alert policy (all three conditions must hold simultaneously):
 *  1. Current temperature >= [THRESHOLD_C] (80 °C)
 *  2. The streak of consecutive hot readings (before this one) has reached
 *     [STREAK_TRIGGER] - 1, so counting this reading makes it [STREAK_TRIGGER]
 *  3. At least [COOLDOWN_MS] (5 min) has elapsed since the last alert fired
 *
 * Rationale for requiring a streak rather than a single reading:
 *  - A single transient spike (e.g. a burst-mode GPU frame) should not wake
 *    the user. Three consecutive 30-second tile refresh cycles at ≥ 80 °C
 *    means the SoC has been running hot for at least 60 seconds.
 *  - The 5-minute cooldown prevents notification spam when temperature is
 *    hovering around the threshold.
 */
object ThermalAlert {

    /** Minimum sustained temperature in °C before an alert is considered. */
    const val THRESHOLD_C: Float = 80f

    /**
     * Number of consecutive tile refreshes at >= [THRESHOLD_C] required
     * before the first alert fires.
     *
     * At the default 30-second tile refresh interval, STREAK_TRIGGER = 3
     * means the SoC must be hot for at least 60 seconds continuously.
     */
    const val STREAK_TRIGGER: Int = 3

    /**
     * Minimum milliseconds between successive alerts.
     *
     * Even if the device stays hot, we won't fire more than one notification
     * per [COOLDOWN_MS]. Callers reset [hotStreakCount] after an alert fires,
     * so the streak must rebuild before the next alert is even considered.
     */
    const val COOLDOWN_MS: Long = 5L * 60L * 1_000L // 5 minutes

    /** Notification channel id — must be stable across app upgrades. */
    const val CHANNEL_ID = "marrow_thermal"

    /** Notification id — stable so repeated alerts update the same notification. */
    const val NOTIFICATION_ID = 1001

    /**
     * Returns `true` when a thermal notification should be fired.
     *
     * @param tempC           Current CPU/SoC temperature in °C; negative = unavailable.
     * @param hotStreakCount  Number of consecutive previous tile refreshes where
     *                        [tempC] was >= [THRESHOLD_C]. Does NOT include the
     *                        current reading — callers increment after this call.
     * @param lastAlertMs     Epoch milliseconds when the last alert was fired,
     *                        or 0 if no alert has ever fired this session.
     * @param nowMs           Current epoch milliseconds (System.currentTimeMillis()).
     */
    fun shouldAlert(
        tempC: Float,
        hotStreakCount: Int,
        lastAlertMs: Long,
        nowMs: Long,
    ): Boolean {
        if (tempC < THRESHOLD_C) return false
        // hotStreakCount holds previous consecutive hot readings. Adding the
        // current reading means the streak length is hotStreakCount + 1.
        if (hotStreakCount + 1 < STREAK_TRIGGER) return false
        return nowMs - lastAlertMs >= COOLDOWN_MS
    }
}

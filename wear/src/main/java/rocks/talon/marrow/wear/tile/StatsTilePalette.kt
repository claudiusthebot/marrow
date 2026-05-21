package rocks.talon.marrow.wear.tile

/**
 * Pure threshold + formatting helpers for the stats tile.
 *
 * Extracted from [StatsTileService] so they can be unit-tested without
 * the Wear OS tile framework present.
 */
internal object StatsTilePalette {

    /** Background fill for a metric in the "good / calm" band. */
    const val COLOR_GOOD: Int = 0xFF3B4A39.toInt()   // darkened primaryContainer

    /** Background fill for a metric in the "OK / moderate" band. */
    const val COLOR_OK: Int = 0xFF4A4436.toInt()      // darkened tertiaryContainer

    /** Background fill for a metric in the "warning / high" band. */
    const val COLOR_WARN: Int = 0xFF5C3030.toInt()    // darkened errorContainer

    /** Text tint for a warm-but-not-alarming temperature reading (50–69°C). */
    const val COLOR_TEMP_WARM: Int = 0xFFCCAA66.toInt()  // amber-ish warm

    /** Text tint for a hot temperature reading (≥ 70°C). */
    const val COLOR_TEMP_HOT: Int = 0xFFFF8888.toInt()   // soft red

    /**
     * 3-band colour for load metrics (memory, CPU, GPU):
     *   < 40 → GOOD  |  40–69 → OK  |  ≥ 70 → WARN
     * Negative sentinel → GOOD (metric unavailable).
     */
    fun colorForLoad(pct: Int): Int = when {
        pct < 0  -> COLOR_GOOD
        pct < 40 -> COLOR_GOOD
        pct < 70 -> COLOR_OK
        else     -> COLOR_WARN
    }

    /**
     * Inverted colour for battery:
     *   charging → GOOD (always)  |  ≥ 50 → GOOD  |  20–49 → OK  |  < 20 → WARN
     * Negative sentinel → GOOD (metric unavailable).
     */
    fun colorForBattery(pct: Int, charging: Boolean): Int = when {
        pct < 0    -> COLOR_GOOD
        charging   -> COLOR_GOOD
        pct < 20   -> COLOR_WARN
        pct < 50   -> COLOR_OK
        else       -> COLOR_GOOD
    }

    /**
     * Formats a [pct] value (0–100+) as a percent string, or "—" for the
     * negative-sentinel "unavailable" case.
     */
    fun formatPercent(pct: Int): String = if (pct < 0) "—" else "$pct%"

    /**
     * Compact byte-rate label for the network row — no trailing "/s" because
     * horizontal space on a round watch face is limited.
     *
     * Examples:
     *   1 234 567 B/s → "1.2M"
     *     345 000 B/s → "345K"
     *         123 B/s → "123B"
     *           0 B/s → "0B"
     */
    fun formatNetRate(bytesPerSec: Long): String = when {
        bytesPerSec >= 1_000_000L -> "%.1fM".format(bytesPerSec / 1_000_000f)
        bytesPerSec >= 1_000L     -> "${bytesPerSec / 1_000}K"
        else                      -> "${bytesPerSec}B"
    }

    /**
     * Compact CPU/SoC temperature label for the tile footer.
     *
     * Returns the integer degrees with a degree symbol (e.g. "42°") for valid
     * readings, or "—" for the [LiveStats.cpuTempC] sentinel value of -1f
     * (returned when sysfs thermal zones are unreadable under emulator or a
     * restrictive SELinux profile).
     *
     * Fractional degrees are intentionally truncated — 0.5 °C granularity is
     * meaningless at watch-face glance distance and a whole-number value leaves
     * more horizontal space for the network rates beside it.
     */
    fun formatTemp(tempC: Float): String = if (tempC < 0f) "—" else "${tempC.toInt()}°"

    /**
     * Compact session-peak temperature label for the tile header.
     *
     * Returns the integer degrees with a degree symbol and a trailing up-arrow
     * (e.g. "42°↑") for a valid peak reading, or an empty string when no valid
     * sample has been observed yet (sentinel < 0).
     *
     * The up-arrow visually distinguishes the session-peak from the live
     * temperature shown in the footer's `· 42°` slot.
     */
    fun formatPeakTemp(peakTempC: Float): String =
        if (peakTempC < 0f) "" else "${peakTempC.toInt()}°↑"

    /**
     * Text color for the live temperature reading in the footer row.
     *
     * Three-band threshold — same thresholds as `colorForLoad` but tuned for
     * °C rather than percent.  Wear OS SoC temperatures at idle sit in the
     * 35–48 °C range; light load tops out around 55 °C; sustained load or a
     * warm environment can push 65–75 °C.
     *
     *   < 50 °C → [normalLabelColor]  (calm — pass in `COLOR_LABEL` from the caller)
     *   50–69°C → [COLOR_TEMP_WARM]   (elevated, amber tint — worth noticing)
     *   ≥ 70 °C → [COLOR_TEMP_HOT]    (hot, soft red — act on it)
     *
     * Negative sentinel (unavailable) → [normalLabelColor].
     *
     * The [normalLabelColor] is supplied by the caller rather than hard-coded
     * here so [StatsTilePalette] stays independent of the tile service's colour
     * constants.
     */
    fun colorForTemp(tempC: Float, normalLabelColor: Int): Int = when {
        tempC < 0f  -> normalLabelColor
        tempC < 50f -> normalLabelColor
        tempC < 70f -> COLOR_TEMP_WARM
        else        -> COLOR_TEMP_HOT
    }
}

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
}

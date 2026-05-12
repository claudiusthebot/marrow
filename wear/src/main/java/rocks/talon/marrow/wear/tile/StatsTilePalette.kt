package rocks.talon.marrow.wear.tile

/**
 * Pure colour-band + label formatting helpers for the Marrow stats tile.
 *
 * Extracted from [StatsTileService] so the comfort-band thresholds are
 * unit-testable without standing up a Wear OS tile context. The tile
 * service itself is untestable on the JVM (the `TileService` superclass
 * pulls in `androidx.wear.tiles`) but the colour bands and value
 * formatting are pure `Int → Int / String` and live here.
 *
 * Talon orange family — mirrors `MarrowWearTheme.MarrowWearColors`:
 *  * [COLOR_GOOD]: primaryContainer  (calm — low load / charging / unknown)
 *  * [COLOR_OK]:   tertiaryContainer (medium load)
 *  * [COLOR_WARN]: errorContainer    (high load / low-battery warning)
 */
internal object StatsTilePalette {

    const val COLOR_GOOD: Int = 0xFF7A3411.toInt()       // primaryContainer
    const val COLOR_OK: Int = 0xFF52461D.toInt()         // tertiaryContainer
    const val COLOR_WARN: Int = 0xFF93000A.toInt()       // errorContainer

    /**
     * Comfort bands for "load"-style metrics (memory / CPU / GPU).
     *
     * A negative input is the "metric unavailable" sentinel — these stay
     * calm (GOOD) rather than flashing red so a missing GPU or blocked
     * `/proc/stat` doesn't visually scream.
     */
    fun colorForLoad(pct: Int): Int = when {
        pct < 0 -> COLOR_GOOD
        pct < 40 -> COLOR_GOOD
        pct < 70 -> COLOR_OK
        else -> COLOR_WARN
    }

    /**
     * Inverted bands for battery — low charge is the warning state. A
     * charging device always reads GOOD regardless of percent: the user
     * is actively recovering charge, no warning needed.
     */
    fun colorForBattery(pct: Int, charging: Boolean): Int = when {
        pct < 0 -> COLOR_GOOD
        charging -> COLOR_GOOD
        pct < 20 -> COLOR_WARN
        pct < 50 -> COLOR_OK
        else -> COLOR_GOOD
    }

    /** Render an int percent as `<n>%`; the negative sentinel becomes "—". */
    fun formatPercent(pct: Int): String =
        if (pct < 0) "—" else "$pct%"
}

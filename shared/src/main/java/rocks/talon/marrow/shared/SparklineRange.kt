package rocks.talon.marrow.shared

/**
 * Time window for sparkline history charts.
 *
 * Each range maps to a [samples] count — the number of recent [HistoryBuffer]
 * entries to slice for display. At the current ~1-second polling interval:
 *  - [ONE_MIN]   → 60 samples  ≈ 1 minute
 *  - [FIVE_MIN]  → 300 samples ≈ 5 minutes
 *  - [FIF_MIN]   → 900 samples ≈ 15 minutes
 *
 * All four sparkline history buffers in [rocks.talon.marrow.phone.MarrowViewModel]
 * are sized at [FIF_MIN.samples] so any window can be satisfied from a single buffer.
 */
enum class SparklineRange(val samples: Int, val label: String) {
    ONE_MIN(samples = 60, label = "1m"),
    FIVE_MIN(samples = 300, label = "5m"),
    FIF_MIN(samples = 900, label = "15m"),
}

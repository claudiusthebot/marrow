package rocks.talon.marrow.wear.complication

import android.app.ActivityManager
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Wear OS complication data source: RAM usage %.
 *
 * Supports RANGED_VALUE (arc 0-100%) and SHORT_TEXT (e.g. "62%").
 * Reads MemoryInfo from ActivityManager on request — no sensor registration.
 * Update period: 300 s (5 min) — coarse-grained memory indicator.
 *
 * Added in v0.47.0.
 */
class MemoryComplicationDataSourceService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val usedPct = if (info.totalMem > 0L) {
            ((info.totalMem - info.availMem) * 100L / info.totalMem).toInt().coerceIn(0, 100)
        } else 0
        return buildComplicationData(request.complicationType, usedPct)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        buildComplicationData(type, 62)

    private fun buildComplicationData(type: ComplicationType, usedPct: Int): ComplicationData {
        val label = PlainComplicationText.Builder("RAM").build()
        val text = PlainComplicationText.Builder("$usedPct%").build()
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = usedPct.toFloat(),
                min = 0f,
                max = 100f,
                contentDescription = label,
            ).setText(text).build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = text,
                contentDescription = label,
            ).build()
            else -> throw IllegalArgumentException("Unsupported complication type: $type")
        }
    }
}

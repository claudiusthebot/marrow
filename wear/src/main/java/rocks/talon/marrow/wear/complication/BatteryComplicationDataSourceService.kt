package rocks.talon.marrow.wear.complication

import android.hardware.BatteryManager
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Wear OS complication data source: battery level.
 *
 * Supports RANGED_VALUE (arc 0-100%) and SHORT_TEXT (e.g. "78%").
 * No sensor registration needed — reads directly from BatteryManager on request.
 * Update period: 300 s (5 min) — battery level rarely changes faster than this.
 *
 * Added in v0.47.0. Shows Marrow battery data on any compatible watch face.
 */
class BatteryComplicationDataSourceService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        return buildComplicationData(request.complicationType, level)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        buildComplicationData(type, 78)

    private fun buildComplicationData(type: ComplicationType, level: Int): ComplicationData {
        val label = PlainComplicationText.Builder("Battery").build()
        val text = PlainComplicationText.Builder("$level%").build()
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = level.toFloat(),
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

package rocks.talon.marrow.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import rocks.talon.marrow.shared.LiveStats

// Static dark M3-purple palette — readable on any home-screen wallpaper.
// Dynamic colour via GlanceTheme / glance-material3 caused unresolved-reference
// errors in CI (GlanceTheme not exported from glance-material3:1.1.0 in this
// Compose-BOM configuration); plain ColorProvider avoids the dependency entirely.
private val BgColor    = ColorProvider(Color(0xFF1C1B1F))
private val LabelColor = ColorProvider(Color(0xFFCAC4D0))
private val ValueColor = ColorProvider(Color(0xFFEADDFF))

/**
 * Marrow home-screen widget — battery %, RAM %, storage % at a glance.
 *
 * Stats are read directly in [provideGlance] (coroutine context), so no
 * WorkManager or ViewModel wiring is needed. The system auto-refreshes via
 * [MarrowWidgetReceiver] every 30 minutes (Android platform minimum).
 */
class MarrowWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val battery = LiveStats.battery(context)
        val memory = LiveStats.memory(context)
        val volumes = LiveStats.volumes()
        val storagePct = (LiveStats.storageUsedFraction(volumes) * 100f).toInt()
        val battText = if (battery.percent >= 0) {
            if (battery.charging) "+${battery.percent}%" else "${battery.percent}%"
        } else "—"

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(BgColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                StatRow(label = "Battery", value = battText)
                StatRow(label = "RAM",     value = "${memory.usedPercent}%")
                StatRow(label = "Storage", value = "$storagePct%")
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = "$label: ",
            style = TextStyle(
                fontSize = 11.sp,
                color = LabelColor,
            ),
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ValueColor,
            ),
        )
    }
}

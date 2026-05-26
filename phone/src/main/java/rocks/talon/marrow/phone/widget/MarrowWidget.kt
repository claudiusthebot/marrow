package rocks.talon.marrow.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.material3.GlanceTheme
import androidx.glance.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import rocks.talon.marrow.shared.LiveStats

/**
 * Marrow home-screen widget — battery %, RAM %, storage % at a glance.
 *
 * Stats are read directly in [provideGlance] (coroutine context), so no
 * WorkManager or ViewModel wiring is needed. The system auto-refreshes via
 * [MarrowWidgetReceiver] every 30 minutes (Android platform minimum).
 *
 * Dynamic-color theming: on Android 12+ the widget adopts the wallpaper palette
 * via [GlanceTheme]; on API 30/31 it falls back to static M3 defaults.
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
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    StatRow(label = "Battery", value = battText)
                    StatRow(label = "RAM", value = "${memory.usedPercent}%")
                    StatRow(label = "Storage", value = "$storagePct%")
                }
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
                color = GlanceTheme.colors.onBackground,
            ),
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onBackground,
            ),
        )
    }
}

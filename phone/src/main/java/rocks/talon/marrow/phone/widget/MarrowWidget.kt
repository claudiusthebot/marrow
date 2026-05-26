package rocks.talon.marrow.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
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
        val battery    = LiveStats.battery(context)
        val memory     = LiveStats.memory(context)
        val volumes    = LiveStats.volumes()
        val storagePct = (LiveStats.storageUsedFraction(volumes) * 100f).toInt()
        val battText   = buildBatteryText(battery.percent, battery.charging)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .background(GlanceTheme.colors.background)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    StatRow(label = "Battery", value = battText)
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    StatRow(label = "RAM",     value = "${memory.usedPercent}%")
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    StatRow(label = "Storage", value = "$storagePct%")
                }
            }
        }
    }

    private fun buildBatteryText(percent: Int, charging: Boolean): String {
        val pct = if (percent >= 0) "${percent}%" else "—"
        return if (charging) "+$pct" else pct
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier          = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onBackground),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text  = value,
            style = TextStyle(
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                color      = GlanceTheme.colors.onBackground,
            ),
        )
    }
}

package rocks.talon.marrow.phone.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** BroadcastReceiver that owns the Marrow home-screen widget lifecycle. */
class MarrowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarrowWidget()
}

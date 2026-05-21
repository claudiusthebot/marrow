package rocks.talon.marrow.wear

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import rocks.talon.marrow.wear.tile.ThermalAlert

class MarrowWearApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                ThermalAlert.CHANNEL_ID,
                getString(R.string.notification_channel_thermal_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.notification_channel_thermal_desc)
                enableVibration(true)
            }
        )
    }
}

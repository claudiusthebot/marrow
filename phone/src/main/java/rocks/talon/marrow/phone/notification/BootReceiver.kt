package rocks.talon.marrow.phone.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import rocks.talon.marrow.phone.prefs.SettingsRepository

/**
 * Restores the live-stats foreground service after a device reboot.
 *
 * On [Intent.ACTION_BOOT_COMPLETED] (and the Huawei/OnePlus equivalent
 * QUICKBOOT_POWERON), the receiver reads the persisted
 * [SettingsRepository.settings] and fires [MarrowNotificationService.ACTION_START]
 * if the preference was enabled before the reboot.
 *
 * The DataStore read is async — [goAsync] extends the broadcast deadline so
 * the coroutine has time to complete without risking an ANR.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = SettingsRepository.get(context).settings.first()
                    .liveNotificationEnabled
                if (enabled) {
                    val startIntent =
                        Intent(context, MarrowNotificationService::class.java)
                            .setAction(MarrowNotificationService.ACTION_START)
                    context.startForegroundService(startIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

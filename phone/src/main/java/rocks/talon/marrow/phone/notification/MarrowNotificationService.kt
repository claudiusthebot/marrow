package rocks.talon.marrow.phone.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import rocks.talon.marrow.phone.MainActivity
import rocks.talon.marrow.phone.R
import rocks.talon.marrow.shared.LiveStats

/**
 * Optional foreground service that maintains a persistent ambient notification
 * showing a live snapshot of key device stats in the notification shade.
 *
 * Start the service with [ACTION_START], stop it with [ACTION_STOP].
 *
 * Stats are read directly via [LiveStats] every [REFRESH_MS] milliseconds
 * using a [Handler] post-delay loop — no ViewModel needed.
 *
 * Notification channel "marrow_live" uses [NotificationManager.IMPORTANCE_LOW]:
 * shown in the shade, no sound, no badge.
 *
 * Android 14+ (API 34): foreground service type declared as DATA_SYNC.
 * Android 13+ (API 33): POST_NOTIFICATIONS is declared in the manifest;
 * if the user has not granted it the notification is silently suppressed
 * but the service still runs (it can be granted later in app settings).
 */
class MarrowNotificationService : Service() {

    companion object {
        const val ACTION_START = "rocks.talon.marrow.NOTIF_START"
        const val ACTION_STOP  = "rocks.talon.marrow.NOTIF_STOP"

        private const val CHANNEL_ID  = "marrow_live"
        private const val NOTIF_ID    = 1001
        private const val REFRESH_MS  = 30_000L   // 30 s
    }

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            handler.removeCallbacks(refreshRunnable)
            return START_NOT_STICKY
        }
        // ACTION_START (or null on restart)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, buildNotification())
        }
        handler.post(refreshRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

    // -------------------------------------------------------------------------

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Live Stats",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent ambient device stats in the notification shade"
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val ctx = applicationContext
        val battery = LiveStats.battery(ctx)
        val memory  = LiveStats.memory(ctx)

        val battStr = when {
            battery.percent < 0 -> "—"                // em-dash = unknown
            battery.charging    -> "+${battery.percent}%"
            else                -> "${battery.percent}%"
        }

        val launchIntent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            ctx, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Marrow  •  Batt: $battStr  •  RAM: ${memory.usedPercent}%")
            .setContentText("Tap to open")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pending)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }
}

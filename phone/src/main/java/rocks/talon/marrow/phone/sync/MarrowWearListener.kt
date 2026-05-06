package rocks.talon.marrow.phone.sync

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import rocks.talon.marrow.shared.DeviceInfoSnapshot
import rocks.talon.marrow.shared.MarrowPaths

class MarrowWearListener : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            MarrowPaths.DEVICE_INFO -> {
                val text = runCatching { event.data.toString(Charsets.UTF_8) }.getOrNull() ?: return
                val snapshot = runCatching { json.decodeFromString<DeviceInfoSnapshot>(text) }.getOrNull() ?: return
                scope.launch { WatchInfoRepository.get(applicationContext).cacheIncoming(snapshot) }
            }
            MarrowPaths.PING -> {
                main.post {
                    Toast.makeText(applicationContext, "Marrow watch says hi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

package rocks.talon.marrow.wear.sync

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import rocks.talon.marrow.shared.DeviceInfoCollector
import rocks.talon.marrow.shared.DeviceInfoSnapshot
import rocks.talon.marrow.shared.MarrowPaths

/**
 * Watch-side listener: when the phone asks for device info, we collect a fresh
 * snapshot and ship it back over MessageClient.
 */
class MarrowWearListener : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != MarrowPaths.REQUEST_INFO) return
        scope.launch {
            val snap = DeviceInfoCollector.collect(applicationContext, DeviceInfoSnapshot.Source.WEAR)
            val payload = Json.encodeToString(DeviceInfoSnapshot.serializer(), snap)
            runCatching {
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(event.sourceNodeId, MarrowPaths.DEVICE_INFO, payload.toByteArray(Charsets.UTF_8))
                    .await()
            }
        }
    }
}

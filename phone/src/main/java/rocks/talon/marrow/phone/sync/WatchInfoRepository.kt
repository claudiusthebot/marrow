package rocks.talon.marrow.phone.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import rocks.talon.marrow.shared.DeviceInfoSnapshot
import rocks.talon.marrow.shared.MarrowPaths

private val Context.watchDataStore by preferencesDataStore(name = "marrow_watch_cache")
private val LAST_PAYLOAD = stringPreferencesKey("last_payload_json")

/**
 * Caches the last DeviceInfoSnapshot received from the watch and exposes a
 * fire-and-forget request method.
 */
class WatchInfoRepository private constructor(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val capabilityClient: CapabilityClient by lazy { Wearable.getCapabilityClient(context) }
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }

    private val _liveSnapshot = MutableStateFlow<DeviceInfoSnapshot?>(null)
    val liveSnapshot: StateFlow<DeviceInfoSnapshot?> = _liveSnapshot.asStateFlow()

    val cachedSnapshot: Flow<DeviceInfoSnapshot?> = context.watchDataStore.data.map { prefs ->
        prefs[LAST_PAYLOAD]?.let { runCatching { json.decodeFromString<DeviceInfoSnapshot>(it) }.getOrNull() }
    }

    private val _connection = MutableStateFlow(WatchConnectionState.UNKNOWN)
    val connection: StateFlow<WatchConnectionState> = _connection.asStateFlow()

    suspend fun cacheIncoming(snapshot: DeviceInfoSnapshot) {
        _liveSnapshot.value = snapshot
        context.watchDataStore.edit { it[LAST_PAYLOAD] = json.encodeToString(DeviceInfoSnapshot.serializer(), snapshot) }
    }

    suspend fun requestRefresh(): Boolean {
        val nodes = runCatching {
            capabilityClient
                .getCapability(MarrowPaths.CAPABILITY_PROVIDER, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrNull()

        if (nodes.isNullOrEmpty()) {
            _connection.value = WatchConnectionState.NOT_PAIRED
            return false
        }

        _connection.value = WatchConnectionState.CONNECTED
        var sent = false
        for (node in nodes) {
            runCatching {
                messageClient.sendMessage(node.id, MarrowPaths.REQUEST_INFO, ByteArray(0)).await()
                sent = true
            }
        }
        return sent
    }

    enum class WatchConnectionState { UNKNOWN, CONNECTED, NOT_PAIRED }

    companion object {
        @Volatile private var instance: WatchInfoRepository? = null
        fun get(context: Context): WatchInfoRepository =
            instance ?: synchronized(this) {
                instance ?: WatchInfoRepository(context.applicationContext).also { instance = it }
            }
    }
}

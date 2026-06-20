package rocks.talon.marrow.phone.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sparklineDataStore by preferencesDataStore(name = "marrow_sparklines")

private val CPU_HISTORY_KEY        = stringPreferencesKey("cpu_history")
private val RAM_HISTORY_KEY        = stringPreferencesKey("ram_history")
private val NETWORK_RX_HISTORY_KEY = stringPreferencesKey("network_rx_history")
private val BATTERY_MA_HISTORY_KEY = stringPreferencesKey("battery_ma_history")

/**
 * Persisted snapshots of the four sparkline history buffers.
 * Loaded on ViewModel init and saved on ViewModel clear, so charts
 * survive app restarts even if the data is a few seconds stale.
 */
data class SparklineSnapshot(
    val cpu:       List<Float> = emptyList(),
    val ram:       List<Float> = emptyList(),
    val networkRx: List<Float> = emptyList(),
    val batteryMa: List<Float> = emptyList(),
)

/**
 * DataStore-backed repository for sparkline history persistence.
 *
 * Each metric is stored as a comma-separated string of Float values
 * so no serialization library dependency is needed beyond what
 * DataStore already provides.
 *
 * Singleton — acquire via [get].
 */
class SparklineHistoryRepository private constructor(private val context: Context) {

    /** Load all four histories from disk (suspends until first read). */
    suspend fun load(): SparklineSnapshot {
        val prefs = context.sparklineDataStore.data.first()
        return SparklineSnapshot(
            cpu       = parseFloats(prefs[CPU_HISTORY_KEY]),
            ram       = parseFloats(prefs[RAM_HISTORY_KEY]),
            networkRx = parseFloats(prefs[NETWORK_RX_HISTORY_KEY]),
            batteryMa = parseFloats(prefs[BATTERY_MA_HISTORY_KEY]),
        )
    }

    /** Persist all four histories to disk. */
    suspend fun save(snapshot: SparklineSnapshot) {
        context.sparklineDataStore.edit { prefs ->
            prefs[CPU_HISTORY_KEY]        = encodeFloats(snapshot.cpu)
            prefs[RAM_HISTORY_KEY]        = encodeFloats(snapshot.ram)
            prefs[NETWORK_RX_HISTORY_KEY] = encodeFloats(snapshot.networkRx)
            prefs[BATTERY_MA_HISTORY_KEY] = encodeFloats(snapshot.batteryMa)
        }
    }

    private fun parseFloats(raw: String?): List<Float> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.toFloatOrNull() }
    }

    private fun encodeFloats(values: List<Float>): String =
        values.joinToString(",")

    companion object {
        @Volatile private var instance: SparklineHistoryRepository? = null
        fun get(context: Context): SparklineHistoryRepository =
            instance ?: synchronized(this) {
                instance ?: SparklineHistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}

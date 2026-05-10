package rocks.talon.marrow.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import rocks.talon.marrow.shared.DeviceInfoCollector
import rocks.talon.marrow.shared.DeviceInfoSnapshot
import rocks.talon.marrow.shared.LiveStats
import rocks.talon.marrow.wear.sync.pingPhone

/**
 * Holds the shared device-info snapshot for every screen in the watch app and
 * the small ping-phone state machine.
 *
 * v0.2.0 additions:
 * - Live battery/memory polling while the screen is on (driven by detail
 *   screens calling `startLive()` / `stopLive()` from `DisposableEffect`).
 * - Phone connectivity flag (used by the disconnected state).
 */
class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val _snapshot = MutableStateFlow<DeviceInfoSnapshot?>(null)
    val snapshot: StateFlow<DeviceInfoSnapshot?> = _snapshot.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _pingState = MutableStateFlow(PingState.IDLE)
    val pingState: StateFlow<PingState> = _pingState.asStateFlow()

    private val _phoneReachable = MutableStateFlow(true)
    val phoneReachable: StateFlow<Boolean> = _phoneReachable.asStateFlow()

    private val _battery = MutableStateFlow<LiveStats.Battery?>(null)
    val battery: StateFlow<LiveStats.Battery?> = _battery.asStateFlow()

    private val _memory = MutableStateFlow<LiveStats.Memory?>(null)
    val memory: StateFlow<LiveStats.Memory?> = _memory.asStateFlow()

    /** Live CPU core frequency snapshots. Updated in the live loop. Empty list until
     *  first tick, or on emulators / SELinux-restricted devices. */
    private val _cpuCores = MutableStateFlow<List<LiveStats.CpuCore>>(emptyList())
    val cpuCores: StateFlow<List<LiveStats.CpuCore>> = _cpuCores.asStateFlow()

    /** Live GPU snapshot. null until the live loop has ticked at least once.
     *  On watches without an exposed GPU sysfs path the snapshot will report
     *  `available = false` and the GPU detail card falls back to its raw rows. */
    private val _gpu = MutableStateFlow<LiveStats.Gpu?>(null)
    val gpu: StateFlow<LiveStats.Gpu?> = _gpu.asStateFlow()

    private var liveJob: Job? = null

    init {
        refresh()
        checkPhoneReachable()
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) {
                DeviceInfoCollector.collect(getApplication(), DeviceInfoSnapshot.Source.WEAR)
            }
            _snapshot.value = snap
            // Also seed live stats from this collection
            _battery.value = withContext(Dispatchers.IO) { LiveStats.battery(getApplication()) }
            _memory.value = withContext(Dispatchers.IO) { LiveStats.memory(getApplication()) }
            _refreshing.value = false
            checkPhoneReachable()
        }
    }

    /** Start the live battery/memory/CPU loop. Idempotent — safe to call repeatedly. */
    fun startLive() {
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                _battery.value = LiveStats.battery(getApplication())
                _memory.value = LiveStats.memory(getApplication())
                _cpuCores.value = LiveStats.cpuCores()
                _gpu.value = LiveStats.gpu()
                delay(10_000L)
            }
        }
    }

    fun stopLive() {
        liveJob?.cancel()
        liveJob = null
    }

    fun ping() {
        viewModelScope.launch {
            _pingState.value = PingState.SENDING
            val ok = withContext(Dispatchers.IO) { pingPhone(getApplication()) }
            _pingState.value = if (ok) PingState.SENT else PingState.FAILED
            delay(1500)
            _pingState.value = PingState.IDLE
        }
    }

    private fun checkPhoneReachable() {
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) {
                runCatching {
                    Wearable.getNodeClient(getApplication()).connectedNodes.await()
                }.getOrNull().orEmpty()
            }
            _phoneReachable.value = nodes.isNotEmpty()
        }
    }

    override fun onCleared() {
        liveJob?.cancel()
        super.onCleared()
    }

    enum class PingState { IDLE, SENDING, SENT, FAILED }
}

package rocks.talon.marrow.phone

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rocks.talon.marrow.phone.prefs.Settings
import rocks.talon.marrow.phone.prefs.SettingsRepository
import rocks.talon.marrow.phone.prefs.ThemeMode
import rocks.talon.marrow.phone.sync.WatchInfoRepository
import rocks.talon.marrow.shared.DeviceInfoCollector
import rocks.talon.marrow.shared.DeviceInfoSnapshot
import rocks.talon.marrow.shared.LiveStats

/**
 * App-wide state holder.
 *
 * - **Snapshot** for the phone (full collector dump) and watch (cached/live).
 * - **Live stats** (battery / memory / cpu / storage / cpu-temp / network / disk I/O)
 *   polled every refresh interval — used by the Device tab's hero/strip and per-section heroes.
 * - **Settings** stream from DataStore so the theme/interval react to changes.
 */
class MarrowViewModel(app: Application) : AndroidViewModel(app) {

    private val watchRepo = WatchInfoRepository.get(app)
    private val settingsRepo = SettingsRepository.get(app)

    // -- Snapshots ---------------------------------------------------------------

    private val _phoneSnapshot = MutableStateFlow<DeviceInfoSnapshot?>(null)
    val phoneSnapshot: StateFlow<DeviceInfoSnapshot?> = _phoneSnapshot.asStateFlow()

    private val _phoneRefreshing = MutableStateFlow(false)
    val phoneRefreshing: StateFlow<Boolean> = _phoneRefreshing.asStateFlow()

    private val _watchRefreshing = MutableStateFlow(false)
    val watchRefreshing: StateFlow<Boolean> = _watchRefreshing.asStateFlow()

    val watchSnapshot: StateFlow<DeviceInfoSnapshot?> = watchRepo.liveSnapshot
    val watchConnection: StateFlow<WatchInfoRepository.WatchConnectionState> = watchRepo.connection
    val cachedWatchSnapshot: StateFlow<DeviceInfoSnapshot?> =
        watchRepo.cachedSnapshot.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // -- Live stats --------------------------------------------------------------

    private val _battery = MutableStateFlow<LiveStats.Battery?>(null)
    val battery: StateFlow<LiveStats.Battery?> = _battery.asStateFlow()

    private val _memory = MutableStateFlow<LiveStats.Memory?>(null)
    val memory: StateFlow<LiveStats.Memory?> = _memory.asStateFlow()

    private val _cpuCores = MutableStateFlow<List<LiveStats.CpuCore>>(emptyList())
    val cpuCores: StateFlow<List<LiveStats.CpuCore>> = _cpuCores.asStateFlow()

    private val _volumes = MutableStateFlow<List<LiveStats.Volume>>(emptyList())
    val volumes: StateFlow<List<LiveStats.Volume>> = _volumes.asStateFlow()

    /** Live network throughput as (rxBytesPerSec, txBytesPerSec). Starts at 0/0 until the
     *  second polling tick — one snapshot is needed for baseline, two for a rate. */
    private val _networkRate = MutableStateFlow<Pair<Long, Long>>(0L to 0L)
    val networkRate: StateFlow<Pair<Long, Long>> = _networkRate.asStateFlow()
    private var prevNetSnapshot: LiveStats.NetworkSpeed? = null

    /** CPU / SoC die temperature in °C. -1f when unavailable (emulator, restricted
     *  SELinux, device without accessible thermal_zone sysfs nodes). */
    private val _cpuTempC = MutableStateFlow(-1f)
    val cpuTempC: StateFlow<Float> = _cpuTempC.asStateFlow()

    /** Live disk I/O rate as (readBytesPerSec, writeBytesPerSec). Same delta pattern as
     *  network — needs two snapshots for first rate. */
    private val _diskRate = MutableStateFlow(0L to 0L)
    val diskRate: StateFlow<Pair<Long, Long>> = _diskRate.asStateFlow()
    private var prevDiskSnapshot: LiveStats.DiskSnapshot? = null

    // -- Settings ----------------------------------------------------------------

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(
        viewModelScope, SharingStarted.Eagerly, Settings(),
    )

    private var liveJob: Job? = null

    init {
        refreshPhone()
        requestWatchInfo()
        startLiveLoop()
    }

    // -- Operations --------------------------------------------------------------

    fun refreshPhone() {
        if (_phoneRefreshing.value) return
        viewModelScope.launch {
            _phoneRefreshing.value = true
            try {
                val snap = withContext(Dispatchers.IO) {
                    DeviceInfoCollector.collect(getApplication(), DeviceInfoSnapshot.Source.PHONE)
                }
                _phoneSnapshot.value = snap
            } finally {
                _phoneRefreshing.value = false
            }
        }
    }

    fun requestWatchInfo() {
        if (_watchRefreshing.value) return
        viewModelScope.launch {
            _watchRefreshing.value = true
            try {
                watchRepo.requestRefresh()
            } finally {
                _watchRefreshing.value = false
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun setRefreshInterval(seconds: Int) {
        viewModelScope.launch { settingsRepo.setRefreshIntervalSeconds(seconds) }
    }

    // -- Live loop ---------------------------------------------------------------

    private fun startLiveLoop() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val ctx = getApplication<Application>()
                _battery.value = LiveStats.battery(ctx)
                _memory.value = LiveStats.memory(ctx)
                _cpuCores.value = LiveStats.cpuCores()
                _volumes.value = LiveStats.volumes()
                val netSnap = LiveStats.networkSnapshot()
                prevNetSnapshot?.let { prev ->
                    _networkRate.value = LiveStats.networkRate(prev, netSnap)
                }
                prevNetSnapshot = netSnap
                _cpuTempC.value = LiveStats.cpuTempC()
                // Disk I/O: two-snapshot delta pattern
                val diskSnap = LiveStats.diskSnapshot()
                val prevSnap = prevDiskSnapshot
                if (diskSnap != null && prevSnap != null) {
                    _diskRate.value = LiveStats.diskRate(prevSnap, diskSnap)
                }
                if (diskSnap != null) prevDiskSnapshot = diskSnap
                val intervalMs = (settings.value.refreshIntervalSeconds.coerceIn(1, 60)) * 1000L
                delay(intervalMs)
            }
        }
    }

    override fun onCleared() {
        liveJob?.cancel()
        super.onCleared()
    }
}

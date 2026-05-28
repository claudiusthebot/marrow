package rocks.talon.marrow.phone

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
 * - **Live stats** (battery / memory / cpu / storage / cpu-temp / network / disk I/O / uptime / gpu)
 *   polled every refresh interval — used by the Device tab’s hero/strip and per-section heroes.
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

    /** System uptime in seconds since last boot (from /proc/uptime).
     *  0L until the first live-loop tick. */
    private val _systemUptimeSeconds = MutableStateFlow(0L)
    val systemUptimeSeconds: StateFlow<Long> = _systemUptimeSeconds.asStateFlow()

    /** Total CPU utilisation as a percentage (0–100f).
     *  -1f until the second live-loop tick provides a /proc/stat delta.
     *  Returns -1f on emulators or devices with restricted SELinux policy. */
    private val _cpuUsagePercent = MutableStateFlow(-1f)
    val cpuUsagePercent: StateFlow<Float> = _cpuUsagePercent.asStateFlow()
    private var prevCpuStat: LiveStats.CpuStatSnapshot? = null

    /** All readable thermal zones ≥ 25 °C, sorted hottest-first.
     *  Empty until the first live-loop tick, or on emulators / restricted SELinux. */
    private val _thermalZones = MutableStateFlow<List<LiveStats.ThermalZone>>(emptyList())
    val thermalZones: StateFlow<List<LiveStats.ThermalZone>> = _thermalZones.asStateFlow()

    /**
     * Live GPU stats — current frequency, min/max, governor, and utilisation.
     * null until the first live-loop tick.
     * [LiveStats.Gpu.available] is false on emulators or devices where the GPU
     * sysfs path is inaccessible (SELinux restriction, non-standard SoC layout).
     */
    private val _gpu = MutableStateFlow<LiveStats.Gpu?>(null)
    val gpu: StateFlow<LiveStats.Gpu?> = _gpu.asStateFlow()

    /** Live Wi-Fi RSSI in dBm. null when not connected to Wi-Fi or unavailable. */
    private val _wifiRssiDbm = MutableStateFlow<Int?>(null)
    val wifiRssiDbm: StateFlow<Int?> = _wifiRssiDbm.asStateFlow()
    /** Live Wi-Fi link speed in Mbps. null when not connected to Wi-Fi or unavailable. */
    private val _wifiLinkSpeedMbps = MutableStateFlow<Int?>(null)
    val wifiLinkSpeedMbps: StateFlow<Int?> = _wifiLinkSpeedMbps.asStateFlow()
    /** Estimated time to full charge in milliseconds. -1L when discharging or unavailable. */
    private val _chargeTimeRemainingMs = MutableStateFlow(-1L)
    val chargeTimeRemainingMs: StateFlow<Long> = _chargeTimeRemainingMs.asStateFlow()

    /** Live screen brightness as a percentage (0–100). null when the settings read fails. */
    private val _screenBrightnessPct = MutableStateFlow<Int?>(null)
    val screenBrightnessPct: StateFlow<Int?> = _screenBrightnessPct.asStateFlow()
    private val _screenBrightnessAuto = MutableStateFlow<Boolean?>(null)
    val screenBrightnessAuto: StateFlow<Boolean?> = _screenBrightnessAuto.asStateFlow()

    private val _batterySaverActive = MutableStateFlow<Boolean?>(null)
    val batterySaverActive: StateFlow<Boolean?> = _batterySaverActive.asStateFlow()

    /** Whether airplane mode is currently on. */
    private val _isAirplaneModeOn = MutableStateFlow<Boolean?>(null)
    val isAirplaneModeOn: StateFlow<Boolean?> = _isAirplaneModeOn.asStateFlow()

    /** Whether NFC is enabled, null when device has no NFC hardware. */
    private val _isNfcEnabled = MutableStateFlow<Boolean?>(null)
    val isNfcEnabled: StateFlow<Boolean?> = _isNfcEnabled.asStateFlow()

    /** Cumulative bytes received across all interfaces since the last device reboot.
     *  Polled each live-loop tick from [LiveStats.totalRxBytes].
     *  null when [android.net.TrafficStats.UNSUPPORTED] is returned by the system. */
    private val _totalRxBytes = MutableStateFlow<Long?>(null)
    val totalRxBytes: StateFlow<Long?> = _totalRxBytes.asStateFlow()

    /** Cumulative bytes transmitted across all interfaces since the last device reboot.
     *  Polled each live-loop tick from [LiveStats.totalTxBytes].
     *  null when [android.net.TrafficStats.UNSUPPORTED] is returned by the system. */
    private val _totalTxBytes = MutableStateFlow<Long?>(null)
    val totalTxBytes: StateFlow<Long?> = _totalTxBytes.asStateFlow()

    /** Ambient light sensor reading in lux. null when no hardware or before first event. */
    private val _lightLux = MutableStateFlow<Float?>(null)
    val lightLux: StateFlow<Float?> = _lightLux.asStateFlow()

    /** Barometric pressure in hPa from [Sensor.TYPE_PRESSURE]. null before first event or
     *  when the device has no barometer hardware. Silent no-op on devices without the sensor. */
    private val _pressureHpa = MutableStateFlow<Float?>(null)
    val pressureHpa: StateFlow<Float?> = _pressureHpa.asStateFlow()

    /** Ambient air temperature in °C from [Sensor.TYPE_AMBIENT_TEMPERATURE]. null before first
     *  event or when the device has no ambient temperature sensor hardware. This sensor is rare
     *  on consumer Android phones — most devices will stay null indefinitely. Silent no-op. */
    private val _ambientTempC = MutableStateFlow<Float?>(null)
    val ambientTempC: StateFlow<Float?> = _ambientTempC.asStateFlow()

    /**
     * Steps accumulated since the last device reboot from [Sensor.TYPE_STEP_COUNTER].
     * null before the first event fires or on devices without a hardware step counter.
     *
     * **Accumulator semantics:** the counter monotonically increases from an arbitrary
     * baseline (typically the total steps since boot, reset to 0 on reboot). It is NOT
     * a daily step count. On most flagships (Pixel, Samsung) the counter survives the
     * app process being killed and restarted — it reflects real hardware odometer state.
     *
     * Zero permissions required — [Sensor.TYPE_STEP_COUNTER] is readable by any app
     * targeting API 29+. Silent no-op on devices without a hardware pedometer.
     */
    private val _stepCount = MutableStateFlow<Long?>(null)
    val stepCount: StateFlow<Long?> = _stepCount.asStateFlow()

    /**
     * Current screen refresh rate in Hz from [android.view.Display.getRefreshRate].
     * null in non-UI contexts or when [Context.getDisplay] returns null (service env).
     * On LTPO / VRR displays (Pixel 8 Pro, Samsung Galaxy S24 Ultra) this adapts
     * dynamically — 120 Hz under load, as low as 1 Hz when the screen is static.
     *
     * No permissions required.
     */
    private val _screenRefreshRateHz = MutableStateFlow<Float?>(null)
    val screenRefreshRateHz: StateFlow<Float?> = _screenRefreshRateHz.asStateFlow()

    // -- Settings ----------------------------------------------------------------

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(
        viewModelScope, SharingStarted.Eagerly, Settings(),
    )

    private var liveJob: Job? = null
    private var sensorManager: SensorManager? = null
    private var lightSensorListener: SensorEventListener? = null
    private var pressureSensorListener: SensorEventListener? = null
    private var ambientTempSensorListener: SensorEventListener? = null
    private var stepCounterListener: SensorEventListener? = null

    init {
        refreshPhone()
        requestWatchInfo()
        startLiveLoop()
        startLightSensor()
        startPressureSensor()
        startAmbientTempSensor()
        startStepCounterSensor()
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
                // System uptime — cheap file read, no permissions needed
                _systemUptimeSeconds.value = LiveStats.systemUptimeSeconds()
                // CPU utilisation — two-snapshot /proc/stat delta (same pattern as disk/network)
                val cpuStat = LiveStats.cpuStatSnapshot()
                val prevCpu = prevCpuStat
                if (cpuStat != null && prevCpu != null) {
                    _cpuUsagePercent.value = LiveStats.cpuUsagePercent(prevCpu, cpuStat)
                }
                prevCpuStat = cpuStat
                // Thermal zones — all accessible zones ≥ 25 °C, sorted hottest-first
                _thermalZones.value = LiveStats.thermalZones()
                // GPU — frequency, utilisation, governor (kgsl or generic devfreq)
                _gpu.value = LiveStats.gpu()
                // Wi-Fi RSSI — live signal strength in dBm (null when not on Wi-Fi)
                _wifiRssiDbm.value = LiveStats.wifiRssi(ctx)
                // Wi-Fi link speed — negotiated data rate in Mbps (null when not on Wi-Fi)
                _wifiLinkSpeedMbps.value = LiveStats.wifiLinkSpeedMbps(ctx)
                // Charge time remaining — BatteryManager estimate, -1L when discharging or unavailable
                _chargeTimeRemainingMs.value = LiveStats.chargeTimeRemainingMs(ctx)
                // Screen brightness — Settings.System.SCREEN_BRIGHTNESS, no permissions needed
                _screenBrightnessPct.value = LiveStats.screenBrightnessPercent(ctx)
                // Adaptive brightness mode — Settings.System.SCREEN_BRIGHTNESS_MODE, no permissions needed
                _screenBrightnessAuto.value = LiveStats.screenBrightnessAuto(ctx)
                // Battery saver mode — PowerManager.isPowerSaveMode(), no permissions needed
                _batterySaverActive.value = LiveStats.batterySaverActive(ctx)
                // Airplane mode — Settings.Global.AIRPLANE_MODE_ON, no permissions needed
                _isAirplaneModeOn.value = LiveStats.isAirplaneModeOn(ctx)
                // NFC state — NfcAdapter.getDefaultAdapter, null when no NFC hardware
                _isNfcEnabled.value = LiveStats.isNfcEnabled(ctx)
                // Network traffic totals since boot — TrafficStats, polled, no permissions needed
                _totalRxBytes.value = LiveStats.totalRxBytes()
                _totalTxBytes.value = LiveStats.totalTxBytes()
                // Screen refresh rate — Display.getRefreshRate(), no permissions needed
                _screenRefreshRateHz.value = LiveStats.screenRefreshRateHz(ctx)
                val intervalMs = (settings.value.refreshIntervalSeconds.coerceIn(1, 60)) * 1000L
                delay(intervalMs)
            }
        }
    }

    // -- Sensor listeners --------------------------------------------------------

    /**
     * Registers a [SensorEventListener] for [Sensor.TYPE_LIGHT] using
     * [SensorManager.SENSOR_DELAY_NORMAL] (~200ms). Unlike polled stats, the
     * ambient-light sensor is push-based — it fires on value changes rather than
     * on a fixed timer. The listener updates [_lightLux] directly; the StateFlow
     * is thread-safe so no coroutine dispatch is needed.
     *
     * No-ops silently when the device has no ambient-light sensor hardware.
     */
    private fun startLightSensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
        sensorManager = sm
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val lux = event?.values?.firstOrNull() ?: return
                _lightLux.value = lux
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        lightSensorListener = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * Registers a [SensorEventListener] for [Sensor.TYPE_PRESSURE] using
     * [SensorManager.SENSOR_DELAY_NORMAL] (~200ms). Follows the same push-based
     * pattern as [startLightSensor] — the hardware fires events on value change.
     * The listener updates [_pressureHpa] directly (StateFlow is thread-safe).
     *
     * No-ops silently when the device has no barometer hardware.
     */
    private fun startPressureSensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val hpa = event?.values?.firstOrNull() ?: return
                _pressureHpa.value = hpa
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        pressureSensorListener = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * Registers a [SensorEventListener] for [Sensor.TYPE_AMBIENT_TEMPERATURE] using
     * [SensorManager.SENSOR_DELAY_NORMAL] (~200ms). Follows the same push-based pattern
     * as [startLightSensor] and [startPressureSensor] — events fire on value change.
     * The listener updates [_ambientTempC] directly (StateFlow is thread-safe).
     *
     * This sensor is rare on consumer Android phones (mostly present on IoT/industrial
     * devices). No-ops silently when the device has no ambient temperature hardware.
     */
    private fun startAmbientTempSensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val tempC = event?.values?.firstOrNull() ?: return
                _ambientTempC.value = tempC
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        ambientTempSensorListener = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * Registers a [SensorEventListener] for [Sensor.TYPE_STEP_COUNTER] using
     * [SensorManager.SENSOR_DELAY_NORMAL] (~200ms). The step counter is a hardware
     * accumulator — it fires events with the running total of steps since the last
     * device reboot. Unlike level sensors (lux, hPa, °C), its value is a monotonically
     * increasing [Long], so [_stepCount] stores the raw counter value directly.
     *
     * The listener writes [_stepCount] from the hardware callback thread; [MutableStateFlow]
     * is thread-safe so no coroutine dispatch is needed.
     *
     * Zero permissions required. Silent no-op on devices without a hardware step counter.
     */
    private fun startStepCounterSensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val steps = event?.values?.firstOrNull() ?: return
                _stepCount.value = steps.toLong()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        stepCounterListener = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onCleared() {
        liveJob?.cancel()
        lightSensorListener?.let { sensorManager?.unregisterListener(it) }
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        pressureSensorListener?.let { sm?.unregisterListener(it) }
        ambientTempSensorListener?.let { sm?.unregisterListener(it) }
        stepCounterListener?.let { sm?.unregisterListener(it) }
        super.onCleared()
    }
}

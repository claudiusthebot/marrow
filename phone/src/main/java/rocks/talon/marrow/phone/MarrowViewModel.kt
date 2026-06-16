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

    /** System uptime in seconds since last boot (from /proc/uptime).
     *  0L until the first live-loop tick. */
    private val _systemUptimeSeconds = MutableStateFlow(0L)
    val systemUptimeSeconds: StateFlow<Long> = _systemUptimeSeconds.asStateFlow()

    private val _deepSleepFractionPct = MutableStateFlow(-1)
    val deepSleepFractionPct: StateFlow<Int> = _deepSleepFractionPct.asStateFlow()

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
    /** Live Wi-Fi frequency in MHz. null when not connected to Wi-Fi or unavailable.
     *  2.4 GHz band ≈ 2412–2484 MHz; 5 GHz band ≈ 5180–5825 MHz; 6 GHz (Wi-Fi 6E/7) ≈ 5945–7125 MHz. */
    private val _wifiFrequencyMhz = MutableStateFlow<Int?>(null)
    val wifiFrequencyMhz: StateFlow<Int?> = _wifiFrequencyMhz.asStateFlow()
    /** 802.11 Wi-Fi standard label for the current connection.
     *  "Wi-Fi 4" (802.11n) / "Wi-Fi 5" (802.11ac) / "Wi-Fi 6" (802.11ax) / "Wi-Fi 7" (802.11be).
     *  null when not on Wi-Fi, standard is legacy/unknown, or before first live-loop tick. */
    private val _wifiStandard = MutableStateFlow<String?>(null)
    val wifiStandard: StateFlow<String?> = _wifiStandard.asStateFlow()
    /** Device's primary IPv4 address (e.g. "192.168.1.42"). null when no active IPv4 interface. */
    private val _localIpV4 = MutableStateFlow<String?>(null)
    val localIpV4: StateFlow<String?> = _localIpV4.asStateFlow()
    /** Device's primary global-scope IPv6 address. null when no active global IPv6 interface. */
    private val _localIpV6 = MutableStateFlow<String?>(null)
    val localIpV6: StateFlow<String?> = _localIpV6.asStateFlow()

    private val _wifiSsid = MutableStateFlow<String?>(null)
    val wifiSsid: StateFlow<String?> = _wifiSsid.asStateFlow()
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

    /**
     * System thermal throttle status: "None"/"Light"/"Moderate"/"Severe"/"Critical"/
     * "Emergency"/"Shutdown". Null on API < 29 or unavailable PowerManager.
     * Polled each live-loop tick. No permissions required.
     */
    private val _thermalStatus = MutableStateFlow<String?>(null)
    val thermalStatus: StateFlow<String?> = _thermalStatus.asStateFlow()

    /** Whether airplane mode is currently on. */
    private val _isAirplaneModeOn = MutableStateFlow<Boolean?>(null)
    val isAirplaneModeOn: StateFlow<Boolean?> = _isAirplaneModeOn.asStateFlow()

    /** Whether NFC is enabled, null when device has no NFC hardware. */
    private val _isNfcEnabled = MutableStateFlow<Boolean?>(null)
    val isNfcEnabled: StateFlow<Boolean?> = _isNfcEnabled.asStateFlow()

    /** Whether Bluetooth is enabled. Reads Settings.Global "bluetooth_on" — zero permissions. */
    private val _isBluetoothEnabled = MutableStateFlow<Boolean?>(null)
    val isBluetoothEnabled: StateFlow<Boolean?> = _isBluetoothEnabled.asStateFlow()

    /** Whether the mobile hotspot (Wi-Fi tethering) is currently active.
     *  Reads [android.net.wifi.WifiManager.isWifiApEnabled] — zero permissions required.
     *  null before first live-loop tick or on any exception. */
    private val _isHotspotEnabled = MutableStateFlow<Boolean?>(null)
    val isHotspotEnabled: StateFlow<Boolean?> = _isHotspotEnabled.asStateFlow()

    /** Whether a VPN tunnel is currently active. Checked via NetworkCapabilities.TRANSPORT_VPN.
     *  null when ConnectivityManager is unavailable; false when no active network. */
    private val _isVpnActive = MutableStateFlow<Boolean?>(null)
    val isVpnActive: StateFlow<Boolean?> = _isVpnActive.asStateFlow()

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

    /** Relative humidity percentage from [Sensor.TYPE_RELATIVE_HUMIDITY]. null before the
     *  first event or when the device has no humidity sensor. This sensor is rare on
     *  consumer phones — present on some Samsung devices but absent on most Pixels and
     *  other flagships. Silent no-op when unavailable. */
    private val _relativeHumidityPct = MutableStateFlow<Float?>(null)
    val relativeHumidityPct: StateFlow<Float?> = _relativeHumidityPct.asStateFlow()

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

    /**
     * Live compass bearing in degrees (0–360) from [Sensor.TYPE_ROTATION_VECTOR].
     * The rotation vector sensor fuses accelerometer + magnetometer + gyroscope via
     * Android's built-in sensor fusion stack — no raw sensor maths required.
     * null before the first sensor event fires or on devices without the hardware.
     *
     * Zero permissions required. Silent no-op on devices without the sensor
     * (TYPE_ROTATION_VECTOR is available on API 9+ and present on all modern flagships).
     */
    private val _compassBearingDeg = MutableStateFlow<Float?>(null)
    val compassBearingDeg: StateFlow<Float?> = _compassBearingDeg.asStateFlow()

    /** Device tilt angle in degrees from [Sensor.TYPE_GRAVITY].
     * Tilt = acos(gz / magnitude) × 180/π, where (gx, gy, gz) is the gravity vector.
     * 0° = device lying flat face-up; ~90° = device held upright in portrait orientation.
     * null before first sensor event or on devices without the sensor.
     * Zero permissions required.
     */
    private val _tiltAngleDeg = MutableStateFlow<Float?>(null)
    val tiltAngleDeg: StateFlow<Float?> = _tiltAngleDeg.asStateFlow()

    /** Gyroscope rotation rate magnitude in rad/s from [Sensor.TYPE_GYROSCOPE].
     * magnitude = √(ωx² + ωy² + ωz²), where (ωx, ωy, ωz) are angular velocities
     * around the X, Y, and Z axes respectively.
     * 0 rad/s = device at rest; increases as the device rotates.
     * null before first sensor event or on devices without a gyroscope.
     * Zero permissions required.
     */
    private val _gyroMagnitude = MutableStateFlow<Float?>(null)
    val gyroMagnitude: StateFlow<Float?> = _gyroMagnitude.asStateFlow()

    /**
     * Linear acceleration magnitude in m/s² from [Sensor.TYPE_LINEAR_ACCELERATION].
     * [Sensor.TYPE_LINEAR_ACCELERATION] is a composite sensor — Android subtracts the
     * gravity vector from the raw accelerometer reading, leaving only motion-induced
     * acceleration. Near 0 m/s² when the device is at rest; spikes when the device
     * is jolted, shaken, tapped, or dropped.
     *
     * magnitude = √(ax² + ay² + az²), where (ax, ay, az) are the linear acceleration
     * components in m/s².
     *
     * null before the first sensor event fires or on devices without an accelerometer
     * (extremely rare — all modern Android phones have one). Zero permissions required.
     */
    private val _linearAccelMagnitude = MutableStateFlow<Float?>(null)
    val linearAccelMagnitude: StateFlow<Float?> = _linearAccelMagnitude.asStateFlow()

    /**
     * Magnetic field strength magnitude in µT from [Sensor.TYPE_MAGNETIC_FIELD].
     * magnitude = √(Bx² + By² + Bz²), where (Bx, By, Bz) are the raw magnetometer
     * components in microtesla.
     *
     * Earth's ambient geomagnetic field is typically 25–65 µT depending on location.
     * Anomalies from nearby power cables, electric motors, speaker magnets, or strong
     * permanent magnets deviate significantly from this range.
     *
     * Distinct from the compass bearing ([_compassBearingDeg] / [Sensor.TYPE_ROTATION_VECTOR]):
     * that sensor fuses accelerometer + magnetometer + gyroscope into a heading angle;
     * this is the raw magnetometer magnitude — a physical field-strength measurement
     * rather than a directional reading.
     *
     * null before the first sensor event fires. Present on all modern Android phones.
     * Zero permissions required.
     */
    private val _magneticFieldUt = MutableStateFlow<Float?>(null)
    val magneticFieldUt: StateFlow<Float?> = _magneticFieldUt.asStateFlow()

    // -- Proximity ---------------------------------------------------------------

    /**
     * Distance to the nearest detected object from [Sensor.TYPE_PROXIMITY] in centimetres.
     * On most phones the sensor is binary: 0.0 cm = object detected (NEAR), and
     * [Sensor.getMaximumRange] (typically 5.0 cm) = no object (FAR). Some phones
     * (e.g. Sony Xperia) return actual interpolated distances. null before the first
     * sensor event. Phone only. Zero permissions required.
     */
    private val _proximityDistanceCm = MutableStateFlow<Float?>(null)
    val proximityDistanceCm: StateFlow<Float?> = _proximityDistanceCm.asStateFlow()

    // -- Audio -------------------------------------------------------------------

    /** Current ringer mode from [android.media.AudioManager]. null when unavailable.
     *  Polled each live-loop tick. No permissions required. */
    private val _ringerMode = MutableStateFlow<LiveStats.RingerMode?>(null)
    val ringerMode: StateFlow<LiveStats.RingerMode?> = _ringerMode.asStateFlow()

    /** Current media (music) stream volume as a percentage (0–100).
     *  null when [android.media.AudioManager] is unavailable or max volume is 0.
     *  Polled each live-loop tick. No permissions required. */
    private val _mediaVolumePct = MutableStateFlow<Int?>(null)
    val mediaVolumePct: StateFlow<Int?> = _mediaVolumePct.asStateFlow()

    /** Whether music is currently active via [android.media.AudioManager.isMusicActive].
     *  null when [AudioManager] is unavailable. Polled each live-loop tick.
     *  No permissions required. */
    private val _isMusicActive = MutableStateFlow<Boolean?>(null)
    val isMusicActive: StateFlow<Boolean?> = _isMusicActive.asStateFlow()

    /** Current Do Not Disturb interruption filter as a label ("Off", "Priority", "Alarms",
     *  "Total Silence"). null when [NotificationManager] is unavailable or filter is unknown.
     *  Polled each live-loop tick. No permissions required. */
    private val _dndMode = MutableStateFlow<String?>(null)
    val dndMode: StateFlow<String?> = _dndMode.asStateFlow()

    /** Current ring stream volume as a percentage (0–100).
     *  null when [android.media.AudioManager] is unavailable or max volume is 0.
     *  Polled each live-loop tick. No permissions required. */
    private val _ringVolumePct = MutableStateFlow<Int?>(null)
    val ringVolumePct: StateFlow<Int?> = _ringVolumePct.asStateFlow()

    /** Current alarm stream volume as a percentage (0–100).
     *  null when [android.media.AudioManager] is unavailable or max volume is 0.
     *  Polled each live-loop tick. No permissions required. */
    private val _alarmVolumePct = MutableStateFlow<Int?>(null)
    val alarmVolumePct: StateFlow<Int?> = _alarmVolumePct.asStateFlow()

    /** Current notification stream volume as a percentage (0–100).
     *  null when [android.media.AudioManager] is unavailable or max volume is 0.
     *  Polled each live-loop tick. No permissions required. */
    private val _notificationVolumePct = MutableStateFlow<Int?>(null)
    val notificationVolumePct: StateFlow<Int?> = _notificationVolumePct.asStateFlow()

    /** Current system stream volume as a percentage (0–100), polled from [LiveStats.systemVolumePct].
     *  null until first live-loop tick. No permissions required. */
    private val _systemVolumePct = MutableStateFlow<Int?>(null)
    val systemVolumePct: StateFlow<Int?> = _systemVolumePct.asStateFlow()

    /** Current voice-call stream volume as a percentage (0–100), polled from [LiveStats.voiceCallVolumePct].
     *  Reflects [android.media.AudioManager.STREAM_VOICE_CALL] — the in-ear / hands-free call volume.
     *  null until first live-loop tick. No permissions required. */
    private val _voiceCallVolumePct = MutableStateFlow<Int?>(null)
    val voiceCallVolumePct: StateFlow<Int?> = _voiceCallVolumePct.asStateFlow()

    /** Accessibility audio stream volume as a percentage (0–100).
     *  Reflects [android.media.AudioManager.STREAM_ACCESSIBILITY] (API 26+).
     *  null until first live-loop tick. No permissions required. */
    private val _accessibilityVolumePct = MutableStateFlow<Int?>(null)
    val accessibilityVolumePct: StateFlow<Int?> = _accessibilityVolumePct.asStateFlow()

    // -- Display mode ------------------------------------------------------------

    /** Whether the system is currently in dark mode. Reads [android.content.res.Configuration.uiMode].
     *  null on exception (should not occur in practice). Polled each live-loop tick.
     *  No permissions required. */
    private val _isDarkMode = MutableStateFlow<Boolean?>(null)
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    /** Whether auto-rotate is enabled. Reads [android.provider.Settings.System.ACCELEROMETER_ROTATION].
     *  null when the setting key is absent. Polled each live-loop tick. No permissions required. */
    private val _isAutoRotateEnabled = MutableStateFlow<Boolean?>(null)
    val isAutoRotateEnabled: StateFlow<Boolean?> = _isAutoRotateEnabled.asStateFlow()

    /** Screen-off timeout in milliseconds from [android.provider.Settings.System.SCREEN_OFF_TIMEOUT].
     *  null when the key is absent or before first live-loop tick. No permissions required. */
    private val _screenTimeoutMs = MutableStateFlow<Int?>(null)
    val screenTimeoutMs: StateFlow<Int?> = _screenTimeoutMs.asStateFlow()

    // -- Cellular ----------------------------------------------------------------

    /**
     * Live cellular/telephony snapshot from [LiveStats.cellularInfo].
     *
     * Updated each live-loop tick. Null on devices without telephony hardware
     * (Wi-Fi-only tablets, emulators) or before the first live-loop tick.
     *
     * Fields that require [android.Manifest.permission.READ_BASIC_PHONE_STATE]
     * (API 33+ normal permission) are populated automatically without any runtime prompt.
     * They fall back to null on API < 33 if [android.Manifest.permission.READ_PHONE_STATE]
     * is not held.
     */
    private val _cellular = MutableStateFlow<LiveStats.Cellular?>(null)
    val cellular: StateFlow<LiveStats.Cellular?> = _cellular.asStateFlow()

    /** Current phone call state: "Idle", "Ringing", or "In Call". Null on devices
     *  without telephony or when READ_BASIC_PHONE_STATE is unavailable (API < 31). */
    private val _callState = MutableStateFlow<String?>(null)
    val callState: StateFlow<String?> = _callState.asStateFlow()

    // -- Location ----------------------------------------------------------------

    /**
     * Most recent [android.location.Location] fix from GPS or network provider.
     * null until [initLocationUpdates] is called (after ACCESS_FINE_LOCATION is
     * granted) and at least one fix arrives. Updated via [android.location.LocationListener]
     * registered with [android.location.LocationManager.GPS_PROVIDER] and
     * [android.location.LocationManager.NETWORK_PROVIDER] simultaneously — GPS
     * provides higher accuracy when available; network fills in between GPS fixes.
     *
     * Requires ACCESS_FINE_LOCATION runtime permission. Caller must invoke
     * [initLocationUpdates] after the user grants the permission.
     */
    private val _lastLocation = MutableStateFlow<android.location.Location?>(null)
    val lastLocation: StateFlow<android.location.Location?> = _lastLocation.asStateFlow()

    // -- Settings ----------------------------------------------------------------

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(
        viewModelScope, SharingStarted.Eagerly, Settings(),
    )

    private var liveJob: Job? = null
    private var sensorManager: SensorManager? = null
    private var locationManager: android.location.LocationManager? = null
    private var locationListener: android.location.LocationListener? = null
    private var lightSensorListener: SensorEventListener? = null
    private var pressureSensorListener: SensorEventListener? = null
    private var ambientTempSensorListener: SensorEventListener? = null
    private var stepCounterListener: SensorEventListener? = null
    private var rotationVectorListener: SensorEventListener? = null
    private var gravitySensorListener: SensorEventListener? = null
    private var gyroscopeListener: SensorEventListener? = null
    private var linearAccelListener: SensorEventListener? = null
    private var magnetometerListener: SensorEventListener? = null
    private var proximityListener: SensorEventListener? = null
    private var humidityListener: SensorEventListener? = null

    init {
        refreshPhone()
        requestWatchInfo()
        startLiveLoop()
        startLightSensor()
        startPressureSensor()
        startAmbientTempSensor()
        startRelativeHumiditySensor()
        startStepCounterSensor()
        startRotationVectorSensor()
        startGravitySensor()
        startGyroscopeSensor()
        startLinearAccelSensor()
        startMagnetometerSensor()
        startProximitySensor()
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
                // Deep sleep fraction — SystemClock delta, no permissions
                _deepSleepFractionPct.value = LiveStats.deepSleepFractionPct()
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
                // Wi-Fi frequency band — MHz for 2.4/5/6 GHz band detection (null when not on Wi-Fi)
                _wifiFrequencyMhz.value = LiveStats.wifiFrequencyMhz(ctx)
                // Wi-Fi standard — 802.11n/ac/ax/be label (null when not on Wi-Fi or legacy)
                _wifiStandard.value = LiveStats.wifiStandard(ctx)
                // Wi-Fi SSID — ACCESS_FINE_LOCATION required (API 29+); null when not connected or permission denied
                _wifiSsid.value = LiveStats.wifiSsid(ctx)
                // Local IPv4 address — NetworkInterface, no permissions needed
                _localIpV4.value = LiveStats.localIpV4()
                // Local IPv6 address — NetworkInterface, no permissions needed; global scope only
                _localIpV6.value = LiveStats.localIpV6()
                // Charge time remaining — BatteryManager estimate, -1L when discharging or unavailable
                _chargeTimeRemainingMs.value = LiveStats.chargeTimeRemainingMs(ctx)
                // Screen brightness — Settings.System.SCREEN_BRIGHTNESS, no permissions needed
                _screenBrightnessPct.value = LiveStats.screenBrightnessPercent(ctx)
                // Adaptive brightness mode — Settings.System.SCREEN_BRIGHTNESS_MODE, no permissions needed
                _screenBrightnessAuto.value = LiveStats.screenBrightnessAuto(ctx)
                // Battery saver mode — PowerManager.isPowerSaveMode(), no permissions needed
                _batterySaverActive.value = LiveStats.batterySaverActive(ctx)
                // Thermal throttle status — PowerManager.getCurrentThermalStatus(), API 29+, no permissions
                _thermalStatus.value = LiveStats.thermalStatusStr(ctx)
                // Airplane mode — Settings.Global.AIRPLANE_MODE_ON, no permissions needed
                _isAirplaneModeOn.value = LiveStats.isAirplaneModeOn(ctx)
                // NFC state — NfcAdapter.getDefaultAdapter, null when no NFC hardware
                _isNfcEnabled.value = LiveStats.isNfcEnabled(ctx)
                // Bluetooth state — Settings.Global "bluetooth_on", zero permissions
                _isBluetoothEnabled.value = LiveStats.isBluetoothEnabled(ctx)
                // Hotspot state — WifiManager.isWifiApEnabled(), no permissions needed
                _isHotspotEnabled.value = LiveStats.isHotspotEnabled(ctx)
                // VPN state — NetworkCapabilities.TRANSPORT_VPN, no extra permissions
                _isVpnActive.value = LiveStats.isVpnActive(ctx)
                // Network traffic totals since boot — TrafficStats, polled, no permissions needed
                _totalRxBytes.value = LiveStats.totalRxBytes()
                _totalTxBytes.value = LiveStats.totalTxBytes()
                // Screen refresh rate — Display.getRefreshRate(), no permissions needed
                _screenRefreshRateHz.value = LiveStats.screenRefreshRateHz(ctx)
                // Audio stats — AudioManager, no permissions needed
                _ringerMode.value = LiveStats.ringerMode(ctx)
                _mediaVolumePct.value = LiveStats.mediaVolumePct(ctx)
                _isMusicActive.value = LiveStats.isMusicActive(ctx)
                // DND mode — NotificationManager.currentInterruptionFilter, no permissions needed
                _dndMode.value = LiveStats.dndMode(ctx)
                // Ring, alarm, notification, system, and voice-call volume — AudioManager, no permissions needed
                _ringVolumePct.value = LiveStats.ringVolumePct(ctx)
                _alarmVolumePct.value = LiveStats.alarmVolumePct(ctx)
                _notificationVolumePct.value = LiveStats.notificationVolumePct(ctx)
                _systemVolumePct.value = LiveStats.systemVolumePct(ctx)
                _voiceCallVolumePct.value = LiveStats.voiceCallVolumePct(ctx)
                // Accessibility volume — STREAM_ACCESSIBILITY (API 26+, minSdk=30), no permissions needed
                _accessibilityVolumePct.value = LiveStats.accessibilityVolumePct(ctx)
                // Dark mode — Configuration.uiMode, no permissions needed
                _isDarkMode.value = LiveStats.isDarkMode(ctx)
                // Auto-rotate — Settings.System.ACCELEROMETER_ROTATION, no permissions needed
                _isAutoRotateEnabled.value = LiveStats.isAutoRotateEnabled(ctx)
                // Screen timeout — Settings.System.SCREEN_OFF_TIMEOUT, no permissions needed
                _screenTimeoutMs.value = LiveStats.screenTimeoutMs(ctx)
                // Cellular stats — READ_BASIC_PHONE_STATE (normal, API 33+); null on Wi-Fi-only devices
                _cellular.value = LiveStats.cellularInfo(ctx)
                // Call state — READ_BASIC_PHONE_STATE (normal, API 31+); null on API 30 or Wi-Fi-only
                _callState.value = LiveStats.callState(ctx)
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
     * Registers a [SensorEventListener] for [Sensor.TYPE_RELATIVE_HUMIDITY] using
     * [SensorManager.SENSOR_DELAY_NORMAL]. Events fire when humidity changes; values
     * are in % RH (0.0–100.0). Follows the same push-based pattern as
     * [startAmbientTempSensor] — rare on consumer phones (absent on most Pixels,
     * present on some Samsung devices). Silent no-op when sensor unavailable.
     */
    private fun startRelativeHumiditySensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val pct = event?.values?.firstOrNull() ?: return
                _relativeHumidityPct.value = pct
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        humidityListener = listener
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

    /**
     * Registers a [SensorEventListener] for [Sensor.TYPE_ROTATION_VECTOR] using
     * [SensorManager.SENSOR_DELAY_NORMAL] (~200ms). The rotation vector is a virtual /
     * composite sensor that fuses accelerometer + magnetometer + gyroscope data via
     * Android's own sensor fusion stack, producing a smoothed orientation estimate.
     *
     * The azimuth (orientation[0]) is extracted via [SensorManager.getRotationMatrixFromVector]
     * + [SensorManager.getOrientation], then converted from radians to a 0–360° bearing and
     * stored in [_compassBearingDeg]. Updates [_compassBearingDeg] directly from the hardware
     * callback thread — [MutableStateFlow] is thread-safe, no coroutine dispatch needed.
     *
     * Zero permissions required. Silent no-op on devices without the sensor (extremely rare;
     * [Sensor.TYPE_ROTATION_VECTOR] has been mandatory since Android 2.3).
     */
    private fun startRotationVectorSensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        val rotMat = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val v = event?.values ?: return
                SensorManager.getRotationMatrixFromVector(rotMat, v)
                SensorManager.getOrientation(rotMat, orientation)
                val deg = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
                _compassBearingDeg.value = deg
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        rotationVectorListener = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * Registers a [SensorEventListener] for [Sensor.TYPE_GRAVITY] using
     * [SensorManager.SENSOR_DELAY_NORMAL] (~200ms). The gravity sensor reports the
     * component of the acceleration due to gravity along each axis (x, y, z) in m/s².
     *
     * Tilt from vertical = acos(gz / magnitude) × 180/π. When the phone lies flat
     * face-up gz ≈ 9.81, tilt ≈ 0°. When held upright in portrait mode gz ≈ 0, tilt ≈ 90°.
     * Stored in [_tiltAngleDeg]. Zero permissions required.
     */
    private fun startGravitySensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val v = event?.values ?: return
                val gx = v[0]; val gy = v[1]; val gz = v[2]
                val mag = Math.sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()
                if (mag == 0f) return
                val tilt = Math.toDegrees(Math.acos((gz / mag).coerceIn(-1f, 1f).toDouble())).toFloat()
                _tiltAngleDeg.value = tilt
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        gravitySensorListener = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * Registers a [SensorEventListener] for [Sensor.TYPE_GYROSCOPE] using
     * [SensorManager.SENSOR_DELAY_NORMAL] (~200ms). Reports angular velocity in rad/s
     * around each axis. Magnitude = √(ωx² + ωy² + ωz²) is stored in [_gyroMagnitude].
     * 0 rad/s at rest; increases when the device rotates.
     * Zero permissions required.
     */
    private fun startGyroscopeSensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val v = event?.values ?: return
                val wx = v[0]; val wy = v[1]; val wz = v[2]
                val mag = Math.sqrt((wx * wx + wy * wy + wz * wz).toDouble()).toFloat()
                _gyroMagnitude.value = mag
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        gyroscopeListener = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * Registers a [SensorEventListener] for [Sensor.TYPE_LINEAR_ACCELERATION] using
     * [SensorManager.SENSOR_DELAY_NORMAL] (~200ms). [Sensor.TYPE_LINEAR_ACCELERATION] is a
     * composite sensor — Android subtracts the gravity vector from the raw accelerometer,
     * leaving only motion-induced acceleration. Near 0 m/s² at rest; spikes on jolts, taps,
     * drops, or walking.
     *
     * magnitude = √(ax² + ay² + az²). Stored in [_linearAccelMagnitude].
     * Zero permissions required. Silent no-op on devices without an accelerometer (extremely rare).
     */
    private fun startLinearAccelSensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val v = event?.values ?: return
                val ax = v[0]; val ay = v[1]; val az = v[2]
                val mag = Math.sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
                _linearAccelMagnitude.value = mag
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        linearAccelListener = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * Registers a [SensorEventListener] for [Sensor.TYPE_MAGNETIC_FIELD] using
     * [SensorManager.SENSOR_DELAY_NORMAL] (~200ms). Reports the raw geomagnetic
     * field vector (Bx, By, Bz) in microtesla. The magnitude √(Bx²+By²+Bz²) is
     * stored in [_magneticFieldUt].
     *
     * Earth's ambient field is ~25–65 µT; anomalies from cables, motors, or magnets
     * deviate significantly. Present on all modern Android phones.
     * Zero permissions required.
     */
    private fun startMagnetometerSensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val v = event?.values ?: return
                val bx = v[0]; val by = v[1]; val bz = v[2]
                val mag = Math.sqrt((bx * bx + by * by + bz * bz).toDouble()).toFloat()
                _magneticFieldUt.value = mag
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        magnetometerListener = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * Registers a [SensorEventListener] for [Sensor.TYPE_PROXIMITY] at
     * [SensorManager.SENSOR_DELAY_NORMAL]. Reports distance to the nearest
     * detected object in centimetres, stored in [_proximityDistanceCm].
     *
     * On most phones the sensor is binary: 0.0 cm when an object is near the
     * earpiece (call proximity) and [Sensor.getMaximumRange] (typically 5.0 cm)
     * when no object is detected. Some devices return interpolated values.
     * Useful for detecting whether the phone is held to the ear, face-down on a
     * surface, or placed near a reflective object. Zero permissions required.
     */
    private fun startProximitySensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val dist = event?.values?.getOrNull(0) ?: return
                _proximityDistanceCm.value = dist
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        proximityListener = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    // -- Location updates --------------------------------------------------------

    /**
     * Registers [android.location.LocationListener]s for GPS and network providers.
     *
     * Must only be called after ACCESS_FINE_LOCATION has been granted at runtime.
     * Safe to call multiple times — a second call is a no-op if the listener is
     * already registered (guard: [_lastLocation] listener exists check skipped;
     * LocationManager internally deduplicates same-listener registrations).
     *
     * Update cadence: minimum 3 seconds / 0 metres. Both providers are registered
     * simultaneously — GPS fires accurate fixes when the device has satellite lock;
     * the network provider fills in between GPS updates.
     *
     * Call from a LaunchedEffect after the permission dialog returns granted,
     * identical to the [initHeartRateSensor] pattern in WearViewModel.
     */
    fun initLocationUpdates() {
        val ctx = getApplication<Application>()
        if (ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE)
            as? android.location.LocationManager ?: return
        locationManager = lm
        val listener = android.location.LocationListener { loc ->
            // Prefer the most accurate fix: keep incoming if we have nothing,
            // or if it is more accurate than the cached fix.
            val prev = _lastLocation.value
            if (prev == null || loc.accuracy <= prev.accuracy) {
                _lastLocation.value = loc
            }
        }
        locationListener = listener
        // Register GPS first (highest accuracy), then network (fills gaps).
        runCatching {
            lm.requestLocationUpdates(
                android.location.LocationManager.GPS_PROVIDER,
                3_000L, 0f, listener,
                android.os.Looper.getMainLooper(),
            )
        }
        runCatching {
            lm.requestLocationUpdates(
                android.location.LocationManager.NETWORK_PROVIDER,
                3_000L, 0f, listener,
                android.os.Looper.getMainLooper(),
            )
        }
    }

    override fun onCleared() {
        liveJob?.cancel()
        locationListener?.let { locationManager?.removeUpdates(it) }
        lightSensorListener?.let { sensorManager?.unregisterListener(it) }
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        pressureSensorListener?.let { sm?.unregisterListener(it) }
        ambientTempSensorListener?.let { sm?.unregisterListener(it) }
        stepCounterListener?.let { sm?.unregisterListener(it) }
        rotationVectorListener?.let { sm?.unregisterListener(it) }
        gravitySensorListener?.let { sm?.unregisterListener(it) }
        gyroscopeListener?.let { sm?.unregisterListener(it) }
        linearAccelListener?.let { sm?.unregisterListener(it) }
        magnetometerListener?.let { sm?.unregisterListener(it) }
        proximityListener?.let { sm?.unregisterListener(it) }
        humidityListener?.let { sm?.unregisterListener(it) }
        super.onCleared()
    }
}

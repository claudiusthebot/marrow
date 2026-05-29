package rocks.talon.marrow.wear

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
 * v0.45.0 additions:
 * - Heart rate sensor via SensorEventListener on TYPE_HEART_RATE, registered in
 *   [initHeartRateSensor] (called from [init]) and unregistered in [onCleared].
 *   Exposed as [heartRateBpm] StateFlow<Float?>. Requires BODY_SENSORS permission
 *   (declared in AndroidManifest.xml; runtime permission request delegated to
 *   MainActivity on first use via Compose permission state). Stays null on devices
 *   without the sensor or when permission is denied.
 *
 * v0.15.0 additions:
 * - Step counter via SensorEventListener on TYPE_STEP_COUNTER, registered in
 *   [initStepCounter] (called from [init]) and unregistered in [onCleared].
 *   Exposed as [stepCount] StateFlow. Mirrors the tile-side implementation
 *   in StatsTileService. Zero new permissions — TYPE_STEP_COUNTER is available
 *   without ACTIVITY_RECOGNITION on API 29+.
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

    /**
     * Cumulative step count since last device reboot via TYPE_STEP_COUNTER.
     * null until the first sensor event fires (typically < 1 s on hardware that
     * has a step-counter pedometer; stays null on emulators and watches without
     * the sensor).
     *
     * Updated on the main thread by [stepListener]; safe to collect from
     * Compose.
     */
    private val _stepCount = MutableStateFlow<Long?>(null)
    val stepCount: StateFlow<Long?> = _stepCount.asStateFlow()

    /**
     * Live heart rate in beats per minute via TYPE_HEART_RATE.
     *
     * null until the first sensor event fires or when the BODY_SENSORS
     * permission has not been granted. Typical latency after grant: 1–3 s
     * depending on the watch's optical HR sensor warm-up cycle. The sensor
     * delivers updates at ~1 Hz while the display is on.
     *
     * Requires android.permission.BODY_SENSORS — the first dangerous
     * permission in Marrow. Declared in AndroidManifest.xml; the runtime
     * grant must be requested from the Compose UI before the reading will
     * appear (WearRoot requests it on HeartRateCard display).
     */
    private val _heartRateBpm = MutableStateFlow<Float?>(null)
    val heartRateBpm: StateFlow<Float?> = _heartRateBpm.asStateFlow()

    /** Retained so we can unregister in [onCleared]. */
    private var stepSensorManager: SensorManager? = null
    private var heartRateSensorManager: SensorManager? = null

    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                _stepCount.value = event.values[0].toLong()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val heartRateListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_HEART_RATE && event.values.isNotEmpty()) {
                val bpm = event.values[0]
                // values[0] == 0 means the sensor has not produced a valid reading yet
                if (bpm > 0f) _heartRateBpm.value = bpm
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private var liveJob: Job? = null

    init {
        refresh()
        checkPhoneReachable()
        initStepCounter()
        initHeartRateSensor()
    }

    /**
     * Register a TYPE_STEP_COUNTER SensorEventListener. Idempotent — silently
     * no-ops on devices without a pedometer sensor.
     */
    private fun initStepCounter() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        stepSensorManager = sm
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        sm.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * Register a TYPE_HEART_RATE SensorEventListener.
     *
     * The BODY_SENSORS permission is required at runtime on Wear OS 4+ (API 33).
     * This method silently no-ops if the sensor is absent or the permission has
     * not been granted yet — the HR card remains hidden until a valid reading
     * arrives. [WearRoot] requests the permission before this value is checked.
     *
     * SENSOR_DELAY_NORMAL (~5 Hz max) is sufficient; HR sensors self-throttle
     * to ~1 Hz regardless of the requested rate.
     */
    fun initHeartRateSensor() {
        val sm = getApplication<Application>()
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        heartRateSensorManager = sm
        val sensor = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE) ?: return
        sm.registerListener(heartRateListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
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
        stepSensorManager?.unregisterListener(stepListener)
        heartRateSensorManager?.unregisterListener(heartRateListener)
        super.onCleared()
    }

    enum class PingState { IDLE, SENDING, SENT, FAILED }
}

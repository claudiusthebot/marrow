package rocks.talon.marrow.shared

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.provider.Settings
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.math.roundToInt

/**
 * Lightweight, polled snapshots of the device’s most-watched stats. Designed
 * for the phone’s “live stats strip” + per-section hero treatments — they update
 * every few seconds and need numeric values, not pre-formatted strings.
 *
 * Every call is safe on a missing-hardware device: each accessor returns a
 * sentinel when it can’t read the value, never throws.
 */
object LiveStats {

    // -- Battery -----------------------------------------------------------------

    data class Battery(
        val percent: Int,                 // 0..100, -1 unknown
        val charging: Boolean,
        val plugged: PlugType,
        val temperatureC: Float,          // -1f unknown
        val voltageV: Float,              // -1f unknown
        val currentMa: Int,               // Int.MIN_VALUE unknown
        val technology: String,
        val healthy: Boolean,
        /** Battery wear level: charge_full / charge_full_design × 100.
         *  -1 when the sysfs nodes are absent or unreadable (emulator / OEM restriction). */
        val healthPercent: Int = -1,      // 0..100, -1 unknown
        /** Charge cycle count from sysfs. -1 when absent (emulator / OEM restriction). */
        val cycleCount: Int = -1,         // ≥0, -1 unknown
        /**
         * Instantaneous power draw in milliwatts (mW).
         * Computed as voltageV × |currentMa|; positive = charging, negative = discharging.
         * 0 when both voltage and current are unknown / unavailable.
         */
        val powerMw: Int = 0,
    ) {
        enum class PlugType { NONE, AC, USB, WIRELESS, DOCK }
    }

    fun battery(context: Context): Battery {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale    = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent  = if (rawLevel >= 0 && scale > 0) (rawLevel * 100 / scale).coerceIn(0, 100) else -1
        val status   = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged  = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tempRaw  = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -10) ?: -10
        val voltRaw  = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val tech     = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""
        val healthy  = (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN) == BatteryManager.BATTERY_HEALTH_GOOD
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == BatteryManager.BATTERY_STATUS_FULL
        val plugType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC       -> Battery.PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB      -> Battery.PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> Battery.PlugType.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK     -> Battery.PlugType.DOCK
            else                                    -> Battery.PlugType.NONE
        }
        val tempC  = if (tempRaw > -10) tempRaw / 10f else -1f
        val voltV  = if (voltRaw > 0)   voltRaw / 1000f else -1f
        val bm     = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currMa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                       ?.let { if (it == Int.MIN_VALUE) Int.MIN_VALUE else it / 1000 }
                       ?: Int.MIN_VALUE
        val healthPct = runCatching {
            val full   = readLong("/sys/class/power_supply/battery/charge_full")
            val design = readLong("/sys/class/power_supply/battery/charge_full_design")
            if (design > 0) (full * 100 / design).toInt().coerceIn(0, 100) else -1
        }.getOrDefault(-1)
        val cycleCnt = runCatching {
            readLong("/sys/class/power_supply/battery/cycle_count").toInt().coerceAtLeast(0)
        }.getOrDefault(-1)
        val powerMw = if (voltV > 0 && currMa != Int.MIN_VALUE) {
            (voltV * currMa).roundToInt()
        } else 0
        return Battery(
            percent       = percent,
            charging      = charging,
            plugged       = plugType,
            temperatureC  = tempC,
            voltageV      = voltV,
            currentMa     = currMa,
            technology    = tech,
            healthy       = healthy,
            healthPercent = healthPct,
            cycleCount    = cycleCnt,
            powerMw       = powerMw,
        )
    }

    /**
     * Estimated time until the battery reaches 100% charge, in milliseconds.
     *
     * Reads [BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER] (charge remaining in μAh)
     * and [BatteryManager.BATTERY_PROPERTY_CURRENT_NOW] (instantaneous current in μA)
     * to compute a simple linear estimate. Returns -1L when the battery is not charging,
     * when either property is unavailable, when current is zero or negative (charging
     * hasn’t started or the reading is stale), or when the charge counter is missing.
     *
     * Note: The estimate is linear and ignores charging curve tapering (e.g., the CC/CV
     * transition near 80–100%), so it will typically underestimate the remaining charge
     * time when the battery is above ~80%. It’s a best-effort live read, not a firmware
     * prediction.
     */
    fun chargeTimeRemainingMs(context: Context): Long {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    ?: return -1L
        // is battery charging?
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        if (status != BatteryManager.BATTERY_STATUS_CHARGING &&
            status != BatteryManager.BATTERY_STATUS_FULL) return -1L
        val chargeUah  = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentUa  = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (chargeUah  == Int.MIN_VALUE) return -1L
        if (currentUa  == Int.MIN_VALUE || currentUa <= 0) return -1L
        val capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (capacity == Int.MIN_VALUE || capacity >= 100) return -1L
        // charge_counter is remaining charge in μAh; current_now is in μA.
        // remaining_h = charge_remaining / charging_current.
        // We can’t read charge_full directly from BatteryManager, but:
        //   remaining% = 100 - capacity
        //   remaining_μAh = charge_counter (reported by the FG chip as remaining capacity)
        // So ETA = remaining_μAh / current_μA hours = remaining_μAh * 3_600_000 / current_μA ms
        return (chargeUah.toLong() * 3_600_000L) / currentUa.toLong()
    }

    // -- Memory ------------------------------------------------------------------

    data class Memory(
        val totalBytes: Long,
        val availBytes: Long,
        val threshold: Long,   // low-memory threshold
        val lowMemory: Boolean,
    )

    fun memory(context: Context): Memory {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return Memory(info.totalMem, info.availMem, info.threshold, info.lowMemory)
    }

    // -- CPU / Cores -------------------------------------------------------------

    data class CpuCore(
        val index: Int,
        val currentFreqKhz: Long,  // -1 if offline / not readable
        val minFreqKhz: Long,
        val maxFreqKhz: Long,
    )

    fun cpuCores(): List<CpuCore> {
        val cores = mutableListOf<CpuCore>()
        var i = 0
        while (true) {
            val cur = readLong("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val min = readLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
            val max = readLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (max <= 0L && min <= 0L) break   // no more cores
            cores += CpuCore(i, cur, min, max)
            i++
        }
        return cores
    }

    // -- CPU utilisation (\/proc\/stat delta) ------------------------------------

    data class CpuStatSnapshot(val idle: Long, val total: Long)

    fun cpuStatSnapshot(): CpuStatSnapshot? = try {
        val line = File("/proc/stat").bufferedReader().readLine() ?: return null
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 5) return null
        // user, nice, system, idle, iowait, irq, softirq, steal
        val user    = parts.getOrElse(1) { "0" }.toLong()
        val nice    = parts.getOrElse(2) { "0" }.toLong()
        val system  = parts.getOrElse(3) { "0" }.toLong()
        val idle    = parts.getOrElse(4) { "0" }.toLong()
        val iowait  = parts.getOrElse(5) { "0" }.toLong()
        val irq     = parts.getOrElse(6) { "0" }.toLong()
        val softirq = parts.getOrElse(7) { "0" }.toLong()
        val steal   = parts.getOrElse(8) { "0" }.toLong()
        val total   = user + nice + system + idle + iowait + irq + softirq + steal
        CpuStatSnapshot(idle, total)
    } catch (_: Exception) { null }

    fun cpuUsagePercent(prev: CpuStatSnapshot, curr: CpuStatSnapshot): Float {
        val deltaTotal = curr.total - prev.total
        val deltaIdle  = curr.idle  - prev.idle
        return if (deltaTotal <= 0L) -1f
        else ((deltaTotal - deltaIdle).toFloat() / deltaTotal * 100f).coerceIn(0f, 100f)
    }

    // -- CPU temperature ---------------------------------------------------------

    fun cpuTempC(): Float {
        val paths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
        )
        for (path in paths) {
            val raw = readLong(path)
            if (raw > 0) return (raw / 1000f).coerceIn(-40f, 150f)
        }
        return -1f
    }

    // -- Thermal zones -----------------------------------------------------------

    data class ThermalZone(val name: String, val tempC: Float)

    fun thermalZones(): List<ThermalZone> {
        val zones = mutableListOf<ThermalZone>()
        var i = 0
        while (true) {
            val base = "/sys/class/thermal/thermal_zone$i"
            val rawTemp = readLong("$base/temp")
            if (rawTemp == -1L && !File(base).exists()) break
            if (rawTemp <= 0L) { i++; continue }
            val tempC = rawTemp / 1000f
            if (tempC < 25f) { i++; continue }   // skip ambient-noise readings
            val typeName = runCatching { File("$base/type").readText().trim() }.getOrElse { "zone$i" }
            zones += ThermalZone(typeName, tempC)
            i++
        }
        return zones.sortedByDescending { it.tempC }
    }

    // -- GPU ---------------------------------------------------------------------

    data class Gpu(
        val available: Boolean,
        val currentFreqMhz: Int    = 0,
        val minFreqMhz: Int        = 0,
        val maxFreqMhz: Int        = 0,
        val governor: String       = "",
        val utilPercent: Int       = -1,    // -1 if not readable
    )

    fun gpu(): Gpu {
        val kgslBase   = "/sys/class/kgsl/kgsl-3d0"
        val devfreqBase = "/sys/class/devfreq"
        // Qualcomm KGSL path
        val kgslCur = readLong("$kgslBase/gpuclk")
        if (kgslCur > 0) {
            val curMhz  = (kgslCur / 1_000_000).toInt()
            val minMhz  = (readLong("$kgslBase/gpu_min_clock") / 1_000_000).toInt()
            val maxMhz  = (readLong("$kgslBase/gpu_max_clock") / 1_000_000).toInt()
            val governor = runCatching { File("$kgslBase/pwrscale/trustzone/governor").readText().trim() }
                              .getOrElse { "" }
            val util    = readLong("$kgslBase/gpu_busy_percentage").toInt()
            return Gpu(true, curMhz, minMhz, maxMhz, governor, if (util < 0) -1 else util)
        }
        // ARM Mali / generic devfreq path
        val devfreqDir = File(devfreqBase)
        if (devfreqDir.exists()) {
            for (child in devfreqDir.listFiles() ?: emptyArray()) {
                val cur = readLong("${child.path}/cur_freq")
                if (cur <= 0) continue
                val min = readLong("${child.path}/min_freq")
                val max = readLong("${child.path}/max_freq")
                val gov = runCatching { File("${child.path}/governor").readText().trim() }.getOrElse { "" }
                return Gpu(
                    available = true,
                    currentFreqMhz = (cur / 1_000_000).toInt(),
                    minFreqMhz     = (min / 1_000_000).toInt(),
                    maxFreqMhz     = (max / 1_000_000).toInt(),
                    governor       = gov,
                    utilPercent    = -1,
                )
            }
        }
        return Gpu(available = false)
    }

    // -- Storage volumes ---------------------------------------------------------

    data class Volume(
        val path: String,
        val label: String,
        val totalBytes: Long,
        val freeBytes: Long,
    )

    fun volumes(): List<Volume> {
        val vols = mutableListOf<Volume>()
        runCatching {
            val sf = StatFs(Environment.getDataDirectory().path)
            vols += Volume(
                path       = Environment.getDataDirectory().path,
                label      = "Internal",
                totalBytes = sf.blockCountLong * sf.blockSizeLong,
                freeBytes  = sf.availableBlocksLong * sf.blockSizeLong,
            )
        }
        return vols
    }

    // -- Disk I\/O ---------------------------------------------------------------

    data class DiskSnapshot(val readBytes: Long, val writeBytes: Long, val ts: Long)

    fun diskSnapshot(): DiskSnapshot? = try {
        var totalRead = 0L; var totalWrite = 0L
        File("/proc/diskstats").forEachLine { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 10) {
                totalRead  += parts[5].toLong()  * 512
                totalWrite += parts[9].toLong()  * 512
            }
        }
        DiskSnapshot(totalRead, totalWrite, System.currentTimeMillis())
    } catch (_: Exception) { null }

    fun diskRate(prev: DiskSnapshot, curr: DiskSnapshot): Pair<Long, Long> {
        val dt = (curr.ts - prev.ts).coerceAtLeast(1)
        val readBps  = ((curr.readBytes  - prev.readBytes)  * 1000 / dt).coerceAtLeast(0)
        val writeBps = ((curr.writeBytes - prev.writeBytes) * 1000 / dt).coerceAtLeast(0)
        return readBps to writeBps
    }

    // -- Network throughput ------------------------------------------------------

    data class NetworkSpeed(val rxBytes: Long, val txBytes: Long, val ts: Long)

    fun networkSnapshot(): NetworkSpeed {
        val rx = try { File("/proc/net/dev").readLines()
            .drop(2)
            .sumOf { it.trim().split("\\s+".toRegex()).getOrElse(1) { "0" }.toLong() }
        } catch (_: Exception) { 0L }
        val tx = try { File("/proc/net/dev").readLines()
            .drop(2)
            .sumOf { it.trim().split("\\s+".toRegex()).getOrElse(9) { "0" }.toLong() }
        } catch (_: Exception) { 0L }
        return NetworkSpeed(rx, tx, System.currentTimeMillis())
    }

    fun networkRate(prev: NetworkSpeed, curr: NetworkSpeed): Pair<Long, Long> {
        val dt = (curr.ts - prev.ts).coerceAtLeast(1)
        return ((curr.rxBytes - prev.rxBytes) * 1000 / dt) to
               ((curr.txBytes - prev.txBytes) * 1000 / dt)
    }

    // -- Network traffic totals (cumulative since boot) --------------------------

    /**
     * Cumulative bytes received across all interfaces since the last device reboot.
     * Reads [android.net.TrafficStats.getTotalRxBytes] — returns null when the
     * system returns [android.net.TrafficStats.UNSUPPORTED].
     * No permissions required.
     */
    fun totalRxBytes(): Long? {
        val v = android.net.TrafficStats.getTotalRxBytes()
        return if (v == android.net.TrafficStats.UNSUPPORTED.toLong()) null else v
    }

    /**
     * Cumulative bytes transmitted across all interfaces since the last device reboot.
     * Reads [android.net.TrafficStats.getTotalTxBytes] — returns null when the
     * system returns [android.net.TrafficStats.UNSUPPORTED].
     * No permissions required.
     */
    fun totalTxBytes(): Long? {
        val v = android.net.TrafficStats.getTotalTxBytes()
        return if (v == android.net.TrafficStats.UNSUPPORTED.toLong()) null else v
    }

    // -- Uptime ------------------------------------------------------------------

    fun systemUptimeSeconds(): Long = try {
        File("/proc/uptime").readText().trim().split(" ").first().toFloat().toLong()
    } catch (_: Exception) { 0L }

    // -- Wi-Fi -------------------------------------------------------------------

    @Suppress("DEPRECATION")
    fun wifiRssi(context: Context): Int? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as? WifiManager ?: return null
        val info = wm.connectionInfo ?: return null
        val rssi = info.rssi
        return if (rssi == Int.MIN_VALUE || rssi == 0) null else rssi
    }

    @Suppress("DEPRECATION")
    fun wifiLinkSpeedMbps(context: Context): Int? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as? WifiManager ?: return null
        val info = wm.connectionInfo ?: return null
        val speed = info.linkSpeed
        return if (speed <= 0) null else speed
    }

    /**
     * Current Wi-Fi operating frequency in MHz.
     *
     * Returns null when the device is not connected to Wi-Fi, or when the frequency
     * is unavailable. Band classification:
     * - 2.4 GHz: 2412–2484 MHz
     * - 5 GHz: 5180–5825 MHz
     * - 6 GHz (Wi-Fi 6E\/7): 5945–7125 MHz
     *
     * No special permissions required (uses the deprecated [android.net.wifi.WifiInfo.getFrequency]
     * which is available without [android.Manifest.permission.ACCESS_FINE_LOCATION] from API 31+
     * when network-scoped access is used). Same [Suppress(DEPRECATION)] pattern as rssi\/linkspeed.
     */
    @Suppress("DEPRECATION")
    fun wifiFrequencyMhz(context: Context): Int? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as? WifiManager ?: return null
        val info = wm.connectionInfo ?: return null
        val freq = info.frequency
        return if (freq <= 0) null else freq
    }

    /**
     * 802.11 Wi-Fi standard label for the current connection.
     *
     * Maps [android.net.wifi.WifiInfo.getWifiStandard] → [android.net.wifi.ScanResult.WIFI_STANDARD_*]
     * constants to human-readable strings:
     * - [android.net.wifi.ScanResult.WIFI_STANDARD_LEGACY] (1) → “Wi-Fi 1”
     * - [android.net.wifi.ScanResult.WIFI_STANDARD_11N] (4) → “Wi-Fi 4” (802.11n)
     * - [android.net.wifi.ScanResult.WIFI_STANDARD_11AC] (5) → “Wi-Fi 5” (802.11ac)
     * - [android.net.wifi.ScanResult.WIFI_STANDARD_11AX] (6) → “Wi-Fi 6” (802.11ax)
     * - [android.net.wifi.ScanResult.WIFI_STANDARD_11BE] (8) → “Wi-Fi 7” (802.11be)
     * - Other / unknown → null
     *
     * No special permissions required (same as rssi\/linkspeed\/frequency).
     */
    @Suppress("DEPRECATION")
    fun wifiStandard(context: Context): String? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as? WifiManager ?: return null
        val info = wm.connectionInfo ?: return null
        return when (info.wifiStandard) {
            android.net.wifi.ScanResult.WIFI_STANDARD_LEGACY -> "Wi-Fi 1"
            android.net.wifi.ScanResult.WIFI_STANDARD_11N    -> "Wi-Fi 4"
            android.net.wifi.ScanResult.WIFI_STANDARD_11AC   -> "Wi-Fi 5"
            android.net.wifi.ScanResult.WIFI_STANDARD_11AX   -> "Wi-Fi 6"
            android.net.wifi.ScanResult.WIFI_STANDARD_11BE   -> "Wi-Fi 7"
            else -> null
        }
    }

    /**
     * Device’s primary local IPv4 address.
     *
     * Iterates all [NetworkInterface]s and returns the first non-loopback
     * [Inet4Address] it finds. Returns null when no active IPv4 interface is
     * found (e.g. airplane mode, or an IPv6-only connection).
     *
     * No permissions required — [NetworkInterface.getNetworkInterfaces] is available
     * to all apps on all API levels.
     */
    fun localIpV4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.asSequence()
            ?.flatMap { iface -> iface.inetAddresses.asSequence() }
            ?.firstOrNull { addr ->
                addr is Inet4Address && !addr.isLoopbackAddress
            }?.hostAddress
    }.getOrNull()

    // -- VPN state ---------------------------------------------------------------

    /**
     * Whether a VPN tunnel is currently active.
     *
     * Checks [android.net.NetworkCapabilities.TRANSPORT_VPN] on the active network.
     * Returns false when no active network is present, null when [android.net.ConnectivityManager]
     * is unavailable. No extra permissions required.
     */
    fun isVpnActive(context: Context): Boolean? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return@runCatching null
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
    }.getOrNull()

    // -- Screen brightness -------------------------------------------------------

    /**
     * Current screen brightness as a percentage (0–100).
     *
     * Reads [Settings.System.SCREEN_BRIGHTNESS] (raw 0–255) and scales to 0–100.
     * Returns null if the setting cannot be read (should not occur on standard Android).
     * No special permissions required.
     */
    fun screenBrightnessPercent(context: Context): Int? = try {
        val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        (raw * 100 / 255).coerceIn(0, 100)
    } catch (_: Settings.SettingNotFoundException) {
        null
    }

    /**
     * Whether adaptive (automatic) brightness is currently enabled.
     *
     * Reads [Settings.System.SCREEN_BRIGHTNESS_MODE]. Returns true when automatic,
     * false when manual, null when the key is absent (should not occur on
     * standard Android). No special permissions required.
     */
    fun screenBrightnessAuto(context: Context): Boolean? = try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    } catch (_: Settings.SettingNotFoundException) {
        null
    }

    // -- Dark mode & auto-rotate ------------------------------------------------

    /**
     * Whether the system is currently in dark (night) mode.
     *
     * Reads [android.content.res.Configuration.uiMode] which reflects the actual
     * active night mode — including battery-saver dark mode and scheduled dark mode.
     * Returns true when dark, false when light. No special permissions required.
     */
    fun isDarkMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /**
     * Whether auto-rotate (accelerometer-based rotation) is currently enabled.
     *
     * Reads [Settings.System.ACCELEROMETER_ROTATION]. Returns true when auto-rotate
     * is active, false when the device is locked to portrait. null when the setting
     * is unavailable (rare on standard Android). No special permissions required.
     */
    fun isAutoRotateEnabled(context: Context): Boolean? = runCatching {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
        ) == 1
    }.getOrNull()

    // -- Display refresh rate ---------------------------------------------------

    /**
     * Current screen refresh rate in Hz.
     *
     * Reads [android.view.Display.getRefreshRate] via [Context.getDisplay] (API 30+,
     * matching Marrow’s minSdk). On fixed-rate displays this always returns the panel’s
     * configured rate. On LTPO / VRR panels (e.g. Pixel 8 Pro, Samsung Galaxy S24 Ultra)
     * the value adapts dynamically — 120 Hz when scrolling, as low as 1 Hz when the screen
     * is static.
     *
     * Returns null in non-UI contexts or when [Context.getDisplay] returns null (e.g. when
     * called from a service without a display). No permissions required.
     */
    fun screenRefreshRateHz(context: Context): Float? = runCatching {
        context.display?.refreshRate
    }.getOrNull()

    // -- Battery saver -----------------------------------------------------------

    /**
     * Whether battery saver (low-power) mode is currently active.
     *
     * Reads [android.os.PowerManager.isPowerSaveMode]. Returns null when
     * [PowerManager] is unavailable. No permissions required.
     */
    fun batterySaverActive(context: Context): Boolean? = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        pm?.isPowerSaveMode
    }.getOrNull()


    // -- Connectivity toggles ----------------------------------------------------

    /**
     * Whether airplane mode is currently enabled.
     *
     * Reads [Settings.Global.AIRPLANE_MODE_ON]. Returns null when the setting
     * cannot be read. No special permissions required.
     */
    fun isAirplaneModeOn(context: Context): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1
    }.getOrNull()

    /**
     * Whether NFC is enabled on this device.
     *
     * Returns null when the device has no NFC hardware. Returns false when NFC hardware
     * is present but currently disabled. No special permissions required.
     */
    fun isNfcEnabled(context: Context): Boolean? = runCatching {
        val nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(context) ?: return@runCatching null
        nfcAdapter.isEnabled
    }.getOrNull()

    /**
     * Whether Bluetooth is currently enabled.
     *
     * Reads [Settings.Global] “bluetooth_on” — a world-readable system setting available
     * on all API levels without any Bluetooth permission (no BLUETOOTH_CONNECT needed).
     *
     * Returns true when BT is on, false when off, null when the setting is absent
     * (should not occur on standard Android). Zero permissions required.
     */
    fun isBluetoothEnabled(context: Context): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, "bluetooth_on") == 1
    }.getOrNull()

    // -- Audio -------------------------------------------------------------------

    enum class RingerMode { NORMAL, VIBRATE, SILENT }

    /**
     * Current ringer mode from [android.media.AudioManager].
     * Maps to [RingerMode.NORMAL], [RingerMode.VIBRATE], or [RingerMode.SILENT].
     * Returns null when [AudioManager] is unavailable. No permissions required.
     */
    fun ringerMode(context: Context): RingerMode? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    ?: return@runCatching null
        when (am.ringerMode) {
            android.media.AudioManager.RINGER_MODE_NORMAL  -> RingerMode.NORMAL
            android.media.AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            android.media.AudioManager.RINGER_MODE_SILENT  -> RingerMode.SILENT
            else -> null
        }
    }.getOrNull()

    /**
     * Current media (music) stream volume as a percentage (0–100).
     *
     * Reads [android.media.AudioManager.STREAM_MUSIC] volume scaled to
     * [android.media.AudioManager.getStreamMaxVolume]. Returns null when
     * [AudioManager] is unavailable or when max volume is 0. No permissions required.
     */
    fun mediaVolumePct(context: Context): Int? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    ?: return@runCatching null
        val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        if (max <= 0) null else (cur * 100 / max).coerceIn(0, 100)
    }.getOrNull()

    /**
     * Whether music is currently active.
     *
     * Reads [android.media.AudioManager.isMusicActive]. Returns null when
     * [AudioManager] is unavailable. No permissions required.
     */
    fun isMusicActive(context: Context): Boolean? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    ?: return@runCatching null
        am.isMusicActive
    }.getOrNull()

    /**
     * Current Do Not Disturb interruption filter as a human-readable label.
     *
     * Reads [android.app.NotificationManager.getCurrentInterruptionFilter]:
     * - [android.app.NotificationManager.INTERRUPTION_FILTER_ALL] → “Off” (DND is off, all notifications pass)
     * - [android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY] → “Priority”
     * - [android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS] → “Alarms”
     * - [android.app.NotificationManager.INTERRUPTION_FILTER_NONE] → “Total Silence”
     *
     * Returns null when [NotificationManager] is unavailable or when the
     * interruption filter value is unrecognised. No permissions required.
     */
    fun dndMode(context: Context): String? = runCatching {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? android.app.NotificationManager ?: return@runCatching null
        when (nm.currentInterruptionFilter) {
            android.app.NotificationManager.INTERRUPTION_FILTER_ALL      -> "Off"
            android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority"
            android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS   -> "Alarms"
            android.app.NotificationManager.INTERRUPTION_FILTER_NONE     -> "Total Silence"
            else -> null
        }
    }.getOrNull()

    // -- Telephony / Cellular ----------------------------------------------------

    data class Cellular(
        val carrierName: String?,
        val networkType: String?,       // "5G", "LTE", "3G", "2G", "Wi-Fi Call", etc.
        val signalLevel: Int?,          // 0–4 bars; null when unavailable
        val roaming: Boolean,
    )

    /**
     * Snapshot of the current cellular state.
     *
     * Reads carrier name, network type, signal level (bars), and roaming status
     * via [android.telephony.TelephonyManager].
     *
     * Requires [android.Manifest.permission.READ_BASIC_PHONE_STATE] (normal permission,
     * API 33+; degrades to null on API < 33 unless the legacy
     * [android.Manifest.permission.READ_PHONE_STATE] is held).
     *
     * Returns null on devices without telephony hardware (Wi-Fi-only tablets, emulators
     * without telephony support).
     */
    fun cellularInfo(context: Context): Cellular? = runCatching {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE)
            as? android.telephony.TelephonyManager ?: return@runCatching null
        val carrier = tm.networkOperatorName?.takeIf { it.isNotBlank() }
        val netType = when (tm.dataNetworkType) {
            android.telephony.TelephonyManager.NETWORK_TYPE_NR        -> "5G"
            android.telephony.TelephonyManager.NETWORK_TYPE_LTE       -> "LTE"
            android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP,
            android.telephony.TelephonyManager.NETWORK_TYPE_HSPA,
            android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA,
            android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA,
            android.telephony.TelephonyManager.NETWORK_TYPE_UMTS      -> "3G"
            android.telephony.TelephonyManager.NETWORK_TYPE_EDGE,
            android.telephony.TelephonyManager.NETWORK_TYPE_GPRS,
            android.telephony.TelephonyManager.NETWORK_TYPE_CDMA,
            android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT,
            android.telephony.TelephonyManager.NETWORK_TYPE_IDEN      -> "2G"
            android.telephony.TelephonyManager.NETWORK_TYPE_IWLAN     -> "Wi-Fi Call"
            else -> null
        }
        val signalLevel = runCatching {
            tm.signalStrength?.level
        }.getOrNull()
        val roaming = tm.isNetworkRoaming
        Cellular(carrier, netType, signalLevel, roaming)
    }.getOrNull()

    // -- Helpers -----------------------------------------------------------------

    private fun readLong(path: String): Long = try {
        File(path).readText().trim().toLong()
    } catch (_: Exception) { -1L }
}

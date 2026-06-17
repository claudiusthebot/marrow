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
import java.net.Inet6Address
import java.net.NetworkInterface
import kotlin.math.roundToInt

/**
 * Lightweight, polled snapshots of the device's most-watched stats. Designed
 * for the phone's "live stats strip" + per-section hero treatments — they update
 * every few seconds and need numeric values, not pre-formatted strings.
 *
 * Every call is safe on a missing-hardware device: each accessor returns a
 * sentinel when it can't read the value, never throws.
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
        /** Human-readable health condition from BatteryManager.EXTRA_HEALTH.
         *  E.g. "Good", "Overheat", "Dead", "Over Voltage", "Cold".
         *  Empty string when healthInt is -1 / unknown. */
        val healthStatus: String = "",
        /** Charge cycle count from sysfs. -1 when absent (emulator / OEM restriction). */
        val cycleCount: Int = -1,          // ≥0, -1 unknown
        /** Instantaneous charge from BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER converted
         *  to mAh (divide µAh by 1000). -1 when unavailable (emulator / non-standard OEM). */
        val chargeCounterMah: Int = -1,    // ≥0 mAh, -1 unknown
    ) {
        enum class PlugType { UNPLUGGED, AC, USB, WIRELESS, DOCK }

        /**
         * Instantaneous power draw in milliwatts.
         *
         * Positive = net input to the battery (charging path active).
         * Negative = net draw from the battery (discharging / screen on).
         *
         * Sign follows [currentMa]: on most OEMs (Qualcomm/Google Tensor)
         * [BatteryManager.BATTERY_PROPERTY_CURRENT_NOW] is signed in µA —
         * positive when charging current is flowing into the cell, negative
         * when load current flows out. A small minority of OEMs report the
         * absolute value regardless of direction; on those devices the sign
         * will be wrong (always positive), but the magnitude is still useful.
         *
         * Returns [Int.MIN_VALUE] when either [currentMa] or [voltageV] is
         * unavailable (i.e., [currentMa] == [Int.MIN_VALUE] or [voltageV] < 0).
         */
        val powerMw: Int
            get() = if (currentMa != Int.MIN_VALUE && voltageV >= 0f)
                (voltageV * currentMa.toFloat()).roundToInt()
            else Int.MIN_VALUE
    }

    fun battery(context: Context): Battery {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val mgr = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else -1
        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
            statusInt == BatteryManager.BATTERY_STATUS_FULL
        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val plug = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> Battery.PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> Battery.PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> Battery.PlugType.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> Battery.PlugType.DOCK
            else -> Battery.PlugType.UNPLUGGED
        }
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "?"
        val current = mgr?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        val healthPct = batteryHealthPercent(
            readLong("/sys/class/power_supply/battery/charge_full"),
            readLong("/sys/class/power_supply/battery/charge_full_design"),
        )
        return Battery(
            percent = percent,
            charging = charging,
            plugged = plug,
            temperatureC = if (tempTenths >= 0) tempTenths / 10f else -1f,
            voltageV = if (voltageMv >= 0) voltageMv / 1000f else -1f,
            currentMa = if (current != Int.MIN_VALUE) current / 1000 else Int.MIN_VALUE,
            technology = tech,
            healthy = healthInt == BatteryManager.BATTERY_HEALTH_GOOD || healthInt == -1,
            healthPercent = healthPct,
            healthStatus = batteryHealthLabel(healthInt),
            cycleCount = readLong("/sys/class/power_supply/battery/cycle_count")
                .let { if (it > 0L) it.toInt() else -1 },
            chargeCounterMah = chargeCounterMah(
                mgr?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0,
            ),
        )
    }

    /**
     * Battery wear percentage from sysfs capacity nodes.
     *
     * Both [chargeFull] and [chargeFullDesign] are typically in µAh, but the
     * calculation is unit-agnostic — only their ratio matters.
     * OEMs use two common paths; the collector always passes the standard path:
     *   /sys/class/power_supply/battery/charge_full          (actual max)
     *   /sys/class/power_supply/battery/charge_full_design   (factory spec)
     *
     * Returns -1 when either value is ≤ 0 (absent sysfs node, emulator,
     * or OEM using a non-standard power_supply name like "bms" or "BAT0").
     *
     * Exposed as a pure function for unit testability — no filesystem access here.
     */
    fun batteryHealthPercent(chargeFull: Long, chargeFullDesign: Long): Int {
        if (chargeFull <= 0 || chargeFullDesign <= 0) return -1
        return ((chargeFull.toFloat() / chargeFullDesign.toFloat()) * 100f)
            .roundToInt().coerceIn(0, 100)
    }

    /**
     * Maps a [BatteryManager.EXTRA_HEALTH] integer to a human-readable condition string.
     *
     * Returns an empty string when [healthInt] is -1 (unknown / intent unavailable),
     * so callers can use `healthStatus.isNotEmpty()` as the visibility guard.
     *
     * Exposed as a pure function for unit testability — no Android context needed.
     */
    fun batteryHealthLabel(healthInt: Int): String = when (healthInt) {
        BatteryManager.BATTERY_HEALTH_GOOD               -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT           -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD               -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE       -> "Over Voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        BatteryManager.BATTERY_HEALTH_COLD               -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNKNOWN            -> "Unknown"
        else                                              -> ""
    }

    /**
     * Convert a raw [BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER] µAh value to mAh.
     *
     * BatteryManager returns the property as µAh (or 0 / negative when unavailable).
     * Exposed as a pure function so it can be unit-tested without an Android context.
     *
     * @param uah Raw value from [BatteryManager.getIntProperty] in µAh.
     * @return Charge in mAh, or -1 when [uah] ≤ 0 (sensor absent / emulator).
     */
    internal fun chargeCounterMah(uah: Int): Int = if (uah > 0) uah / 1000 else -1

    // -- Memory ------------------------------------------------------------------

    data class Memory(
        val totalBytes: Long,
        val availBytes: Long,
        val thresholdBytes: Long,
        val lowMemory: Boolean,
        /** Swap / zRAM total in bytes. 0 when no swap is configured on this device. */
        val swapTotalBytes: Long = 0L,
        /** Swap / zRAM currently in use (SwapTotal − SwapFree from /proc/meminfo). */
        val swapUsedBytes: Long = 0L,
    ) {
        val usedBytes: Long get() = (totalBytes - availBytes).coerceAtLeast(0L)
        val usedFraction: Float
            get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
        val usedPercent: Int get() = (usedFraction * 100f).toInt()
        val swapUsedFraction: Float
            get() = if (swapTotalBytes > 0) (swapUsedBytes.toFloat() / swapTotalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    }

    fun memory(context: Context): Memory {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        // Read zRAM / swap stats from /proc/meminfo (values are in kB — multiply × 1024).
        // /proc/meminfo is world-readable on standard Android; no special permissions needed.
        var swapTotalKb = 0L
        var swapFreeKb = 0L
        runCatching {
            File("/proc/meminfo").forEachLine { line ->
                when {
                    line.startsWith("SwapTotal:") ->
                        swapTotalKb = line.trim().split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: 0L
                    line.startsWith("SwapFree:") ->
                        swapFreeKb = line.trim().split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: 0L
                }
            }
        }
        val swapTotal = swapTotalKb * 1024L
        val swapFree = swapFreeKb * 1024L
        return Memory(
            totalBytes = mi.totalMem,
            availBytes = mi.availMem,
            thresholdBytes = mi.threshold,
            lowMemory = mi.lowMemory,
            swapTotalBytes = swapTotal,
            swapUsedBytes = (swapTotal - swapFree).coerceAtLeast(0L),
        )
    }

    // -- CPU ---------------------------------------------------------------------

    data class CpuCore(val index: Int, val curMhz: Long, val minMhz: Long, val maxMhz: Long, val governor: String?)

    /** Reads /sys per-core frequencies. Cheap; safe on hardware where the files
     *  aren't readable (returns 0 for that core's freq). */
    fun cpuCores(): List<CpuCore> {
        val cores = Runtime.getRuntime().availableProcessors()
        return (0 until cores).map { i ->
            val cur = readLong("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq") / 1000
            val min = readLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq") / 1000
            val max = readLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq") / 1000
            val gov = readString("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor")
            CpuCore(i, cur, min, max, gov)
        }
    }

    fun avgCurMhz(cores: List<CpuCore>): Long {
        val active = cores.filter { it.curMhz > 0 }
        if (active.isEmpty()) return 0L
        return active.sumOf { it.curMhz } / active.size
    }

    /**
     * Snapshot of cumulative CPU time jiffies from `/proc/stat` (first "cpu" line).
     *
     * Fields: user nice system idle iowait irq softirq steal [guest guestNice]
     * [total] = sum of all fields.  [idle] = idle + iowait.
     *
     * Returns null when the file is unreadable (emulator / SELinux restriction).
     */
    data class CpuStatSnapshot(
        val total: Long,
        val idle: Long,
        val timestampMs: Long,
    )

    /** Read a single [CpuStatSnapshot]. Returns null on any failure. */
    fun cpuStatSnapshot(): CpuStatSnapshot? = runCatching {
        val line = File("/proc/stat").useLines { it.firstOrNull() } ?: return@runCatching null
        // format: "cpu  user nice system idle iowait irq softirq steal guest guestNice"
        val parts = line.trim().split(Regex("\\s+")).drop(1) // drop "cpu" label
        val longs = parts.mapNotNull { it.toLongOrNull() }
        if (longs.size < 5) return@runCatching null
        val idle = longs[3] + longs.getOrElse(4) { 0L }   // idle + iowait
        CpuStatSnapshot(
            total = longs.sum(),
            idle = idle,
            timestampMs = System.currentTimeMillis(),
        )
    }.getOrNull()

    /**
     * Total CPU utilisation (0–100f) derived from two consecutive snapshots.
     *
     * Returns 0f when the elapsed jiffy delta is zero or negative (clock skew,
     * first-tick edge case).
     */
    fun cpuUsagePercent(prev: CpuStatSnapshot, curr: CpuStatSnapshot): Float {
        val dTotal = curr.total - prev.total
        if (dTotal <= 0L) return 0f
        val dIdle = curr.idle - prev.idle
        return ((dTotal - dIdle).toFloat() / dTotal.toFloat()).coerceIn(0f, 1f) * 100f
    }

    /** Reads the highest CPU / SoC thermal zone temperature in °C.
     *
     *  Scans `/sys/class/thermal/thermal_zone*` and filters zones whose `type`
     *  file contains "cpu" or "soc" (case-insensitive). Vendor type strings vary:
     *  "CPU-therm", "cpu0", "tsens_tz_sensor0", "SoC-therm", "CPU_therm"…
     *
     *  Returns the maximum of all matching zone temperatures, converted from
     *  millidegrees to °C. Returns -1f when the sysfs path is absent or no
     *  CPU/SoC zones are readable (e.g. emulator, restricted SELinux policy). */
    fun cpuTempC(): Float {
        val thermalRoot = File("/sys/class/thermal")
        if (!thermalRoot.exists()) return -1f
        val temps = thermalRoot.listFiles { f ->
            f.isDirectory && f.name.startsWith("thermal_zone")
        }?.mapNotNull { zone ->
            val type = readString("${zone.path}/type")?.lowercase() ?: return@mapNotNull null
            if (!type.contains("cpu") && !type.contains("soc")) return@mapNotNull null
            readLong("${zone.path}/temp").takeIf { it > 0L }
        }.orEmpty()
        if (temps.isEmpty()) return -1f
        return temps.max() / 1000f
    }

    // -- Thermal zones -----------------------------------------------------------

    /** One readable thermal zone: human-readable name + current temperature. */
    data class ThermalZone(
        val name: String,   // normalised zone type (e.g. "CPU Therm", "Battery", "GPU")
        val tempC: Float,   // temperature in °C
    )

    /**
     * Reads all accessible thermal zones from `/sys/class/thermal/thermal_zone*`
     * and returns those reporting a temperature ≥ [minTempC], sorted hottest-first,
     * capped at [limit] entries.
     *
     * Zone type strings vary wildly across SoC vendors (Qualcomm tsens_tz_sensor*,
     * MediaTek thermal_zone*, Samsung exynos*, etc.). Names are normalised:
     * underscores/dashes → spaces, common abbreviations expanded, title-cased.
     *
     * Returns an empty list when sysfs is inaccessible (emulator, SELinux restriction).
     */
    fun thermalZones(minTempC: Float = 25f, limit: Int = 12): List<ThermalZone> {
        val thermalRoot = File("/sys/class/thermal")
        if (!thermalRoot.exists()) return emptyList()
        return thermalRoot.listFiles { f ->
            f.isDirectory && f.name.startsWith("thermal_zone")
        }?.mapNotNull { zone ->
            val rawType = readString("${zone.path}/type") ?: return@mapNotNull null
            val tempMillis = readLong("${zone.path}/temp").takeIf { it > 0L } ?: return@mapNotNull null
            val tempC = tempMillis / 1000f
            if (tempC < minTempC) return@mapNotNull null
            ThermalZone(normalizeThermalName(rawType), tempC)
        }
            ?.sortedByDescending { it.tempC }
            ?.take(limit)
            .orEmpty()
    }

    private fun normalizeThermalName(raw: String): String {
        // Replace separators with spaces, split into tokens, map well-known abbreviations
        val tokens = raw.replace(Regex("[_\\-]"), " ").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val mapped = tokens.joinToString(" ") { word ->
            when (word.lowercase()) {
                "cpu"                    -> "CPU"
                "gpu"                    -> "GPU"
                "soc"                    -> "SoC"
                "pmic"                   -> "PMIC"
                "batt", "battery"        -> "Battery"
                "therm", "thermal"       -> "Therm"
                "temp"                   -> "Temp"
                "tz", "tsens"            -> "Sensor"
                "npu"                    -> "NPU"
                "wifi", "wlan"           -> "WiFi"
                "modem", "mdm"           -> "Modem"
                "cam", "camera"          -> "Camera"
                "usb"                    -> "USB"
                "ddr", "mem", "memory"   -> "Memory"
                "bcl"                    -> "BCL"
                "pa"                     -> "PA"
                else                     -> word.replaceFirstChar { it.uppercaseChar() }
            }
        }
        // Cap display length
        return if (mapped.length > 22) mapped.take(21).trimEnd() + "…" else mapped
    }

    // -- Storage -----------------------------------------------------------------

    data class Volume(
        val label: String,
        val totalBytes: Long,
        val availBytes: Long,
    ) {
        val usedBytes: Long get() = (totalBytes - availBytes).coerceAtLeast(0L)
        val usedFraction: Float
            get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    }

    fun volumes(): List<Volume> {
        val out = mutableListOf<Volume>()
        runCatching {
            val internalRoot = Environment.getDataDirectory()
            val s = StatFs(internalRoot.path)
            out += Volume(
                label = "Internal",
                totalBytes = s.blockCountLong * s.blockSizeLong,
                availBytes = s.availableBlocksLong * s.blockSizeLong,
            )
        }
        runCatching {
            val ext = Environment.getExternalStorageDirectory()
            val s = StatFs(ext.path)
            val total = s.blockCountLong * s.blockSizeLong
            // Only add if it looks distinct from /data — most modern Android phones
            // share emulated storage with /data so the numbers are equal.
            val internalSize = out.firstOrNull()?.totalBytes ?: 0L
            if (total > 0 && total != internalSize) {
                out += Volume(
                    label = "External",
                    totalBytes = total,
                    availBytes = s.availableBlocksLong * s.blockSizeLong,
                )
            }
        }
        return out
    }

    fun storageUsedFraction(volumes: List<Volume>): Float {
        val total = volumes.sumOf { it.totalBytes }
        if (total <= 0) return 0f
        val used = volumes.sumOf { it.usedBytes }
        return (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    // -- Network speed -----------------------------------------------------------

    data class NetworkSpeed(
        val rxBytesTotal: Long,
        val txBytesTotal: Long,
        val timestampMs: Long,
    )

    /**
     * Snapshot of all network-interface byte counters (loopback excluded).
     * Reads /proc/net/dev — no I/O blocking, safe on all API levels.
     */
    fun networkSnapshot(): NetworkSpeed {
        var rxTotal = 0L
        var txTotal = 0L
        runCatching {
            File("/proc/net/dev").forEachLine { line ->
                val trimmed = line.trim()
                // Skip header lines and loopback
                if (trimmed.startsWith("Inter") || trimmed.startsWith("face") ||
                    trimmed.startsWith("lo:")) return@forEachLine
                val colon = trimmed.indexOf(':')
                if (colon < 0) return@forEachLine
                // Columns: rx_bytes packets errs drop fifo frame compressed multicast
                //          tx_bytes packets errs drop fifo colls carrier compressed
                val parts = trimmed.substring(colon + 1).trim().split(Regex("\\s+"))
                rxTotal += parts.getOrNull(0)?.toLongOrNull() ?: 0L
                txTotal += parts.getOrNull(8)?.toLongOrNull() ?: 0L
            }
        }
        return NetworkSpeed(rxTotal, txTotal, System.currentTimeMillis())
    }

    /** Returns (rxBytesPerSec, txBytesPerSec) from two consecutive snapshots. */
    fun networkRate(prev: NetworkSpeed, curr: NetworkSpeed): Pair<Long, Long> {
        val dtMs = curr.timestampMs - prev.timestampMs
        if (dtMs <= 0L) return 0L to 0L
        val rxRate = ((curr.rxBytesTotal - prev.rxBytesTotal) * 1000L / dtMs).coerceAtLeast(0L)
        val txRate = ((curr.txBytesTotal - prev.txBytesTotal) * 1000L / dtMs).coerceAtLeast(0L)
        return rxRate to txRate
    }

    /** Human-readable byte rate: "1.2 MB/s", "345 KB/s", "12 B/s". */
    fun formatSpeedBps(bytesPerSec: Long): String = when {
        bytesPerSec >= 1_000_000L -> "%.1f MB/s".format(bytesPerSec / 1_000_000f)
        bytesPerSec >= 1_000L    -> "${bytesPerSec / 1_000} KB/s"
        else                     -> "$bytesPerSec B/s"
    }

    // -- Disk I/O ----------------------------------------------------------------

    data class DiskSnapshot(
        val readSectors: Long,
        val writeSectors: Long,
        val timestampMs: Long,
    )

    /**
     * Reads /proc/diskstats to get cumulative sector counts across top-level
     * block devices. Skips loop, ram, zram, dm-*, sr devices and partition
     * subdevices (e.g. mmcblk0p1, sda1). Returns null when the file can't
     * be read (SELinux restriction or sandboxed environment).
     *
     * /proc/diskstats column layout (0-indexed):
     *   2=device name, 5=sectors_read, 9=sectors_written. Each sector = 512 B.
     */
    fun diskSnapshot(): DiskSnapshot? = runCatching {
        val lines = File("/proc/diskstats").readLines()
        var readSectors = 0L
        var writeSectors = 0L
        for (line in lines) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 10) continue
            val name = parts[2]
            // Skip virtual / noise devices
            if (name.matches(Regex("(loop|ram|zram|dm-|sr)\\d*.*"))) continue
            // Skip partition subdevices
            if (name.matches(Regex("mmcblk\\d+p\\d+.*"))) continue
            if (name.matches(Regex("nvme\\d+n\\d+p\\d+.*"))) continue
            if (name.matches(Regex("sd[a-z]\\d+.*"))) continue
            if (name.matches(Regex("vd[a-z]\\d+.*"))) continue
            readSectors += parts[5].toLongOrNull() ?: 0L
            writeSectors += parts[9].toLongOrNull() ?: 0L
        }
        DiskSnapshot(readSectors, writeSectors, System.currentTimeMillis())
    }.getOrNull()

    /**
     * Returns (readBps, writeBps) from two consecutive [DiskSnapshot]s.
     * Returns (0, 0) if elapsed time is zero or negative.
     */
    fun diskRate(prev: DiskSnapshot, curr: DiskSnapshot): Pair<Long, Long> {
        val elapsedMs = curr.timestampMs - prev.timestampMs
        if (elapsedMs <= 0L) return 0L to 0L
        val readBps = ((curr.readSectors - prev.readSectors).coerceAtLeast(0L) * 512L * 1000L) / elapsedMs
        val writeBps = ((curr.writeSectors - prev.writeSectors).coerceAtLeast(0L) * 512L * 1000L) / elapsedMs
        return readBps to writeBps
    }

    /** Formats a byte-per-second disk rate as a concise human-readable string. */
    fun formatDiskBps(bytesPerSec: Long): String = when {
        bytesPerSec >= 1_000_000L -> "%.1f MB/s".format(bytesPerSec / 1_000_000.0)
        bytesPerSec >= 1_000L -> "${bytesPerSec / 1_000} KB/s"
        else -> "$bytesPerSec B/s"
    }

    // -- GPU ---------------------------------------------------------------------

    /**
     * Live GPU snapshot: frequency, utilisation, and DVFS governor.
     *
     * Probes multiple sysfs paths in priority order:
     * 1. **Qualcomm Adreno** — `/sys/class/kgsl/kgsl-3d0/devfreq/` for frequencies,
     *    `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage` for utilisation.
     * 2. **Generic devfreq** — scans `/sys/class/devfreq/` for an entry whose
     *    directory name contains "gpu", "mali", "pvr", "rogue", "sgx", or "g3d".
     *
     * Returns an unavailable [Gpu] (`available = false`) on emulators or when
     * SELinux policy blocks access to GPU sysfs nodes.
     */
    data class Gpu(
        /** Current frequency in MHz. 0 when unreadable. */
        val curMhz: Long,
        /** Minimum governor-allowed frequency in MHz. 0 when unreadable. */
        val minMhz: Long,
        /** Maximum governor-allowed frequency in MHz. 0 when unreadable. */
        val maxMhz: Long,
        /** Devfreq / kgsl governor name. null when unreadable. */
        val governor: String?,
        /**
         * GPU utilisation 0–100. -1 when not exposed by this SoC's driver
         * (most generic devfreq implementations lack a `load` or `busy_pct` file).
         */
        val usagePercent: Int,
    ) {
        /** Current frequency as a fraction of max (0f–1f). 0f when either value is 0. */
        val freqFraction: Float
            get() = if (maxMhz > 0 && curMhz > 0)
                (curMhz.toFloat() / maxMhz.toFloat()).coerceIn(0f, 1f)
            else 0f

        /** true if at least one GPU frequency stat was readable from sysfs. */
        val available: Boolean get() = maxMhz > 0
    }

    /**
     * Read a single [Gpu] snapshot.
     *
     * Safe on any device — returns a zeroed sentinel rather than throwing when
     * sysfs paths are absent or restricted by SELinux.
     */
    fun gpu(): Gpu {
        // 1. Qualcomm Adreno (kgsl) — most popular Android GPU family.
        //    Frequencies live in devfreq/ subdirectory; utilisation via gpu_busy_percentage.
        val kgslDevfreq = "/sys/class/kgsl/kgsl-3d0/devfreq"
        if (File("$kgslDevfreq/cur_freq").exists()) {
            val cur = readLong("$kgslDevfreq/cur_freq") / 1_000_000L
            val min = readLong("$kgslDevfreq/min_freq") / 1_000_000L
            val max = readLong("$kgslDevfreq/max_freq") / 1_000_000L
            val gov = readString("$kgslDevfreq/governor")
            val busyPct = readLong("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage").toInt()
            return Gpu(cur, min, max, gov, if (busyPct in 0..100) busyPct else -1)
        }
        // 2. Generic devfreq — Mali, PowerVR, IMG GPU (Google Tensor), etc.
        //    /sys/class/devfreq/ entries are symlinks; listFiles() follows them automatically.
        val gpuEntry = runCatching {
            File("/sys/class/devfreq").listFiles()
                ?.firstOrNull { f ->
                    val name = f.name.lowercase()
                    name.contains("gpu") || name.contains("mali") ||
                        name.contains("pvr") || name.contains("rogue") ||
                        name.contains("sgx") || name.contains("g3d")
                }
        }.getOrNull()
        if (gpuEntry != null) {
            val p = runCatching { gpuEntry.canonicalPath }.getOrElse { gpuEntry.absolutePath }
            val cur = readLong("$p/cur_freq") / 1_000_000L
            val min = readLong("$p/min_freq") / 1_000_000L
            val max = readLong("$p/max_freq") / 1_000_000L
            val gov = readString("$p/governor")
            // Some devfreq drivers expose a `load` integer (0–100) for GPU utilisation
            val usage = readString("$p/load")?.trim()?.toLongOrNull()?.toInt() ?: -1
            return Gpu(cur, min, max, gov, if (usage in 0..100) usage else -1)
        }
        return Gpu(0L, 0L, 0L, null, -1)
    }

    // -- System uptime -----------------------------------------------------------

    /**
     * System uptime in seconds since the last boot.
     *
     * Reads the first floating-point field from `/proc/uptime` (total elapsed
     * seconds since boot, including sleep time). No permissions required — the
     * file is world-readable on standard Android. Returns 0L when inaccessible
     * (sandboxed test environment, unusual SELinux policy).
     */
    fun systemUptimeSeconds(): Long =
        runCatching {
            File("/proc/uptime").readText().trim()
                .split(Regex("\\s+")).firstOrNull()
                ?.toDoubleOrNull()?.toLong() ?: 0L
        }.getOrDefault(0L)

    /**
     * Formats an uptime in seconds as a concise human-readable string.
     *
     * Examples:
     * - 47 minutes → "47m"
     * - 2 hours 34 minutes → "2h 34m"
     * - 5 days 3 hours → "5d 3h"
     * - 0 / unavailable → "—"
     */
    fun formatUptime(seconds: Long): String {
        if (seconds <= 0L) return "—"
        val days = seconds / 86_400L
        val hours = (seconds % 86_400L) / 3_600L
        val mins = (seconds % 3_600L) / 60L
        return when {
            days > 0 && hours > 0 -> "${days}d ${hours}h"
            days > 0              -> "${days}d"
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0             -> "${hours}h"
            else                  -> "${mins}m"
        }
    }

    // -- Deep sleep --------------------------------------------------------------

    /**
     * Fraction of boot time the device has spent in deep sleep (0..100).
     *
     * Pure helper, testable without Android runtime. Pass:
     *   [elapsedMs] — [android.os.SystemClock.elapsedRealtime] (includes deep sleep)
     *   [uptimeMs]  — [android.os.SystemClock.uptimeMillis] (excludes deep sleep)
     *
     * Returns -1 when [elapsedMs] is zero.
     */
    internal fun deepSleepFractionPct(elapsedMs: Long, uptimeMs: Long): Int {
        if (elapsedMs <= 0L) return -1
        val sleepMs = (elapsedMs - uptimeMs).coerceAtLeast(0L)
        return ((sleepMs * 100L) / elapsedMs).coerceIn(0L, 100L).toInt()
    }

    /**
     * Fraction of boot time the device has spent in deep sleep (0..100).
     *
     * Uses [android.os.SystemClock.elapsedRealtime] (includes deep sleep) minus
     * [android.os.SystemClock.uptimeMillis] (excludes deep sleep). No permissions
     * required — both clocks are always available on Android. Returns -1 only when
     * elapsedRealtime() is zero (impossible on a live device, guards robustness).
     */
    fun deepSleepFractionPct(): Int =
        deepSleepFractionPct(
            android.os.SystemClock.elapsedRealtime(),
            android.os.SystemClock.uptimeMillis(),
        )

    // -- Wi-Fi signal strength --------------------------------------------------

    /**
     * Live Wi-Fi signal strength in dBm, or null when not connected.
     *
     * Reads [WifiManager.connectionInfo.rssi] via ACCESS_WIFI_STATE (already held
     * by Marrow). Returns null when not connected, WifiManager unavailable, or
     * RSSI is outside the valid range. [WifiManager.getConnectionInfo] is
     * deprecated on API 31+ but still functional for RSSI reads without
     * ACCESS_FINE_LOCATION. Suppressing is intentional.
     */
    @Suppress("DEPRECATION")
    fun wifiRssi(context: Context): Int? {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val rssi = wm.connectionInfo?.rssi ?: return null
        // Valid RSSI: negative and >= -120 dBm; 0 = disconnected sentinel.
        return if (rssi < 0 && rssi >= -120) rssi else null
    }


    /**
     * Current Wi-Fi link speed in Mbps, or null when not connected to Wi-Fi.
     *
     * Uses the same deprecated-but-functional [WifiManager.getConnectionInfo] path as
     * [wifiRssi]. [WifiManager.LINK_SPEED_UNKNOWN] (-1) is returned when the device is
     * not associated; any non-positive value is treated as unavailable.
     */
    @Suppress("DEPRECATION")
    fun wifiLinkSpeedMbps(context: Context): Int? {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val speed = wm.connectionInfo?.linkSpeed ?: return null
        return if (speed > 0) speed else null
    }


    /**
     * Live Wi-Fi frequency in MHz from [android.net.wifi.WifiInfo.getFrequency].
     * Returns null when not connected to a Wi-Fi network or frequency is unavailable.
     *
     * Typical values:
     * - 2.4 GHz band: 2412–2484 MHz
     * - 5 GHz band:   5180–5825 MHz
     * - 6 GHz band:   5945–7125 MHz (Wi-Fi 6E / 7)
     *
     * No permissions required (uses same deprecated [WifiInfo] as [wifiRssi]).
     */
    @Suppress("DEPRECATION")
    fun wifiFrequencyMhz(context: Context): Int? {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val freq = wm.connectionInfo?.frequency ?: return null
        return if (freq > 0) freq else null
    }

    /**
     * The 802.11 Wi-Fi standard of the current connection as a user-facing label,
     * or null when not connected to Wi-Fi, the standard is unknown/legacy, or the
     * device reports [android.net.wifi.ScanResult.WIFI_STANDARD_UNKNOWN].
     *
     * Uses [android.net.wifi.WifiInfo.getWifiStandard] (API 30+, matching minSdk).
     * Returns one of: "Wi-Fi 4" (802.11n), "Wi-Fi 5" (802.11ac), "Wi-Fi 6" (802.11ax /
     * Wi-Fi 6E), "Wi-Fi 7" (802.11be). Legacy 802.11a/b/g returns null — too old to be
     * worth displaying on a modern device. Uses same deprecated [WifiInfo] as [wifiRssi].
     *
     * Note: the WIFI_STANDARD_* constants are defined in [android.net.wifi.ScanResult],
     * not [android.net.wifi.WifiInfo], despite being returned by [WifiInfo.getWifiStandard].
     */
    @Suppress("DEPRECATION")
    fun wifiStandard(context: Context): String? {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val info = wm.connectionInfo ?: return null
        return when (info.wifiStandard) {
            android.net.wifi.ScanResult.WIFI_STANDARD_11N  -> "Wi-Fi 4"  // 802.11n
            android.net.wifi.ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"  // 802.11ac
            android.net.wifi.ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"  // 802.11ax / Wi-Fi 6E
            android.net.wifi.ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7"  // 802.11be
            else -> null  // UNKNOWN(0), LEGACY(1), or future undeclared standards
        }
    }

    /**
     * The SSID (network name) of the currently connected Wi-Fi network, or null
     * when not connected, the SSID is unknown, or the SSID is unavailable because
     * [android.Manifest.permission.ACCESS_FINE_LOCATION] has not been granted.
     *
     * Android wraps SSIDs in double-quote characters (`"example"`); this function
     * strips them so the value is ready to display as-is. Returns null rather than
     * `<unknown ssid>` or an empty string.
     *
     * Uses the deprecated [WifiInfo] API (same path as [wifiRssi]), which is the
     * only approach that works reliably without requiring a [android.net.NetworkCallback].
     * ACCESS_FINE_LOCATION must be granted at runtime on API 29+ for this to return
     * anything other than null/unknown; the permission is already in the manifest and
     * prompted via [LocationHero].
     */
    @Suppress("DEPRECATION")
    fun wifiSsid(context: Context): String? = runCatching {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return@runCatching null
        val raw = wm.connectionInfo?.ssid ?: return@runCatching null
        if (raw.isBlank() || raw == WifiManager.UNKNOWN_SSID) return@runCatching null
        raw.trim('"').takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Returns the device's primary IPv4 address (e.g. "192.168.1.42"), or null if the
     * device has no active non-loopback IPv4 interface.
     *
     * Uses [NetworkInterface.getNetworkInterfaces] which works on all API levels and
     * does not require any permissions.
     */
    fun localIpV4(): String? = try {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    } catch (_: Exception) { null }

    /**
     * Returns the device's primary global-scope IPv6 address (e.g. "2001:db8::1"), or null if
     * the device has no active global-scope IPv6 interface (link-local fe80:: addresses are
     * excluded as they are not routable).
     *
     * Uses [NetworkInterface.getNetworkInterfaces] which works on all API levels and
     * does not require any permissions.
     */
    fun localIpV6(): String? = try {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress && it is Inet6Address }
            ?.hostAddress
            ?.substringBefore('%')   // strip any zone-id suffix (e.g. %wlan0)
    } catch (_: Exception) { null }

    // -- Charge time remaining --------------------------------------------------

    /**
     * Estimated time remaining until the battery is fully charged, in milliseconds.
     *
     * Uses [BatteryManager.computeChargeTimeRemaining] (API 21+, always available
     * since Marrow's minSdk is 30). Returns -1L when the device is not charging,
     * the estimate is unavailable, or BatteryManager cannot be obtained.
     */
    fun chargeTimeRemainingMs(context: Context): Long {
        val mgr = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return -1L
        return mgr.computeChargeTimeRemaining()
    }

    /**
     * Formats a charge time remaining in milliseconds as a concise human-readable string.
     *
     * Examples: 3_960_000ms (66 min) → "1h 6m", 2_700_000ms (45 min) → "45m", ≤ 0 → "—"
     */
    fun formatChargeEta(ms: Long): String {
        if (ms <= 0L) return "—"
        val totalSeconds = ms / 1000L
        val hours = totalSeconds / 3_600L
        val mins = (totalSeconds % 3_600L) / 60L
        return when {
            hours > 0L && mins > 0L -> "${hours}h ${mins}m"
            hours > 0L              -> "${hours}h"
            mins > 0L               -> "${mins}m"
            else                    -> "<1m"
        }
    }


    // -- Screen brightness -------------------------------------------------------

    /**
     * Current screen brightness as a percentage (0–100).
     *
     * Reads [Settings.System.SCREEN_BRIGHTNESS] (raw 0–255 integer). Responds to
     * adaptive brightness changes in real time — [SCREEN_BRIGHTNESS] tracks the actual
     * hardware backlight level even when automatic mode is active.
     *
     * No special permissions required; [Settings.System] is always readable by
     * standard apps. Returns null when the key is absent (should not occur on
     * standard Android).
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

    /**
     * Whether the system is currently in dark mode (Night theme active).
     *
     * Reads [android.content.res.Configuration.uiMode] from the app's resources,
     * which reflects the current effective night mode state. Returns true when dark
     * mode is active, false when light mode is active.
     *
     * No permissions required. Available on all API levels.
     */
    fun isDarkMode(context: Context): Boolean? = try {
        (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    } catch (_: Exception) { null }

    /**
     * Whether auto-rotate (orientation sensor control) is enabled.
     *
     * Reads [Settings.System.ACCELEROMETER_ROTATION]: 1 = auto-rotate on,
     * 0 = orientation locked. Same zero-permission approach as
     * [screenBrightnessAuto] — [Settings.System] is world-readable.
     */
    fun isAutoRotateEnabled(context: Context): Boolean? = try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
        ) == 1
    } catch (_: Settings.SettingNotFoundException) { null }

    /**
     * Screen-off timeout in milliseconds from [Settings.System.SCREEN_OFF_TIMEOUT].
     *
     * Common values: 15 000 (15 s), 30 000 (30 s), 60 000 (1 m), 120 000 (2 m),
     * 300 000 (5 m), 600 000 (10 m), 1 800 000 (30 m). Returns null when the key
     * is absent (should not occur on standard Android) or the raw value is ≤ 0.
     * No permissions required.
     */
    fun screenTimeoutMs(context: Context): Int? {
        val ms = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
        return if (ms > 0) ms else null
    }

    /**
     * Formats a screen-off timeout in milliseconds as a concise human-readable string.
     *
     * Examples: 30 000 → "30s", 90 000 → "1m 30s", 120 000 → "2m", 3 600 000 → "1h 0m".
     * Exposed as a pure function for unit testability.
     */
    fun formatScreenTimeout(ms: Int): String = when {
        ms < 60_000 -> "${ms / 1000}s"
        ms < 3_600_000 -> {
            val m = ms / 60_000
            val s = (ms % 60_000) / 1000
            if (s == 0) "${m}m" else "${m}m ${s}s"
        }
        else -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
    }

    // -- Display refresh rate ---------------------------------------------------

    /**
     * Current screen refresh rate in Hz.
     *
     * Reads [android.view.Display.getRefreshRate] via [Context.getDisplay] (API 30+,
     * matching Marrow's minSdk). On fixed-rate displays this always returns the panel's
     * configured rate. On LTPO / VRR panels (e.g. Pixel 8 Pro, Samsung Galaxy S24 Ultra)
     * the value adapts dynamically — 120 Hz when scrolling, as low as 1 Hz when the
     * screen is static. No permissions required.
     *
     * Returns null when [Context.getDisplay] is unavailable (service environment,
     * non-UI context, or unusual device configuration).
     */
    fun screenRefreshRateHz(context: Context): Float? = runCatching {
        context.display?.refreshRate?.takeIf { it > 0f }
    }.getOrNull()


    /**
     * Returns whether battery saver (low-power) mode is currently active,
     * null when [PowerManager] is unavailable. No special permissions required.
     */
    fun batterySaverActive(context: Context): Boolean? = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        pm?.isPowerSaveMode
    }.getOrNull()

    /**
     * Maps a raw [PowerManager.currentThermalStatus] integer to a human-readable label.
     *
     * Extracted as a pure function so it can be unit-tested without an Android context.
     * Constants are stable since API 29 (Android 10):
     *   0 = NONE, 1 = LIGHT, 2 = MODERATE, 3 = SEVERE, 4 = CRITICAL,
     *   5 = EMERGENCY, 6 = SHUTDOWN.
     */
    internal fun thermalStatusLabel(status: Int): String = when (status) {
        0 -> "None"
        1 -> "Light"
        2 -> "Moderate"
        3 -> "Severe"
        4 -> "Critical"
        5 -> "Emergency"
        6 -> "Shutdown"
        else -> "Unknown"
    }

    /**
     * Returns the system-level thermal throttling status as a human-readable string,
     * or null when [PowerManager] is unavailable (API < 29).
     *
     * Values: "None", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown".
     * No special permissions required.
     */
    fun thermalStatusStr(context: Context): String? = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            ?: return@runCatching null
        if (android.os.Build.VERSION.SDK_INT < 29) return@runCatching null
        thermalStatusLabel(pm.currentThermalStatus)
    }.getOrNull()

    /**
     * Maps a Wi-Fi frequency in MHz to an 802.11 channel number, or null when the
     * frequency is not within a known Wi-Fi band.
     *
     * Extracted as a pure function so it can be unit-tested without an Android context.
     * No new data reads or permissions — callers use the already-polled [wifiFrequencyMhz].
     *
     * Band→channel mapping:
     *   2.4 GHz: channels 1–13 at 2412–2472 MHz (Δ5 MHz each), channel 14 at 2484 MHz
     *   5 GHz:   channels 36–165 at 5180–5825 MHz (Δ5 MHz each, (freq-5000)/5)
     *   6 GHz:   channels 1–93 at 5955–7115 MHz (Δ20 MHz spacing, (freq-5950)/5)
     */
    fun wifiChannelFromMhz(freqMhz: Int): Int? = when {
        freqMhz == 2484              -> 14                      // 2.4 GHz ch14 (Japan)
        freqMhz in 2412..2472        -> (freqMhz - 2407) / 5   // 2.4 GHz ch1–ch13
        freqMhz in 5180..5825        -> (freqMhz - 5000) / 5   // 5 GHz ch36–ch165
        freqMhz in 5955..7115        -> (freqMhz - 5950) / 5   // 6 GHz ch1–ch93
        else                         -> null
    }

    // -- Wi-Fi signal quality ---------------------------------------------------

    /**
     * Maps a Wi-Fi RSSI value in dBm to a user-facing signal quality label.
     *
     * Thresholds are derived from Android's own [WifiManager.calculateSignalLevel] tiers
     * and common industry conventions:
     *   ≥ −50 dBm → "Excellent"  (full bars, streaming / gaming reliable)
     *   ≥ −65 dBm → "Good"       (typical home Wi-Fi, video calls fine)
     *   ≥ −80 dBm → "Fair"       (usable, buffering may occur)
     *    < −80 dBm → "Poor"       (weak signal, frequent drops)
     *
     * Extracted as a pure function so it can be unit-tested without an Android context.
     * Callers use the already-polled [wifiRssi] value — no new permissions required.
     *
     * @param rssiDbm RSSI in dBm; valid range is typically −120..0. Values outside
     *                this range still produce a label (clamped to "Poor" or "Excellent").
     */
    fun wifiSignalQuality(rssiDbm: Int): String = when {
        rssiDbm >= -50 -> "Excellent"
        rssiDbm >= -65 -> "Good"
        rssiDbm >= -80 -> "Fair"
        else           -> "Poor"
    }

    // -- Audio output routing ----------------------------------------------------

    /**
     * Maps a list of [android.media.AudioDeviceInfo] TYPE_ integer constants to a user-facing
     * audio output route label. Extracted as a pure function so it can be unit-tested without
     * an Android context.
     *
     * Priority order: Bluetooth > Wired > Earpiece > Speaker.
     *
     * Type constant values (android.media.AudioDeviceInfo):
     *   Bluetooth: TYPE_BLUETOOTH_SCO=7, TYPE_BLUETOOTH_A2DP=13, TYPE_BLE_HEADSET=26, TYPE_BLE_SPEAKER=27
     *   Wired:     TYPE_WIRED_HEADSET=3, TYPE_WIRED_HEADPHONES=8, TYPE_USB_HEADSET=23
     *   Earpiece:  TYPE_BUILTIN_EARPIECE=1
     *   Speaker:   TYPE_BUILTIN_SPEAKER=2
     */
    internal fun audioOutputLabel(types: List<Int>): String? {
        if (types.isEmpty()) return null
        return when {
            types.any { it in listOf(7, 13, 26, 27) } -> "Bluetooth"
            types.any { it in listOf(3, 8, 23) }       -> "Wired"
            types.any { it == 1 }                      -> "Earpiece"
            types.any { it == 2 }                      -> "Speaker"
            else                                       -> null
        }
    }

    /**
     * Current audio output route as a user-facing label: "Bluetooth", "Wired", "Earpiece",
     * or "Speaker". Returns null when [android.media.AudioManager] is unavailable or no
     * known outputs are active.
     *
     * Reads [android.media.AudioManager.getDevices] with [android.media.AudioManager.GET_DEVICES_OUTPUTS]
     * — zero permissions required (available API 23+, minSdk=30).
     *
     * Phone only. Zero permissions required.
     */
    fun audioOutputRoute(context: Context): String? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@runCatching null
        val types = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        audioOutputLabel(types)
    }.getOrNull()

    // -- Connectivity toggles ----------------------------------------------------

    /**
     * Whether airplane mode is currently enabled.
     *
     * Reads [Settings.Global.AIRPLANE_MODE_ON]. Zero permissions required.
     */
    fun isAirplaneModeOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    /**
     * Whether NFC is currently enabled, or null if the device has no NFC hardware.
     *
     * Uses [android.nfc.NfcAdapter.getDefaultAdapter]; returns null when the adapter
     * is not present (hardware absent). Zero additional permissions required.
     */
    fun isNfcEnabled(context: Context): Boolean? =
        runCatching {
            android.nfc.NfcAdapter.getDefaultAdapter(context)?.isEnabled
        }.getOrNull()

    /**
     * Whether Bluetooth is currently enabled on this device.
     *
     * Reads Settings.Global key "bluetooth_on" which is world-readable —
     * zero additional permissions required on all API levels (including API 31+
     * where BLUETOOTH_CONNECT would otherwise be needed for BluetoothAdapter).
     *
     * Returns null only on unexpected exceptions (should never happen in practice).
     */
    fun isBluetoothEnabled(context: Context): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, "bluetooth_on", -1)
            .takeIf { it >= 0 }
            ?.let { it != 0 }
    }.getOrNull()

    /**
     * Whether the mobile hotspot (Wi-Fi tethering / soft AP) is currently active.
     *
     * [WifiManager.isWifiApEnabled] is a `@hide` API since Android 8 (API 26) and is
     * not accessible via the public SDK. Accessed via reflection — safe on minSdk 30
     * since the method has been present in AOSP since API 8. Returns null on any
     * exception (including reflection failures). Zero new permissions required.
     */
    fun isHotspotEnabled(context: Context): Boolean? = runCatching {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DiscouragedPrivateApi")
        val m = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
        m.invoke(wm) as? Boolean ?: false
    }.getOrNull()

    /**
     * Whether a VPN tunnel is currently active on this device.
     *
     * Checks [android.net.NetworkCapabilities.TRANSPORT_VPN] on the active network
     * via [android.net.ConnectivityManager]. ACCESS_NETWORK_STATE is already declared
     * in the manifest — no additional permissions required.
     *
     * Returns false when there is no active network (airplane mode, etc.);
     * null when ConnectivityManager is unavailable (should not occur in practice).
     */
    fun isVpnActive(context: Context): Boolean? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return@runCatching null
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
    }.getOrNull()

    // -- Network traffic totals -------------------------------------------------

    /**
     * Total bytes received across all network interfaces since the last device boot.
     *
     * Reads [android.net.TrafficStats.getTotalRxBytes]. No permissions required.
     * Returns null when the counter is unavailable
     * ([android.net.TrafficStats.UNSUPPORTED] = -1L).
     */
    fun totalRxBytes(): Long? {
        val v = android.net.TrafficStats.getTotalRxBytes()
        return if (v == android.net.TrafficStats.UNSUPPORTED.toLong()) null else v
    }

    /**
     * Total bytes transmitted across all network interfaces since the last device boot.
     *
     * Reads [android.net.TrafficStats.getTotalTxBytes]. No permissions required.
     * Returns null when the counter is unavailable
     * ([android.net.TrafficStats.UNSUPPORTED] = -1L).
     */
    fun totalTxBytes(): Long? {
        val v = android.net.TrafficStats.getTotalTxBytes()
        return if (v == android.net.TrafficStats.UNSUPPORTED.toLong()) null else v
    }

    /**
     * Formats a byte count as a concise human-readable string using SI prefixes.
     *
     * Examples: 999 → "999 B", 1234 → "1.2 KB", 1_234_567 → "1.2 MB",
     * 1_234_567_890 → "1.2 GB".
     */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000f)
        bytes >= 1_000_000L     -> "%.1f MB".format(bytes / 1_000_000f)
        bytes >= 1_000L         -> "%.1f KB".format(bytes / 1_000f)
        else                    -> "$bytes B"
    }

    // -- Cellular ----------------------------------------------------------------

    /**
     * Live cellular/telephony snapshot. Fields that require [android.Manifest.permission.READ_BASIC_PHONE_STATE]
     * (normal permission, API 33+) or [android.Manifest.permission.READ_PHONE_STATE] (dangerous) are
     * wrapped in [runCatching] and fall back to `null` when the permission is absent or the device
     * does not support telephony.
     *
     * @param operatorName    Registered network operator display name (e.g. "Vodafone IE"). Null when
     *                        the radio is unregistered, in airplane mode, or the SIM is absent.
     * @param simOperatorName SIM service provider name from the carrier file on the SIM card.
     *                        May differ from [operatorName] when roaming.
     * @param simState        Human-readable SIM state: "Ready", "Absent", "PIN Required",
     *                        "PUK Required", "Network Locked", "I/O Error", "Restricted", or "Unknown".
     * @param networkTypeName Generation label of the active data network: "5G", "LTE", "3G", "2G",
     *                        "Wi-Fi Call", or null when the type is unknown or unavailable.
     * @param signalLevel     Signal strength bar count: 0 (none) – 4 (excellent). Derived from
     *                        [android.telephony.SignalStrength.getLevel]. Null when unavailable.
     * @param isRoaming       True when the device is using a network other than its home network.
     */
    data class Cellular(
        val operatorName: String?,
        val simOperatorName: String?,
        val simState: String,
        val networkTypeName: String?,
        val signalLevel: Int?,
        val isRoaming: Boolean,
    )

    /**
     * Returns a [Cellular] snapshot from [android.telephony.TelephonyManager].
     *
     * Zero-permission fields are always populated. Fields requiring
     * [android.Manifest.permission.READ_BASIC_PHONE_STATE] (API 33+ normal permission)
     * fall back to null gracefully on API < 33 or when the permission is missing.
     * Always safe — every access is wrapped in [runCatching].
     *
     * Returns null when the device has no telephony hardware
     * ([android.content.pm.PackageManager.FEATURE_TELEPHONY] absent).
     */
    fun cellularInfo(context: Context): Cellular? = runCatching {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            ?: return@runCatching null
        if (!context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)) {
            return@runCatching null
        }

        // Zero-permission fields
        val operatorName = tm.networkOperatorName.takeIf { it.isNotBlank() }
        val simOperatorName = tm.simOperatorName.takeIf { it.isNotBlank() }
        val simState = when (tm.simState) {
            android.telephony.TelephonyManager.SIM_STATE_READY           -> "Ready"
            android.telephony.TelephonyManager.SIM_STATE_ABSENT          -> "Absent"
            android.telephony.TelephonyManager.SIM_STATE_PIN_REQUIRED    -> "PIN Required"
            android.telephony.TelephonyManager.SIM_STATE_PUK_REQUIRED    -> "PUK Required"
            android.telephony.TelephonyManager.SIM_STATE_NETWORK_LOCKED  -> "Network Locked"
            android.telephony.TelephonyManager.SIM_STATE_CARD_IO_ERROR   -> "I/O Error"
            android.telephony.TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "Restricted"
            else                                                          -> "Unknown"
        }
        val isRoaming = tm.isNetworkRoaming

        // READ_BASIC_PHONE_STATE (normal, API 33+) — network type + signal level
        // runCatching handles SecurityException on API < 33 without READ_PHONE_STATE
        val networkTypeName = runCatching {
            cellularNetworkTypeName(tm.dataNetworkType)
        }.getOrNull()
        val signalLevel = runCatching {
            tm.signalStrength?.level?.takeIf { it in 0..4 }
        }.getOrNull()

        Cellular(operatorName, simOperatorName, simState, networkTypeName, signalLevel, isRoaming)
    }.getOrNull()

    /** Maps a [android.telephony.TelephonyManager.getDataNetworkType] constant to a
     *  generation label. Returns null for unknown / unavailable. */
    private fun cellularNetworkTypeName(type: Int): String? = when (type) {
        android.telephony.TelephonyManager.NETWORK_TYPE_GPRS,
        android.telephony.TelephonyManager.NETWORK_TYPE_EDGE,
        android.telephony.TelephonyManager.NETWORK_TYPE_CDMA,
        android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT,
        android.telephony.TelephonyManager.NETWORK_TYPE_IDEN    -> "2G"
        android.telephony.TelephonyManager.NETWORK_TYPE_UMTS,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSPA,
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0,
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A,
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B,
        android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP   -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_LTE     -> "LTE"
        android.telephony.TelephonyManager.NETWORK_TYPE_IWLAN   -> "Wi-Fi Call"
        android.telephony.TelephonyManager.NETWORK_TYPE_NR      -> "5G"
        else                                                     -> null
    }


    /**
     * Current phone call state as a human-readable label.
     *
     * Reads [android.telephony.TelephonyManager.callState]:
     * CALL_STATE_IDLE → "Idle", CALL_STATE_RINGING → "Ringing",
     * CALL_STATE_OFFHOOK → "In Call".
     *
     * [android.Manifest.permission.READ_BASIC_PHONE_STATE] (normal, API 31+) is already
     * declared in the phone manifest from [CellularHero]. On API 30 a SecurityException
     * may be thrown — the [runCatching] wrapper returns null gracefully in that case.
     * Always returns null on devices without telephony hardware.
     */
    fun callState(context: Context): String? = runCatching {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE)
            as? android.telephony.TelephonyManager ?: return@runCatching null
        when (tm.callState) {
            android.telephony.TelephonyManager.CALL_STATE_IDLE    -> "Idle"
            android.telephony.TelephonyManager.CALL_STATE_RINGING -> "Ringing"
            android.telephony.TelephonyManager.CALL_STATE_OFFHOOK -> "In Call"
            else -> null
        }
    }.getOrNull()

    // -- Audio -------------------------------------------------------------------

    /** Current ringer mode from [android.media.AudioManager.getRingerMode]. */
    enum class RingerMode { NORMAL, VIBRATE, SILENT }

    /**
     * Current ringer mode, or null when [AudioManager] is unavailable.
     *
     * Maps [android.media.AudioManager.RINGER_MODE_NORMAL] / [RINGER_MODE_VIBRATE] /
     * [RINGER_MODE_SILENT] to [RingerMode]. No permissions required.
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
     * Reads [android.media.AudioManager.getStreamVolume] /
     * [android.media.AudioManager.getStreamMaxVolume] for [STREAM_MUSIC].
     * Returns null when [AudioManager] is unavailable or max volume is 0.
     * No permissions required.
     */
    fun mediaVolumePct(context: Context): Int? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@runCatching null
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        if (max <= 0) return@runCatching null
        val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        (cur * 100 / max).coerceIn(0, 100)
    }.getOrNull()

    /**
     * Current ring stream volume as a percentage (0–100).
     * Reads [android.media.AudioManager.getStreamVolume] /
     * [android.media.AudioManager.getStreamMaxVolume] for [STREAM_RING].
     * Returns null when [AudioManager] is unavailable or max volume is 0.
     * No permissions required.
     */
    fun ringVolumePct(context: Context): Int? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@runCatching null
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_RING)
        if (max <= 0) return@runCatching null
        val cur = am.getStreamVolume(android.media.AudioManager.STREAM_RING)
        (cur * 100 / max).coerceIn(0, 100)
    }.getOrNull()

    /**
     * Current alarm stream volume as a percentage (0–100).
     * Reads [android.media.AudioManager.getStreamVolume] /
     * [android.media.AudioManager.getStreamMaxVolume] for [STREAM_ALARM].
     * Returns null when [AudioManager] is unavailable or max volume is 0.
     * No permissions required.
     */
    fun alarmVolumePct(context: Context): Int? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@runCatching null
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
        if (max <= 0) return@runCatching null
        val cur = am.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
        (cur * 100 / max).coerceIn(0, 100)
    }.getOrNull()

    /**
     * Current notification stream volume as a percentage (0–100).
     * Reads [android.media.AudioManager.getStreamVolume] /
     * [android.media.AudioManager.getStreamMaxVolume] for [STREAM_NOTIFICATION].
     * Returns null when [AudioManager] is unavailable or max volume is 0.
     * No permissions required.
     */
    fun notificationVolumePct(context: Context): Int? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@runCatching null
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_NOTIFICATION)
        if (max <= 0) return@runCatching null
        val cur = am.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION)
        (cur * 100 / max).coerceIn(0, 100)
    }.getOrNull()

    /**
     * Current system stream volume as a percentage (0–100).
     * Reads [android.media.AudioManager.getStreamVolume] /
     * [android.media.AudioManager.getStreamMaxVolume] for [STREAM_SYSTEM].
     * Returns null when [AudioManager] is unavailable or max volume is 0.
     * No permissions required.
     */
    fun systemVolumePct(context: Context): Int? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@runCatching null
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_SYSTEM)
        if (max <= 0) return@runCatching null
        val cur = am.getStreamVolume(android.media.AudioManager.STREAM_SYSTEM)
        (cur * 100 / max).coerceIn(0, 100)
    }.getOrNull()

    /**
     * Current voice-call volume as a 0–100 % integer.
     *
     * Reads [android.media.AudioManager.STREAM_VOICE_CALL] — the in-ear earpiece / hands-free
     * call volume, distinct from the ringer, media, and notification streams. Shows "—" when
     * no call is in progress but the slot is always present and adjustable by the user.
     * Zero permissions required.
     *
     * Returns null when [AudioManager] is unavailable or the stream has no valid max.
     */
    fun voiceCallVolumePct(context: Context): Int? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@runCatching null
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL)
        if (max <= 0) return@runCatching null
        val cur = am.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL)
        (cur * 100 / max).coerceIn(0, 100)
    }.getOrNull()

    /**
     * Accessibility audio stream volume as a percentage (0–100).
     *
     * Reads [android.media.AudioManager.STREAM_ACCESSIBILITY] — the stream used by
     * screen readers and other accessibility services. API 26+. No permissions required.
     * Returns null when [AudioManager] is unavailable or the stream has no valid max.
     */
    fun accessibilityVolumePct(context: Context): Int? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@runCatching null
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ACCESSIBILITY)
        if (max <= 0) return@runCatching null
        val cur = am.getStreamVolume(android.media.AudioManager.STREAM_ACCESSIBILITY)
        (cur * 100 / max).coerceIn(0, 100)
    }.getOrNull()

    /**
     * Whether music is currently active (playing or paused-recently) via
     * [android.media.AudioManager.isMusicActive]. No permissions required.
     * Returns null when [AudioManager] is unavailable.
     */
    fun isMusicActive(context: Context): Boolean? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@runCatching null
        am.isMusicActive
    }.getOrNull()

    /**
     * Current Do Not Disturb (DND) interruption filter as a user-facing label.
     *
     * Reads [android.app.NotificationManager.getCurrentInterruptionFilter]. Zero permissions
     * required — the interruption filter is readable by any app without special permissions.
     *
     * Returns one of: "Off" (all notifications pass through),
     * "Priority" (priority-only mode — calls/alarms from priority contacts),
     * "Alarms" (alarms only), "Total Silence" (all sounds blocked).
     * Returns null when [NotificationManager] is unavailable or the filter is
     * [android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN] (0).
     *
     * Phone only. Zero permissions required.
     */
    fun dndMode(context: Context): String? = runCatching {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? android.app.NotificationManager ?: return@runCatching null
        when (nm.currentInterruptionFilter) {
            android.app.NotificationManager.INTERRUPTION_FILTER_ALL      -> "Off"
            android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority"
            android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS   -> "Alarms"
            android.app.NotificationManager.INTERRUPTION_FILTER_NONE     -> "Total Silence"
            else                                                          -> null
        }
    }.getOrNull()

    // -- helpers -----------------------------------------------------------------

    private fun readLong(path: String): Long =
        runCatching { File(path).readText().trim().toLong() }.getOrDefault(0L)

    private fun readString(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()
}

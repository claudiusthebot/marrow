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
        /** Charge cycle count from sysfs. -1 when absent (emulator / OEM restriction). */
        val cycleCount: Int = -1,          // ≥0, -1 unknown
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
            cycleCount = readLong("/sys/class/power_supply/battery/cycle_count")
                .let { if (it > 0L) it.toInt() else -1 },
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
     * Whether music is currently active (playing or paused-recently) via
     * [android.media.AudioManager.isMusicActive]. No permissions required.
     * Returns null when [AudioManager] is unavailable.
     */
    fun isMusicActive(context: Context): Boolean? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@runCatching null
        am.isMusicActive
    }.getOrNull()

    // -- helpers -----------------------------------------------------------------

    private fun readLong(path: String): Long =
        runCatching { File(path).readText().trim().toLong() }.getOrDefault(0L)

    private fun readString(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()
}

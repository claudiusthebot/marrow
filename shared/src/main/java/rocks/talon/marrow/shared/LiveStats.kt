package rocks.talon.marrow.shared

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.io.File

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
    ) {
        enum class PlugType { UNPLUGGED, AC, USB, WIRELESS, DOCK }
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
        return Battery(
            percent = percent,
            charging = charging,
            plugged = plug,
            temperatureC = if (tempTenths >= 0) tempTenths / 10f else -1f,
            voltageV = if (voltageMv >= 0) voltageMv / 1000f else -1f,
            currentMa = if (current != Int.MIN_VALUE) current / 1000 else Int.MIN_VALUE,
            technology = tech,
            healthy = healthInt == BatteryManager.BATTERY_HEALTH_GOOD || healthInt == -1,
        )
    }

    // -- Memory ------------------------------------------------------------------

    data class Memory(
        val totalBytes: Long,
        val availBytes: Long,
        val thresholdBytes: Long,
        val lowMemory: Boolean,
    ) {
        val usedBytes: Long get() = (totalBytes - availBytes).coerceAtLeast(0L)
        val usedFraction: Float
            get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
        val usedPercent: Int get() = (usedFraction * 100f).toInt()
    }

    fun memory(context: Context): Memory {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return Memory(mi.totalMem, mi.availMem, mi.threshold, mi.lowMemory)
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

    // -- helpers -----------------------------------------------------------------

    private fun readLong(path: String): Long =
        runCatching { File(path).readText().trim().toLong() }.getOrDefault(0L)

    private fun readString(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()
}

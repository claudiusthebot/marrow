package rocks.talon.marrow.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure (non-Android-Context) helpers in [LiveStats].
 *
 * These cover the silent-bug surface: arithmetic on snapshot deltas, byte/duration
 * formatters, and derived properties on the data classes. A regression here
 * would mis-display device stats in the phone hero strip and the watch tile
 * with no crash and no log line — exactly the kind of thing tests catch best.
 */
class LiveStatsTest {

    // ── cpuUsagePercent ────────────────────────────────────────────────────────

    @Test fun cpuUsagePercent_normal_50_percent() {
        // 100 jiffies elapsed total, 50 of them idle → 50% busy.
        val prev = LiveStats.CpuStatSnapshot(total = 1000L, idle = 900L, timestampMs = 0L)
        val curr = LiveStats.CpuStatSnapshot(total = 1100L, idle = 950L, timestampMs = 1000L)
        assertEquals(50f, LiveStats.cpuUsagePercent(prev, curr), 0.001f)
    }

    @Test fun cpuUsagePercent_fully_busy() {
        val prev = LiveStats.CpuStatSnapshot(total = 0L, idle = 0L, timestampMs = 0L)
        val curr = LiveStats.CpuStatSnapshot(total = 1000L, idle = 0L, timestampMs = 1000L)
        assertEquals(100f, LiveStats.cpuUsagePercent(prev, curr), 0.001f)
    }

    @Test fun cpuUsagePercent_fully_idle() {
        val prev = LiveStats.CpuStatSnapshot(total = 0L, idle = 0L, timestampMs = 0L)
        val curr = LiveStats.CpuStatSnapshot(total = 1000L, idle = 1000L, timestampMs = 1000L)
        assertEquals(0f, LiveStats.cpuUsagePercent(prev, curr), 0.001f)
    }

    @Test fun cpuUsagePercent_zero_delta_returns_zero() {
        // Identical snapshots (same total) → no jiffies elapsed → return 0, not NaN.
        val snap = LiveStats.CpuStatSnapshot(total = 1000L, idle = 500L, timestampMs = 0L)
        assertEquals(0f, LiveStats.cpuUsagePercent(snap, snap), 0.001f)
    }

    @Test fun cpuUsagePercent_negative_delta_returns_zero() {
        // Curr.total < prev.total — clock skew / counter rollover.
        val prev = LiveStats.CpuStatSnapshot(total = 2000L, idle = 1000L, timestampMs = 0L)
        val curr = LiveStats.CpuStatSnapshot(total = 1000L, idle = 500L, timestampMs = 1000L)
        assertEquals(0f, LiveStats.cpuUsagePercent(prev, curr), 0.001f)
    }

    @Test fun cpuUsagePercent_idle_grew_more_than_total_clamped_to_zero() {
        // Pathological: idle counter advanced more than total — coerce 0..1 → 0%.
        val prev = LiveStats.CpuStatSnapshot(total = 1000L, idle = 900L, timestampMs = 0L)
        val curr = LiveStats.CpuStatSnapshot(total = 1100L, idle = 1100L, timestampMs = 1000L)
        assertEquals(0f, LiveStats.cpuUsagePercent(prev, curr), 0.001f)
    }

    // ── networkRate ────────────────────────────────────────────────────────────

    @Test fun networkRate_one_second_one_kbyte() {
        val prev = LiveStats.NetworkSpeed(rxBytesTotal = 0L, txBytesTotal = 0L, timestampMs = 0L)
        val curr = LiveStats.NetworkSpeed(rxBytesTotal = 1000L, txBytesTotal = 2000L, timestampMs = 1000L)
        val (rx, tx) = LiveStats.networkRate(prev, curr)
        assertEquals(1000L, rx)
        assertEquals(2000L, tx)
    }

    @Test fun networkRate_half_second_doubles_rate() {
        val prev = LiveStats.NetworkSpeed(rxBytesTotal = 0L, txBytesTotal = 0L, timestampMs = 0L)
        val curr = LiveStats.NetworkSpeed(rxBytesTotal = 500L, txBytesTotal = 1000L, timestampMs = 500L)
        val (rx, tx) = LiveStats.networkRate(prev, curr)
        assertEquals(1000L, rx)
        assertEquals(2000L, tx)
    }

    @Test fun networkRate_zero_elapsed_returns_zero() {
        val snap = LiveStats.NetworkSpeed(rxBytesTotal = 100L, txBytesTotal = 200L, timestampMs = 1000L)
        assertEquals(0L to 0L, LiveStats.networkRate(snap, snap))
    }

    @Test fun networkRate_negative_elapsed_returns_zero() {
        val prev = LiveStats.NetworkSpeed(rxBytesTotal = 0L, txBytesTotal = 0L, timestampMs = 2000L)
        val curr = LiveStats.NetworkSpeed(rxBytesTotal = 1000L, txBytesTotal = 1000L, timestampMs = 1000L)
        assertEquals(0L to 0L, LiveStats.networkRate(prev, curr))
    }

    @Test fun networkRate_counter_decrease_clamped_to_zero() {
        // Interface reset — curr counters lower than prev.
        val prev = LiveStats.NetworkSpeed(rxBytesTotal = 5000L, txBytesTotal = 5000L, timestampMs = 0L)
        val curr = LiveStats.NetworkSpeed(rxBytesTotal = 100L, txBytesTotal = 100L, timestampMs = 1000L)
        val (rx, tx) = LiveStats.networkRate(prev, curr)
        assertEquals(0L, rx)
        assertEquals(0L, tx)
    }

    // ── diskRate ───────────────────────────────────────────────────────────────

    @Test fun diskRate_one_sector_one_second_is_512_bps() {
        // 1 sector = 512 B per /proc/diskstats convention.
        val prev = LiveStats.DiskSnapshot(readSectors = 0L, writeSectors = 0L, timestampMs = 0L)
        val curr = LiveStats.DiskSnapshot(readSectors = 1L, writeSectors = 1L, timestampMs = 1000L)
        val (read, write) = LiveStats.diskRate(prev, curr)
        assertEquals(512L, read)
        assertEquals(512L, write)
    }

    @Test fun diskRate_thousand_sectors_one_second() {
        val prev = LiveStats.DiskSnapshot(readSectors = 0L, writeSectors = 0L, timestampMs = 0L)
        val curr = LiveStats.DiskSnapshot(readSectors = 1000L, writeSectors = 2000L, timestampMs = 1000L)
        val (read, write) = LiveStats.diskRate(prev, curr)
        assertEquals(512_000L, read)
        assertEquals(1_024_000L, write)
    }

    @Test fun diskRate_zero_elapsed_returns_zero() {
        val snap = LiveStats.DiskSnapshot(readSectors = 100L, writeSectors = 100L, timestampMs = 1000L)
        assertEquals(0L to 0L, LiveStats.diskRate(snap, snap))
    }

    @Test fun diskRate_negative_elapsed_returns_zero() {
        val prev = LiveStats.DiskSnapshot(readSectors = 0L, writeSectors = 0L, timestampMs = 2000L)
        val curr = LiveStats.DiskSnapshot(readSectors = 1000L, writeSectors = 1000L, timestampMs = 1000L)
        assertEquals(0L to 0L, LiveStats.diskRate(prev, curr))
    }

    @Test fun diskRate_counter_decrease_clamped_to_zero() {
        val prev = LiveStats.DiskSnapshot(readSectors = 5000L, writeSectors = 5000L, timestampMs = 0L)
        val curr = LiveStats.DiskSnapshot(readSectors = 100L, writeSectors = 100L, timestampMs = 1000L)
        val (read, write) = LiveStats.diskRate(prev, curr)
        assertEquals(0L, read)
        assertEquals(0L, write)
    }

    // ── formatSpeedBps ─────────────────────────────────────────────────────────

    @Test fun formatSpeedBps_below_1kb() {
        assertEquals("0 B/s", LiveStats.formatSpeedBps(0L))
        assertEquals("1 B/s", LiveStats.formatSpeedBps(1L))
        assertEquals("999 B/s", LiveStats.formatSpeedBps(999L))
    }

    @Test fun formatSpeedBps_kb_range() {
        assertEquals("1 KB/s", LiveStats.formatSpeedBps(1_000L))
        assertEquals("345 KB/s", LiveStats.formatSpeedBps(345_000L))
        assertEquals("999 KB/s", LiveStats.formatSpeedBps(999_999L))
    }

    @Test fun formatSpeedBps_mb_range() {
        assertEquals("1.0 MB/s", LiveStats.formatSpeedBps(1_000_000L))
        assertEquals("1.2 MB/s", LiveStats.formatSpeedBps(1_200_000L))
        assertEquals("12.3 MB/s", LiveStats.formatSpeedBps(12_345_678L))
    }

    // ── formatDiskBps ──────────────────────────────────────────────────────────

    @Test fun formatDiskBps_below_1kb() {
        assertEquals("0 B/s", LiveStats.formatDiskBps(0L))
        assertEquals("999 B/s", LiveStats.formatDiskBps(999L))
    }

    @Test fun formatDiskBps_kb_range() {
        assertEquals("1 KB/s", LiveStats.formatDiskBps(1_000L))
        assertEquals("999 KB/s", LiveStats.formatDiskBps(999_999L))
    }

    @Test fun formatDiskBps_mb_range() {
        assertEquals("1.0 MB/s", LiveStats.formatDiskBps(1_000_000L))
        assertEquals("12.3 MB/s", LiveStats.formatDiskBps(12_345_678L))
    }

    // ── formatUptime ───────────────────────────────────────────────────────────

    @Test fun formatUptime_zero_or_negative_returns_em_dash() {
        assertEquals("—", LiveStats.formatUptime(0L))
        assertEquals("—", LiveStats.formatUptime(-1L))
        assertEquals("—", LiveStats.formatUptime(Long.MIN_VALUE))
    }

    @Test fun formatUptime_minutes_only() {
        assertEquals("1m", LiveStats.formatUptime(60L))
        assertEquals("47m", LiveStats.formatUptime(47L * 60L))
        assertEquals("59m", LiveStats.formatUptime(59L * 60L))
    }

    @Test fun formatUptime_hours_only_when_minutes_zero() {
        assertEquals("1h", LiveStats.formatUptime(60L * 60L))
        assertEquals("5h", LiveStats.formatUptime(5L * 3600L))
    }

    @Test fun formatUptime_hours_and_minutes() {
        assertEquals("2h 34m", LiveStats.formatUptime(2L * 3600L + 34L * 60L))
        assertEquals("23h 59m", LiveStats.formatUptime(23L * 3600L + 59L * 60L))
    }

    @Test fun formatUptime_days_only_when_hours_zero() {
        assertEquals("1d", LiveStats.formatUptime(86_400L))
        assertEquals("7d", LiveStats.formatUptime(7L * 86_400L))
    }

    @Test fun formatUptime_days_and_hours() {
        assertEquals("5d 3h", LiveStats.formatUptime(5L * 86_400L + 3L * 3600L))
        assertEquals("1d 23h", LiveStats.formatUptime(86_400L + 23L * 3600L))
    }

    @Test fun formatUptime_days_hides_minutes_when_hours_present() {
        // Display rule: once we have days+hours, drop the minute granularity.
        // 1d 2h 30m → "1d 2h"
        val seconds = 86_400L + 2L * 3600L + 30L * 60L
        assertEquals("1d 2h", LiveStats.formatUptime(seconds))
    }

    @Test fun formatUptime_under_one_minute_renders_as_zero_minutes() {
        // 30 seconds → 0 minutes — falls through to "${mins}m" branch with mins=0.
        assertEquals("0m", LiveStats.formatUptime(30L))
    }

    // ── avgCurMhz ──────────────────────────────────────────────────────────────

    @Test fun avgCurMhz_empty_returns_zero() {
        assertEquals(0L, LiveStats.avgCurMhz(emptyList()))
    }

    @Test fun avgCurMhz_all_zero_returns_zero() {
        val cores = listOf(
            LiveStats.CpuCore(0, 0L, 300L, 2000L, "schedutil"),
            LiveStats.CpuCore(1, 0L, 300L, 2000L, "schedutil"),
        )
        assertEquals(0L, LiveStats.avgCurMhz(cores))
    }

    @Test fun avgCurMhz_excludes_zero_cores_from_average() {
        // Three cores: 1000, 0 (offline), 2000 → average over the two active = 1500.
        val cores = listOf(
            LiveStats.CpuCore(0, 1000L, 300L, 2000L, "schedutil"),
            LiveStats.CpuCore(1, 0L,    300L, 2000L, "schedutil"),
            LiveStats.CpuCore(2, 2000L, 300L, 2000L, "schedutil"),
        )
        assertEquals(1500L, LiveStats.avgCurMhz(cores))
    }

    @Test fun avgCurMhz_single_core() {
        val cores = listOf(LiveStats.CpuCore(0, 800L, 300L, 2000L, "schedutil"))
        assertEquals(800L, LiveStats.avgCurMhz(cores))
    }

    // ── storageUsedFraction ────────────────────────────────────────────────────

    @Test fun storageUsedFraction_empty_returns_zero() {
        assertEquals(0f, LiveStats.storageUsedFraction(emptyList()), 0.001f)
    }

    @Test fun storageUsedFraction_single_volume_half_used() {
        val vols = listOf(LiveStats.Volume("Internal", totalBytes = 100L, availBytes = 50L))
        assertEquals(0.5f, LiveStats.storageUsedFraction(vols), 0.001f)
    }

    @Test fun storageUsedFraction_combines_multiple_volumes() {
        val vols = listOf(
            LiveStats.Volume("Internal", totalBytes = 100L, availBytes = 25L), // 75 used
            LiveStats.Volume("External", totalBytes = 100L, availBytes = 75L), // 25 used
        )
        // (75 + 25) / (100 + 100) = 0.5
        assertEquals(0.5f, LiveStats.storageUsedFraction(vols), 0.001f)
    }

    @Test fun storageUsedFraction_zero_total_returns_zero_no_div_by_zero() {
        val vols = listOf(LiveStats.Volume("Empty", totalBytes = 0L, availBytes = 0L))
        assertEquals(0f, LiveStats.storageUsedFraction(vols), 0.001f)
    }

    @Test fun storageUsedFraction_clamped_to_one() {
        // Pathological availBytes > totalBytes — usedBytes is coerced ≥ 0 per Volume,
        // so used fraction stays in [0f, 1f].
        val vols = listOf(LiveStats.Volume("Bug", totalBytes = 100L, availBytes = 200L))
        val frac = LiveStats.storageUsedFraction(vols)
        assertTrue("expected $frac in [0,1]", frac in 0f..1f)
    }

    // ── Memory derived properties ──────────────────────────────────────────────

    @Test fun memory_used_bytes_basic() {
        val mem = LiveStats.Memory(totalBytes = 1000L, availBytes = 800L, thresholdBytes = 100L, lowMemory = false)
        assertEquals(200L, mem.usedBytes)
    }

    @Test fun memory_used_bytes_clamped_to_zero_when_avail_exceeds_total() {
        val mem = LiveStats.Memory(totalBytes = 1000L, availBytes = 1100L, thresholdBytes = 100L, lowMemory = false)
        assertEquals(0L, mem.usedBytes)
    }

    @Test fun memory_used_fraction_and_percent() {
        val mem = LiveStats.Memory(totalBytes = 1000L, availBytes = 250L, thresholdBytes = 100L, lowMemory = false)
        assertEquals(0.75f, mem.usedFraction, 0.001f)
        assertEquals(75, mem.usedPercent)
    }

    @Test fun memory_used_fraction_zero_total_returns_zero() {
        val mem = LiveStats.Memory(totalBytes = 0L, availBytes = 0L, thresholdBytes = 0L, lowMemory = false)
        assertEquals(0f, mem.usedFraction, 0.001f)
        assertEquals(0, mem.usedPercent)
    }

    @Test fun memory_swap_used_fraction_no_swap_returns_zero() {
        val mem = LiveStats.Memory(totalBytes = 1000L, availBytes = 500L, thresholdBytes = 100L, lowMemory = false)
        // swapTotalBytes defaults to 0 → fraction is 0 (no div by zero).
        assertEquals(0f, mem.swapUsedFraction, 0.001f)
    }

    @Test fun memory_swap_used_fraction_partial() {
        val mem = LiveStats.Memory(
            totalBytes = 1000L, availBytes = 500L, thresholdBytes = 100L, lowMemory = false,
            swapTotalBytes = 400L, swapUsedBytes = 100L,
        )
        assertEquals(0.25f, mem.swapUsedFraction, 0.001f)
    }

    // ── Volume derived properties ──────────────────────────────────────────────

    @Test fun volume_used_bytes_basic() {
        val vol = LiveStats.Volume("Internal", totalBytes = 1000L, availBytes = 600L)
        assertEquals(400L, vol.usedBytes)
    }

    @Test fun volume_used_bytes_clamped_to_zero() {
        val vol = LiveStats.Volume("Internal", totalBytes = 1000L, availBytes = 1500L)
        assertEquals(0L, vol.usedBytes)
    }

    @Test fun volume_used_fraction_basic() {
        val vol = LiveStats.Volume("Internal", totalBytes = 1000L, availBytes = 250L)
        assertEquals(0.75f, vol.usedFraction, 0.001f)
    }

    @Test fun volume_used_fraction_zero_total_returns_zero() {
        val vol = LiveStats.Volume("Empty", totalBytes = 0L, availBytes = 0L)
        assertEquals(0f, vol.usedFraction, 0.001f)
    }

    // ── Gpu derived properties ─────────────────────────────────────────────────

    @Test fun gpu_freq_fraction_half() {
        val gpu = LiveStats.Gpu(curMhz = 500L, minMhz = 200L, maxMhz = 1000L, governor = "msm-adreno-tz", usagePercent = 50)
        assertEquals(0.5f, gpu.freqFraction, 0.001f)
    }

    @Test fun gpu_freq_fraction_zero_when_max_unknown() {
        val gpu = LiveStats.Gpu(curMhz = 500L, minMhz = 0L, maxMhz = 0L, governor = null, usagePercent = -1)
        assertEquals(0f, gpu.freqFraction, 0.001f)
    }

    @Test fun gpu_freq_fraction_zero_when_cur_zero() {
        val gpu = LiveStats.Gpu(curMhz = 0L, minMhz = 200L, maxMhz = 1000L, governor = null, usagePercent = -1)
        assertEquals(0f, gpu.freqFraction, 0.001f)
    }

    @Test fun gpu_freq_fraction_clamped_to_one() {
        // cur somehow exceeds max — coerce to ≤ 1f.
        val gpu = LiveStats.Gpu(curMhz = 1500L, minMhz = 200L, maxMhz = 1000L, governor = null, usagePercent = -1)
        assertEquals(1f, gpu.freqFraction, 0.001f)
    }

    @Test fun gpu_available_true_when_max_positive() {
        val gpu = LiveStats.Gpu(curMhz = 0L, minMhz = 0L, maxMhz = 800L, governor = null, usagePercent = -1)
        assertTrue(gpu.available)
    }

    @Test fun gpu_available_false_when_max_zero() {
        val gpu = LiveStats.Gpu(curMhz = 0L, minMhz = 0L, maxMhz = 0L, governor = null, usagePercent = -1)
        assertFalse(gpu.available)
    }
}

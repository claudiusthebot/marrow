package rocks.talon.marrow.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ── cpuUsagePercent ────────────────────────────────────────────────────────────

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

    // ── networkRate ───────────────────────────────────────────────────────────

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

    // ── diskRate ────────────────────────────────────────────────────────────

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

    // ── formatSpeedBps ───────────────────────────────────────────────────────────

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

    // ── formatDiskBps ───────────────────────────────────────────────────────────

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

    // ── avgCurMhz ───────────────────────────────────────────────────────────

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

    // ── storageUsedFraction ──────────────────────────────────────────────────────────

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

    // ── Memory derived properties ────────────────────────────────────────────────────────

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

    // ── Volume derived properties ────────────────────────────────────────────────────────

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

    // ── Gpu derived properties ────────────────────────────────────────────────────────

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

    // ── batteryHealthPercent ────────────────────────────────────────────────────────

    @Test fun batteryHealthPercent_normal_wear_90_percent() {
        // 4500 µAh actual vs 5000 µAh design → 90% health.
        assertEquals(90, LiveStats.batteryHealthPercent(4500L, 5000L))
    }

    @Test fun batteryHealthPercent_perfect_battery_returns_100() {
        assertEquals(100, LiveStats.batteryHealthPercent(5000L, 5000L))
    }

    @Test fun batteryHealthPercent_over_100_clamped_to_100() {
        // charge_full can briefly read higher than design on some OEMs.
        assertEquals(100, LiveStats.batteryHealthPercent(5100L, 5000L))
    }

    @Test fun batteryHealthPercent_zero_chargeFull_returns_minus1() {
        assertEquals(-1, LiveStats.batteryHealthPercent(0L, 5000L))
    }

    @Test fun batteryHealthPercent_zero_chargeFullDesign_returns_minus1() {
        assertEquals(-1, LiveStats.batteryHealthPercent(4500L, 0L))
    }

    @Test fun batteryHealthPercent_negative_chargeFull_returns_minus1() {
        assertEquals(-1, LiveStats.batteryHealthPercent(-1L, 5000L))
    }

    @Test fun batteryHealthPercent_negative_chargeFullDesign_returns_minus1() {
        assertEquals(-1, LiveStats.batteryHealthPercent(4500L, -1L))
    }

    @Test fun batteryHealthPercent_both_zero_returns_minus1() {
        // Both absent (emulator path) — no div-by-zero, just sentinel.
        assertEquals(-1, LiveStats.batteryHealthPercent(0L, 0L))
    }

    // ── powerMw ────────────────────────────────────────────────────────────

    private fun makeBattery(
        voltageV: Float = 3.85f,
        currentMa: Int = -1000,
    ): LiveStats.Battery = LiveStats.Battery(
        percent = 80,
        charging = currentMa > 0,
        plugged = LiveStats.Battery.PlugType.UNPLUGGED,
        temperatureC = 30f,
        voltageV = voltageV,
        currentMa = currentMa,
        technology = "Li-ion",
        healthy = true,
        healthPercent = 95,
    )

    @Test fun powerMw_chargingPositive() {
        val b = makeBattery(voltageV = 4.1f, currentMa = 1800)
        assertEquals(7380, b.powerMw)                        // 4.1V × 1800mA ≈ 7380mW (rounded)
    }

    @Test fun powerMw_dischargingNegative() {
        val b = makeBattery(voltageV = 3.85f, currentMa = -1500)
        assertEquals(-5775, b.powerMw)                       // 3.85V × −1500mA ≈ −5775mW (rounded)
    }

    @Test fun powerMw_unavailableCurrentMa() {
        val b = makeBattery(voltageV = 3.8f, currentMa = Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, b.powerMw)
    }

    @Test fun powerMw_unavailableVoltage() {
        val b = makeBattery(voltageV = -1f, currentMa = 1000)
        assertEquals(Int.MIN_VALUE, b.powerMw)
    }

    @Test fun powerMw_zeroCurrent() {
        val b = makeBattery(voltageV = 3.9f, currentMa = 0)
        assertEquals(0, b.powerMw)
    }

    // ── formatScreenTimeout ────────────────────────────────────────────────────────

    @Test fun formatScreenTimeout_seconds() {
        assertEquals("30s", LiveStats.formatScreenTimeout(30_000))
    }

    @Test fun formatScreenTimeout_fifteen_seconds() {
        assertEquals("15s", LiveStats.formatScreenTimeout(15_000))
    }

    @Test fun formatScreenTimeout_minutes_exact() {
        // Exactly 2 minutes with no leftover seconds → "2m"
        assertEquals("2m", LiveStats.formatScreenTimeout(120_000))
    }

    @Test fun formatScreenTimeout_minutes_and_seconds() {
        // 90 000 ms = 1 min 30 s
        assertEquals("1m 30s", LiveStats.formatScreenTimeout(90_000))
    }

    @Test fun formatScreenTimeout_hours_zero_minutes() {
        // Exactly 1 hour → "1h 0m"
        assertEquals("1h 0m", LiveStats.formatScreenTimeout(3_600_000))
    }

    @Test fun formatScreenTimeout_hours_and_minutes() {
        // 5 400 000 ms = 1 hour 30 minutes
        assertEquals("1h 30m", LiveStats.formatScreenTimeout(5_400_000))
    }

    // ── deepSleepFractionPct ────────────────────────────────────────────────────────

    @Test fun deepSleepFractionPct_no_sleep() {
        // elapsed == uptime → device never slept → 0%
        assertEquals(0, LiveStats.deepSleepFractionPct(10_000L, 10_000L))
    }

    @Test fun deepSleepFractionPct_half_sleep() {
        // 5s asleep out of 10s uptime → 50%
        assertEquals(50, LiveStats.deepSleepFractionPct(10_000L, 5_000L))
    }

    @Test fun deepSleepFractionPct_mostly_sleep() {
        // 9s asleep out of 10s uptime → 90% (overnight on shelf)
        assertEquals(90, LiveStats.deepSleepFractionPct(10_000L, 1_000L))
    }

    @Test fun deepSleepFractionPct_zero_elapsed_returns_minus_one() {
        // Guard against division by zero / uninitialized clocks
        assertEquals(-1, LiveStats.deepSleepFractionPct(0L, 0L))
    }

    // ── thermalStatusLabel ────────────────────────────────────────────────────────

    @Test fun thermalStatusLabel_none() {
        assertEquals("None", LiveStats.thermalStatusLabel(0))
    }

    @Test fun thermalStatusLabel_light() {
        assertEquals("Light", LiveStats.thermalStatusLabel(1))
    }

    @Test fun thermalStatusLabel_moderate() {
        assertEquals("Moderate", LiveStats.thermalStatusLabel(2))
    }

    @Test fun thermalStatusLabel_severe() {
        assertEquals("Severe", LiveStats.thermalStatusLabel(3))
    }

    @Test fun thermalStatusLabel_critical() {
        assertEquals("Critical", LiveStats.thermalStatusLabel(4))
    }

    @Test fun thermalStatusLabel_emergency() {
        assertEquals("Emergency", LiveStats.thermalStatusLabel(5))
    }

    @Test fun thermalStatusLabel_shutdown() {
        assertEquals("Shutdown", LiveStats.thermalStatusLabel(6))
    }

    @Test fun thermalStatusLabel_unknown_value_returns_unknown() {
        // Any undocumented status code should be gracefully labelled
        assertEquals("Unknown", LiveStats.thermalStatusLabel(99))
    }

    // -- chargeCounterMah ----------------------------------------------------

    @Test fun chargeCounterMah_typical_4000mah() {
        // 4,000,000 µAh → 4,000 mAh
        assertEquals(4000, LiveStats.chargeCounterMah(4_000_000))
    }

    @Test fun chargeCounterMah_typical_3847mah() {
        // 3,847,412 µAh → 3,847 mAh (truncated, not rounded)
        assertEquals(3847, LiveStats.chargeCounterMah(3_847_412))
    }

    @Test fun chargeCounterMah_zero_returns_minus_one() {
        // BatteryManager returns 0 when sensor is absent
        assertEquals(-1, LiveStats.chargeCounterMah(0))
    }

    @Test fun chargeCounterMah_negative_returns_minus_one() {
        // Some OEMs or emulators return -1 for unavailable
        assertEquals(-1, LiveStats.chargeCounterMah(-1))
    }

    @Test fun chargeCounterMah_min_value_returns_minus_one() {
        // Int.MIN_VALUE is the sentinel for "not available" from getIntProperty
        assertEquals(-1, LiveStats.chargeCounterMah(Int.MIN_VALUE))
    }

    @Test fun chargeCounterMah_one_mah_boundary() {
        // 1,000 µAh → exactly 1 mAh
        assertEquals(1, LiveStats.chargeCounterMah(1_000))
    }

    @Test fun chargeCounterMah_below_one_mah_returns_zero() {
        // 999 µAh → 0 mAh (truncated) — note: still positive so not unknown
        assertEquals(0, LiveStats.chargeCounterMah(999))
    }

    // ── Memory.thresholdBytes — kill threshold MB display ──────────────────────────

    @Test fun memory_threshold_mb_typical() {
        // 256 MB threshold (common on phones with ~4–6 GB RAM)
        val mem = LiveStats.Memory(
            totalBytes = 6L * 1024 * 1024 * 1024,
            availBytes = 2L * 1024 * 1024 * 1024,
            thresholdBytes = 256L * 1024 * 1024,
            lowMemory = false,
        )
        val thresholdMb = mem.thresholdBytes / (1024L * 1024L)
        assertEquals(256L, thresholdMb)
    }

    @Test fun memory_threshold_mb_zero_when_unavailable() {
        // thresholdBytes = 0 → display guard must suppress the BigStat
        val mem = LiveStats.Memory(
            totalBytes = 4L * 1024 * 1024 * 1024,
            availBytes = 2L * 1024 * 1024 * 1024,
            thresholdBytes = 0L,
            lowMemory = false,
        )
        val thresholdMb = mem.thresholdBytes / (1024L * 1024L)
        assertEquals(0L, thresholdMb)
    }

    @Test fun memory_threshold_mb_rounds_down() {
        // 300.5 MB worth of bytes → should show 300 MB (integer division)
        val bytes = (300.5 * 1024 * 1024).toLong()
        val mem = LiveStats.Memory(
            totalBytes = 8L * 1024 * 1024 * 1024,
            availBytes = 4L * 1024 * 1024 * 1024,
            thresholdBytes = bytes,
            lowMemory = false,
        )
        val thresholdMb = mem.thresholdBytes / (1024L * 1024L)
        assertEquals(300L, thresholdMb)
    }

    // ── wifiChannelFromMhz ──────────────────────────────────────────────────────────

    @Test fun wifiChannelFromMhz_24ghz_channel1() {
        // 2412 MHz = 2.4 GHz channel 1 (first standard 2.4 GHz channel)
        assertEquals(1, LiveStats.wifiChannelFromMhz(2412))
    }

    @Test fun wifiChannelFromMhz_24ghz_channel6() {
        // 2437 MHz = 2.4 GHz channel 6 (most common default SSID channel)
        assertEquals(6, LiveStats.wifiChannelFromMhz(2437))
    }

    @Test fun wifiChannelFromMhz_24ghz_channel14_japan() {
        // 2484 MHz = channel 14, special-case Japan-only frequency
        assertEquals(14, LiveStats.wifiChannelFromMhz(2484))
    }

    @Test fun wifiChannelFromMhz_5ghz_channel36() {
        // 5180 MHz = 5 GHz channel 36 (first 5 GHz channel)
        assertEquals(36, LiveStats.wifiChannelFromMhz(5180))
    }

    @Test fun wifiChannelFromMhz_5ghz_channel149() {
        // 5745 MHz = 5 GHz channel 149 (common in North America)
        assertEquals(149, LiveStats.wifiChannelFromMhz(5745))
    }

    @Test fun wifiChannelFromMhz_6ghz_channel1() {
        // 5955 MHz = 6 GHz channel 1 (first Wi-Fi 6E channel)
        assertEquals(1, LiveStats.wifiChannelFromMhz(5955))
    }

    @Test fun wifiChannelFromMhz_out_of_band_returns_null() {
        // Frequency not in any known Wi-Fi band → null
        assertNull(LiveStats.wifiChannelFromMhz(0))
        assertNull(LiveStats.wifiChannelFromMhz(3000))
        assertNull(LiveStats.wifiChannelFromMhz(9999))
    }

    // ── audioOutputLabel ────────────────────────────────────────────────────────────

    @Test fun audioOutputLabel_bluetooth_a2dp() {
        // TYPE_BLUETOOTH_A2DP=13 → "Bluetooth"
        assertEquals("Bluetooth", LiveStats.audioOutputLabel(listOf(13)))
    }

    @Test fun audioOutputLabel_bluetooth_ble_headset() {
        // TYPE_BLE_HEADSET=26 → "Bluetooth"
        assertEquals("Bluetooth", LiveStats.audioOutputLabel(listOf(26)))
    }

    @Test fun audioOutputLabel_bluetooth_sco() {
        // TYPE_BLUETOOTH_SCO=7 → "Bluetooth" (used for calls)
        assertEquals("Bluetooth", LiveStats.audioOutputLabel(listOf(7)))
    }

    @Test fun audioOutputLabel_wired_headphones() {
        // TYPE_WIRED_HEADPHONES=8 → "Wired"
        assertEquals("Wired", LiveStats.audioOutputLabel(listOf(8)))
    }

    @Test fun audioOutputLabel_wired_headset() {
        // TYPE_WIRED_HEADSET=3 → "Wired"
        assertEquals("Wired", LiveStats.audioOutputLabel(listOf(3)))
    }

    @Test fun audioOutputLabel_speaker() {
        // TYPE_BUILTIN_SPEAKER=2 → "Speaker"
        assertEquals("Speaker", LiveStats.audioOutputLabel(listOf(2)))
    }

    @Test fun audioOutputLabel_earpiece() {
        // TYPE_BUILTIN_EARPIECE=1 → "Earpiece"
        assertEquals("Earpiece", LiveStats.audioOutputLabel(listOf(1)))
    }

    @Test fun audioOutputLabel_bluetooth_wins_over_wired() {
        // Bluetooth takes priority over wired when both present
        assertEquals("Bluetooth", LiveStats.audioOutputLabel(listOf(13, 8)))
    }

    @Test fun audioOutputLabel_empty_list_returns_null() {
        // No output devices → null
        assertNull(LiveStats.audioOutputLabel(emptyList()))
    }

    @Test fun audioOutputLabel_unknown_type_returns_null() {
        // Unknown/unrecognised device type → null (no match)
        assertNull(LiveStats.audioOutputLabel(listOf(99)))
    }

    // ── batteryHealthLabel ─────────────────────────────────────────────────────────

    @Test fun batteryHealthLabel_good() {
        // BatteryManager.BATTERY_HEALTH_GOOD = 2
        assertEquals("Good", LiveStats.batteryHealthLabel(2))
    }

    @Test fun batteryHealthLabel_overheat() {
        // BatteryManager.BATTERY_HEALTH_OVERHEAT = 3
        assertEquals("Overheat", LiveStats.batteryHealthLabel(3))
    }

    @Test fun batteryHealthLabel_dead() {
        // BatteryManager.BATTERY_HEALTH_DEAD = 4
        assertEquals("Dead", LiveStats.batteryHealthLabel(4))
    }

    @Test fun batteryHealthLabel_over_voltage() {
        // BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE = 5
        assertEquals("Over Voltage", LiveStats.batteryHealthLabel(5))
    }

    @Test fun batteryHealthLabel_unspecified_failure() {
        // BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE = 6
        assertEquals("Failure", LiveStats.batteryHealthLabel(6))
    }

    @Test fun batteryHealthLabel_cold() {
        // BatteryManager.BATTERY_HEALTH_COLD = 7
        assertEquals("Cold", LiveStats.batteryHealthLabel(7))
    }

    @Test fun batteryHealthLabel_unknown_returns_unknown_string() {
        // BatteryManager.BATTERY_HEALTH_UNKNOWN = 1
        assertEquals("Unknown", LiveStats.batteryHealthLabel(1))
    }

    @Test fun batteryHealthLabel_minus_one_returns_empty() {
        // -1 = intent unavailable / no data → empty string (visibility guard)
        assertEquals("", LiveStats.batteryHealthLabel(-1))
    }

    // ── wifiSignalQuality ─────────────────────────────────────────────────────────

    @Test fun wifiSignalQuality_at_minus50_is_excellent() {
        assertEquals("Excellent", LiveStats.wifiSignalQuality(-50))
    }

    @Test fun wifiSignalQuality_above_minus50_is_excellent() {
        assertEquals("Excellent", LiveStats.wifiSignalQuality(-30))
        assertEquals("Excellent", LiveStats.wifiSignalQuality(-1))
    }

    @Test fun wifiSignalQuality_just_below_minus50_is_good() {
        assertEquals("Good", LiveStats.wifiSignalQuality(-51))
        assertEquals("Good", LiveStats.wifiSignalQuality(-65))
    }

    @Test fun wifiSignalQuality_just_below_minus65_is_fair() {
        assertEquals("Fair", LiveStats.wifiSignalQuality(-66))
        assertEquals("Fair", LiveStats.wifiSignalQuality(-80))
    }

    @Test fun wifiSignalQuality_below_minus80_is_poor() {
        assertEquals("Poor", LiveStats.wifiSignalQuality(-81))
        assertEquals("Poor", LiveStats.wifiSignalQuality(-120))
    }


    // ── displayDensityBucket ──────────────────────────────────────────────────────

    @Test fun displayDensityBucket_120_is_ldpi() {
        assertEquals("ldpi", LiveStats.displayDensityBucket(120))
    }

    @Test fun displayDensityBucket_160_is_mdpi() {
        assertEquals("mdpi", LiveStats.displayDensityBucket(160))
    }

    @Test fun displayDensityBucket_240_is_hdpi() {
        assertEquals("hdpi", LiveStats.displayDensityBucket(240))
    }

    @Test fun displayDensityBucket_320_is_xhdpi() {
        assertEquals("xhdpi", LiveStats.displayDensityBucket(320))
    }

    @Test fun displayDensityBucket_480_is_xxhdpi() {
        assertEquals("xxhdpi", LiveStats.displayDensityBucket(480))
    }

    @Test fun displayDensityBucket_560_is_xxxhdpi() {
        // 560 dpi — common Pixel display; above 480 but below 640 → xxxhdpi bucket
        assertEquals("xxxhdpi", LiveStats.displayDensityBucket(560))
    }

    @Test fun displayDensityBucket_640_is_xxxhdpi() {
        assertEquals("xxxhdpi", LiveStats.displayDensityBucket(640))
    }

    @Test fun displayDensityBucket_above_640_returns_raw_dpi() {
        assertEquals("800dpi", LiveStats.displayDensityBucket(800))
    }



    // ── storageUsedSeverity ────────────────────────────────────────────────────────

    @Test fun storageUsedSeverity_0_is_normal() {
        assertEquals("normal", LiveStats.storageUsedSeverity(0))
    }

    @Test fun storageUsedSeverity_74_is_normal() {
        assertEquals("normal", LiveStats.storageUsedSeverity(74))
    }

    @Test fun storageUsedSeverity_75_is_warning() {
        assertEquals("warning", LiveStats.storageUsedSeverity(75))
    }

    @Test fun storageUsedSeverity_89_is_warning() {
        assertEquals("warning", LiveStats.storageUsedSeverity(89))
    }

    @Test fun storageUsedSeverity_90_is_critical() {
        assertEquals("critical", LiveStats.storageUsedSeverity(90))
    }

    @Test fun storageUsedSeverity_100_is_critical() {
        assertEquals("critical", LiveStats.storageUsedSeverity(100))
    }

    // ── lightLevelCategory ─────────────────────────────────────────────────────

    @Test fun lightLevelCategory_zero_is_dark() {
        assertEquals("Dark", LiveStats.lightLevelCategory(0f))
    }

    @Test fun lightLevelCategory_below_1_is_dark() {
        assertEquals("Dark", LiveStats.lightLevelCategory(0.5f))
    }

    @Test fun lightLevelCategory_at_1_is_dim() {
        assertEquals("Dim", LiveStats.lightLevelCategory(1f))
    }

    @Test fun lightLevelCategory_at_9_is_dim() {
        assertEquals("Dim", LiveStats.lightLevelCategory(9f))
    }

    @Test fun lightLevelCategory_at_10_is_indoor() {
        assertEquals("Indoor", LiveStats.lightLevelCategory(10f))
    }

    @Test fun lightLevelCategory_at_199_is_indoor() {
        assertEquals("Indoor", LiveStats.lightLevelCategory(199f))
    }

    @Test fun lightLevelCategory_at_200_is_bright() {
        assertEquals("Bright", LiveStats.lightLevelCategory(200f))
    }

    @Test fun lightLevelCategory_at_999_is_bright() {
        assertEquals("Bright", LiveStats.lightLevelCategory(999f))
    }

    @Test fun lightLevelCategory_at_1000_is_outdoor() {
        assertEquals("Outdoor", LiveStats.lightLevelCategory(1000f))
    }

    @Test fun lightLevelCategory_large_value_is_outdoor() {
        assertEquals("Outdoor", LiveStats.lightLevelCategory(50000f))
    }

    // ── parseLoadAvg ──────────────────────────────────────────────────────────

    @Test fun parseLoadAvg_typical_line_returns_correct_triple() {
        val result = LiveStats.parseLoadAvg("1.23 4.56 7.89 12/345 6789")
        assertNotNull(result)
        assertEquals(1.23f, result!!.first, 0.001f)
        assertEquals(4.56f, result.second, 0.001f)
        assertEquals(7.89f, result.third, 0.001f)
    }

    @Test fun parseLoadAvg_zeros_returns_zero_triple() {
        val result = LiveStats.parseLoadAvg("0.00 0.00 0.00 0/1 1")
        assertNotNull(result)
        assertEquals(0.00f, result!!.first, 0.001f)
        assertEquals(0.00f, result.second, 0.001f)
        assertEquals(0.00f, result.third, 0.001f)
    }

    @Test fun parseLoadAvg_high_load_round_trips() {
        val result = LiveStats.parseLoadAvg("16.00 12.50 8.75 64/512 99999")
        assertNotNull(result)
        assertEquals(16.00f, result!!.first, 0.01f)
        assertEquals(12.50f, result.second, 0.01f)
        assertEquals(8.75f, result.third, 0.01f)
    }

    @Test fun parseLoadAvg_extra_whitespace_is_tolerated() {
        val result = LiveStats.parseLoadAvg("  2.10  3.20  4.30  8/256 1234  ")
        assertNotNull(result)
        assertEquals(2.10f, result!!.first, 0.001f)
    }

    @Test fun parseLoadAvg_empty_string_returns_null() {
        assertNull(LiveStats.parseLoadAvg(""))
    }

    @Test fun parseLoadAvg_only_two_fields_returns_null() {
        assertNull(LiveStats.parseLoadAvg("1.23 4.56"))
    }

    @Test fun parseLoadAvg_non_numeric_field_returns_null() {
        assertNull(LiveStats.parseLoadAvg("abc 4.56 7.89 12/345 6789"))
    }

}
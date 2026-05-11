package rocks.talon.marrow.wear.tile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [StatsTilePalette] — the pure threshold + formatting
 * helpers behind the Marrow stats tile.
 *
 * The thresholds matter for at-a-glance correctness on the watch face:
 *  * load colour swap at 40 / 70 (memory, CPU, GPU)
 *  * battery colour swap at 20 / 50 (inverted — low % is the warning)
 *  * charging always reads GOOD, regardless of percent
 *  * negative percent is the "metric unavailable" sentinel — stays calm,
 *    not red
 *
 * Every band edge is asserted on both sides so a silent off-by-one in
 * the `when` ladder is caught by CI rather than by a confused glance at
 * the watch.
 */
class StatsTilePaletteTest {

    // --- colorForLoad: 3-band ladder, sentinel handling ---

    @Test
    fun `colorForLoad returns GOOD for unavailable sentinel`() {
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForLoad(-1))
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForLoad(-100))
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForLoad(Int.MIN_VALUE))
    }

    @Test
    fun `colorForLoad returns GOOD at zero`() {
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForLoad(0))
    }

    @Test
    fun `colorForLoad stays GOOD just below the low band edge`() {
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForLoad(39))
    }

    @Test
    fun `colorForLoad flips to OK at the low band edge`() {
        assertEquals(StatsTilePalette.COLOR_OK, StatsTilePalette.colorForLoad(40))
    }

    @Test
    fun `colorForLoad stays OK just below the high band edge`() {
        assertEquals(StatsTilePalette.COLOR_OK, StatsTilePalette.colorForLoad(69))
    }

    @Test
    fun `colorForLoad flips to WARN at the high band edge`() {
        assertEquals(StatsTilePalette.COLOR_WARN, StatsTilePalette.colorForLoad(70))
    }

    @Test
    fun `colorForLoad stays WARN at fully loaded`() {
        assertEquals(StatsTilePalette.COLOR_WARN, StatsTilePalette.colorForLoad(100))
    }

    @Test
    fun `colorForLoad stays WARN above 100 (defensive)`() {
        // Shouldn't happen — but if a future metric path overshoots, the
        // tint should still read as "high", not wrap.
        assertEquals(StatsTilePalette.COLOR_WARN, StatsTilePalette.colorForLoad(150))
        assertEquals(StatsTilePalette.COLOR_WARN, StatsTilePalette.colorForLoad(Int.MAX_VALUE))
    }

    // --- colorForBattery: inverted ladder + charging override ---

    @Test
    fun `colorForBattery returns GOOD for unavailable sentinel`() {
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForBattery(-1, charging = false))
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForBattery(-1, charging = true))
    }

    @Test
    fun `colorForBattery reads GOOD while charging regardless of percent`() {
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForBattery(0, charging = true))
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForBattery(5, charging = true))
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForBattery(19, charging = true))
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForBattery(50, charging = true))
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForBattery(100, charging = true))
    }

    @Test
    fun `colorForBattery reads WARN below 20 when discharging`() {
        assertEquals(StatsTilePalette.COLOR_WARN, StatsTilePalette.colorForBattery(0, charging = false))
        assertEquals(StatsTilePalette.COLOR_WARN, StatsTilePalette.colorForBattery(19, charging = false))
    }

    @Test
    fun `colorForBattery flips to OK at 20 when discharging`() {
        assertEquals(StatsTilePalette.COLOR_OK, StatsTilePalette.colorForBattery(20, charging = false))
    }

    @Test
    fun `colorForBattery stays OK just below 50 when discharging`() {
        assertEquals(StatsTilePalette.COLOR_OK, StatsTilePalette.colorForBattery(49, charging = false))
    }

    @Test
    fun `colorForBattery flips to GOOD at 50 when discharging`() {
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForBattery(50, charging = false))
    }

    @Test
    fun `colorForBattery reads GOOD at full charge when discharging`() {
        assertEquals(StatsTilePalette.COLOR_GOOD, StatsTilePalette.colorForBattery(100, charging = false))
    }

    // --- formatPercent: sentinel handling + plain rendering ---

    @Test
    fun `formatPercent renders em-dash for the negative sentinel`() {
        assertEquals("—", StatsTilePalette.formatPercent(-1))
        assertEquals("—", StatsTilePalette.formatPercent(-100))
        assertEquals("—", StatsTilePalette.formatPercent(Int.MIN_VALUE))
    }

    @Test
    fun `formatPercent renders zero as zero percent`() {
        assertEquals("0%", StatsTilePalette.formatPercent(0))
    }

    @Test
    fun `formatPercent renders integer percents verbatim`() {
        assertEquals("1%", StatsTilePalette.formatPercent(1))
        assertEquals("42%", StatsTilePalette.formatPercent(42))
        assertEquals("100%", StatsTilePalette.formatPercent(100))
    }

    @Test
    fun `formatPercent does not clamp at 100 (defensive)`() {
        // We trust callers to clamp upstream; the formatter is honest
        // about whatever it's handed so a clamp bug is visible on-screen.
        assertEquals("150%", StatsTilePalette.formatPercent(150))
    }
}

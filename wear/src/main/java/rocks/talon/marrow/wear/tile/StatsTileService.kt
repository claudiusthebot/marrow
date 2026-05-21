package rocks.talon.marrow.wear.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_MEDIUM
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spannable
import androidx.wear.protolayout.LayoutElementBuilders.SpanText
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.expression.ProtoLayoutExperimental
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import rocks.talon.marrow.shared.LiveStats

/**
 * Wear OS tile that surfaces Marrow's headline live stats as a 2×2 grid of
 * coloured pills plus a compact footer row beneath them.
 *
 * Layout (v0.11.0):
 * ```
 *        MARROW  42°↑
 *  ┌──────────┐ ┌──────────┐
 *  │  67%     │ │  42%     │
 *  │  BAT     │ │  MEM     │
 *  └──────────┘ └──────────┘
 *  ┌──────────┐ ┌──────────┐
 *  │  18%     │ │   2%     │
 *  │  CPU     │ │  GPU     │
 *  └──────────┘ └──────────┘
 *      ↓ 1.2M  ↑ 345K  · 42°
 * ```
 *
 * The footer temperature segment is rendered as a separate [SpanText] span
 * inside a [Spannable] so it can carry its own colour — amber at 50–69°C,
 * soft red at ≥ 70°C. Below 50°C it inherits the normal label tint.
 *
 * Cell tints follow the same comfort bands as the phone's section cards:
 * green-ish (low load) → tertiary (medium) → error red (high). Battery is
 * inverted — low percent reads as warning. Charging always reads green.
 *
 * The header shows a session-peak temperature (e.g. "42°↑") when at least
 * one valid thermal reading has been collected since the tile service started.
 * This lets the user spot "my phone hit 87°C at some point today" even if the
 * current temperature has since dropped.
 *
 * The footer row shows three live signals:
 *  - receive throughput (↓) and transmit throughput (↑) — sampled across the
 *    same 180 ms window as CPU utilisation so no extra blocking is added
 *  - CPU/SoC temperature (·) — read from /sys/class/thermal at the same time;
 *    shows "—" when sysfs is inaccessible (emulator / SELinux restriction)
 *
 * The tile re-evaluates every 30 s when visible. CPU% and network rate are
 * sampled by comparing two `/proc/stat` and `/proc/net/dev` snapshots across
 * the same ~180 ms window on the tile request thread — deliberately blocking
 * but well within the platform's 5 s tile-request budget.
 *
 * Hardware that doesn't expose a metric (GPU sysfs missing, `/proc/stat`
 * blocked under restrictive SELinux profiles) shows "—" and stays at the
 * calm primary tint instead of flashing red.
 *
 * Taps fall through to the platform default of launching the app's main
 * activity.
 */
class StatsTileService : TileService() {

    /**
     * Session-peak CPU/SoC temperature in °C, or -1f when no valid sample
     * has been collected yet.
     *
     * Updated on every [onTileRequest] call. Survives across tile refreshes
     * for as long as this service instance lives — typically from when the
     * tile first becomes visible until the watch face is unloaded or the app
     * is killed. Effectively "since last boot" in normal use.
     *
     * Not persisted to disk — intentionally ephemeral. The goal is a quick
     * "did my phone run hot today?" glance, not long-term thermal history.
     */
    private var peakTempC: Float = -1f

    /**
     * Container for all metrics that require either a delta window or a
     * sysfs read taken at request time.
     *
     * CPU and network are sampled across the same 180 ms blocking window.
     * Temperature is read once, at the end of the window (sysfs is cheap).
     */
    private data class DynamicStats(
        /** Total CPU utilisation 0–100, or -1 when /proc/stat is unreadable. */
        val cpuPct: Int,
        /** Network receive rate in bytes/sec. */
        val rxBps: Long,
        /** Network transmit rate in bytes/sec. */
        val txBps: Long,
        /** CPU/SoC thermal temperature in °C, or -1f when sysfs is unreadable. */
        val tempC: Float,
    )

    override fun onTileRequest(
        requestParams: TileRequest,
    ): ListenableFuture<Tile> {
        val battery = LiveStats.battery(applicationContext)
        val memory = LiveStats.memory(applicationContext)
        val dynamic = sampleDynamicStats()
        val gpu = LiveStats.gpu()
        val gpuPct = when {
            gpu.usagePercent in 0..100 -> gpu.usagePercent
            gpu.available -> (gpu.freqFraction * 100f).toInt()
            else -> -1
        }

        // Update session-peak temperature.
        if (dynamic.tempC >= 0f && (peakTempC < 0f || dynamic.tempC > peakTempC)) {
            peakTempC = dynamic.tempC
        }

        val root = buildLayout(
            batteryPct = battery.percent,
            batteryCharging = battery.charging,
            memoryPct = memory.usedPercent,
            cpuPct = dynamic.cpuPct,
            gpuPct = gpuPct,
            rxBps = dynamic.rxBps,
            txBps = dynamic.txBps,
            tempC = dynamic.tempC,
            peakTempC = peakTempC,
        )

        val tile = Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                Timeline.Builder()
                    .addTimelineEntry(
                        TimelineEntry.Builder()
                            .setLayout(Layout.Builder().setRoot(root).build())
                            .build(),
                    )
                    .build(),
            )
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: ResourcesRequest,
    ): ListenableFuture<Resources> =
        Futures.immediateFuture(
            Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    /**
     * Two-snapshot CPU utilisation AND network rate — both sampled across the
     * same 180 ms window so no additional blocking is introduced. CPU/SoC
     * temperature is read at the end of the window (cheap sysfs call).
     *
     * Returns cpuPct = -1 when /proc/stat is unreadable (emulator / SELinux).
     * Network rates default to 0 when /proc/net/dev is unreadable.
     * Temperature defaults to -1f when sysfs thermal zones are unreadable.
     */
    private fun sampleDynamicStats(): DynamicStats {
        val cpuFirst = LiveStats.cpuStatSnapshot()
        val netFirst = LiveStats.networkSnapshot()
        Thread.sleep(180L)
        val cpuSecond = LiveStats.cpuStatSnapshot()
        val netSecond = LiveStats.networkSnapshot()
        val cpuPct = if (cpuFirst != null && cpuSecond != null)
            LiveStats.cpuUsagePercent(cpuFirst, cpuSecond).toInt()
        else -1
        val (rxBps, txBps) = LiveStats.networkRate(netFirst, netSecond)
        val tempC = LiveStats.cpuTempC()
        return DynamicStats(cpuPct, rxBps, txBps, tempC)
    }

    private fun buildLayout(
        batteryPct: Int,
        batteryCharging: Boolean,
        memoryPct: Int,
        cpuPct: Int,
        gpuPct: Int,
        rxBps: Long,
        txBps: Long,
        tempC: Float,
        peakTempC: Float,
    ): LayoutElement {
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(OUTER_PADDING))
                            .setEnd(dp(OUTER_PADDING))
                            .setTop(dp(OUTER_PADDING))
                            .setBottom(dp(OUTER_PADDING))
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                Column.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(header(peakTempC))
                    .addContent(spacer(SPACING_HEADER))
                    .addContent(
                        statsRow(
                            leftLabel = "BAT",
                            leftValue = StatsTilePalette.formatPercent(batteryPct),
                            leftColor = StatsTilePalette.colorForBattery(batteryPct, batteryCharging),
                            rightLabel = "MEM",
                            rightValue = StatsTilePalette.formatPercent(memoryPct),
                            rightColor = StatsTilePalette.colorForLoad(memoryPct),
                        ),
                    )
                    .addContent(spacer(SPACING_ROW))
                    .addContent(
                        statsRow(
                            leftLabel = "CPU",
                            leftValue = StatsTilePalette.formatPercent(cpuPct),
                            leftColor = StatsTilePalette.colorForLoad(cpuPct),
                            rightLabel = "GPU",
                            rightValue = StatsTilePalette.formatPercent(gpuPct),
                            rightColor = StatsTilePalette.colorForLoad(gpuPct),
                        ),
                    )
                    .addContent(spacer(SPACING_NET))
                    .addContent(networkRow(rxBps, txBps, tempC))
                    .build(),
            )
            .build()
    }

    /**
     * Title row. Shows "MARROW" when no valid peak has been collected yet;
     * shows "MARROW  42°↑" once a thermal reading is available so the user
     * can glance at the session-high temperature without any extra tap.
     */
    private fun header(peakTempC: Float): LayoutElement {
        val peakSuffix = StatsTilePalette.formatPeakTemp(peakTempC)
        val label = if (peakSuffix.isNotEmpty()) "MARROW  $peakSuffix" else "MARROW"
        return Text.Builder()
            .setText(label)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(11f))
                    .setColor(argb(COLOR_LABEL_DIM))
                    .build(),
            )
            .build()
    }

    private fun statsRow(
        leftLabel: String,
        leftValue: String,
        leftColor: Int,
        rightLabel: String,
        rightValue: String,
        rightColor: Int,
    ): LayoutElement =
        Row.Builder()
            .setWidth(expand())
            .setHeight(dp(CELL_HEIGHT))
            .addContent(cell(leftLabel, leftValue, leftColor))
            .addContent(spacer(SPACING_CELL))
            .addContent(cell(rightLabel, rightValue, rightColor))
            .build()

    /**
     * Compact footer row spanning the full tile width with three live signals:
     *
     * ```
     *   ↓ 1.2M  ↑ 345K  · 42°
     * ```
     *
     * Rates formatted as "1.2M", "345K", "123B" (no "/s" — space is tight).
     * Temperature formatted as "42°" or "—" when sysfs is unreadable.
     * The middle-dot (·) visually separates throughput from thermal.
     *
     * The temperature segment is rendered as a separate [SpanText] inside a
     * [Spannable] so it can carry its own colour:
     *   < 50°C  → normal label tint (calm)
     *   50–69°C → amber  ([StatsTilePalette.COLOR_TEMP_WARM])
     *   ≥ 70°C  → soft red ([StatsTilePalette.COLOR_TEMP_HOT])
     */
    private fun networkRow(rxBps: Long, txBps: Long, tempC: Float): LayoutElement {
        val tempColor = StatsTilePalette.colorForTemp(tempC, COLOR_LABEL)
        val footerStyle = FontStyle.Builder().setSize(sp(10f)).setColor(argb(COLOR_LABEL)).build()
        val tempStyle   = FontStyle.Builder().setSize(sp(10f)).setColor(argb(tempColor)).build()
        return Box.Builder()
            .setWidth(expand())
            .setHeight(dp(NET_ROW_HEIGHT))
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Spannable.Builder()
                    .addSpan(
                        SpanText.Builder()
                            .setText(
                                "↓ ${StatsTilePalette.formatNetRate(rxBps)}" +
                                    "  ↑ ${StatsTilePalette.formatNetRate(txBps)}" +
                                    "  · ",
                            )
                            .setFontStyle(footerStyle)
                            .build(),
                    )
                    .addSpan(
                        SpanText.Builder()
                            .setText(StatsTilePalette.formatTemp(tempC))
                            .setFontStyle(tempStyle)
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    @androidx.annotation.OptIn(ProtoLayoutExperimental::class)
    private fun cell(label: String, value: String, bg: Int): LayoutElement =
        Box.Builder()
            .setWidth(dp(CELL_WIDTH))
            .setHeight(dp(CELL_HEIGHT))
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(bg))
                            .setCorner(Corner.Builder().setRadius(dp(CELL_RADIUS)).build())
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText(value)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(22f))
                                    .setWeight(FONT_WEIGHT_MEDIUM)
                                    .setColor(argb(COLOR_VALUE))
                                    .build(),
                            )
                            .build(),
                    )
                    .addContent(
                        Text.Builder()
                            .setText(label)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(10f))
                                    .setColor(argb(COLOR_LABEL))
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun spacer(value: Float): LayoutElement =
        Spacer.Builder()
            .setWidth(dp(value))
            .setHeight(dp(value))
            .build()

    private companion object {
        const val RESOURCES_VERSION = "marrow-stats-5"
        const val FRESHNESS_MS = 30_000L

        // Layout dimensions (dp).
        const val OUTER_PADDING = 12f
        const val CELL_WIDTH = 70f
        // Trimmed 56 → 52 to make room for the network/temp footer row.
        // Total height: 12 + 11 + 8 + 52 + 8 + 52 + 6 + 22 + 12 ≈ 183 dp.
        // Pixel Watch 3 round display ≈ 205 dp — fits with comfortable margin.
        const val CELL_HEIGHT = 52f
        const val CELL_RADIUS = 18f
        const val SPACING_HEADER = 8f
        const val SPACING_ROW = 8f
        const val SPACING_CELL = 8f
        const val SPACING_NET = 6f
        const val NET_ROW_HEIGHT = 22f

        // Text-tinting constants from the Talon orange family — mirrors
        // `MarrowWearTheme.MarrowWearColors`. Background fills come from
        // [StatsTilePalette].
        const val COLOR_VALUE: Int = 0xFFFFDBC9.toInt()      // onPrimaryContainer
        const val COLOR_LABEL: Int = 0xFFEDE0DA.toInt()      // onSurface
        const val COLOR_LABEL_DIM: Int = 0xFFA08C82.toInt()  // outline
    }
}

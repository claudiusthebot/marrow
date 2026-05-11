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
 * Wear OS tile that surfaces Marrow's four headline live stats — battery,
 * memory pressure, total CPU utilisation, GPU utilisation — as a 2×2 grid
 * of coloured pills.
 *
 * Cell tints follow the same comfort bands as the phone's section cards:
 * green-ish (low load) → tertiary (medium) → error red (high). Battery is
 * inverted — low percent reads as warning. Charging always reads green.
 *
 * The tile re-evaluates every 30 s when visible. CPU% is sampled by
 * comparing two `/proc/stat` snapshots ~180 ms apart on the request thread,
 * which is deliberately blocking — the tile platform schedules our work
 * off the UI thread already and a sub-200 ms tile request is well within
 * the 5 s budget.
 *
 * Hardware that doesn't expose a metric (GPU sysfs missing on the Pixel
 * Watch, `/proc/stat` blocked under restrictive SELinux profiles) shows
 * "—" and stays at the calm primary tint instead of flashing red.
 *
 * The tile is intentionally non-interactive — taps fall through to the
 * platform default of launching the app's main activity.
 */
class StatsTileService : TileService() {

    override fun onTileRequest(
        requestParams: TileRequest,
    ): ListenableFuture<Tile> {
        val battery = LiveStats.battery(applicationContext)
        val memory = LiveStats.memory(applicationContext)
        val cpuPct = sampleCpuPercent()
        val gpu = LiveStats.gpu()
        val gpuPct = when {
            gpu.usagePercent in 0..100 -> gpu.usagePercent
            gpu.available -> (gpu.freqFraction * 100f).toInt()
            else -> -1
        }

        val root = buildLayout(
            batteryPct = battery.percent,
            batteryCharging = battery.charging,
            memoryPct = memory.usedPercent,
            cpuPct = cpuPct,
            gpuPct = gpuPct,
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

    /** Two-snapshot total CPU utilisation. Returns -1 when /proc/stat is blocked. */
    private fun sampleCpuPercent(): Int {
        val first = LiveStats.cpuStatSnapshot() ?: return -1
        Thread.sleep(180L)
        val second = LiveStats.cpuStatSnapshot() ?: return -1
        return LiveStats.cpuUsagePercent(first, second).toInt()
    }

    private fun buildLayout(
        batteryPct: Int,
        batteryCharging: Boolean,
        memoryPct: Int,
        cpuPct: Int,
        gpuPct: Int,
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
                    .addContent(header())
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
                    .build(),
            )
            .build()
    }

    private fun header(): LayoutElement =
        Text.Builder()
            .setText("MARROW")
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(11f))
                    .setColor(argb(COLOR_LABEL_DIM))
                    .build(),
            )
            .build()

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
        const val RESOURCES_VERSION = "marrow-stats-1"
        const val FRESHNESS_MS = 30_000L

        // Layout dimensions (dp).
        const val OUTER_PADDING = 12f
        const val CELL_WIDTH = 70f
        const val CELL_HEIGHT = 56f
        const val CELL_RADIUS = 18f
        const val SPACING_HEADER = 8f
        const val SPACING_ROW = 8f
        const val SPACING_CELL = 8f

        // Text-tinting constants from the Talon orange family — mirrors
        // `MarrowWearTheme.MarrowWearColors`. Background fills come from
        // [StatsTilePalette].
        const val COLOR_VALUE: Int = 0xFFFFDBC9.toInt()      // onPrimaryContainer
        const val COLOR_LABEL: Int = 0xFFEDE0DA.toInt()      // onSurface
        const val COLOR_LABEL_DIM: Int = 0xFFA08C82.toInt()  // outline
    }
}

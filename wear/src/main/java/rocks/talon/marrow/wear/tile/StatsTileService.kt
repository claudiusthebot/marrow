package rocks.talon.marrow.wear.tile

import android.app.NotificationManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.protolayout.ActionBuilders
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
import androidx.wear.protolayout.ModifiersBuilders.Clickable
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
import rocks.talon.marrow.wear.R

/**
 * Wear OS tile that surfaces Marrow's headline live stats as a 2×2 grid of
 * coloured pills, a step-count row, and a compact footer row beneath them.
 *
 * Layout (v0.14.0):
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
 *          12,345 steps
 *      ↓ 1.2M  ↑ 345K  · 42°
 * ```
 *
 * Tapping anywhere on the tile opens [MainActivity] — the Marrow stats
 * screen. An explicit [ActionBuilders.LaunchAction] with a typed
 * [ActionBuilders.AndroidActivity] target replaces the old implicit
 * platform-default behaviour, guaranteeing the correct destination on
 * all Wear OS 4+ devices.
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
 * The step-count row shows cumulative steps since last reboot via
 * [Sensor.TYPE_STEP_COUNTER], registered in [onCreate] and unregistered in
 * [onDestroy]. Shows "—" until the first sensor event is delivered (typically
 * < 1 s after the service starts). Silently absent on hardware without a
 * step-counter pedometer. Zero additional permissions required.
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
     * Number of consecutive tile refreshes where the measured temperature
     * was >= [ThermalAlert.THRESHOLD_C].
     *
     * Reset to 0 whenever temperature drops below the threshold OR immediately
     * after a thermal alert fires (so the user needs another sustained-hot
     * streak before the next notification).
     */
    private var hotStreakCount: Int = 0

    /**
     * Epoch milliseconds when the most recent thermal notification was posted,
     * or 0L if none has been posted this session.
     *
     * Used to enforce [ThermalAlert.COOLDOWN_MS] between successive alerts so
     * the user isn't spammed when temperature is hovering around the threshold.
     */
    private var lastAlertMs: Long = 0L

    /**
     * Cumulative step count since last device reboot, or null when no sensor
     * event has been received yet. Updated by [stepListener] on the main thread;
     * read on the tile-request thread — annotated @Volatile for visibility.
     */
    @Volatile private var stepCount: Long? = null

    /** SensorManager retained so we can unregister in [onDestroy]. */
    private var sensorManager: SensorManager? = null

    /**
     * SensorEventListener for [Sensor.TYPE_STEP_COUNTER].
     * Registered in [onCreate], unregistered in [onDestroy].
     * Callbacks are delivered on the main thread (default Looper).
     */
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                stepCount = event.values[0].toLong()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

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

    override fun onCreate() {
        super.onCreate()
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        sm.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(stepListener)
        super.onDestroy()
    }

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

        // Thermal alert: fire a notification when the SoC stays hot for a
        // sustained period. Decision is made BEFORE updating hotStreakCount so
        // the pure helper receives the pre-update count.
        val nowMs = System.currentTimeMillis()
        if (ThermalAlert.shouldAlert(dynamic.tempC, hotStreakCount, lastAlertMs, nowMs)) {
            fireThermalNotification(dynamic.tempC)
            lastAlertMs = nowMs
            hotStreakCount = 0  // require a fresh streak before the next alert
        }
        if (dynamic.tempC >= ThermalAlert.THRESHOLD_C) {
            hotStreakCount++
        } else {
            hotStreakCount = 0
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
            stepCount = stepCount,
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
        stepCount: Long?,
    ): LayoutElement {
        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName("rocks.talon.marrow.wear.MainActivity")
                    .build(),
            )
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setClickable(
                        Clickable.Builder()
                            .setId("open_main")
                            .setOnClick(launchAction)
                            .build(),
                    )
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
                    .addContent(spacer(SPACING_STEPS))
                    .addContent(stepsRow(stepCount))
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
     * Compact centred row showing cumulative steps since last reboot.
     *
     * Formatted as "12,345 steps" for a valid reading, or "— steps" when no
     * sensor event has been received yet (hardware missing or first sample
     * not yet delivered).
     */
    private fun stepsRow(steps: Long?): LayoutElement =
        Box.Builder()
            .setWidth(expand())
            .setHeight(dp(STEP_ROW_HEIGHT))
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder()
                    .setText("${StatsTilePalette.formatSteps(steps)} steps")
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(sp(10f))
                            .setColor(argb(COLOR_LABEL))
                            .build(),
                    )
                    .build(),
            )
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

    /**
     * Posts a high-priority Wear OS notification warning the user that the
     * SoC has been running hot.
     *
     * Silently no-ops if [POST_NOTIFICATIONS] permission has not been granted
     * (which should never happen on Wear OS 4+ where the permission is
     * pre-granted for on-device apps, but is handled defensively).
     */
    private fun fireThermalNotification(tempC: Float) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (nm.importance == NotificationManager.IMPORTANCE_NONE) return

        val title = getString(R.string.notification_thermal_title)
        val text  = getString(R.string.notification_thermal_text, tempC)

        val notification = NotificationCompat.Builder(this, ThermalAlert.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

        // NotificationManagerCompat handles the POST_NOTIFICATIONS permission
        // check on API 33+ gracefully (throws SecurityException if missing,
        // which we swallow rather than crash the tile refresh).
        try {
            NotificationManagerCompat.from(this)
                .notify(ThermalAlert.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — skip silently.
        }
    }

    private companion object {
        const val RESOURCES_VERSION = "marrow-stats-7"
        const val FRESHNESS_MS = 30_000L

        // Layout dimensions (dp).
        const val OUTER_PADDING = 12f
        const val CELL_WIDTH = 70f
        // Trimmed 56 → 52 to make room for the network/temp footer row.
        // Added step-count row (v0.14.0): SPACING_NET trimmed 6 → 4 to offset.
        // Total height: 12 + 11 + 8 + 52 + 8 + 52 + 4 + 16 + 4 + 22 + 12 ≈ 201 dp.
        // Pixel Watch 3 round display ≈ 205 dp — fits with comfortable margin.
        const val CELL_HEIGHT = 52f
        const val CELL_RADIUS = 18f
        const val SPACING_HEADER = 8f
        const val SPACING_ROW = 8f
        const val SPACING_CELL = 8f
        const val SPACING_STEPS = 4f   // gap between second stats row and step row
        const val STEP_ROW_HEIGHT = 16f
        const val SPACING_NET = 4f     // trimmed from 6f to offset the new step row
        const val NET_ROW_HEIGHT = 22f

        // Text-tinting constants from the Talon orange family — mirrors
        // `MarrowWearTheme.MarrowWearColors`. Background fills come from
        // [StatsTilePalette].
        const val COLOR_VALUE: Int = 0xFFFFDBC9.toInt()      // onPrimaryContainer
        const val COLOR_LABEL: Int = 0xFFEDE0DA.toInt()      // onSurface
        const val COLOR_LABEL_DIM: Int = 0xFFA08C82.toInt()  // outline
    }
}

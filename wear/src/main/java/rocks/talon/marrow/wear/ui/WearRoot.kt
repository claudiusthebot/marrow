package rocks.talon.marrow.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import rocks.talon.marrow.shared.LiveStats
import rocks.talon.marrow.shared.Row as InfoRow
import rocks.talon.marrow.shared.Section
import rocks.talon.marrow.shared.Sections
import rocks.talon.marrow.wear.WearViewModel
import rocks.talon.marrow.wear.ui.theme.MarrowWearTheme

/**
 * Root composable for the watch app.
 *
 * v0.4.0 additions over v0.2.0:
 *   - LiveStatsRow — compact battery% + RAM% card on the home list screen,
 *     visible at a glance without entering a detail screen.
 *   - BatteryArcCard — 240° canvas arc gauge with level-coded colour
 *     (primary ≥60%, tertiary 20-59%, error <20%) replacing the plain flat
 *     Card on the Battery detail screen.
 *   - MemoryBarCard — horizontal segmented bar (used vs free) replacing the
 *     plain flat Card on the Memory detail screen.
 *
 * Detail-screen data rows are preserved below each gauge — the gauges are a
 * visual hero, not a replacement for the raw figures.
 */
@Composable
fun WearRoot() {
    MarrowWearTheme {
        val owner = checkNotNull(LocalViewModelStoreOwner.current) {
            "No ViewModelStoreOwner — WearRoot must be hosted by a ComponentActivity."
        }
        val vm: WearViewModel = viewModel(viewModelStoreOwner = owner)

        AppScaffold(timeText = { TimeText() }) {
            val nav = rememberSwipeDismissableNavController()
            SwipeDismissableNavHost(
                navController = nav,
                startDestination = ROUTE_LIST,
            ) {
                composable(ROUTE_LIST) { SectionListScreen(vm = vm, nav = nav) }
                composable("$ROUTE_DETAIL/{idx}") { entry ->
                    val idx = entry.arguments?.getString("idx")?.toIntOrNull() ?: 0
                    SectionDetailScreen(vm = vm, index = idx)
                }
            }
        }
    }
}

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail"

// -- List screen ------------------------------------------------------------

@Composable
private fun SectionListScreen(vm: WearViewModel, nav: NavHostController) {
    val snapshot by vm.snapshot.collectAsState()
    val pingState by vm.pingState.collectAsState()
    val phoneReachable by vm.phoneReachable.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val battery by vm.battery.collectAsState()
    val memory by vm.memory.collectAsState()

    val listState = rememberTransformingLazyColumnState()
    val transformSpec = rememberTransformationSpec()

    val sections = remember(snapshot) { snapshot?.sections.orEmpty() }
    val isLoading = snapshot == null

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            if (!phoneReachable) {
                EdgeButton(
                    onClick = { vm.refresh() },
                    buttonSize = EdgeButtonSize.Small,
                ) {
                    Text(if (refreshing) "Refreshing…" else "Refresh")
                }
            } else {
                EdgeButton(
                    onClick = { vm.ping() },
                    buttonSize = EdgeButtonSize.Small,
                ) {
                    Text(
                        when (pingState) {
                            WearViewModel.PingState.IDLE -> "Ping phone"
                            WearViewModel.PingState.SENDING -> "Sending…"
                            WearViewModel.PingState.SENT -> "Sent"
                            WearViewModel.PingState.FAILED -> "No phone"
                        },
                    )
                }
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformSpec),
                    transformation = SurfaceTransformation(transformSpec),
                ) { Text("Marrow") }
            }

            // At-a-glance stats: battery % + RAM % without entering a detail screen.
            // Uses snapshot data — not live-polled from home to avoid unnecessary
            // background work. Detail screens start live polling on entry.
            if (!isLoading && (battery != null || memory != null)) {
                item {
                    LiveStatsRow(
                        battery = battery,
                        memory = memory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec),
                    )
                }
            }

            if (!phoneReachable && !isLoading) {
                item {
                    DisconnectedNotice(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec),
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformSpec),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    }
                }
            } else {
                itemsIndexed(
                    items = sections,
                    key = { _, s -> s.id },
                ) { idx, section ->
                    SectionTitleCard(
                        section = section,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this@itemsIndexed, transformSpec),
                        transformation = SurfaceTransformation(transformSpec),
                        onClick = { nav.navigate("$ROUTE_DETAIL/$idx") },
                    )
                }
            }
        }
    }
}

/**
 * Compact two-column stats row for the home screen.
 * Left: battery icon + percentage (colour-coded) + "charging" if plugged.
 * Right: RAM icon + used percentage + "RAM" label.
 *
 * Shows the most useful numbers without requiring a detail-screen tap.
 */
@Composable
private fun LiveStatsRow(
    battery: LiveStats.Battery?,
    memory: LiveStats.Memory?,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val battPct = battery?.percent ?: -1
    val memPct = memory?.usedPercent ?: -1
    val battColor = when {
        battPct < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        battPct < 20 -> MaterialTheme.colorScheme.error
        battPct < 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = {},
        modifier = modifier,
        transformation = transformation,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = WearIcons.Battery,
                    contentDescription = "Battery",
                    tint = battColor,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (battPct >= 0) "$battPct%" else "--",
                    style = MaterialTheme.typography.labelMedium,
                    color = battColor,
                )
                if (battery?.charging == true) {
                    Text(
                        text = "charging",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = WearIcons.Memory,
                    contentDescription = "RAM",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (memPct >= 0) "$memPct%" else "--",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "RAM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DisconnectedNotice(modifier: Modifier, transformation: SurfaceTransformation) {
    Card(
        onClick = {},
        modifier = modifier,
        transformation = transformation,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = WearIcons.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Phone disconnected", maxLines = 1)
                Text(
                    "Showing cached data",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SectionTitleCard(
    section: Section,
    modifier: Modifier,
    transformation: SurfaceTransformation,
    onClick: () -> Unit,
) {
    TitleCard(
        onClick = onClick,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = WearIcons.forSection(section.id),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(section.title, maxLines = 1)
            }
        },
        subtitle = if (section.preview.isNotBlank()) {
            { Text(section.preview, maxLines = 1) }
        } else null,
        modifier = modifier,
        transformation = transformation,
    )
}

// -- Detail screen ----------------------------------------------------------

@Composable
private fun SectionDetailScreen(vm: WearViewModel, index: Int) {
    val snapshot by vm.snapshot.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val section = remember(snapshot, index) { snapshot?.sections?.getOrNull(index) }

    // Live 10s polling for Battery and Memory while on this detail screen.
    val needsLive = section?.id == Sections.BATTERY || section?.id == Sections.MEMORY
    DisposableEffect(needsLive) {
        if (needsLive) vm.startLive()
        onDispose { if (needsLive) vm.stopLive() }
    }

    val battery by vm.battery.collectAsState()
    val memory by vm.memory.collectAsState()

    val listState = rememberTransformingLazyColumnState()
    val transformSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = { vm.refresh() },
                buttonSize = EdgeButtonSize.Small,
            ) {
                Text(if (refreshing) "Refreshing…" else "Refresh")
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformSpec),
                    transformation = SurfaceTransformation(transformSpec),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (section != null) {
                            Icon(
                                imageVector = WearIcons.forSection(section.id),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(text = section?.title ?: "Detail", textAlign = TextAlign.Center)
                    }
                }
            }

            // Arc gauge for Battery — replaces the flat text card from v0.2.0.
            if (section?.id == Sections.BATTERY && battery != null) {
                item {
                    BatteryArcCard(
                        battery = battery!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec),
                    )
                }
            }

            // Segmented bar for Memory — replaces the flat text card from v0.2.0.
            if (section?.id == Sections.MEMORY && memory != null) {
                item {
                    MemoryBarCard(
                        memory = memory!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec),
                    )
                }
            }

            val rows = section?.rows.orEmpty()
            if (rows.isEmpty()) {
                item {
                    Card(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec),
                    ) { Text(if (section == null) "Not available" else "No data") }
                }
            } else {
                itemsIndexed(items = rows, key = { i, row -> "${row.label}-$i" }) { _, row ->
                    DetailRowCard(
                        row = row,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this@itemsIndexed, transformSpec),
                        transformation = SurfaceTransformation(transformSpec),
                    )
                }
            }
        }
    }
}

/**
 * 240° canvas arc gauge for the Battery detail screen.
 *
 * Geometry: sweep starts at 150° (7 o'clock), ends at 30° (5 o'clock)
 * clockwise — the same layout as a watch-face power ring. The dim track
 * covers the full 240°; the coloured fill sweeps proportionally to level.
 *
 * Colour coding uses MarrowWearColors theme roles:
 *   primary   (Talon orange)   — ≥60%  (normal)
 *   tertiary  (golden yellow)  — 20–59% (moderate)
 *   error     (warning red)    — <20%  (attention)
 *
 * Live-updates every 10 s while the detail screen is composed
 * (driven by WearViewModel.startLive / stopLive via DisposableEffect).
 */
@Composable
private fun BatteryArcCard(
    battery: LiveStats.Battery,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val pct = battery.percent.coerceIn(0, 100)
    val hasPct = battery.percent >= 0
    val arcColor = when {
        !hasPct || pct < 20 -> MaterialTheme.colorScheme.error
        pct < 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Card(
        onClick = {},
        modifier = modifier,
        transformation = transformation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 9.dp.toPx()
                    val inset = strokePx / 2f
                    val arcSize = Size(size.width - strokePx, size.height - strokePx)
                    val topLeft = Offset(inset, inset)
                    // Dim background track: full 240° sweep
                    drawArc(
                        color = trackColor,
                        startAngle = 150f,
                        sweepAngle = 240f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                    // Coloured fill proportional to battery level
                    if (hasPct && pct > 0) {
                        drawArc(
                            color = arcColor,
                            startAngle = 150f,
                            sweepAngle = 240f * (pct / 100f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round),
                        )
                    }
                }
                // Percentage + charging indicator centred inside the arc
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (hasPct) "$pct%" else "--",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = arcColor,
                    )
                    if (battery.charging) {
                        Text(
                            text = "⚡",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            // Temperature below the gauge when the sensor is readable
            if (battery.temperatureC >= 0f) {
                Text(
                    text = "${"%.1f".format(battery.temperatureC)}°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Horizontal segmented bar for the Memory detail screen.
 *
 * A full-width RoundedCornerShape(4dp) bar split between used (primary)
 * and free (primaryContainer @ 35% alpha) portions. GiB counts below.
 *
 * Live-updates every 10 s while the detail screen is composed.
 */
@Composable
private fun MemoryBarCard(
    memory: LiveStats.Memory,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    Card(
        onClick = {},
        modifier = modifier,
        transformation = transformation,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${memory.usedPercent}% used",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            // Segmented bar: used (primary) over free (primaryContainer dim)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(memory.usedFraction)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text(
                text = "${formatGib(memory.usedBytes)} / ${formatGib(memory.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailRowCard(
    row: InfoRow,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    TitleCard(
        onClick = {},
        title = { Text(row.label, maxLines = 1) },
        modifier = modifier,
        transformation = transformation,
    ) {
        Text(text = row.value)
    }
}

private fun formatGib(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024.0; i++ }
    return "%.1f %s".format(v, units[i])
}

package rocks.talon.marrow.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Structure mirrors Google's `ComposeStarter` Wear M3 sample, with v0.2.0
 * additions:
 *   AppScaffold (TimeText to every screen)
 *     └─ SwipeDismissableNavHost
 *         ├─ "list"   → TransformingLazyColumn of TitleCards (icon + title +
 *         │             preview); EdgeButton ping/refresh contextual
 *         └─ "detail/{idx}" → ScreenScaffold + TransformingLazyColumn; for
 *                             Battery / Memory the hero shows live values
 *                             refreshed every 10s while composed.
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

    val listState = rememberTransformingLazyColumnState()
    val transformSpec = rememberTransformationSpec()

    val sections = remember(snapshot) { snapshot?.sections.orEmpty() }
    val isLoading = snapshot == null

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            // Two contextual actions: when phone unreachable, primary CTA is
            // "Refresh"; otherwise it's "Ping phone" (debug helper).
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

    // Live polling for Battery and Memory while we're on this screen
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

            // Live overlay row for Battery / Memory
            if (section?.id == Sections.BATTERY && battery != null) {
                item {
                    Card(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec),
                    ) {
                        Column {
                            Text(
                                "${battery!!.percent}%",
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                if (battery!!.charging) "Charging" else "Live",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (section?.id == Sections.MEMORY && memory != null) {
                item {
                    Card(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec),
                    ) {
                        Column {
                            Text(
                                "${memory!!.usedPercent}% used",
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${formatGib(memory!!.usedBytes)} / ${formatGib(memory!!.totalBytes)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            val rows = section?.rows.orEmpty()
            if (rows.isEmpty()) {
                item {
                    Card(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformSpec),
                        transformation = SurfaceTransformation(transformSpec),
                    ) { Text(if (section == null) "Not available" else "No data") }
                }
            } else {
                itemsIndexed(items = rows, key = { i, row -> "${row.label}-$i" }) { _, row ->
                    DetailRowCard(
                        row = row,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this@itemsIndexed, transformSpec),
                        transformation = SurfaceTransformation(transformSpec),
                    )
                }
            }
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

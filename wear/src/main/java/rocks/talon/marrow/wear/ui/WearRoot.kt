package rocks.talon.marrow.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.wear.compose.material3.ListHeader
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
import rocks.talon.marrow.shared.Row as InfoRow
import rocks.talon.marrow.shared.Section
import rocks.talon.marrow.wear.WearViewModel
import rocks.talon.marrow.wear.ui.theme.MarrowWearTheme

/**
 * Root composable for the watch app.
 *
 * Structure mirrors Google's `ComposeStarter` Wear M3 sample:
 *   AppScaffold (provides TimeText to every screen)
 *     └─ SwipeDismissableNavHost
 *         ├─ "list"   → ScreenScaffold + TransformingLazyColumn of TitleCards
 *         └─ "detail" → ScreenScaffold + TransformingLazyColumn of value Cards
 *
 * The `WearViewModel` is created at activity scope (via the activity-level
 * `LocalViewModelStoreOwner`) so list and detail screens share one snapshot —
 * collection runs once, not per navigation, which was a major source of jank in
 * v0.1.0.
 */
@Composable
fun WearRoot() {
    MarrowWearTheme {
        // Hoist the VM at the activity store owner so every nav destination
        // observes the same StateFlow.
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
                composable(ROUTE_LIST) {
                    SectionListScreen(vm = vm, nav = nav)
                }
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

    val listState = rememberTransformingLazyColumnState()
    val transformSpec = rememberTransformationSpec()

    val sections = remember(snapshot) { snapshot?.sections.orEmpty() }
    val isLoading = snapshot == null

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
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
private fun SectionTitleCard(
    section: Section,
    modifier: Modifier,
    transformation: SurfaceTransformation,
    onClick: () -> Unit,
) {
    // Use the M3 TitleCard from wear-os-samples / ComposeStarter. The title
    // slot drives typography; subtitle (preview) is rendered as a single line.
    TitleCard(
        onClick = onClick,
        title = { Text(section.title, maxLines = 1) },
        subtitle = if (section.preview.isNotBlank()) {
            { Text(section.preview, maxLines = 1) }
        } else {
            null
        },
        modifier = modifier,
        transformation = transformation,
    )
}

// -- Detail screen ----------------------------------------------------------

@Composable
private fun SectionDetailScreen(vm: WearViewModel, index: Int) {
    val snapshot by vm.snapshot.collectAsState()
    val section = remember(snapshot, index) { snapshot?.sections?.getOrNull(index) }

    val listState = rememberTransformingLazyColumnState()
    val transformSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
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
                    Text(
                        text = section?.title ?: "Detail",
                        textAlign = TextAlign.Center,
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
                    ) {
                        Text(
                            text = if (section == null) "Not available" else "No data",
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = rows,
                    key = { i, row -> "${row.label}-$i" },
                ) { _, row ->
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

@Composable
private fun DetailRowCard(
    row: InfoRow,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    // Wear M3 has no `ListItem`; the canonical 2-line key/value row is a
    // `TitleCard` with `title = label` + a body line for the value, which
    // matches the typography Google uses for settings rows in their sample.
    TitleCard(
        onClick = {},
        title = { Text(row.label, maxLines = 1) },
        modifier = modifier,
        transformation = transformation,
    ) {
        Text(text = row.value)
    }
}

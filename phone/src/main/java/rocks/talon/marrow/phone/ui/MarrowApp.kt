package rocks.talon.marrow.phone.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rocks.talon.marrow.phone.MarrowViewModel
import rocks.talon.marrow.phone.sync.WatchInfoRepository
import rocks.talon.marrow.shared.DeviceInfoSnapshot
import rocks.talon.marrow.shared.Section

@Composable
fun MarrowApp(vm: MarrowViewModel) {
    MarrowTheme {
        var tab by remember { mutableStateOf(Tab.PHONE) }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = { MarrowBottomBar(tab) { tab = it } },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
                transitionSpec = { fadeIn(tween()) togetherWith fadeOut(tween()) },
                label = "tab",
            ) { active ->
                when (active) {
                    Tab.PHONE -> PhoneTab(vm)
                    Tab.WATCH -> WatchTab(vm)
                    Tab.ABOUT -> AboutTab()
                }
            }
        }
    }
}

private enum class Tab { PHONE, WATCH, ABOUT }

@Composable
private fun MarrowBottomBar(active: Tab, onTab: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        NavigationBarItem(
            selected = active == Tab.PHONE,
            onClick = { onTab(Tab.PHONE) },
            icon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) },
            label = { Text("Phone") },
        )
        NavigationBarItem(
            selected = active == Tab.WATCH,
            onClick = { onTab(Tab.WATCH) },
            icon = { Icon(Icons.Outlined.Watch, contentDescription = null) },
            label = { Text("Watch") },
        )
        NavigationBarItem(
            selected = active == Tab.ABOUT,
            onClick = { onTab(Tab.ABOUT) },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            label = { Text("About") },
        )
    }
}

// -- Phone tab ---------------------------------------------------------------

@Composable
private fun PhoneTab(vm: MarrowViewModel) {
    val snapshot by vm.phoneSnapshot.collectAsState()
    SnapshotScreen(
        title = "Marrow",
        subtitle = "to the marrow of your devices",
        snapshot = snapshot,
        onRefresh = vm::refreshPhone,
        emptyHint = "Collecting device info…",
    )
}

// -- Watch tab ---------------------------------------------------------------

@Composable
private fun WatchTab(vm: MarrowViewModel) {
    val live by vm.watchSnapshot.collectAsState()
    val cached by vm.cachedWatchSnapshot.collectAsState()
    val refreshing by vm.watchRefreshing.collectAsState()
    val connection by vm.watchConnection.collectAsState()
    val snapshot = live ?: cached

    if (snapshot == null && connection == WatchInfoRepository.WatchConnectionState.NOT_PAIRED) {
        WatchEmptyState(onRetry = vm::requestWatchInfo, refreshing = refreshing)
        return
    }

    SnapshotScreen(
        title = "Watch",
        subtitle = if (snapshot != null) "from your Wear OS device" else "asking your watch…",
        snapshot = snapshot,
        onRefresh = vm::requestWatchInfo,
        refreshing = refreshing,
        emptyHint = "Asking the watch — make sure Marrow is installed there too.",
    )
}

@Composable
private fun WatchEmptyState(onRetry: () -> Unit, refreshing: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Watch,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "No watch paired",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pair Marrow on your Wear OS device to see its info here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalIconButton(onClick = onRetry, enabled = !refreshing) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Retry")
        }
    }
}

// -- About tab ---------------------------------------------------------------

@Composable
private fun AboutTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(24.dp),
    ) {
        MarrowWordmark()
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("About", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Marrow shows the bones of your phone and watch — Android version, " +
                        "battery chemistry, sensor list, every CPU's current frequency. " +
                        "Phone and watch are the same app twice; the two halves talk over " +
                        "the Wear OS Data Layer so the Watch tab mirrors what the watch sees.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text("Inspired by SebaUbuntu/Athena.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("Material 3 Expressive · Wear OS 6 · Android 16/17.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("MIT licensed.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Open source", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "github.com/claudiusthebot/marrow",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// -- Shared snapshot screen --------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnapshotScreen(
    title: String,
    subtitle: String,
    snapshot: DeviceInfoSnapshot?,
    onRefresh: () -> Unit,
    refreshing: Boolean = false,
    emptyHint: String = "",
) {
    var openSection by remember { mutableStateOf<Section?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Header(title = title, subtitle = subtitle, refreshing = refreshing, onRefresh = onRefresh)
            }
            if (snapshot == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Text(
                            emptyHint,
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(snapshot.sections, key = { it.id }) { section ->
                    SectionCard(section) { openSection = section }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (openSection != null) {
        ModalBottomSheet(
            onDismissRequest = { scope.launch { sheetState.hide() }.invokeOnCompletion { openSection = null } },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DetailSheet(openSection!!)
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, refreshing: Boolean, onRefresh: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(section: Section, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(section.id),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.titleLarge)
                if (section.preview.isNotEmpty()) {
                    Text(
                        section.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSheet(section: Section) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFor(section.id), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Text(section.title, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))
        if (section.rows.isEmpty()) {
            Text(
                "Not available on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            section.rows.forEach { row ->
                KeyValueRow(row.label, row.value)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MarrowWordmark() {
    Column {
        Text(
            "Marrow",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.SansSerif,
        )
        Text(
            "to the marrow of your devices",
            style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun tween() = androidx.compose.animation.core.tween<Float>(durationMillis = 220)

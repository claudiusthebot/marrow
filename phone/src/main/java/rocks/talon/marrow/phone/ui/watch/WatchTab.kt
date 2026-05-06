package rocks.talon.marrow.phone.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rocks.talon.marrow.phone.MarrowViewModel
import rocks.talon.marrow.phone.sync.WatchInfoRepository
import rocks.talon.marrow.phone.ui.components.MarrowCapabilityCard
import rocks.talon.marrow.phone.ui.components.MarrowHero
import rocks.talon.marrow.phone.ui.components.ScreenSectionTitle
import rocks.talon.marrow.phone.ui.icons.MarrowIcons

/**
 * Watch tab — mirrors the phone Device tab's layout but feeds from the
 * connected watch's snapshot, kept in sync via the Wear OS Data Layer.
 *
 * Single LazyColumn host with consistent 16dp horizontal contentPadding —
 * same rule as DeviceTab so the two tabs feel identical.
 */
@Composable
fun WatchTab(
    vm: MarrowViewModel,
    onSection: (String) -> Unit,
) {
    val live by vm.watchSnapshot.collectAsState()
    val cached by vm.cachedWatchSnapshot.collectAsState()
    val refreshing by vm.watchRefreshing.collectAsState()
    val connection by vm.watchConnection.collectAsState()
    val snapshot = live ?: cached

    if (snapshot == null && connection == WatchInfoRepository.WatchConnectionState.NOT_PAIRED) {
        WatchEmptyState(refreshing = refreshing, onRetry = vm::requestWatchInfo)
        return
    }
    if (snapshot == null) {
        WatchLoading(refreshing = refreshing, onRetry = vm::requestWatchInfo)
        return
    }

    val deviceSection = snapshot.sections.firstOrNull { it.id == "device" }
    val systemSection = snapshot.sections.firstOrNull { it.id == "system" }
    val title = deviceSection?.rows?.firstOrNull { it.label == "Model" }?.value ?: "Wear OS"
    val manufacturer = deviceSection?.rows?.firstOrNull { it.label == "Manufacturer" }?.value ?: "—"
    val soc = deviceSection?.rows?.firstOrNull { it.label.startsWith("SoC", ignoreCase = true) || it.label.equals("Hardware", ignoreCase = true) }?.value ?: "—"
    val androidVer = systemSection?.rows?.firstOrNull { it.label == "Android version" }?.value?.let { "Android $it" } ?: "Wear OS"
    val sdkVer = systemSection?.rows?.firstOrNull { it.label == "SDK" }?.value?.let { "API $it" } ?: "—"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("hero") {
            MarrowHero(
                title = title,
                manufacturer = manufacturer,
                soc = soc,
                android = androidVer,
                sdk = sdkVer,
                icon = MarrowIcons.Watch,
            )
        }
        item("title") {
            ScreenSectionTitle(
                title = "From your watch",
                subtitle = if (refreshing) "Refreshing…" else "Tap any section for the full read",
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                trailing = {
                    FilledTonalButton(onClick = vm::requestWatchInfo) { Text("Refresh") }
                },
            )
        }
        items(snapshot.sections, key = { it.id }) { section ->
            MarrowCapabilityCard(
                title = section.title,
                icon = MarrowIcons.forSection(section.id),
                onClick = { onSection("watch:${section.id}") },
                verticalSpacing = 8.dp,
            ) {
                if (section.preview.isNotBlank()) {
                    Text(
                        text = section.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchEmptyState(refreshing: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(128.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MarrowIcons.Watch,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "No watch reachable",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pair Marrow on your Wear OS device to see its info here. If it's already installed, make sure the watch is connected to your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onRetry, enabled = !refreshing) {
            Text(if (refreshing) "Looking…" else "Try again")
        }
    }
}

@Composable
private fun WatchLoading(refreshing: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = MarrowIcons.Watch,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text("Asking the watch…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Marrow on your watch should respond in a moment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onRetry, enabled = !refreshing) {
            Text(if (refreshing) "Looking…" else "Try again")
        }
    }
}

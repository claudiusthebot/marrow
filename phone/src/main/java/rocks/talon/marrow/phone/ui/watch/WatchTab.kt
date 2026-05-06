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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rocks.talon.marrow.phone.MarrowViewModel
import rocks.talon.marrow.phone.sync.WatchInfoRepository
import rocks.talon.marrow.phone.ui.components.DeviceHero
import rocks.talon.marrow.phone.ui.components.SectionGridCard
import rocks.talon.marrow.phone.ui.components.SectionTitle
import rocks.talon.marrow.phone.ui.icons.MarrowIcons

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

    // Title from the watch's first section, fallback to "Wear OS device".
    val deviceSection = snapshot.sections.firstOrNull { it.id == "device" }
    val title = deviceSection?.rows?.firstOrNull { it.label == "Model" }?.value ?: "Wear OS"
    val subtitle = run {
        val mfg = deviceSection?.rows?.firstOrNull { it.label == "Manufacturer" }?.value ?: ""
        val systemSection = snapshot.sections.firstOrNull { it.id == "system" }
        val release = systemSection?.rows?.firstOrNull { it.label == "Android version" }?.value
        val sdk = systemSection?.rows?.firstOrNull { it.label == "SDK" }?.value
        buildList {
            if (mfg.isNotBlank()) add(mfg)
            if (release != null) add("Wear OS · Android $release")
            if (sdk != null) add("API $sdk")
        }.joinToString(" · ")
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                DeviceHero(title = title, subtitle = subtitle)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(
                title = "From your watch",
                subtitle = if (refreshing) "Refreshing…" else "Tap any section for the full read",
                trailing = {
                    FilledTonalButton(onClick = vm::requestWatchInfo) { Text("Refresh") }
                },
            )
        }
        items(snapshot.sections, key = { it.id }) { section ->
            SectionGridCard(
                title = section.title,
                icon = MarrowIcons.forSection(section.id),
                secondary = section.preview.takeIf { it.isNotBlank() },
                modifier = Modifier.padding(horizontal = 6.dp),
                onClick = { onSection(section.id) },
            )
        }
    }
}

@Composable
private fun WatchEmptyState(refreshing: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(128.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                MarrowIcons.Watch,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "No watch reachable",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pair Marrow on your Wear OS device to see its info here.\n\nIf you've already installed it, make sure the watch is connected to your phone.",
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
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            MarrowIcons.Watch,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text("Asking the watch…", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Marrow on your watch should respond in a moment.",
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

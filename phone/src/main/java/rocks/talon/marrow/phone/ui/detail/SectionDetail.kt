package rocks.talon.marrow.phone.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import rocks.talon.marrow.phone.MarrowViewModel
import rocks.talon.marrow.phone.ui.components.KeyValueRow
import rocks.talon.marrow.phone.ui.components.MarrowCard
import rocks.talon.marrow.phone.ui.device.copyToClipboard
import rocks.talon.marrow.phone.ui.device.shareText
import rocks.talon.marrow.shared.Section
import rocks.talon.marrow.shared.Sections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionDetail(
    vm: MarrowViewModel,
    sectionId: String,
    source: String,
    onBack: () -> Unit,
) {
    val phone by vm.phoneSnapshot.collectAsState()
    val watch by vm.watchSnapshot.collectAsState()
    val watchCached by vm.cachedWatchSnapshot.collectAsState()
    val phoneRefreshing by vm.phoneRefreshing.collectAsState()
    val watchRefreshing by vm.watchRefreshing.collectAsState()
    val refreshing = if (source == "watch") watchRefreshing else phoneRefreshing

    val snapshot = if (source == "watch") (watch ?: watchCached) else phone
    val section = remember(snapshot, sectionId) { snapshot?.sections?.firstOrNull { it.id == sectionId } }

    val ctx = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(section?.title ?: "Section")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val text = sectionAsText(section)
                        shareText(ctx, "Marrow — ${section?.title ?: ""}", text)
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share section")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                if (source == "watch") vm.requestWatchInfo() else vm.refreshPhone()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (section == null) {
                    item { NoData() }
                    return@LazyColumn
                }

                // Per-section hero
                item {
                    SectionHero(vm = vm, section = section, source = source)
                }

                // Section rows as a card list
                item {
                    MarrowCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            section.rows.forEachIndexed { i, row ->
                                KeyValueRow(
                                    label = row.label,
                                    value = row.value,
                                    monospaceValue = isMonospace(row.label),
                                    onClick = {
                                        copyToClipboard(ctx, row.label, row.value)
                                        android.widget.Toast.makeText(
                                            ctx,
                                            "Copied ${row.label}",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                                if (i < section.rows.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Tap-to-copy hint
                item {
                    Text(
                        text = "Tap any row to copy its value.",
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHero(vm: MarrowViewModel, section: Section, source: String) {
    when (section.id) {
        Sections.BATTERY -> BatteryHero(vm = vm, section = section, isWatch = source == "watch")
        Sections.CPU -> CpuHero(vm = vm, section = section, isWatch = source == "watch")
        Sections.MEMORY -> MemoryHero(vm = vm, section = section, isWatch = source == "watch")
        Sections.STORAGE -> StorageHero(vm = vm, section = section, isWatch = source == "watch")
        Sections.DISPLAY -> DisplayHero(section = section)
        Sections.NETWORK -> NetworkHero(vm = vm, section = section)
        Sections.SENSORS -> SensorsHero(section = section, isPhone = source != "watch")
        Sections.CAMERAS -> CamerasHero(section = section)
        Sections.BUILD_FLAGS -> BuildFlagsHero(section = section)
        Sections.DEVICE -> DeviceHero(section = section)
        Sections.SYSTEM -> SystemHero(section = section)
        Sections.SOFTWARE -> SoftwareHero(section = section)
        Sections.GPU -> GpuHero(vm = vm, section = section)
        else -> GenericHero(section = section)
    }
}

@Composable
private fun NoData() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            "Section not available on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun isMonospace(label: String): Boolean {
    val l = label.lowercase()
    return "fingerprint" in l || "ips" in l || l == "id" ||
        "kernel" in l || "patch" in l || "incremental" in l ||
        "build host" in l || "build user" in l || "java vm" in l
}

private fun sectionAsText(section: Section?): String {
    if (section == null) return "(no data)"
    val sb = StringBuilder()
    sb.append("# ").append(section.title).append("\n\n")
    for (row in section.rows) {
        sb.append("**").append(row.label).append("**: ").append(row.value).append("\n")
    }
    return sb.toString()
}

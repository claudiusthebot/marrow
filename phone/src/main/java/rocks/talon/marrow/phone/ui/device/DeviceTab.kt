package rocks.talon.marrow.phone.ui.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rocks.talon.marrow.phone.MarrowViewModel
import rocks.talon.marrow.phone.ui.components.CollapsibleTopBar
import rocks.talon.marrow.phone.ui.components.LiveStatsStrip
import rocks.talon.marrow.phone.ui.components.MarrowCapabilityCard
import rocks.talon.marrow.phone.ui.components.MarrowHero
import rocks.talon.marrow.phone.ui.components.ScreenSectionTitle
import rocks.talon.marrow.phone.ui.components.rememberCollapsibleTopBarState
import rocks.talon.marrow.phone.ui.icons.MarrowIcons
import rocks.talon.marrow.shared.DeviceInfoSnapshot
import rocks.talon.marrow.shared.LiveStats
import rocks.talon.marrow.shared.Section
import rocks.talon.marrow.shared.Sections

/**
 * Device tab.
 *
 * Layout:
 *   - A [CollapsibleTopBar] overlay at TopStart of a root [Box]. It starts
 *     expanded (188 dp), collapses to (56 dp + statusBarHeight) as the user
 *     scrolls down, and spring-snaps to either extreme on fling.
 *   - A [LazyColumn] behind the bar. ALL horizontal inset comes from
 *     [contentPadding] — no per-item `Modifier.padding(horizontal = …)`.
 *     The top contentPadding tracks the bar height so content is never
 *     hidden under the bar.
 */
@Composable
fun DeviceTab(
    vm: MarrowViewModel,
    onSection: (String) -> Unit,
) {
    val snapshot by vm.phoneSnapshot.collectAsState()
    val battery by vm.battery.collectAsState()
    val memory by vm.memory.collectAsState()
    val cpuCores by vm.cpuCores.collectAsState()
    val volumes by vm.volumes.collectAsState()
    val uptimeSeconds by vm.systemUptimeSeconds.collectAsState()
    val gpu by vm.gpu.collectAsState()

    val cpuAvg = remember(cpuCores) { LiveStats.avgCurMhz(cpuCores) }
    val storageFrac = remember(volumes) { LiveStats.storageUsedFraction(volumes) }
    val uptimeFormatted = remember(uptimeSeconds) { LiveStats.formatUptime(uptimeSeconds) }

    val title = remember { Build.MODEL ?: "Android device" }
    val manufacturer = remember {
        Build.MANUFACTURER?.replaceFirstChar { it.titlecase() } ?: "—"
    }
    val soc = remember {
        (Build.SOC_MODEL?.takeIf { it.isNotBlank() && it != "unknown" } ?: Build.HARDWARE)
            ?.takeIf { it.isNotBlank() } ?: "—"
    }
    val androidVer = remember { "Android ${Build.VERSION.RELEASE ?: "?"}" }
    val sdkVer = remember { "API ${Build.VERSION.SDK_INT}" }

    val sections = snapshot?.sections.orEmpty()

    // Collapsible top bar state — starts expanded, collapses on scroll
    val topBarState = rememberCollapsibleTopBarState()
    val topPaddingDp = with(LocalDensity.current) { topBarState.heightPx.value.toDp() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topBarState.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topPaddingDp + 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("hero") {
                MarrowHero(
                    title = title,
                    manufacturer = manufacturer,
                    soc = soc,
                    android = androidVer,
                    sdk = sdkVer,
                    uptime = uptimeFormatted,
                )
            }
            item("stats") {
                LiveStatsStrip(
                    battery = battery,
                    memory = memory,
                    cpuAvgMhz = cpuAvg,
                    storageUsedFraction = storageFrac,
                    onChipClick = onSection,
                )
            }
            item("section-title") {
                ScreenSectionTitle(
                    title = "Sections",
                    subtitle = "Tap any section for the full read",
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                )
            }

            if (sections.isEmpty()) {
                item("collecting") {
                    Text(
                        text = "Collecting device info…",
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(sections, key = { it.id }) { section ->
                    SectionListCard(
                        section = section,
                        livePreview = livePreviewFor(section, battery, memory, cpuAvg, volumes, gpu),
                        onClick = { onSection(section.id) },
                    )
                }
            }

            item("actions-title") {
                ScreenSectionTitle(
                    title = "Quick actions",
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
                )
            }
            item("actions") {
                QuickActions(snapshot = snapshot, onRefresh = vm::refreshPhone)
            }

            item("footer") {
                Footer()
            }
        }

        // Collapsible bar sits on top of the list, aligned to TopStart
        CollapsibleTopBar(
            state = topBarState,
            title = title,
            subtitle = "$androidVer · $soc",
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun SectionListCard(
    section: Section,
    livePreview: String?,
    onClick: () -> Unit,
) {
    MarrowCapabilityCard(
        title = section.title,
        icon = MarrowIcons.forSection(section.id),
        onClick = onClick,
        verticalSpacing = 8.dp,
        trailing = {
            if (!livePreview.isNullOrBlank()) {
                Text(
                    text = livePreview,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(
    snapshot: DeviceInfoSnapshot?,
    onRefresh: () -> Unit,
) {
    val ctx = LocalContext.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(onClick = {
            val text = renderReportMarkdown(snapshot)
            copyToClipboard(ctx, "Marrow device report", text)
            Toast.makeText(ctx, "Copied report", Toast.LENGTH_SHORT).show()
        }) { Text("Copy report") }

        FilledTonalButton(onClick = {
            val text = renderReportMarkdown(snapshot)
            shareText(ctx, "Marrow device report", text)
        }) { Text("Share") }

        FilledTonalButton(onClick = onRefresh) { Text("Refresh") }
    }
}

@Composable
private fun Footer() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Marrow v1.0.0",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "github.com/claudiusthebot/marrow",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "built with claws",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun livePreviewFor(
    section: Section,
    battery: LiveStats.Battery?,
    memory: LiveStats.Memory?,
    cpuAvg: Long,
    volumes: List<LiveStats.Volume>,
    gpu: LiveStats.Gpu?,
): String? = when (section.id) {
    Sections.BATTERY -> battery?.percent?.takeIf { it >= 0 }?.let { "$it%" }
    Sections.MEMORY -> memory?.let {
        "${formatBytesShort(it.usedBytes)} / ${formatBytesShort(it.totalBytes)}"
    }
    Sections.CPU -> if (cpuAvg > 0) "$cpuAvg MHz" else null
    Sections.STORAGE -> {
        val total = volumes.sumOf { it.totalBytes }
        val avail = volumes.sumOf { it.availBytes }
        if (total > 0) "${formatBytesShort(total - avail)} used" else null
    }
    Sections.GPU -> gpu?.takeIf { it.available }?.let { g ->
        buildString {
            if (g.curMhz > 0) append("${g.curMhz} MHz")
            if (g.usagePercent >= 0) append(" · ${g.usagePercent}%")
        }.ifBlank { null }
    }
    else -> null
}

private fun formatBytesShort(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024.0; i++
    }
    return "%.1f %s".format(v, units[i])
}

internal fun renderReportMarkdown(snapshot: DeviceInfoSnapshot?): String {
    if (snapshot == null) return "(no snapshot)"
    val sb = StringBuilder()
    sb.append("# Marrow device report\n\n")
    sb.append("Captured: ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(snapshot.capturedAtEpochMs)))
        .append("\n\n")
    for (s in snapshot.sections) {
        sb.append("## ").append(s.title).append("\n\n")
        for (row in s.rows) {
            sb.append("- **").append(row.label).append("**: ").append(row.value).append("\n")
        }
        sb.append("\n")
    }
    return sb.toString()
}

internal fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, "Share device report").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(chooser)
}

package rocks.talon.marrow.phone.ui.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rocks.talon.marrow.phone.MarrowViewModel
import rocks.talon.marrow.phone.ui.components.DeviceHero
import rocks.talon.marrow.phone.ui.components.LiveStatsStrip
import rocks.talon.marrow.phone.ui.components.SectionGridCard
import rocks.talon.marrow.phone.ui.components.SectionTitle
import rocks.talon.marrow.phone.ui.icons.MarrowIcons
import rocks.talon.marrow.shared.DeviceInfoSnapshot
import rocks.talon.marrow.shared.LiveStats
import rocks.talon.marrow.shared.Section
import rocks.talon.marrow.shared.Sections

/**
 * Device tab.
 *
 * Layout (LazyVerticalGrid as the scrollable host so the cards naturally form
 * a 2-column grid; full-width items use `span = maxLineSpan`):
 *
 *  1. Status bar spacer
 *  2. Hero header (full span)
 *  3. Live stats strip (full span)
 *  4. "Sections" title (full span)
 *  5. ~12 section cards (one per column)
 *  6. "Quick actions" title + FlowRow of buttons (full span)
 *  7. Footer (full span)
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val cols = when {
        configuration.screenWidthDp >= 840 -> 3
        configuration.screenWidthDp >= 600 -> 2
        else -> 2
    }

    val cpuAvg = remember(cpuCores) { LiveStats.avgCurMhz(cpuCores) }
    val storageFrac = remember(volumes) { LiveStats.storageUsedFraction(volumes) }

    val title = remember { "${Build.MODEL}" }
    val subtitle = remember {
        buildString {
            append(Build.MANUFACTURER.replaceFirstChar { it.titlecase() })
            append(" · ")
            val soc = (Build.SOC_MODEL?.takeIf { it.isNotBlank() && it != "unknown" } ?: Build.HARDWARE).ifBlank { "Android" }
            append(soc)
            append(" · Android ${Build.VERSION.RELEASE}")
            append(" · API ${Build.VERSION.SDK_INT}")
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
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
            LiveStatsStrip(
                battery = battery,
                memory = memory,
                cpuAvgMhz = cpuAvg,
                storageUsedFraction = storageFrac,
                onChipClick = onSection,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(title = "Sections", subtitle = "Tap any section for the full read")
        }

        val sections = snapshot?.sections.orEmpty()
        items(sections, key = { it.id }) { section ->
            SectionGridCard(
                title = section.title,
                icon = MarrowIcons.forSection(section.id),
                livePreview = livePreviewFor(section, battery, memory, cpuAvg, volumes),
                secondary = section.preview.takeIf { it.isNotBlank() },
                modifier = Modifier.padding(horizontal = 6.dp),
                onClick = { onSection(section.id) },
            )
        }

        if (sections.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Collecting device info…",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(8.dp))
            SectionTitle(title = "Quick actions")
            QuickActions(
                snapshot = snapshot,
                onRefresh = vm::refreshPhone,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Footer()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(
    snapshot: DeviceInfoSnapshot?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    FlowRow(
        modifier = modifier.fillMaxWidth(),
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
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            "Marrow v0.2.0",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "github.com/claudiusthebot/marrow",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "built with claws",
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
): String? = when (section.id) {
    Sections.BATTERY -> battery?.percent?.takeIf { it >= 0 }?.let { "$it%" }
    Sections.MEMORY -> memory?.let {
        "${formatBytesShort(it.usedBytes)} / ${formatBytesShort(it.totalBytes)}"
    }
    Sections.CPU -> if (cpuAvg > 0) "$cpuAvg MHz avg" else null
    Sections.STORAGE -> {
        val total = volumes.sumOf { it.totalBytes }
        val avail = volumes.sumOf { it.availBytes }
        if (total > 0) "${formatBytesShort(total - avail)} used" else null
    }
    Sections.DISPLAY -> null
    else -> null
}

private fun formatBytesShort(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
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

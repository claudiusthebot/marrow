package rocks.talon.marrow.phone.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import rocks.talon.marrow.shared.LiveStats

/**
 * Live stats strip — metric tiles in a single row, equal-weighted.
 * When the GPU driver exposes at least one readable stat, a GPU tile
 * is added automatically.
 *
 * Tile chrome matches PixelPlayer's `HeroMetricTile`: 18dp rounded corners,
 * surfaceContainerLow background, value above label, no inner icon. The
 * AnimatedContent slides values when they update so the strip feels alive.
 *
 * Tap any tile → invokes `onChipClick(sectionId)` so the host can navigate to
 * the matching section detail.
 *
 * CPU tile (v0.14.0): prefers [cpuUsagePercent] (0–100 from /proc/stat delta)
 * when ≥ 0; falls back to [cpuAvgMhz] MHz on restricted SELinux profiles where
 * /proc/stat is blocked. Usage-percent matches what the Wear tile already shows.
 *
 * Net tile (v0.15.0): total throughput (rx + tx) as KB/s or MB/s, matching the
 * Wear tile. Always present so the strip layout is stable — shows "—" until the
 * second live-loop tick produces a rate delta. Tapping navigates to the Network
 * section for per-interface detail.
 *
 * Disk tile (v0.19.0): conditional — only shown when /proc/diskstats reports
 * active I/O (read + write > 0). Hides at rest so the strip stays compact.
 * Displays total throughput (read + write) in the same format as LiveStats.
 * Tapping navigates to the Storage section.
 */
@Composable
fun LiveStatsStrip(
    battery: LiveStats.Battery?,
    memory: LiveStats.Memory?,
    cpuAvgMhz: Long,
    cpuUsagePercent: Float = -1f,
    storageUsedFraction: Float,
    gpu: LiveStats.Gpu?,
    networkRate: Pair<Long, Long> = 0L to 0L,
    diskRate: Pair<Long, Long> = 0L to 0L,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedTile(
            label = "Battery",
            value = battery?.percent?.takeIf { it >= 0 }?.let { "$it%" } ?: "—",
            onClick = { onChipClick("battery") },
            modifier = Modifier.weight(1f),
        )
        AnimatedTile(
            label = "RAM used",
            value = memory?.let { "${it.usedPercent}%" } ?: "—",
            onClick = { onChipClick("memory") },
            modifier = Modifier.weight(1f),
        )
        // CPU: prefer usage% (more legible) over avg MHz, fall back when /proc/stat blocked.
        AnimatedTile(
            label = "CPU",
            value = when {
                cpuUsagePercent >= 0f -> "${cpuUsagePercent.toInt()}%"
                cpuAvgMhz > 0 -> "$cpuAvgMhz MHz"
                else -> "—"
            },
            onClick = { onChipClick("cpu") },
            modifier = Modifier.weight(1f),
        )
        AnimatedTile(
            label = "Storage",
            value = if (storageUsedFraction > 0f) "${(storageUsedFraction * 100).toInt()}%" else "—",
            onClick = { onChipClick("storage") },
            modifier = Modifier.weight(1f),
        )
        // Net tile — total throughput (rx + tx). Always shown so the strip
        // layout stays stable; "—" until the second tick produces a rate delta.
        AnimatedTile(
            label = "Net",
            value = run {
                val (rx, tx) = networkRate
                val total = rx + tx
                if (total <= 0L) "—" else formatNetRate(total)
            },
            onClick = { onChipClick("network") },
            modifier = Modifier.weight(1f),
        )
        // Disk tile — conditional. Only rendered when /proc/diskstats reports
        // active I/O. Hides at rest so the strip stays compact when idle.
        val diskTotal = diskRate.first + diskRate.second
        if (diskTotal > 0L) {
            AnimatedTile(
                label = "Disk",
                value = LiveStats.formatDiskBps(diskTotal),
                onClick = { onChipClick("storage") },
                modifier = Modifier.weight(1f),
            )
        }
        // GPU tile — only rendered when the driver exposes at least one
        // readable frequency stat (gpu.available = maxMhz > 0). On emulators
        // and SELinux-locked devices the tile is absent rather than showing "—".
        if (gpu?.available == true) {
            AnimatedTile(
                label = "GPU",
                value = when {
                    gpu.usagePercent >= 0 -> "${gpu.usagePercent}%"
                    gpu.curMhz > 0 -> "${gpu.curMhz} MHz"
                    else -> "—"
                },
                onClick = { onChipClick("gpu") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Format a bytes-per-second throughput rate as a short human-readable string. */
private fun formatNetRate(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_048_576L -> "%.1f MB/s".format(bytesPerSec / 1_048_576.0)
    bytesPerSec >= 1_024L -> "${bytesPerSec / 1_024} KB/s"
    else -> "$bytesPerSec B/s"
}

@Composable
private fun AnimatedTile(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AnimatedContent(
            targetState = value,
            label = "stat-$label",
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 })
            },
        ) { current ->
            HeroMetricTile(
                label = label,
                value = current,
                container = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

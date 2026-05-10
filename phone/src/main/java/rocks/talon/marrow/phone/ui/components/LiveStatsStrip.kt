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
 * Live stats strip — four metric tiles in a single row, equal-weighted.
 * When the GPU driver exposes at least one readable stat, a fifth GPU tile
 * is added automatically.
 *
 * Tile chrome matches PixelPlayer's `HeroMetricTile`: 18dp rounded corners,
 * surfaceContainerLow background, value above label, no inner icon. The
 * AnimatedContent slides values when they update so the strip feels alive.
 *
 * Tap any tile → invokes `onChipClick(sectionId)` so the host can navigate to
 * the matching section detail.
 */
@Composable
fun LiveStatsStrip(
    battery: LiveStats.Battery?,
    memory: LiveStats.Memory?,
    cpuAvgMhz: Long,
    storageUsedFraction: Float,
    gpu: LiveStats.Gpu?,
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
        AnimatedTile(
            label = "CPU avg",
            value = if (cpuAvgMhz > 0) "$cpuAvgMhz MHz" else "—",
            onClick = { onChipClick("cpu") },
            modifier = Modifier.weight(1f),
        )
        AnimatedTile(
            label = "Storage",
            value = if (storageUsedFraction > 0f) "${(storageUsedFraction * 100).toInt()}%" else "—",
            onClick = { onChipClick("storage") },
            modifier = Modifier.weight(1f),
        )
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

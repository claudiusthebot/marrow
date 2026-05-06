package rocks.talon.marrow.phone.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rocks.talon.marrow.phone.ui.icons.MarrowIcons
import rocks.talon.marrow.shared.LiveStats

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveStatsStrip(
    battery: LiveStats.Battery?,
    memory: LiveStats.Memory?,
    cpuAvgMhz: Long,
    storageUsedFraction: Float,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedValueChip(
            label = "Battery",
            value = battery?.percent?.takeIf { it >= 0 }?.let { "$it%" } ?: "—",
            icon = MarrowIcons.Battery,
            onClick = { onChipClick("battery") },
        )
        AnimatedValueChip(
            label = "RAM used",
            value = memory?.let { "${it.usedPercent}%" } ?: "—",
            icon = MarrowIcons.Memory,
            onClick = { onChipClick("memory") },
        )
        AnimatedValueChip(
            label = "CPU",
            value = if (cpuAvgMhz > 0) "$cpuAvgMhz MHz" else "—",
            icon = MarrowIcons.Cpu,
            onClick = { onChipClick("cpu") },
        )
        AnimatedValueChip(
            label = "Storage",
            value = if (storageUsedFraction > 0f) "${(storageUsedFraction * 100).toInt()}%" else "—",
            icon = MarrowIcons.Storage,
            onClick = { onChipClick("storage") },
        )
    }
}

@Composable
private fun AnimatedValueChip(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(0.dp),
    ) {
        AnimatedContent(
            targetState = value,
            label = "stat-$label",
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 })
            },
        ) { current ->
            LiveStatChip(label = label, value = current, icon = icon, onClick = onClick)
        }
    }
}

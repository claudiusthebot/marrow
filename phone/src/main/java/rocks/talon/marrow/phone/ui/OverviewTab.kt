package rocks.talon.marrow.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import rocks.talon.marrow.phone.MarrowViewModel
import rocks.talon.marrow.phone.ui.components.SparklineChart

/**
 * OverviewTab — a single-glance dashboard showing all four sparklines
 * (CPU, RAM, Network RX, Battery Current) side by side in card form.
 *
 * First Marrow screen that aggregates data across heroes rather than
 * drilling into a single section. Intentionally minimal — just the shape
 * of the signal, the current value, and the label.
 */
@Composable
fun OverviewTab(vm: MarrowViewModel) {
    val cpuHistory by vm.cpuHistory.collectAsState()
    val ramHistory by vm.ramHistory.collectAsState()
    val rxHistory by vm.rxHistory.collectAsState()
    val batteryCurrentHistory by vm.batteryCurrentHistory.collectAsState()
    val cpuUsage by vm.cpuUsagePercent.collectAsState()
    val memory by vm.memory.collectAsState()
    val networkRate by vm.networkRate.collectAsState()
    val battery by vm.battery.collectAsState()

    val cpuColor = when {
        cpuUsage > 80f -> MaterialTheme.colorScheme.error
        cpuUsage > 50f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val ramColor = when {
        (memory?.usedPercent ?: 0) > 80 -> MaterialTheme.colorScheme.error
        (memory?.usedPercent ?: 0) > 60 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val rxColor = MaterialTheme.colorScheme.primary
    val battColor = when {
        (battery?.currentMa ?: 0) > 0 -> MaterialTheme.colorScheme.primary        // charging
        ((battery?.currentMa ?: 0) * -1) > 1500 -> MaterialTheme.colorScheme.error  // heavy drain
        ((battery?.currentMa ?: 0) * -1) > 500 -> MaterialTheme.colorScheme.secondary  // moderate
        else -> MaterialTheme.colorScheme.onSurface                                 // idle
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
    ) {
        item {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            SparklineCard(
                label = "CPU",
                value = if (cpuUsage >= 0f) "${cpuUsage.toInt()}%" else "—",
                history = cpuHistory,
                color = cpuColor,
            )
        }
        item {
            SparklineCard(
                label = "RAM",
                value = memory?.let { "${it.usedPercent}%" } ?: "—",
                history = ramHistory,
                color = ramColor,
            )
        }
        item {
            SparklineCard(
                label = "Network RX",
                value = formatBytesPerSec(networkRate.first),
                history = rxHistory,
                color = rxColor,
            )
        }
        item {
            SparklineCard(
                label = "Battery Current",
                value = battery?.currentMa?.let { mA ->
                    if (mA != Int.MIN_VALUE) {
                        val sign = if (mA >= 0) "+" else ""
                        "$sign$mA mA"
                    } else "—"
                } ?: "—",
                history = batteryCurrentHistory,
                color = battColor,
            )
        }
    }
}

@Composable
private fun SparklineCard(
    label: String,
    value: String,
    history: List<Float>,
    color: Color,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            SparklineChart(
                data = history,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
        }
    }
}

/** Format a bytes/sec value to a human-readable rate string. */
private fun formatBytesPerSec(bps: Long): String = when {
    bps >= 1_000_000L -> "${"%.1f".format(bps / 1_000_000.0)} MB/s"
    bps >= 1_000L -> "${"%.0f".format(bps / 1_000.0)} KB/s"
    else -> "$bps B/s"
}

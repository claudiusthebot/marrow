package rocks.talon.marrow.phone.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.talon.marrow.phone.MarrowViewModel
import rocks.talon.marrow.phone.ui.components.IconBadge
import rocks.talon.marrow.phone.ui.components.MarrowCard
import rocks.talon.marrow.phone.ui.icons.MarrowIcons
import rocks.talon.marrow.shared.LiveStats
import rocks.talon.marrow.shared.Section

private val HERO_RADIUS = RoundedCornerShape(32.dp)

// -- Battery -----------------------------------------------------------------

@Composable
fun BatteryHero(vm: MarrowViewModel, section: Section, isWatch: Boolean) {
    val battery by vm.battery.collectAsState()
    val percent = if (isWatch) {
        // For the watch, parse the battery section's "Level" row (already a string %)
        section.rows.firstOrNull { it.label == "Level" }?.value
            ?.removeSuffix("%")?.toIntOrNull() ?: -1
    } else battery?.percent ?: -1

    val charging = if (isWatch) {
        section.rows.firstOrNull { it.label == "Status" }?.value?.lowercase()?.contains("charging") == true
    } else battery?.charging == true

    val tempC = if (isWatch) {
        section.rows.firstOrNull { it.label == "Temperature" }?.value
            ?.replace(" °C", "")?.toFloatOrNull() ?: -1f
    } else battery?.temperatureC ?: -1f

    val voltageV = if (isWatch) {
        section.rows.firstOrNull { it.label == "Voltage" }?.value
            ?.replace(" mV", "")?.toFloatOrNull()?.let { it / 1000f } ?: -1f
    } else battery?.voltageV ?: -1f

    val animatedPct by animateFloatAsState(
        targetValue = if (percent >= 0) percent / 100f else 0f,
        animationSpec = tween(700),
        label = "battery-pct",
    )

    val tint = when {
        percent < 0 -> MaterialTheme.colorScheme.surfaceVariant
        percent <= 15 -> Color(0xFFE53935)
        percent <= 35 -> Color(0xFFFFA726)
        percent <= 80 -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF66BB6A)
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    HeroBox {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    val stroke = 14.dp.toPx()
                    val pad = stroke / 2f + 4f
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = Size(size.width - pad * 2, size.height - pad * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = tint,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedPct,
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = Size(size.width - pad * 2, size.height - pad * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (percent >= 0) "$percent%" else "—",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (charging) {
                        Text(
                            "charging",
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                        )
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Column {
                BigStat("Voltage", if (voltageV >= 0) "%.2f V".format(voltageV) else "—")
                Spacer(Modifier.height(8.dp))
                BigStat("Temp", if (tempC >= 0) "%.1f °C".format(tempC) else "—")
                Spacer(Modifier.height(8.dp))
                if (!isWatch && battery != null) {
                    BigStat(
                        "Plug",
                        when (battery!!.plugged) {
                            LiveStats.Battery.PlugType.UNPLUGGED -> "Off"
                            LiveStats.Battery.PlugType.AC -> "AC"
                            LiveStats.Battery.PlugType.USB -> "USB"
                            LiveStats.Battery.PlugType.WIRELESS -> "Wireless"
                            LiveStats.Battery.PlugType.DOCK -> "Dock"
                        },
                    )
                } else {
                    BigStat("Status", section.rows.firstOrNull { it.label == "Status" }?.value ?: "—")
                }
                val curMa = battery?.currentMa ?: Int.MIN_VALUE
                if (!isWatch && curMa != Int.MIN_VALUE) {
                    Spacer(Modifier.height(8.dp))
                    BigStat("Current", "$curMa mA")
                }
            }
        }
    }
}

// -- CPU ---------------------------------------------------------------------

@Composable
fun CpuHero(vm: MarrowViewModel, section: Section, isWatch: Boolean) {
    val cores by vm.cpuCores.collectAsState()
    val cpuTempC by vm.cpuTempC.collectAsState()
    val cpuUsage by vm.cpuUsagePercent.collectAsState()
    val thermalZones by vm.thermalZones.collectAsState()
    val coreCount = section.rows.firstOrNull { it.label == "Cores" }?.value?.toIntOrNull() ?: cores.size
    val abis = section.rows.firstOrNull { it.label == "ABIs" }?.value
        ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    val governor = cores.firstOrNull { it.governor != null }?.governor

    // Temperature color: mirrors BatteryHero tint thresholds.
    // Green  < 60 °C — normal operating range.
    // Orange 60–79 °C — warm, worth watching.
    // Red   ≥ 80 °C — hot, potential throttling territory.
    val tempColor = when {
        cpuTempC < 0f -> Color.Unspecified         // unavailable — not shown
        cpuTempC >= 80f -> Color(0xFFE53935)        // red
        cpuTempC >= 60f -> Color(0xFFFFA726)        // orange
        else -> Color(0xFF66BB6A)                   // green
    }

    HeroBox {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = MarrowIcons.Cpu, size = 44, cornerRadius = 14)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "$coreCount cores",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    if (governor != null) {
                        Text(
                            "Governor · $governor",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isWatch && cpuTempC >= 0f) {
                        Text(
                            "%.1f °C".format(cpuTempC),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = tempColor,
                        )
                    }
                }
            }
            // Live CPU utilisation bar — phone only, shown after second tick
            if (!isWatch && cpuUsage >= 0f) {
                Spacer(Modifier.height(12.dp))
                val usageFrac = (cpuUsage / 100f).coerceIn(0f, 1f)
                val animatedUsage by animateFloatAsState(
                    targetValue = usageFrac,
                    animationSpec = tween(450),
                    label = "cpu-usage",
                )
                val usageColor = when {
                    cpuUsage >= 90f -> Color(0xFFE53935)   // red — very high
                    cpuUsage >= 70f -> Color(0xFFFFA726)   // orange — elevated
                    else -> MaterialTheme.colorScheme.primary
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .height(10.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedUsage)
                                .height(10.dp)
                                .background(usageColor),
                        )
                    }
                    Text(
                        "${cpuUsage.toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = usageColor,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            // Per-core bars (live values when on phone, static values when on watch)
            if (!isWatch && cores.isNotEmpty()) {
                // Group cores by maxMhz to surface big.LITTLE / cluster topology.
                // Falls back to a flat list when all cores share the same ceiling
                // (e.g. emulators, or when cpufreq reads return 0).
                val clusters = cores.groupBy { it.maxMhz }.entries.sortedBy { it.key }
                if (clusters.size > 1) {
                    val names = when (clusters.size) {
                        2 -> listOf("Efficiency", "Performance")
                        3 -> listOf("Efficiency", "Mid", "Performance")
                        4 -> listOf("Efficiency", "Core", "Performance", "Prime")
                        else -> clusters.indices.map { "Cluster ${it + 1}" }
                    }
                    clusters.forEachIndexed { idx, (maxMhz, clusterCores) ->
                        val ghz = if (maxMhz > 0) "≤ %.1f GHz".format(maxMhz / 1000f) else "unknown"
                        ClusterDivider("${names[idx]} · ×${clusterCores.size} · $ghz")
                        clusterCores.forEach { core -> CoreBar(core = core) }
                    }
                } else {
                    cores.forEach { core -> CoreBar(core = core) }
                }
            } else {
                // Watch -- pull from rows
                section.rows.filter { it.label.startsWith("CPU ") }.take(coreCount).forEachIndexed { idx, row ->
                    val cur = row.value.substringAfter("now ", "0").substringBefore(" MHz").toLongOrNull() ?: 0L
                    val maxStr = row.value.substringAfter("-").substringBefore(" MHz").trim()
                    val max = maxStr.toLongOrNull() ?: 1L
                    CoreBarStatic(label = "Core $idx", current = cur, max = max)
                }
            }
            // Thermal zones overview — phone only, when we have readable zones
            if (!isWatch && thermalZones.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                ClusterDivider("Thermal")
                thermalZones.forEach { zone ->
                    ThermalZoneRow(zone)
                }
            }
            if (abis.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    abis.forEach { abi ->
                        AssistChip(
                            onClick = {},
                            label = { Text(abi, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoreBar(core: LiveStats.CpuCore) {
    val frac = if (core.maxMhz > 0) (core.curMhz.toFloat() / core.maxMhz.toFloat()).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(targetValue = frac, animationSpec = tween(450), label = "core-${core.index}")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${core.index}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        Box(
            modifier = Modifier
                .height(10.dp)
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (core.curMhz > 0) "${core.curMhz} MHz" else "—",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(80.dp),
        )
    }
}

@Composable
private fun CoreBarStatic(label: String, current: Long, max: Long) {
    val frac = if (max > 0) (current.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
        Box(
            modifier = Modifier
                .height(10.dp)
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.fillMaxWidth(frac).height(10.dp).background(MaterialTheme.colorScheme.primary))
        }
        Spacer(Modifier.width(8.dp))
        Text("$current", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
    }
}

/** One thermal zone row: name label + temperature bar + °C value. */
@Composable
private fun ThermalZoneRow(zone: LiveStats.ThermalZone) {
    val color = when {
        zone.tempC >= 60f -> Color(0xFFE53935)   // red  — hot
        zone.tempC >= 40f -> Color(0xFFFFA726)   // orange — warm
        else              -> Color(0xFF66BB6A)   // green — cool
    }
    // Bar scaled to 100 °C ceiling — most SoC zones throttle before 100 °C
    val frac = (zone.tempC / 100f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = frac,
        animationSpec = tween(450),
        label = "tz-${zone.name}",
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            zone.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .height(8.dp)
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .background(color),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "%.0f°".format(zone.tempC),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.width(30.dp),
        )
    }
}

/** Horizontal rule with a centred label used to separate CPU cluster groups. */
@Composable
private fun ClusterDivider(label: String) {
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
    Spacer(Modifier.height(2.dp))
}

// -- Memory ------------------------------------------------------------------

/**
 * Memory pressure level derived from used fraction and the system's lowMemory flag.
 *
 * Comfortable  < 60% used   — green
 * Moderate     60–80% used  — orange
 * Critical     > 80% OR ActivityManager.lowMemory == true — red
 */
private enum class MemoryPressure(
    val label: String,
    val color: Color,
) {
    COMFORTABLE("Comfortable", Color(0xFF66BB6A)),
    MODERATE("Moderate pressure", Color(0xFFFFA726)),
    CRITICAL("Low memory!", Color(0xFFE53935)),
}

@Composable
fun MemoryHero(vm: MarrowViewModel, section: Section, isWatch: Boolean) {
    val mem by vm.memory.collectAsState()
    val totalBytes = if (isWatch) parseBytes(section.rows.firstOrNull { it.label == "Total RAM" }?.value) else mem?.totalBytes ?: 0L
    val availBytes = if (isWatch) parseBytes(section.rows.firstOrNull { it.label == "Available RAM" }?.value) else mem?.availBytes ?: 0L
    val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)
    val frac = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f
    val animated by animateFloatAsState(targetValue = frac, animationSpec = tween(500), label = "mem-frac")

    // Swap / zRAM — phone only (watch snapshot rows don't include swap)
    val swapTotal = if (!isWatch) mem?.swapTotalBytes ?: 0L else 0L
    val swapUsed = if (!isWatch) mem?.swapUsedBytes ?: 0L else 0L
    val swapFrac = if (swapTotal > 0L) (swapUsed.toFloat() / swapTotal.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedSwap by animateFloatAsState(targetValue = swapFrac, animationSpec = tween(500), label = "swap-frac")

    // Memory pressure level — phone only
    val lowMemory = !isWatch && mem?.lowMemory == true
    val pressure = when {
        !isWatch && (lowMemory || frac > 0.80f) -> MemoryPressure.CRITICAL
        !isWatch && frac > 0.60f               -> MemoryPressure.MODERATE
        else                                   -> MemoryPressure.COMFORTABLE
    }

    HeroBox {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = MarrowIcons.Memory, size = 44, cornerRadius = 14)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "${(animated * 100).toInt()}% used",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${formatGib(usedBytes)} of ${formatGib(totalBytes)} RAM",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            // RAM bar -- used (gradient) | free
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animated)
                        .height(28.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        ),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LegendDot(color = MaterialTheme.colorScheme.primary, label = "Used ${formatGib(usedBytes)}")
                LegendDot(color = MaterialTheme.colorScheme.surfaceVariant, label = "Free ${formatGib(availBytes)}")
            }
            // zRAM bar — only when swap is configured (most modern Android phones use zRAM)
            if (swapTotal > 0L) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "zRAM",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${formatGib(swapUsed)} / ${formatGib(swapTotal)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedSwap)
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                }
            }
            // Memory pressure badge
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(pressure.color),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    pressure.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = pressure.color,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// -- GPU ---------------------------------------------------------------------

@Composable
fun GpuHero(vm: MarrowViewModel, section: Section) {
    val gpu by vm.gpu.collectAsState()

    // Frequency bar: currentFreq / maxFreq (0f when unavailable)
    val freqFrac = gpu?.freqFraction ?: 0f
    val animatedFreq by animateFloatAsState(targetValue = freqFrac, animationSpec = tween(500), label = "gpu-freq")

    // Utilisation bar: usagePercent / 100 (-1 means not exposed by this SoC)
    val util = gpu?.usagePercent ?: -1
    val utilFrac = if (util in 0..100) util / 100f else -1f
    val animatedUtil by animateFloatAsState(
        targetValue = if (utilFrac >= 0f) utilFrac else 0f,
        animationSpec = tween(500),
        label = "gpu-util",
    )

    val curMhz = gpu?.curMhz ?: 0L
    val maxMhz = gpu?.maxMhz ?: 0L
    val minMhz = gpu?.minMhz ?: 0L
    val governor = gpu?.governor ?: section.rows.firstOrNull { it.label == "Governor" }?.value
    val available = gpu?.available ?: false

    HeroBox {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = MarrowIcons.Gpu, size = 44, cornerRadius = 14)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (available && curMhz > 0) "$curMhz MHz" else section.preview.ifBlank { "GPU" },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        if (available && maxMhz > 0) {
                            if (minMhz > 0) "$minMhz–$maxMhz MHz range" else "max $maxMhz MHz"
                        } else {
                            section.rows.firstOrNull { it.label == "GPU family" || it.label == "GPU driver" }?.value
                                ?: "GPU info unavailable"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (available) {
                Spacer(Modifier.height(20.dp))

                // Frequency bar (always shown when available)
                Text(
                    "Frequency",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFreq)
                            .height(18.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    ),
                                ),
                            ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (minMhz > 0) "$minMhz MHz min" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (maxMhz > 0) "$maxMhz MHz max" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Utilisation bar — only when the SoC driver exposes it
                if (utilFrac >= 0f) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Utilisation",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "$util%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedUtil)
                                .height(10.dp)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                    }
                }

                // Governor chip
                if (governor != null) {
                    Spacer(Modifier.height(14.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(governor, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    "GPU stats are unavailable on this device or emulator.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// -- Storage -----------------------------------------------------------------

@Composable
fun StorageHero(vm: MarrowViewModel, section: Section, isWatch: Boolean) {
    val volumes by vm.volumes.collectAsState()
    val diskRate by vm.diskRate.collectAsState()
    val (readBps, writeBps) = diskRate

    val volumeList = if (isWatch || volumes.isEmpty()) {
        // Reconstruct from rows
        val total = parseBytes(section.rows.firstOrNull { it.label == "Internal — total" }?.value)
        val avail = parseBytes(section.rows.firstOrNull { it.label == "Internal — available" }?.value)
        if (total > 0) listOf(LiveStats.Volume("Internal", total, avail)) else emptyList()
    } else volumes

    HeroBox {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = MarrowIcons.Storage, size = 44, cornerRadius = 14)
                Spacer(Modifier.width(12.dp))
                Text("Volumes", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.height(16.dp))
            volumeList.forEach { vol -> VolumeRow(vol) }
            if (volumeList.isEmpty()) {
                Text("Storage info unavailable.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Live I/O rates — shown after first full tick, phone only
            if (!isWatch && (readBps > 0L || writeBps > 0L)) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BigStat("↓ Read", LiveStats.formatDiskBps(readBps))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        BigStat("↑ Write", LiveStats.formatDiskBps(writeBps))
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeRow(vol: LiveStats.Volume) {
    val frac = vol.usedFraction
    val animated by animateFloatAsState(frac, tween(500), label = "vol-${vol.label}")
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(vol.label, style = MaterialTheme.typography.titleSmall)
            Text("${formatGib(vol.usedBytes)} / ${formatGib(vol.totalBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.fillMaxWidth(animated).height(10.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}

// -- Display -----------------------------------------------------------------

@Composable
fun DisplayHero(section: Section) {
    val res = section.rows.firstOrNull { it.label == "Resolution" }?.value ?: "—"
    val dpi = section.rows.firstOrNull { it.label == "Density" }?.value ?: "—"
    val refresh = section.rows.firstOrNull { it.label == "Refresh rate" }?.value ?: "—"
    val hdr = section.rows.firstOrNull { it.label == "HDR" }?.value ?: "none"

    HeroBox {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = MarrowIcons.Display, size = 44, cornerRadius = 14)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(res, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "$dpi · $refresh",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // Phone-shape preview (rough aspect 9:19)
            Box(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(0.5f)
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    res.replace(" px", "").replace(" × ", "\n×\n"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (hdr != "none") {
                AssistChip(
                    onClick = {},
                    label = { Text("HDR · $hdr") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                )
            }
        }
    }
}

// -- Network -----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkHero(vm: MarrowViewModel, section: Section) {
    val transport = section.rows.firstOrNull { it.label == "Connection" }?.value ?: "—"
    val carrier = section.rows.firstOrNull { it.label == "Carrier" }?.value
    val ssid = section.rows.firstOrNull { it.label == "Wi-Fi SSID" }?.value
    val rssi = section.rows.firstOrNull { it.label == "Wi-Fi RSSI" }?.value
    // val (a, b) by delegate is not valid Kotlin syntax — split into two lines.
    val networkRatePair by vm.networkRate.collectAsState()
    val (rxBps, txBps) = networkRatePair
    HeroBox {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = MarrowIcons.Network, size = 44, cornerRadius = 14)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(transport, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    val sub = listOfNotNull(ssid, carrier).joinToString(" · ")
                    if (sub.isNotBlank()) Text(
                        sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rssi != null) {
                Spacer(Modifier.height(8.dp))
                AssistChip(onClick = {}, label = { Text("RSSI $rssi") })
            }
            // Live throughput — shown after the first two polling ticks provide a rate
            if (rxBps > 0L || txBps > 0L) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    BigStat("↓ Download", LiveStats.formatSpeedBps(rxBps))
                    BigStat("↑ Upload", LiveStats.formatSpeedBps(txBps))
                }
            }
        }
    }
}

// -- Sensors -----------------------------------------------------------------

@Composable
fun SensorsHero(section: Section, isPhone: Boolean) {
    HeroBox {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = MarrowIcons.Sensors, size = 44, cornerRadius = 14)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "${section.rows.size} sensors",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        if (isPhone) "Tap a sensor below to see live readings" else "Static read of the watch's sensor list",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isPhone) {
                Spacer(Modifier.height(16.dp))
                LiveSensorPanel()
            }
        }
    }
}

// -- Cameras -----------------------------------------------------------------

@Composable
fun CamerasHero(section: Section) {
    HeroBox {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = MarrowIcons.Cameras, size = 44, cornerRadius = 14)
                Spacer(Modifier.width(12.dp))
                Text(
                    section.preview,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

// -- Build flags -------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildFlagsHero(section: Section) {
    val verifiedBoot = section.rows.firstOrNull { it.label == "Verified boot state" }?.value ?: "?"
    val treble = section.rows.firstOrNull { it.label.startsWith("Treble") }?.value ?: "?"
    val patch = section.rows.firstOrNull { it.label == "Security patch" }?.value
        ?: section.rows.firstOrNull { it.label.contains("Security patch", ignoreCase = true) }?.value
        ?: "?"
    val vbColor = when (verifiedBoot.lowercase()) {
        "green" -> Color(0xFF66BB6A)
        "yellow" -> Color(0xFFFFEE58)
        "orange" -> Color(0xFFFFA726)
        "red" -> Color(0xFFE53935)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    HeroBox {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = MarrowIcons.BuildFlags, size = 44, cornerRadius = 14)
                Spacer(Modifier.width(12.dp))
                Text("Build state",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Badge("Verified boot", verifiedBoot, vbColor)
                Badge("Treble", treble, MaterialTheme.colorScheme.tertiary)
                Badge("Patch", patch, MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

// -- Device / System / Software / Generic ----------------------------------

@Composable
fun DeviceHero(section: Section) {
    val brand = section.rows.firstOrNull { it.label == "Brand" }?.value ?: "—"
    val model = section.rows.firstOrNull { it.label == "Model" }?.value ?: "—"
    val board = section.rows.firstOrNull { it.label == "Board" }?.value ?: "—"
    HeroBox {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = MarrowIcons.Device, size = 44, cornerRadius = 14)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(model, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "$brand · $board",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun SystemHero(section: Section) {
    val v = section.rows.firstOrNull { it.label == "Android version" }?.value ?: "—"
    val sdk = section.rows.firstOrNull { it.label == "SDK" }?.value ?: "—"
    HeroBox {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = MarrowIcons.System, size = 44, cornerRadius = 14)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Android $v",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    "API level $sdk",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SoftwareHero(section: Section) {
    val vm = section.rows.firstOrNull { it.label == "Java VM" }?.value ?: "—"
    HeroBox {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = MarrowIcons.Software, size = 44, cornerRadius = 14)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Runtime", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(vm, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun GenericHero(section: Section) {
    HeroBox {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = MarrowIcons.forSection(section.id), size = 44, cornerRadius = 14)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(section.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                if (section.preview.isNotBlank()) Text(
                    section.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// -- Shared little components ------------------------------------------------

@Composable
private fun HeroBox(content: @Composable () -> Unit) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        start = Offset(0f, 0f),
        end = Offset(1500f, 1500f),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HERO_RADIUS)
            .background(gradient),
    ) { content() }
}

@Composable
private fun BigStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun Badge(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun parseBytes(s: String?): Long {
    if (s == null) return 0L
    val parts = s.trim().split(' ')
    if (parts.size < 2) return 0L
    val v = parts[0].toDoubleOrNull() ?: return 0L
    val unit = parts[1]
    val mult: Double = when (unit) {
        "B" -> 1.0
        "KiB" -> 1024.0
        "MiB" -> 1024.0 * 1024
        "GiB" -> 1024.0 * 1024 * 1024
        "TiB" -> 1024.0 * 1024 * 1024 * 1024
        else -> 1.0
    }
    return (v * mult).toLong()
}

private fun formatGib(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024.0; i++
    }
    return "%.1f %s".format(v, units[i])
}

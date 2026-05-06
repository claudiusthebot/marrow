package rocks.talon.marrow.phone.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rocks.talon.marrow.phone.MarrowViewModel
import rocks.talon.marrow.phone.ui.components.MarrowCapabilityCard
import rocks.talon.marrow.phone.ui.components.StatusIcon
import rocks.talon.marrow.phone.ui.icons.MarrowIcons

/**
 * About tab — Marrow brand block + settings entry + repo card.
 *
 * Same single-LazyColumn pattern as Device/Watch tabs: 16dp horizontal
 * `contentPadding` once, no per-item padding.
 */
@Composable
fun AboutTab(
    vm: MarrowViewModel,
    onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("brand") {
            BrandBlock()
        }

        item("settings") {
            MarrowCapabilityCard(
                title = "Settings",
                icon = MarrowIcons.System,
                onClick = onOpenSettings,
                verticalSpacing = 4.dp,
            ) {
                Text(
                    text = "Theme, refresh interval",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item("about") {
            MarrowCapabilityCard(
                title = "About Marrow",
                icon = MarrowIcons.BuildFlags,
                verticalSpacing = 12.dp,
            ) {
                Text(
                    text = "Marrow shows the bones of your phone and watch — Android version, " +
                        "battery chemistry, sensor list, every CPU's current frequency. Phone and " +
                        "watch are the same app twice; the two halves talk over the Wear OS Data " +
                        "Layer so the Watch tab mirrors what the watch sees.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    text = "v0.3.0  ·  Material 3 Expressive  ·  Wear OS 6  ·  Android 16/17",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "MIT licensed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item("repo") {
            MarrowCapabilityCard(
                title = "Open source",
                icon = MarrowIcons.Software,
                verticalSpacing = 6.dp,
                onClick = {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/claudiusthebot/marrow"))
                    i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    runCatching { ctx.startActivity(i) }
                },
            ) {
                Text(
                    text = "github.com/claudiusthebot/marrow",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Inspired by SebaUbuntu/Athena. Sibling apps in the Talon series: hydrate-pixel-watch, claude-watch-buddy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BrandBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(
                icon = MarrowIcons.Wordmark,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 64.dp,
                iconSize = 32.dp,
            )
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    text = "Marrow",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "to the marrow of your devices",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

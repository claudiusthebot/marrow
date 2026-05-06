package rocks.talon.marrow.phone.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.talon.marrow.phone.MarrowViewModel
import rocks.talon.marrow.phone.ui.components.BoneGlyph
import rocks.talon.marrow.phone.ui.components.IconBadge
import rocks.talon.marrow.phone.ui.components.MarrowCard
import rocks.talon.marrow.phone.ui.icons.MarrowIcons

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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(36.dp))
                    .padding(top = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 24.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BoneGlyph(modifier = Modifier.size(72.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Marrow",
                        style = MaterialTheme.typography.displayLarge.copy(
                            letterSpacing = (-2).sp,
                            fontWeight = FontWeight.Black,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "to the marrow of your devices",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MarrowCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenSettings,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(icon = MarrowIcons.System, size = 44, cornerRadius = 14)
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "Theme, refresh interval",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            MarrowCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "About",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Marrow shows the bones of your phone and watch — Android version, " +
                            "battery chemistry, sensor list, every CPU's current frequency. " +
                            "Phone and watch are the same app twice; the two halves talk over " +
                            "the Wear OS Data Layer so the Watch tab mirrors what the watch sees.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "v0.2.0  ·  Material 3 Expressive  ·  Wear OS 6  ·  Android 16/17",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "MIT licensed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MarrowCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/claudiusthebot/marrow"))
                    i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    runCatching { ctx.startActivity(i) }
                },
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Open source",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "github.com/claudiusthebot/marrow",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Inspired by SebaUbuntu/Athena. Sibling apps in the Talon series: " +
                            "hydrate-pixel-watch, claude-watch-buddy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

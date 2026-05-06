package rocks.talon.marrow.phone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import rocks.talon.marrow.phone.ui.icons.MarrowIcons

/* -------------------------------------------------------------------------- */
/* Hero — the device-name banner that anchors the Device and Watch tabs.      */
/*                                                                             */
/* The PixelPlayer pattern is: a single Surface card on `primaryContainer`    */
/* with a header row (StatusIcon + headline + supporting line) and a tile     */
/* row of three metric pills below. No gradients, no custom mascots — the    */
/* device name is the show.                                                  */
/*                                                                             */
/* Shape: AbsoluteSmoothCornerShape(32.dp, 60) — slightly larger radius than  */
/* the capability cards (28dp) to give the hero its own visual weight, same   */
/* smoothness as the rest of the design system.                               */
/* -------------------------------------------------------------------------- */

/**
 * Marrow hero card.
 *
 * @param title         the device name (`Build.MODEL` on phone; same on watch)
 * @param manufacturer  e.g. "Google"
 * @param soc           SoC string — `Build.SOC_MODEL` or hardware fallback
 * @param android       e.g. "Android 17"
 * @param sdk           e.g. "API 35"
 * @param icon          section/device glyph drawn into the StatusIcon
 */
@Composable
fun MarrowHero(
    title: String,
    manufacturer: String,
    soc: String,
    android: String,
    sdk: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = MarrowIcons.Device,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(32.dp, 60),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusIcon(
                    icon = icon,
                    container = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 56.dp,
                    iconSize = 28.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "MARROW",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroMetricTile(
                    label = "Make",
                    value = manufacturer,
                    modifier = Modifier.weight(1f),
                    container = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                HeroMetricTile(
                    label = "SoC",
                    value = soc,
                    modifier = Modifier.weight(1f),
                    container = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                HeroMetricTile(
                    label = sdk,
                    value = android,
                    modifier = Modifier.weight(1f),
                    container = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Backwards-compat: DeviceHero kept as a thin wrapper around MarrowHero so   */
/* older callers keep compiling. Splits the old "subtitle" string of          */
/* "Make · SoC · Android X · API N" into MarrowHero's four-arg signature.     */
/* -------------------------------------------------------------------------- */

@Composable
fun DeviceHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val parts = subtitle.split("·").map { it.trim() }
    val manufacturer = parts.getOrNull(0) ?: ""
    val soc = parts.getOrNull(1) ?: ""
    val androidLine = parts.getOrNull(2) ?: ""
    val sdkLine = parts.getOrNull(3) ?: ""
    MarrowHero(
        title = title,
        manufacturer = manufacturer.ifBlank { "—" },
        soc = soc.ifBlank { "—" },
        android = androidLine.removePrefix("Android ").ifBlank { "—" }.let { "Android $it" }.removePrefix("Android Android "),
        sdk = sdkLine.ifBlank { "API ?" },
        modifier = modifier,
    )
}

/* -------------------------------------------------------------------------- */
/* About-screen wordmark glyph. Drawn instead of using a vector resource so   */
/* we don't need to ship a font/asset for the brand mark.                     */
/* -------------------------------------------------------------------------- */

@Composable
fun BoneGlyph(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barrelStrokeWidth = w * 0.16f
        drawLine(
            color = onPrimary,
            start = Offset(w * 0.22f, h * 0.78f),
            end = Offset(w * 0.78f, h * 0.22f),
            strokeWidth = barrelStrokeWidth,
            cap = StrokeCap.Round,
        )
        val knobR = w * 0.14f
        drawCircle(color = onPrimary, radius = knobR, center = Offset(w * 0.16f, h * 0.84f))
        drawCircle(color = onPrimary, radius = knobR, center = Offset(w * 0.30f, h * 0.70f))
        drawCircle(color = onPrimary, radius = knobR, center = Offset(w * 0.70f, h * 0.30f))
        drawCircle(color = onPrimary, radius = knobR, center = Offset(w * 0.84f, h * 0.16f))
        drawCircle(color = accent, radius = w * 0.07f, center = Offset(w * 0.84f, h * 0.16f))
    }
}

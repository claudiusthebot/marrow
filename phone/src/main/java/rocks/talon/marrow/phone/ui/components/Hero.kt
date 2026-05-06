package rocks.talon.marrow.phone.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The big device-name hero. A scrolling "above the fold" block:
 *   [Box (rounded, gradient bg)
 *     [Column
 *       big device-name
 *       small subtitle row (manufacturer · soc · android · sdk)
 *       chip row (chips passed in)
 *     ]
 *   ]
 *
 * The hero scrolls naturally with the rest of the LazyColumn — for the
 * collapsing-on-scroll variant we let the LazyColumn handle clipping; this
 * keeps motion simple and performant.
 */
@Composable
fun DeviceHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.surfaceContainer,
        ),
        start = Offset(0f, 0f),
        end = Offset(2000f, 2000f),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(gradient)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BoneGlyph(
                    modifier = Modifier.size(56.dp),
                    accent = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Marrow",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displayMedium.copy(
                            letterSpacing = (-1.5).sp,
                            fontWeight = FontWeight.Black,
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f),
                maxLines = 2,
            )
        }
    }
}

/**
 * The "bone" — Marrow's mascot glyph, drawn in Compose. A stylised dumbbell-
 * cross-section with a hot accent dot on one tip.
 */
@Composable
fun BoneGlyph(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimaryContainer
    val accentColor = accent
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Diagonal bone barrel
        val barrelStrokeWidth = w * 0.18f
        drawLine(
            color = onPrimary,
            start = Offset(w * 0.20f, h * 0.78f),
            end = Offset(w * 0.80f, h * 0.22f),
            strokeWidth = barrelStrokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )

        // Two knobs at each end
        val knobR = w * 0.16f
        drawCircle(color = onPrimary, radius = knobR, center = Offset(w * 0.12f, h * 0.86f))
        drawCircle(color = onPrimary, radius = knobR, center = Offset(w * 0.28f, h * 0.70f))
        drawCircle(color = onPrimary, radius = knobR, center = Offset(w * 0.72f, h * 0.30f))
        drawCircle(color = onPrimary, radius = knobR, center = Offset(w * 0.88f, h * 0.14f))

        // Hot accent dot — Marrow orange
        drawCircle(color = accentColor, radius = w * 0.08f, center = Offset(w * 0.88f, h * 0.14f))
    }
}

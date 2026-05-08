package rocks.talon.marrow.phone.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Minimum bar height below the status bar inset. */
private val COLLAPSED_DP = 56.dp

/** Maximum bar height when fully expanded (includes status bar region). */
private val EXPANDED_DP = 188.dp

/**
 * Retains animation state for a collapsible top bar that reacts to
 * nested scroll events.
 *
 * Mirrors the `CollapsibleCommonTopBar` pattern from PixelPlayer
 * (theovilardo/PixelPlayer, studied in Marrow heartbeat #117):
 * - [nestedScrollConnection] consumes vertical scroll to resize the bar
 * - On fling end, bar spring-snaps to whichever target (min or max) is closer
 * - [expandFraction] in [0,1] drives cross-fade between title styles
 */
class CollapsibleTopBarState(
    internal val heightPx: Animatable<Float, *>,
    private val scope: CoroutineScope,
    val minPx: Float,
    val maxPx: Float,
) {
    /** 0.0 = fully collapsed, 1.0 = fully expanded. */
    val expandFraction: Float
        get() = ((heightPx.value - minPx) / (maxPx - minPx)).coerceIn(0f, 1f)

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val newH = (heightPx.value + available.y).coerceIn(minPx, maxPx)
            val consumed = newH - heightPx.value
            if (consumed != 0f) scope.launch { heightPx.snapTo(newH) }
            return Offset(0f, consumed)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            scope.launch {
                val target = if (heightPx.value < (minPx + maxPx) / 2f) minPx else maxPx
                heightPx.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
            }
            return Velocity.Zero
        }
    }
}

/**
 * Creates and remembers a [CollapsibleTopBarState], incorporating the real
 * status bar height into the collapsed minimum so the bar is never shorter
 * than the system inset.
 */
@Composable
fun rememberCollapsibleTopBarState(): CollapsibleTopBarState {
    val density = LocalDensity.current
    val statusBarH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minPx = with(density) { (COLLAPSED_DP + statusBarH).toPx() }
    val maxPx = with(density) { EXPANDED_DP.toPx() }
    val anim = remember { Animatable(maxPx) }
    val scope = rememberCoroutineScope()
    return remember(anim, scope, minPx, maxPx) {
        CollapsibleTopBarState(anim, scope, minPx, maxPx)
    }
}

/**
 * Collapsible top bar that overlays scrollable content in a [Box].
 *
 * Usage contract for the host:
 * 1. Pass [CollapsibleTopBarState.nestedScrollConnection] via
 *    `Modifier.nestedScroll(state.nestedScrollConnection)` on the LazyColumn.
 * 2. Set the LazyColumn's `contentPadding` top equal to the bar's current
 *    height (`with(LocalDensity.current) { state.heightPx.value.toDp() }`)
 *    so content is never hidden under the bar.
 * 3. Align this composable to [Alignment.TopStart] in the Box.
 *
 * The bar renders with [WindowInsets.statusBars] padding so the title text
 * sits below the status bar regardless of animation state.
 */
@Composable
fun CollapsibleTopBar(
    state: CollapsibleTopBarState,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val heightDp: Dp = with(density) { state.heightPx.value.toDp() }
    val frac = state.expandFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // Subtitle fades in as bar expands past 40%
            if (frac > 0.4f) {
                val alpha = ((frac - 0.4f) / 0.6f).coerceIn(0f, 1f)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            // Title cross-fades between expanded (titleLarge) and collapsed (titleMedium)
            Text(
                text = title,
                style = if (frac > 0.5f)
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                else
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

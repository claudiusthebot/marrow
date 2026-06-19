package rocks.talon.marrow.phone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compact sparkline — a filled polyline showing recent value history.
 *
 * - Requires at least 2 data points; renders nothing for empty or singleton lists.
 * - Y axis: [data].min() maps to the bottom edge, [data].max() to the top. No labels or axes.
 * - Renders a filled translucent area under the line plus a 2dp opaque line on top.
 *
 * @param data  Ordered history values (oldest first, newest last).
 * @param modifier  Defaults to `fillMaxWidth().height(40.dp)` — override to fit the call site.
 * @param color  Line and fill colour; fill opacity is fixed at 15%.
 * @param strokeWidth  Width of the chart line in dp.
 */
@Composable
fun SparklineChart(
    data: List<Float>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(40.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.dp,
) {
    if (data.size < 2) return

    val lineColor = color
    val fillColor = color.copy(alpha = 0.15f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minV = data.min()
        val maxV = data.max()
        // Avoid zero-range division: if all values are equal show a flat mid-line.
        val range = (maxV - minV).coerceAtLeast(1e-6f)

        fun xOf(i: Int): Float = i.toFloat() / (data.size - 1) * w
        fun yOf(v: Float): Float = h - ((v - minV) / range * h)

        // --- Filled area under the line ---
        val fillPath = Path().apply {
            moveTo(xOf(0), h)                       // bottom-left anchor
            lineTo(xOf(0), yOf(data[0]))             // up to first value
            for (i in 1 until data.size) {
                lineTo(xOf(i), yOf(data[i]))
            }
            lineTo(xOf(data.size - 1), h)            // down to bottom-right
            close()
        }
        drawPath(fillPath, fillColor)

        // --- Line ---
        val linePath = Path().apply {
            moveTo(xOf(0), yOf(data[0]))
            for (i in 1 until data.size) {
                lineTo(xOf(i), yOf(data[i]))
            }
        }
        drawPath(linePath, lineColor, style = Stroke(width = strokeWidth.toPx()))
    }
}

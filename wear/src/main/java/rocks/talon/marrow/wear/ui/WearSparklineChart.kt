package rocks.talon.marrow.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme

/**
 * Compact sparkline chart for Wear OS — a filled polyline showing recent value history.
 *
 * Identical in logic to the phone module's SparklineChart composable but using
 * [androidx.wear.compose.material3.MaterialTheme] for the default colour so it
 * integrates with the Wear watch-face palette.
 *
 * - Requires at least 2 data points; renders nothing for empty or singleton lists.
 * - Y axis: [data].min() maps to the bottom, [data].max() to the top — auto-scaled.
 * - Draws a filled translucent area under a 2dp line.
 *
 * @param data     Ordered history values (oldest first, newest last).
 * @param modifier Defaults to `fillMaxWidth().height(24.dp)` — shorter than phone to
 *                 fit the compact Wear card layout.
 * @param color    Line and fill colour; fill opacity is fixed at 15%.
 * @param strokeWidth Width of the chart line in dp.
 */
@Composable
fun WearSparklineChart(
    data: List<Float>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(24.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 1.5.dp,
) {
    if (data.size < 2) return

    val lineColor = color
    val fillColor = color.copy(alpha = 0.15f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minV = data.min()
        val maxV = data.max()
        val range = (maxV - minV).coerceAtLeast(1e-6f)

        fun xOf(i: Int): Float = i.toFloat() / (data.size - 1) * w
        fun yOf(v: Float): Float = h - ((v - minV) / range * h)

        val fillPath = Path().apply {
            moveTo(xOf(0), h)
            lineTo(xOf(0), yOf(data[0]))
            for (i in 1 until data.size) {
                lineTo(xOf(i), yOf(data[i]))
            }
            lineTo(xOf(data.size - 1), h)
            close()
        }
        drawPath(fillPath, fillColor)

        val linePath = Path().apply {
            moveTo(xOf(0), yOf(data[0]))
            for (i in 1 until data.size) {
                lineTo(xOf(i), yOf(data[i]))
            }
        }
        drawPath(linePath, lineColor, style = Stroke(width = strokeWidth.toPx()))
    }
}

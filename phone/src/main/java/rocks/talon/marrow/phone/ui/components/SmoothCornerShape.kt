package rocks.talon.marrow.phone.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * A squircle (superellipse) shape with smooth corners.
 *
 * Drop-in replacement for `racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape`,
 * which is no longer available in any public Maven repository.
 *
 * Algorithm: each corner uses a single cubic bezier with an extended anchor distance.
 * - anchor = (1 + s) * r from the corner along each edge
 * - handle = 0.5523r + s*r (standard circle approximation + smoothness extension)
 *
 * At s=0 (smoothnessPercent=0): identical to [androidx.compose.foundation.shape.RoundedCornerShape].
 * At s=0.6 (smoothnessPercent=60, Marrow's value): smooth PixelPlayer-style squircle.
 * At s=1.0 (smoothnessPercent=100): full iOS-style superellipse.
 *
 * @param cornerRadius Absolute corner radius in dp.
 * @param smoothnessPercent Smoothness 0–100. Marrow uses 60 everywhere.
 */
class AbsoluteSmoothCornerShape(
    private val cornerRadius: Dp,
    private val smoothnessPercent: Int,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }
            .coerceAtMost(min(size.width, size.height) / 2f)
        val s = smoothnessPercent.coerceIn(0, 100) / 100f
        return Outline.Generic(buildPath(size.width, size.height, r, s))
    }

    private fun buildPath(w: Float, h: Float, r: Float, s: Float): Path {
        // Distance from corner where the smooth curve begins (and ends).
        // At s=0 this is r (standard rounded corner start).
        // At s=1 this is 2r (curve begins twice as far from corner → stronger squircle).
        val anchor = r * (1f + s)

        // Bezier handle length. Standard quarter-circle uses 0.5523r.
        // Adding s*r extends the handles, keeping control points near the standard
        // circle position relative to the anchor while the anchor itself moves out.
        val handle = 0.5523f * r + s * r

        return Path().apply {
            moveTo(anchor, 0f)
            // Top edge
            lineTo(w - anchor, 0f)
            // Top-right corner
            cubicTo(w - anchor + handle, 0f, w, anchor - handle, w, anchor)
            // Right edge
            lineTo(w, h - anchor)
            // Bottom-right corner
            cubicTo(w, h - anchor + handle, w - anchor + handle, h, w - anchor, h)
            // Bottom edge
            lineTo(anchor, h)
            // Bottom-left corner
            cubicTo(anchor - handle, h, 0f, h - anchor + handle, 0f, h - anchor)
            // Left edge
            lineTo(0f, anchor)
            // Top-left corner
            cubicTo(0f, anchor - handle, anchor - handle, 0f, anchor, 0f)
            close()
        }
    }
}

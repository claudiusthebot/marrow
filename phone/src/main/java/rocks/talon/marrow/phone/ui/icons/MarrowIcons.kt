package rocks.talon.marrow.phone.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import rocks.talon.marrow.shared.Sections

/**
 * Marrow's hand-drawn glyph set.
 *
 * Every icon is a 24×24 [ImageVector] built up from primitive `path`s using
 * either a stroke (for the line-art aesthetic) or a solid fill — never a mix
 * of `Material Icons.*`. This is the visual signature of v0.2.0: each section
 * gets its own bespoke shape, designed to read as a family.
 *
 * Stroke style: 1.75dp, round caps, round joins. The stroke colour is bound to
 * `currentColor` on the receiving `Icon`/`Box`, so tinting works correctly.
 */

private const val STROKE_WIDTH = 1.75f

private fun marrow(
    name: String,
    block: ImageVector.Builder.() -> ImageVector.Builder,
): ImageVector =
    ImageVector.Builder(
        name = "Marrow.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).run(block).build()

private fun ImageVector.Builder.stroke(
    pathData: String,
    fill: Color? = null,
    width: Float = STROKE_WIDTH,
): ImageVector.Builder = path(
    fill = fill?.let { SolidColor(it) },
    stroke = SolidColor(Color.Black),  // overridden by tint when rendered
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathFillType = PathFillType.NonZero,
    pathBuilder = { addPath(pathData) },
)

private fun ImageVector.Builder.fill(pathData: String): ImageVector.Builder = path(
    fill = SolidColor(Color.Black),  // overridden by tint when rendered
    pathBuilder = { addPath(pathData) },
)

/** Hand-rolled SVG-path parser. We only emit a small dialect (M, L, C, Q, A, Z;
 *  uppercase + lowercase; whitespace + commas as separators) and parse it
 *  ourselves so the icons can stay as pure Kotlin string constants — no XML. */
private fun androidx.compose.ui.graphics.vector.PathBuilder.addPath(d: String) {
    val tokens = mutableListOf<String>()
    var i = 0
    val n = d.length
    val sb = StringBuilder()
    while (i < n) {
        val c = d[i]
        when {
            c.isLetter() -> {
                if (sb.isNotEmpty()) { tokens += sb.toString(); sb.clear() }
                tokens += c.toString()
            }
            c == ',' || c == ' ' || c == '\n' || c == '\t' -> {
                if (sb.isNotEmpty()) { tokens += sb.toString(); sb.clear() }
            }
            c == '-' && sb.isNotEmpty() && sb.last() != 'e' && sb.last() != 'E' -> {
                tokens += sb.toString(); sb.clear(); sb.append(c)
            }
            else -> sb.append(c)
        }
        i++
    }
    if (sb.isNotEmpty()) tokens += sb.toString()

    var idx = 0
    fun num(): Float = tokens[idx++].toFloat()
    while (idx < tokens.size) {
        val cmd = tokens[idx++]
        when (cmd) {
            "M" -> moveTo(num(), num())
            "m" -> moveToRelative(num(), num())
            "L" -> lineTo(num(), num())
            "l" -> lineToRelative(num(), num())
            "H" -> horizontalLineTo(num())
            "h" -> horizontalLineToRelative(num())
            "V" -> verticalLineTo(num())
            "v" -> verticalLineToRelative(num())
            "C" -> curveTo(num(), num(), num(), num(), num(), num())
            "c" -> curveToRelative(num(), num(), num(), num(), num(), num())
            "Q" -> quadTo(num(), num(), num(), num())
            "q" -> quadToRelative(num(), num(), num(), num())
            "A" -> arcTo(num(), num(), num(), num() != 0f, num() != 0f, num(), num())
            "a" -> arcToRelative(num(), num(), num(), num() != 0f, num() != 0f, num(), num())
            "Z", "z" -> close()
            else -> error("Unsupported path command '$cmd'")
        }
    }
}

object MarrowIcons {

    /** Stylised phone outline + screen dot — the Device section. */
    val Device: ImageVector = marrow("Device") {
        stroke("M7 2.5 H17 A2 2 0 0 1 19 4.5 V19.5 A2 2 0 0 1 17 21.5 H7 A2 2 0 0 1 5 19.5 V4.5 A2 2 0 0 1 7 2.5 Z")
            .stroke("M10 18.5 H14")
            .stroke("M9 5 H15")
    }

    /** Concentric arcs (system layers / kernel rings). */
    val System: ImageVector = marrow("System") {
        stroke("M12 4.5 a7.5 7.5 0 1 0 0 15 a7.5 7.5 0 1 0 0 -15 Z")
            .stroke("M12 7.5 a4.5 4.5 0 1 0 0 9 a4.5 4.5 0 1 0 0 -9 Z")
            .stroke("M12 11 a1 1 0 1 0 0 2 a1 1 0 1 0 0 -2 Z")
    }

    /** Marrow battery: rounded body + bolt. */
    val Battery: ImageVector = marrow("Battery") {
        stroke("M5 7 H17 A2 2 0 0 1 19 9 V18 A2 2 0 0 1 17 20 H5 A2 2 0 0 1 3 18 V9 A2 2 0 0 1 5 7 Z")
            .stroke("M20 11 V16")
            .stroke("M9 5 V8")
            .stroke("M15 5 V8")
            .stroke("M11 10 L8 14.5 H12 L10.5 17.5")
    }

    /** CPU: chip with pins on each side. */
    val Cpu: ImageVector = marrow("Cpu") {
        stroke("M7 7 H17 V17 H7 Z")
            .stroke("M9.5 9.5 H14.5 V14.5 H9.5 Z")
            .stroke("M9 4 V7")
            .stroke("M12 4 V7")
            .stroke("M15 4 V7")
            .stroke("M9 17 V20")
            .stroke("M12 17 V20")
            .stroke("M15 17 V20")
            .stroke("M4 9 H7")
            .stroke("M4 12 H7")
            .stroke("M4 15 H7")
            .stroke("M17 9 H20")
            .stroke("M17 12 H20")
            .stroke("M17 15 H20")
    }

    /** Memory: stacked DIMMs. */
    val Memory: ImageVector = marrow("Memory") {
        stroke("M3.5 7.5 H20.5 V11 H3.5 Z")
            .stroke("M3.5 13 H20.5 V16.5 H3.5 Z")
            .stroke("M6 7.5 V5.5")
            .stroke("M9 7.5 V5.5")
            .stroke("M12 7.5 V5.5")
            .stroke("M15 7.5 V5.5")
            .stroke("M18 7.5 V5.5")
            .stroke("M6 18.5 V16.5")
            .stroke("M9 18.5 V16.5")
            .stroke("M12 18.5 V16.5")
            .stroke("M15 18.5 V16.5")
            .stroke("M18 18.5 V16.5")
    }

    /** Storage: stacked discs from above. */
    val Storage: ImageVector = marrow("Storage") {
        stroke("M4 7 a8 2 0 1 0 16 0 a8 2 0 1 0 -16 0 Z")
            .stroke("M4 7 V12 a8 2 0 0 0 16 0 V7")
            .stroke("M4 12 V17 a8 2 0 0 0 16 0 V12")
    }

    /** Display: rounded screen with corners. */
    val Display: ImageVector = marrow("Display") {
        stroke("M3.5 5.5 H20.5 A1.5 1.5 0 0 1 22 7 V16 A1.5 1.5 0 0 1 20.5 17.5 H3.5 A1.5 1.5 0 0 1 2 16 V7 A1.5 1.5 0 0 1 3.5 5.5 Z")
            .stroke("M9 21 H15")
            .stroke("M12 17.5 V21")
    }

    /** Network: three Wi-Fi-style arcs. */
    val Network: ImageVector = marrow("Network") {
        stroke("M3 9 a13 13 0 0 1 18 0")
            .stroke("M6 12.5 a9 9 0 0 1 12 0")
            .stroke("M9 16 a5 5 0 0 1 6 0")
            .stroke("M12 19 a0.5 0.5 0 1 0 0 1 a0.5 0.5 0 1 0 0 -1 Z", fill = Color.Black)
    }

    /** Sensors: radial waves. */
    val Sensors: ImageVector = marrow("Sensors") {
        stroke("M12 11 a1 1 0 1 0 0 2 a1 1 0 1 0 0 -2 Z", fill = Color.Black)
            .stroke("M9 9 a3 3 0 0 0 0 6")
            .stroke("M15 9 a3 3 0 0 1 0 6")
            .stroke("M6.5 6.5 a6.5 6.5 0 0 0 0 11")
            .stroke("M17.5 6.5 a6.5 6.5 0 0 1 0 11")
    }

    /** Cameras: lens with iris. */
    val Cameras: ImageVector = marrow("Cameras") {
        stroke("M3 7 H8 L9.5 5 H14.5 L16 7 H21 A1 1 0 0 1 22 8 V18 A1 1 0 0 1 21 19 H3 A1 1 0 0 1 2 18 V8 A1 1 0 0 1 3 7 Z")
            .stroke("M12 9 a4 4 0 1 0 0 8 a4 4 0 1 0 0 -8 Z")
            .stroke("M12 11 a2 2 0 1 0 0 4 a2 2 0 1 0 0 -2 Z")
    }

    /** Build flags: shield with check. */
    val BuildFlags: ImageVector = marrow("BuildFlags") {
        stroke("M12 3 L19.5 5.5 V12 a8 8 0 0 1 -7.5 8 a8 8 0 0 1 -7.5 -8 V5.5 Z")
            .stroke("M9 12 L11 14 L15 9.5")
    }

    /** Software: layered code brackets. */
    val Software: ImageVector = marrow("Software") {
        stroke("M9 7 L4 12 L9 17")
            .stroke("M15 7 L20 12 L15 17")
            .stroke("M13.5 5 L10.5 19")
    }

    /**
     * GPU: a chip die with raised pads on top/bottom (GPU-die aesthetic).
     * The rectangular body has evenly-spaced notched pads on the long edges,
     * representing the VRAM/power pads visible on discrete and integrated dies.
     */
    val Gpu: ImageVector = marrow("Gpu") {
        // Die body
        stroke("M5 7 H19 V17 H5 Z")
            // Top pads
            .stroke("M8 7 V4")
            .stroke("M12 7 V4")
            .stroke("M16 7 V4")
            // Bottom pads
            .stroke("M8 17 V20")
            .stroke("M12 17 V20")
            .stroke("M16 17 V20")
            // Inner core grid (3×2 cells suggesting shader clusters)
            .stroke("M8 10 H16")
            .stroke("M8 14 H16")
            .stroke("M11 10 V14")
            .stroke("M14 10 V14")
    }

    /** Brand mark — small "M" tilted, used in the wordmark. */
    val Wordmark: ImageVector = marrow("Wordmark") {
        stroke("M4 19 V5 L12 13 L20 5 V19", width = 2.25f)
    }

    /** Watch glyph for the Watch tab. */
    val Watch: ImageVector = marrow("Watch") {
        stroke("M8 4 H16 L15 7.5 H9 Z")
            .stroke("M9 16.5 H15 L16 20 H8 Z")
            .stroke("M5 9.5 H19 V14.5 H5 Z")
            .stroke("M19 11 H21 V13 H19")
            .stroke("M12 11 V12.5 L13.5 13.5")
    }

    fun forSection(id: String): ImageVector = when (id) {
        Sections.DEVICE -> Device
        Sections.SYSTEM -> System
        Sections.BATTERY -> Battery
        Sections.CPU -> Cpu
        Sections.MEMORY -> Memory
        Sections.STORAGE -> Storage
        Sections.DISPLAY -> Display
        Sections.NETWORK -> Network
        Sections.SENSORS -> Sensors
        Sections.CAMERAS -> Cameras
        Sections.BUILD_FLAGS -> BuildFlags
        Sections.SOFTWARE -> Software
        Sections.GPU -> Gpu
        else -> Device
    }
}


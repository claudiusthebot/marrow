package rocks.talon.marrow.wear.ui

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
 * Watch-side glyph set. Same family as the phone's `MarrowIcons` (line-art,
 * 24×24 viewport, round caps + joins) but trimmed for Wear sizing. Strokes are
 * slightly heavier (2.0dp) so they read clearly on a small screen at 200dpi.
 */
private const val STROKE = 2.0f

private fun marrow(name: String, block: ImageVector.Builder.() -> ImageVector.Builder): ImageVector =
    ImageVector.Builder(
        name = "MarrowWear.$name",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).run(block).build()

private fun ImageVector.Builder.s(d: String, fill: Boolean = false): ImageVector.Builder = path(
    fill = if (fill) SolidColor(Color.Black) else null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = STROKE,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathFillType = PathFillType.NonZero,
    pathBuilder = { addPathString(d) },
)

private fun androidx.compose.ui.graphics.vector.PathBuilder.addPathString(d: String) {
    val tokens = mutableListOf<String>()
    val sb = StringBuilder()
    for (c in d) {
        when {
            c.isLetter() -> {
                if (sb.isNotEmpty()) { tokens += sb.toString(); sb.clear() }
                tokens += c.toString()
            }
            c == ',' || c.isWhitespace() -> {
                if (sb.isNotEmpty()) { tokens += sb.toString(); sb.clear() }
            }
            c == '-' && sb.isNotEmpty() && sb.last() != 'e' && sb.last() != 'E' -> {
                tokens += sb.toString(); sb.clear(); sb.append(c)
            }
            else -> sb.append(c)
        }
    }
    if (sb.isNotEmpty()) tokens += sb.toString()
    var idx = 0
    fun n(): Float = tokens[idx++].toFloat()
    while (idx < tokens.size) {
        when (tokens[idx++]) {
            "M" -> moveTo(n(), n())
            "L" -> lineTo(n(), n())
            "H" -> horizontalLineTo(n())
            "V" -> verticalLineTo(n())
            "A" -> arcTo(n(), n(), n(), n() != 0f, n() != 0f, n(), n())
            "C" -> curveTo(n(), n(), n(), n(), n(), n())
            "Q" -> quadTo(n(), n(), n(), n())
            "Z", "z" -> close()
        }
    }
}

object WearIcons {
    val Device = marrow("Device") {
        s("M7 2.5 H17 A2 2 0 0 1 19 4.5 V19.5 A2 2 0 0 1 17 21.5 H7 A2 2 0 0 1 5 19.5 V4.5 A2 2 0 0 1 7 2.5 Z")
            .s("M10 18.5 H14")
    }
    val System = marrow("System") {
        s("M12 4.5 a7.5 7.5 0 1 0 0 15 a7.5 7.5 0 1 0 0 -15 Z")
            .s("M12 7.5 a4.5 4.5 0 1 0 0 9 a4.5 4.5 0 1 0 0 -9 Z")
    }
    val Battery = marrow("Battery") {
        s("M5 7 H17 A2 2 0 0 1 19 9 V18 A2 2 0 0 1 17 20 H5 A2 2 0 0 1 3 18 V9 A2 2 0 0 1 5 7 Z")
            .s("M20 11 V16")
            .s("M11 10 L8 14.5 H12 L10.5 17.5")
    }
    val Cpu = marrow("Cpu") {
        s("M7 7 H17 V17 H7 Z")
            .s("M9.5 9.5 H14.5 V14.5 H9.5 Z")
            .s("M9 4 V7").s("M12 4 V7").s("M15 4 V7")
            .s("M9 17 V20").s("M12 17 V20").s("M15 17 V20")
    }
    val Memory = marrow("Memory") {
        s("M3.5 7.5 H20.5 V11 H3.5 Z")
            .s("M3.5 13 H20.5 V16.5 H3.5 Z")
    }
    val Storage = marrow("Storage") {
        s("M4 7 a8 2 0 1 0 16 0 a8 2 0 1 0 -16 0 Z")
            .s("M4 7 V12 a8 2 0 0 0 16 0 V7")
            .s("M4 12 V17 a8 2 0 0 0 16 0 V12")
    }
    val Display = marrow("Display") {
        s("M3.5 5.5 H20.5 A1.5 1.5 0 0 1 22 7 V16 A1.5 1.5 0 0 1 20.5 17.5 H3.5 A1.5 1.5 0 0 1 2 16 V7 A1.5 1.5 0 0 1 3.5 5.5 Z")
            .s("M9 21 H15")
    }
    val Network = marrow("Network") {
        s("M3 9 a13 13 0 0 1 18 0")
            .s("M6 12.5 a9 9 0 0 1 12 0")
            .s("M9 16 a5 5 0 0 1 6 0")
    }
    val Sensors = marrow("Sensors") {
        s("M9 9 a3 3 0 0 0 0 6")
            .s("M15 9 a3 3 0 0 1 0 6")
            .s("M6.5 6.5 a6.5 6.5 0 0 0 0 11")
            .s("M17.5 6.5 a6.5 6.5 0 0 1 0 11")
    }
    val Cameras = marrow("Cameras") {
        s("M3 7 H8 L9.5 5 H14.5 L16 7 H21 V18 H3 Z")
            .s("M12 9 a4 4 0 1 0 0 8 a4 4 0 1 0 0 -8 Z")
    }
    val BuildFlags = marrow("BuildFlags") {
        s("M12 3 L19.5 5.5 V12 a8 8 0 0 1 -7.5 8 a8 8 0 0 1 -7.5 -8 V5.5 Z")
            .s("M9 12 L11 14 L15 9.5")
    }
    val Software = marrow("Software") {
        s("M9 7 L4 12 L9 17")
            .s("M15 7 L20 12 L15 17")
            .s("M13.5 5 L10.5 19")
    }
    val Refresh = marrow("Refresh") {
        s("M19 12 a7 7 0 1 1 -2.05 -4.95")
            .s("M19 4.5 V8 H15.5")
    }
    val Phone = marrow("Phone") {
        s("M7 2.5 H17 A2 2 0 0 1 19 4.5 V19.5 A2 2 0 0 1 17 21.5 H7 A2 2 0 0 1 5 19.5 V4.5 A2 2 0 0 1 7 2.5 Z")
            .s("M10 18.5 H14")
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
        else -> Device
    }
}

package rocks.talon.marrow.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme

/**
 * Talon-brand Wear M3 colour scheme.
 *
 * Mirrors the structure of the official Wear OS Composables sample (`wearColorScheme`
 * in `ComposeStarter`) — every slot is set so the M3 transform / blur surfaces have
 * the right tones — but tinted toward the talon-agent orange (#FF6B35).
 *
 * Keeping `surfaceContainer*` close to a deep neutral so cards read cleanly on the
 * black AOD background of the Pixel Watch.
 */
internal val MarrowWearColors: ColorScheme =
    ColorScheme(
        primary = Color(0xFFFFB68F),
        onPrimary = Color(0xFF552100),
        primaryContainer = Color(0xFF7A3411),
        onPrimaryContainer = Color(0xFFFFDBC9),
        primaryDim = Color(0xFF7A3411),
        secondary = Color(0xFFE7BDAA),
        onSecondary = Color(0xFF44291C),
        secondaryContainer = Color(0xFF5E3F31),
        onSecondaryContainer = Color(0xFFFFDBC9),
        secondaryDim = Color(0xFF5E3F31),
        tertiary = Color(0xFFD7C58F),
        onTertiary = Color(0xFF3A2F08),
        tertiaryContainer = Color(0xFF52461D),
        onTertiaryContainer = Color(0xFFF5E1A8),
        tertiaryDim = Color(0xFF52461D),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF000000),
        onBackground = Color(0xFFEDE0DA),
        onSurface = Color(0xFFEDE0DA),
        onSurfaceVariant = Color(0xFFD7C2B8),
        outline = Color(0xFFA08C82),
        outlineVariant = Color(0xFF53433B),
        surfaceContainerLow = Color(0xFF1B1410),
        surfaceContainer = Color(0xFF221915),
        surfaceContainerHigh = Color(0xFF2D231D),
    )

/**
 * App-wide theme. The Pixel Watch / Wear OS 5+ honours `MotionScheme.expressive()`
 * to enable the spring-y M3 transitions; on older Wear OS 4 devices it degrades to
 * the standard scheme automatically.
 */
@Composable
fun MarrowWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MarrowWearColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

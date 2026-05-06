package rocks.talon.marrow.phone.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Marrow orange — same accent the talon-agent.github.io site uses. */
val MarrowOrange = Color(0xFFFF6B35)

private val FallbackDark = darkColorScheme(
    primary = MarrowOrange,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A1A0F),
    onPrimaryContainer = Color(0xFFFFCFB8),
    secondary = Color(0xFFE0A088),
    background = Color(0xFF0F0F12),
    surface = Color(0xFF1A1A1F),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF252530),
)

private val FallbackLight = lightColorScheme(
    primary = MarrowOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0D0),
    onPrimaryContainer = Color(0xFF3A1A0F),
)

/** Roboto Flex / Inter ship with Compose 1.4+ — leaning on the M3 default
 *  typography is enough for the wordmark + body rows. */
private val MarrowTypography: Typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.5.sp),
)

@Composable
fun MarrowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val canDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        canDynamic && darkTheme -> dynamicDarkColorScheme(ctx)
        canDynamic && !darkTheme -> dynamicLightColorScheme(ctx)
        darkTheme -> FallbackDark
        else -> FallbackLight
    }
    MaterialTheme(colorScheme = scheme, typography = MarrowTypography, content = content)
}

package rocks.talon.marrow.phone.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import rocks.talon.marrow.phone.prefs.ThemeMode

/** Brand orange — same accent the talon-agent.github.io site and Hydrate use. */
val MarrowOrange = Color(0xFFFF6B35)

private val BrandDark = darkColorScheme(
    primary = MarrowOrange,
    onPrimary = Color(0xFF260C00),
    primaryContainer = Color(0xFF6E2900),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFFFB68F),
    onSecondary = Color(0xFF552100),
    secondaryContainer = Color(0xFF7A3411),
    onSecondaryContainer = Color(0xFFFFDBC9),
    tertiary = Color(0xFFD7C58F),
    onTertiary = Color(0xFF3A2F08),
    tertiaryContainer = Color(0xFF52461D),
    onTertiaryContainer = Color(0xFFF5E1A8),
    background = Color(0xFF14100E),
    onBackground = Color(0xFFEDE0DA),
    surface = Color(0xFF14100E),
    onSurface = Color(0xFFEDE0DA),
    surfaceVariant = Color(0xFF352C28),
    onSurfaceVariant = Color(0xFFD7C2B8),
    surfaceContainerLowest = Color(0xFF0E0B0A),
    surfaceContainerLow = Color(0xFF1B1714),
    surfaceContainer = Color(0xFF221C18),
    surfaceContainerHigh = Color(0xFF2D241F),
    surfaceContainerHighest = Color(0xFF382E28),
    outline = Color(0xFFA08C82),
    outlineVariant = Color(0xFF53433B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val BrandLight = lightColorScheme(
    primary = MarrowOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF360F00),
    secondary = Color(0xFF77574A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBC9),
    onSecondaryContainer = Color(0xFF2C160B),
    tertiary = Color(0xFF6B5D2F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E1A8),
    onTertiaryContainer = Color(0xFF221B00),
    background = Color(0xFFFFFBF8),
    onBackground = Color(0xFF221A16),
    surface = Color(0xFFFFFBF8),
    onSurface = Color(0xFF221A16),
    surfaceVariant = Color(0xFFF5DED4),
    onSurfaceVariant = Color(0xFF53443D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFCEAE0),
    surfaceContainerHigh = Color(0xFFF6E2D8),
    surfaceContainerHighest = Color(0xFFF0DCD2),
    outline = Color(0xFF85746B),
    outlineVariant = Color(0xFFD8C2B8),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/**
 * Marrow's typography. Uses the Compose default sans-serif (Roboto Flex on
 * Android 13+) — the family ships with the OS so we don't need to bundle a
 * font asset. Hierarchy is tuned for the new Display Large hero and the
 * monospace Label Small for fingerprints/IPs.
 */
/**
 * Marrow typography — modelled on PixelPlayer's Google-Sans-Rounded scale.
 * SemiBold/Bold for headlines, no aggressive negative letter-spacing. Display
 * sizes capped at 48sp so the hero reads as confident without shouting.
 */
private val MarrowTypography: Typography = run {
    val sans = FontFamily.SansSerif
    val mono = FontFamily.Monospace
    Typography(
        displayLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = 0.sp),
        displayMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
        displaySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = 0.sp),
        headlineLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
        headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
        headlineSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
        titleLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
        titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        titleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
        bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    )
}

@Composable
fun MarrowTheme(
    themeMode: ThemeMode = ThemeMode.DYNAMIC,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val canDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when (themeMode) {
        ThemeMode.SYSTEM -> if (darkTheme) BrandDark else BrandLight
        ThemeMode.BRAND -> if (darkTheme) BrandDark else BrandLight
        ThemeMode.DYNAMIC -> when {
            canDynamic && darkTheme -> dynamicDarkColorScheme(ctx)
            canDynamic && !darkTheme -> dynamicLightColorScheme(ctx)
            darkTheme -> BrandDark
            else -> BrandLight
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = MarrowTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

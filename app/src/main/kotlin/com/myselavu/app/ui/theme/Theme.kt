package com.myselavu.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette: deep teal + warm saffron on soft warm neutrals. Dark mode is truly dark.
private object Palette {
    val Teal900 = Color(0xFF053B3A)
    val Teal800 = Color(0xFF0A4F4D)
    val Teal700 = Color(0xFF0E6B68)
    val Teal200 = Color(0xFF7FD8D1)
    val Teal100 = Color(0xFFC9EEEA)
    val Saffron700 = Color(0xFF8A4F00)
    val Saffron500 = Color(0xFFF4A62A)
    val Saffron300 = Color(0xFFFFC266)
    val Saffron100 = Color(0xFFFFE3BC)
    val Sand50 = Color(0xFFFBF8F3)
    val Sand100 = Color(0xFFF4EFE7)
    val Sand200 = Color(0xFFEBE4D9)
    val Sand300 = Color(0xFFE2DACD)
    val Ink = Color(0xFF1B1C1A)
    val InkSoft = Color(0xFF4A4843)
    val Night0 = Color(0xFF000000)
    val Night1 = Color(0xFF0A0D0D)
    val Night2 = Color(0xFF111615)
    val Night3 = Color(0xFF171D1C)
    val Night4 = Color(0xFF1E2524)
    val Night5 = Color(0xFF262E2D)
}

private val LightColors = lightColorScheme(
    primary = Palette.Teal700,
    onPrimary = Color.White,
    primaryContainer = Palette.Teal100,
    onPrimaryContainer = Palette.Teal900,
    secondary = Palette.Saffron700,
    onSecondary = Color.White,
    secondaryContainer = Palette.Saffron100,
    onSecondaryContainer = Color(0xFF2C1700),
    tertiary = Palette.Teal800,
    onTertiary = Color.White,
    background = Palette.Sand50,
    onBackground = Palette.Ink,
    surface = Palette.Sand50,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Sand200,
    onSurfaceVariant = Palette.InkSoft,
    surfaceTint = Palette.Teal700,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Palette.Sand100,
    surfaceContainerHigh = Palette.Sand200,
    surfaceContainerHighest = Palette.Sand300,
    surfaceBright = Palette.Sand50,
    surfaceDim = Palette.Sand300,
    outline = Color(0xFF7B776F),
    outlineVariant = Color(0xFFD3CCC0),
    error = Color(0xFFB3261E),
    onError = Color.White,
    inverseSurface = Color(0xFF30302D),
    inverseOnSurface = Color(0xFFF3F0EA),
    inversePrimary = Palette.Teal200,
)

private val DarkColors = darkColorScheme(
    primary = Palette.Teal200,
    onPrimary = Color(0xFF003734),
    primaryContainer = Palette.Teal800,
    onPrimaryContainer = Palette.Teal100,
    secondary = Palette.Saffron300,
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6A3C00),
    onSecondaryContainer = Palette.Saffron100,
    tertiary = Palette.Teal200,
    onTertiary = Color(0xFF003734),
    background = Palette.Night0,
    onBackground = Color(0xFFE8E4DC),
    surface = Palette.Night1,
    onSurface = Color(0xFFE8E4DC),
    surfaceVariant = Palette.Night4,
    onSurfaceVariant = Color(0xFFC4C0B8),
    surfaceTint = Palette.Teal200,
    surfaceContainerLowest = Palette.Night0,
    surfaceContainerLow = Palette.Night2,
    surfaceContainer = Palette.Night3,
    surfaceContainerHigh = Palette.Night4,
    surfaceContainerHighest = Palette.Night5,
    surfaceBright = Palette.Night5,
    surfaceDim = Palette.Night0,
    outline = Color(0xFF8E8A82),
    outlineVariant = Color(0xFF3A403F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    inverseSurface = Color(0xFFE8E4DC),
    inverseOnSurface = Color(0xFF1B1C1A),
    inversePrimary = Palette.Teal700,
)

/** Warning colours (always shown together with an icon and a sentence). */
@Immutable
data class StatusColors(
    val good: Color,
    val goodContainer: Color,
    val careful: Color,
    val carefulContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val accent: Color,
)

private val LightStatus = StatusColors(
    good = Color(0xFF2E7D32), goodContainer = Color(0xFFDDF0DE),
    careful = Color(0xFF9A5B00), carefulContainer = Color(0xFFFFE7C2),
    danger = Color(0xFFC62828), dangerContainer = Color(0xFFFCDAD7),
    accent = Palette.Saffron500,
)

private val DarkStatus = StatusColors(
    good = Color(0xFF81C995), goodContainer = Color(0xFF12301A),
    careful = Color(0xFFFFC266), carefulContainer = Color(0xFF3A2600),
    danger = Color(0xFFFF8A80), dangerContainer = Color(0xFF3D1210),
    accent = Palette.Saffron300,
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Big confident money numbers on the dashboard. */
val MoneyHero = TextStyle(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MySelavuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

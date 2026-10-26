package de.englischlernen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Fundbrueder-Markenfarben: Waldgruen (Expedition), Messing/Gold (Fundstuecke),
 * Erdbraun (Boden, in dem gesucht wird) auf warmem Pergament-Ton.
 */
object Brand {
    val ForestDark = Color(0xFF1F3D2B)
    val Forest = Color(0xFF2F5D40)
    val ForestLight = Color(0xFF8DBE9C)
    val Brass = Color(0xFFC9962B)
    val BrassLight = Color(0xFFF0CF7A)
    val Earth = Color(0xFF7A4E2D)
    val EarthLight = Color(0xFFE0B48F)
    val Parchment = Color(0xFFFBF6EA)
    val Night = Color(0xFF111712)
    val Success = Color(0xFF2E7D32)
    val Error = Color(0xFFC62828)
}

private val Light = lightColorScheme(
    primary = Brand.Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6E8CF),
    onPrimaryContainer = Brand.ForestDark,
    secondary = Brand.Brass,
    onSecondary = Color(0xFF2B1E00),
    secondaryContainer = Color(0xFFFBE7B3),
    onSecondaryContainer = Color(0xFF3B2A00),
    tertiary = Brand.Earth,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6DCC6),
    onTertiaryContainer = Color(0xFF3A2210),
    background = Brand.Parchment,
    onBackground = Color(0xFF1C1B17),
    surface = Brand.Parchment,
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFEDE5D3),
    onSurfaceVariant = Color(0xFF4C463A),
    surfaceContainer = Color(0xFFF4EDDD),
    surfaceContainerHigh = Color(0xFFEFE7D5),
    surfaceContainerLow = Color(0xFFF8F2E4),
    outline = Color(0xFF7E7667),
    error = Brand.Error,
)

private val Dark = darkColorScheme(
    primary = Brand.ForestLight,
    onPrimary = Color(0xFF00391C),
    primaryContainer = Brand.Forest,
    onPrimaryContainer = Color(0xFFC6E8CF),
    secondary = Brand.BrassLight,
    onSecondary = Color(0xFF3B2A00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFBE7B3),
    tertiary = Brand.EarthLight,
    onTertiary = Color(0xFF452B15),
    tertiaryContainer = Color(0xFF5E3C20),
    onTertiaryContainer = Color(0xFFF6DCC6),
    background = Brand.Night,
    onBackground = Color(0xFFE6E2D9),
    surface = Brand.Night,
    onSurface = Color(0xFFE6E2D9),
    surfaceVariant = Color(0xFF3F4A40),
    onSurfaceVariant = Color(0xFFC0C9BE),
    surfaceContainer = Color(0xFF1C231D),
    surfaceContainerHigh = Color(0xFF262E27),
    surfaceContainerLow = Color(0xFF171D18),
    outline = Color(0xFF8A9488),
    error = Color(0xFFFFB4AB),
)

private val base = Typography()
private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
)

@Composable
fun FundbruederTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}

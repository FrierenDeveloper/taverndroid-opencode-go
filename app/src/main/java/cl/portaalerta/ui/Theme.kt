package cl.portaalerta.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Verde de WhatsApp para los accesos directos de mensajería. */
val WhatsAppGreen = Color(0xFF0F7A4F)

private val Light = lightColorScheme(
    primary = Color(0xFF5B2A86),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDDFF),
    onPrimaryContainer = Color(0xFF24005B),
    secondary = Color(0xFF00796B),
    secondaryContainer = Color(0xFFE6DDF0),
    onSecondaryContainer = Color(0xFF3B3447),
    error = Color(0xFF9F1D14),
    errorContainer = Color(0xFFFDE6E3),
    onErrorContainer = Color(0xFF4A100C),
    background = Color(0xFFF7F2FC),
    onBackground = Color(0xFF1E1530),
    surface = Color(0xFFF7F2FC),
    onSurface = Color(0xFF1E1530),
    surfaceVariant = Color(0xFFE6DDF0),
    onSurfaceVariant = Color(0xFF655B73),
    outline = Color(0xFF8A7BA0),
    outlineVariant = Color(0xFFE0D6EC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD3BBFF),
    onPrimary = Color(0xFF3F0F6B),
    primaryContainer = Color(0xFF572A82),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFF4DB6AC),
    secondaryContainer = Color(0xFF3B3447),
    onSecondaryContainer = Color(0xFFE6DDF0),
    error = Color(0xFFFFB4A9),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF15101D),
    onBackground = Color(0xFFEDE6F5),
    surface = Color(0xFF15101D),
    onSurface = Color(0xFFEDE6F5),
    surfaceVariant = Color(0xFF2E2640),
    onSurfaceVariant = Color(0xFFC9BEDB),
    outline = Color(0xFF8A7BA0),
    outlineVariant = Color(0xFF3F3550),
    surfaceContainerLowest = Color(0xFF110C18),
    surfaceContainerLow = Color(0xFF1C1626),
    surfaceContainer = Color(0xFF211A2C),
    surfaceContainerHigh = Color(0xFF2A2236),
    surfaceContainerHighest = Color(0xFF2E2640),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val base = Typography()
private val AppTypography = base.copy(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
)

@Composable
fun PortaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

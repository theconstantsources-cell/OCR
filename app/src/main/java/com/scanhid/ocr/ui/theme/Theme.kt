package com.scanhid.ocr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// One consistent rounded-corner language app-wide instead of ad hoc corner radii per screen.
val ScanHidShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Brand palette matched to the client's own Suzuki red/blue instead of a generic
// invented accent, plus graphite neutrals for everything that isn't the logo itself.
val SuzukiRed = Color(0xFFE4002B)
val SuzukiRedLight = Color(0xFFEF5B6E)
val SuzukiBlue = Color(0xFF004C97)
val SuzukiBlueLight = Color(0xFF6E9FCE)
val ScanHidGraphiteDark = Color(0xFF131415)
val ScanHidSurfaceDark = Color(0xFF1C1E20)
val ScanHidGraphiteLight = Color(0xFFF1F2F0)
val ScanHidSurfaceLight = Color(0xFFFFFFFF)

private val ScanHidDarkColors = darkColorScheme(
    primary = SuzukiRedLight,
    onPrimary = Color(0xFF2A0A08),
    secondary = SuzukiBlueLight,
    onSecondary = Color(0xFF00192E),
    background = ScanHidGraphiteDark,
    onBackground = Color(0xFFECEDEC),
    surface = ScanHidSurfaceDark,
    onSurface = Color(0xFFECEDEC),
    surfaceVariant = Color(0xFF262A2D),
    onSurfaceVariant = Color(0xFFB8C0C6),
    outline = Color(0xFF474C4F),
)

private val ScanHidLightColors = lightColorScheme(
    primary = SuzukiRed,
    onPrimary = Color(0xFFFFFFFF),
    secondary = SuzukiBlue,
    onSecondary = Color(0xFFFFFFFF),
    background = ScanHidGraphiteLight,
    onBackground = Color(0xFF1B1D1F),
    surface = ScanHidSurfaceLight,
    onSurface = Color(0xFF1B1D1F),
    surfaceVariant = Color(0xFFE7E5DF),
    onSurfaceVariant = Color(0xFF4B5359),
    outline = Color(0xFFCBC9C3),
)

@Composable
fun ScanHidOcrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ScanHidDarkColors else ScanHidLightColors,
        shapes = ScanHidShapes,
        content = content,
    )
}

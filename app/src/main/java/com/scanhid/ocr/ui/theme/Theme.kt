package com.scanhid.ocr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Deliberate brand palette (no Material-You dynamic color): garage/pit-lane red as the
// working accent, graphite neutrals, and a hazard-amber reserved for "not connected yet"
// state so it never gets confused with the brand accent.
val ScanHidRed = Color(0xFFC1272D)
val ScanHidRedLight = Color(0xFFE05A52)
val ScanHidGraphiteDark = Color(0xFF131415)
val ScanHidSurfaceDark = Color(0xFF1C1E20)
val ScanHidGraphiteLight = Color(0xFFF1F2F0)
val ScanHidSurfaceLight = Color(0xFFFFFFFF)

private val ScanHidDarkColors = darkColorScheme(
    primary = ScanHidRedLight,
    onPrimary = Color(0xFF2A0A08),
    secondary = Color(0xFFB8C0C6),
    background = ScanHidGraphiteDark,
    onBackground = Color(0xFFECEDEC),
    surface = ScanHidSurfaceDark,
    onSurface = Color(0xFFECEDEC),
    surfaceVariant = Color(0xFF262A2D),
    onSurfaceVariant = Color(0xFFB8C0C6),
    outline = Color(0xFF474C4F),
)

private val ScanHidLightColors = lightColorScheme(
    primary = ScanHidRed,
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF4B5359),
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
        content = content,
    )
}

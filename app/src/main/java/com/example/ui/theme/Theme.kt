package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PresetFxDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = TextPrimary,
    primaryContainer = DeepVioletContainer,
    onPrimaryContainer = OnVioletContainer,
    secondary = CyberCyan,
    onSecondary = ObsidianBackground,
    secondaryContainer = DeepCyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = StudioAmber,
    onTertiary = ObsidianBackground,
    tertiaryContainer = DeepAmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceContainer,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurfaceLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = ObsidianSurfaceHighest,
    outline = OutlineStrong,
    outlineVariant = OutlineSubtle,
    error = StudioError,
    onError = TextPrimary,
    errorContainer = DeepErrorContainer,
    onErrorContainer = OnErrorContainer,
)

private val PresetFxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PresetFxTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PresetFxDarkColorScheme,
        typography = PresetFxTypography,
        shapes = PresetFxShapes,
        content = content,
    )
}

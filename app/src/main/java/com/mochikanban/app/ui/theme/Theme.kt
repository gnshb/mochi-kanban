package com.mochikanban.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MochiDarkScheme = darkColorScheme(
    primary = DarkTokens.MintDark,
    onPrimary = DarkTokens.Background,
    primaryContainer = DarkTokens.MintContainer,
    onPrimaryContainer = DarkTokens.Ink,
    secondary = DarkTokens.RoseDark,
    onSecondary = DarkTokens.Background,
    secondaryContainer = DarkTokens.RoseContainer,
    onSecondaryContainer = DarkTokens.Ink,
    tertiary = DarkTokens.SkyDark,
    onTertiary = DarkTokens.Background,
    tertiaryContainer = DarkTokens.SkyContainer,
    onTertiaryContainer = DarkTokens.Ink,
    background = DarkTokens.Background,
    onBackground = DarkTokens.Ink,
    surface = DarkTokens.Surface,
    onSurface = DarkTokens.Ink,
    surfaceVariant = DarkTokens.SurfaceVariant,
    onSurfaceVariant = DarkTokens.Muted,
    outline = DarkTokens.Outline,
    outlineVariant = DarkTokens.OutlineVariant,
    error = DarkTokens.Error,
)

@Composable
fun MochiKanbanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MochiDarkScheme,
        typography = MochiTypography,
        shapes = MochiShapes,
        content = content,
    )
}

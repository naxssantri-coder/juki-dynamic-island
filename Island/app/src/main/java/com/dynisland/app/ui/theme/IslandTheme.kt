package com.dynisland.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object IslandColors {
    val Surface = Color(0xFF0A0A0B)
    val Artwork = Color(0xFF1F1F22)
    val Secondary = Color(0xFF9A9AA0)
}

private val IslandDarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F7),
    onPrimary = Color(0xFF0A0A0B),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF0A0A0B),
    onSurface = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF9A9AA0),
    surfaceContainer = Color(0xFF151517),
    secondaryContainer = Color(0xFF2A2A2E),
    onSecondaryContainer = Color(0xFFF2F2F7),
)

@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IslandDarkColors, content = content)
}

package com.example.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 固定深色，复刻桌面版配色。不用动态取色，免得界面被系统主题染成紫色。
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4D6BFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B2230),
    onPrimaryContainer = Color(0xFFE6EDF7),
    secondary = Color(0xFF7B93FF),
    background = Color(0xFF0E1117),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF151A23),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1B2230),
    onSurfaceVariant = Color(0xFF8B98AB),
    outline = Color(0xFF242C3A),
    outlineVariant = Color(0xFF242C3A),
    error = Color(0xFFFF7B72)
)

@Composable
fun 随身AITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

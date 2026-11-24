package com.example.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.example.ai.ui.DarkPalette
import com.example.ai.ui.LightPalette
import com.example.ai.ui.LocalChatPalette

/**
 * 固定配色，不用动态取色，免得界面被系统主题染成紫色。
 * 两套方案都从 ChatPalette 派生，保证 Material 组件和自绘控件颜色一致。
 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = Color.White,
    primaryContainer = DarkPalette.panel2,
    onPrimaryContainer = DarkPalette.txt,
    secondary = DarkPalette.accent2,
    background = DarkPalette.bg,
    onBackground = DarkPalette.txt,
    surface = DarkPalette.panel,
    onSurface = DarkPalette.txt,
    surfaceVariant = DarkPalette.panel2,
    onSurfaceVariant = DarkPalette.dim,
    outline = DarkPalette.line,
    outlineVariant = DarkPalette.line,
    error = DarkPalette.err
)

private val LightColorScheme = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = Color.White,
    primaryContainer = LightPalette.panel2,
    onPrimaryContainer = LightPalette.txt,
    secondary = LightPalette.accent2,
    background = LightPalette.bg,
    onBackground = LightPalette.txt,
    surface = LightPalette.panel,
    onSurface = LightPalette.txt,
    surfaceVariant = LightPalette.panel2,
    onSurfaceVariant = LightPalette.dim,
    outline = LightPalette.line,
    outlineVariant = LightPalette.line,
    error = LightPalette.err
)

@Composable
fun 随身AITheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalChatPalette provides if (darkTheme) DarkPalette else LightPalette
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}

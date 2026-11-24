package com.example.ai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 一套完整的界面配色。深色版复刻桌面版观感，浅色版是同一套语义色的亮色映射，
 * 保证两套主题下每个位置都有明确对应，不出现"深色改了一半"的花图。
 */
@Immutable
data class ChatPalette(
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val line: Color,
    val txt: Color,
    val dim: Color,
    val accent: Color,
    val accent2: Color,
    val userBubble: Color,
    val userLine: Color,
    val err: Color,
    val errBg: Color,
    val errLine: Color,
    val ok: Color,
    val codeBg: Color,
    val codeFg: Color,
    val codeTxt: Color,
    val scrim: Color
)

/** 深色：原来的桌面版配色，数值不变 */
val DarkPalette = ChatPalette(
    bg = Color(0xFF0E1117),
    panel = Color(0xFF151A23),
    panel2 = Color(0xFF1B2230),
    line = Color(0xFF242C3A),
    txt = Color(0xFFE6EDF7),
    dim = Color(0xFF8B98AB),
    accent = Color(0xFF4D6BFE),
    accent2 = Color(0xFF7B93FF),
    userBubble = Color(0xFF22304D),
    userLine = Color(0xFF2F4166),
    err = Color(0xFFFF7B72),
    errBg = Color(0xFF2A1A1A),
    errLine = Color(0xFF5C2B2B),
    ok = Color(0xFF7EE2A8),
    codeBg = Color(0xFF0B0F16),
    codeFg = Color(0xFFFFC98A),
    codeTxt = Color(0xFFD6E2F5),
    scrim = Color(0xAA000000)
)

/** 浅色：白底 + 同一支品牌蓝，文字对比度按 WCAG AA 调过 */
val LightPalette = ChatPalette(
    bg = Color(0xFFF7F8FA),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFEFF2F6),
    line = Color(0xFFDFE4EA),
    txt = Color(0xFF1B2230),
    dim = Color(0xFF67707E),
    accent = Color(0xFF4D6BFE),
    accent2 = Color(0xFF3A56D4),
    userBubble = Color(0xFFE4EAFF),
    userLine = Color(0xFFC6D3FF),
    err = Color(0xFFD93025),
    errBg = Color(0xFFFDECEA),
    errLine = Color(0xFFF3C4C0),
    ok = Color(0xFF17864B),
    codeBg = Color(0xFFF2F4F7),
    codeFg = Color(0xFFB4460B),
    codeTxt = Color(0xFF22303F),
    scrim = Color(0x66000000)
)

/** 由 随身AITheme 提供，界面里别直接用，走下面的 ChatColors */
val LocalChatPalette = staticCompositionLocalOf { DarkPalette }

/**
 * 界面统一取色入口。属性都是 @Composable getter，
 * 主题切换时会自动重组，调用方写法保持不变。
 */
object ChatColors {
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.bg
    val Panel: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.panel
    val Panel2: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.panel2
    val Line: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.line
    val Txt: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.txt
    val Dim: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.dim
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.accent
    val Accent2: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.accent2
    val UserBubble: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.userBubble
    val UserLine: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.userLine
    val Err: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.err
    val ErrBg: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.errBg
    val ErrLine: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.errLine
    val Ok: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.ok
    val CodeBg: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.codeBg
    val CodeFg: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.codeFg
    val CodeTxt: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.codeTxt
    val Scrim: Color @Composable @ReadOnlyComposable get() = LocalChatPalette.current.scrim
}

object Dimens {
    val BubbleRadius = 16.dp
    val BubbleTail = 5.dp
    val ScreenPad = 14.dp
}

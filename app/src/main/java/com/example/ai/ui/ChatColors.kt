package com.example.ai.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 直接复刻桌面版的深色配色，保证两端观感一致 */
object ChatColors {
    val Bg = Color(0xFF0E1117)
    val Panel = Color(0xFF151A23)
    val Panel2 = Color(0xFF1B2230)
    val Line = Color(0xFF242C3A)
    val Txt = Color(0xFFE6EDF7)
    val Dim = Color(0xFF8B98AB)
    val Accent = Color(0xFF4D6BFE)
    val Accent2 = Color(0xFF7B93FF)
    val UserBubble = Color(0xFF22304D)
    val UserLine = Color(0xFF2F4166)
    val Err = Color(0xFFFF7B72)
    val ErrBg = Color(0xFF2A1A1A)
    val ErrLine = Color(0xFF5C2B2B)
    val Ok = Color(0xFF7EE2A8)
    val CodeBg = Color(0xFF0B0F16)
    val CodeFg = Color(0xFFFFC98A)
}

object Dimens {
    val BubbleRadius = 16.dp
    val BubbleTail = 5.dp
    val ScreenPad = 14.dp
}

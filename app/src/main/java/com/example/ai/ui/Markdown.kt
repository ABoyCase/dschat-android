package com.example.ai.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/** markdown 切块结果。跟桌面版那套极简规则对齐，不做完整 CommonMark。 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Bullets(val items: List<String>, val ordered: Boolean) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
}

private val FENCE = Regex("```([\\w+#.\\-]*)\\n?([\\s\\S]*?)```")

/** 按行扫描，把整段文本切成可渲染的块 */
fun parseMd(src: String): List<MdBlock> {
    if (src.isEmpty()) return emptyList()
    val out = ArrayList<MdBlock>()
    val codes = ArrayList<MdBlock.Code>()

    // 先把代码块抽出来占位，避免里面的 # - * 被当成语法
    val placeholder = src.replace(FENCE) { m ->
        val lang = m.groupValues[1]
        val code = m.groupValues[2].trimEnd('\n')
        codes.add(MdBlock.Code(lang, code))
        "\u0000B${codes.size - 1}\u0000"
    }

    var bullets = ArrayList<String>()
    var bulletsOrdered = false

    fun flushList() {
        if (bullets.isNotEmpty()) {
            out.add(MdBlock.Bullets(bullets.toList(), bulletsOrdered))
            bullets = ArrayList()
        }
    }

    for (raw in placeholder.split('\n')) {
        val line = raw
        val trimmed = line.trim()

        val boxed = Regex("^\u0000B(\\d+)\u0000$").find(trimmed)
        if (boxed != null) {
            flushList()
            codes.getOrNull(boxed.groupValues[1].toInt())?.let { out.add(it) }
            continue
        }

        if (trimmed.isEmpty()) {
            flushList()
            continue
        }

        val heading = Regex("^(#{1,6})\\s+(.*)$").find(line)
        if (heading != null) {
            flushList()
            out.add(MdBlock.Heading(heading.groupValues[1].length.coerceAtMost(3), heading.groupValues[2]))
            continue
        }

        if (trimmed.startsWith(">")) {
            flushList()
            out.add(MdBlock.Quote(line.replaceFirst(Regex("^\\s*>\\s?"), "")))
            continue
        }

        val ul = Regex("^\\s*[-*+]\\s+(.*)$").find(line)
        if (ul != null) {
            if (bullets.isNotEmpty() && bulletsOrdered) flushList()
            bulletsOrdered = false
            bullets.add(ul.groupValues[1])
            continue
        }

        val ol = Regex("^\\s*\\d+[.)]\\s+(.*)$").find(line)
        if (ol != null) {
            if (bullets.isNotEmpty() && !bulletsOrdered) flushList()
            bulletsOrdered = true
            bullets.add(ol.groupValues[1])
            continue
        }

        if (Regex("^(-{3,}|\\*{3,})$").matches(trimmed)) {
            flushList()
            continue
        }

        flushList()
        out.add(MdBlock.Para(line))
    }
    flushList()
    return out
}

private val INLINE = Regex(
    "\\*\\*(.+?)\\*\\*" +                    // **粗体**
        "|`([^`]+)`" +                        // `行内代码`
        "|\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)" +  // [文字](链接)
        "|(?<![*\\w])\\*([^*\\n]+?)\\*(?![*\\w])"      // *斜体*
)

/** 行内样式：粗体 / 行内代码 / 链接 / 斜体 */
fun inlineMd(
    src: String,
    codeBg: Color = Color(0xFF0B0F16),
    codeFg: Color = Color(0xFFFFC98A),
    linkColor: Color = Color(0xFF7B93FF)
): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in INLINE.findAll(src)) {
        if (m.range.first > last) append(src.substring(last, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(g[1]) }

            g[2].isNotEmpty() -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = codeFg,
                    background = codeBg,
                    fontSize = 14.sp
                )
            ) { append(g[2]) }

            g[3].isNotEmpty() && g[4].isNotEmpty() -> withLink(
                LinkAnnotation.Url(
                    g[4],
                    TextLinkStyles(
                        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    )
                )
            ) { append(g[3]) }

            g[5].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[5]) }

            else -> append(m.value)
        }
        last = m.range.last + 1
    }
    if (last < src.length) append(src.substring(last))
}

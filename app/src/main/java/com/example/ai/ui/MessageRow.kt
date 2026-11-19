package com.example.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.data.ChatMessage
import com.example.ai.data.Pricing
import com.example.ai.data.Source
import com.example.ai.data.Usage

private val BodyStyle = androidx.compose.ui.text.TextStyle(
    fontSize = 16.sp,
    lineHeight = 26.sp,
    color = ChatColors.Txt
)

@Composable
fun MessageRow(
    msg: ChatMessage,
    selectMode: Boolean = false,
    checked: Boolean = false,
    thinkingOpen: Boolean = false,
    onToggle: () -> Unit = {},
    onToggleThinking: () -> Unit = {},
    onEnterSelect: () -> Unit = {}
) {
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val maxW = screenW * 0.88f
    val isUser = msg.role == "user"
    val ctx = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (selectMode && !isUser) {
            CheckDot(checked = checked, onToggle = onToggle)
            Spacer(Modifier.width(8.dp))
        }

        val shape = if (isUser) {
            RoundedCornerShape(
                topStart = Dimens.BubbleRadius,
                topEnd = Dimens.BubbleRadius,
                bottomStart = Dimens.BubbleRadius,
                bottomEnd = Dimens.BubbleTail
            )
        } else {
            RoundedCornerShape(
                topStart = Dimens.BubbleRadius,
                topEnd = Dimens.BubbleRadius,
                bottomStart = Dimens.BubbleTail,
                bottomEnd = Dimens.BubbleRadius
            )
        }

        // 普通模式长按气泡进多选；多选模式点一下切换勾选
        val gesture = if (selectMode) {
            Modifier.clickable { onToggle() }
        } else {
            Modifier.pointerInput(msg.id) {
                detectTapGestures(onLongPress = { onEnterSelect() })
            }
        }

        Surface(
            modifier = Modifier
                .widthIn(max = maxW)
                .then(gesture),
            shape = shape,
            color = when {
                msg.error != null && !isUser -> ChatColors.ErrBg
                isUser -> ChatColors.UserBubble
                else -> ChatColors.Panel
            },
            border = BorderStroke(
                1.dp,
                when {
                    msg.error != null && !isUser -> ChatColors.ErrLine
                    isUser -> ChatColors.UserLine
                    else -> ChatColors.Line
                }
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {

                if (msg.thinking.isNotEmpty()) {
                    ThinkingBlock(
                        text = msg.thinking,
                        running = msg.streaming,
                        hasAnswer = msg.content.isNotEmpty(),
                        userOpen = thinkingOpen,
                        onToggle = onToggleThinking
                    )
                }

                // 只有思考、还没产出正文时给个状态提示
                if (msg.streaming && msg.content.isEmpty()) {
                    Text(
                        text = if (msg.thinking.isNotEmpty()) "正在思考…" else "正在生成…",
                        color = ChatColors.Dim,
                        fontSize = 14.sp
                    )
                }

                if (msg.content.isNotEmpty()) {
                    MarkdownBody(text = msg.content, streaming = msg.streaming)
                }

                if (msg.error != null && !isUser) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "调用失败：${msg.error}",
                        color = ChatColors.Err,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }

                // 底部：用量 + 一键复制（只给 AI 的回复加）
                val canCopy = !isUser && msg.content.isNotEmpty() && !msg.streaming
                if (msg.usage != null || canCopy) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (msg.usage != null) {
                            UsageLine(msg.usage, Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        if (canCopy) {
                            Spacer(Modifier.width(8.dp))
                            CopyChip { copyText(ctx, buildCopyText(msg, thinkingOpen)) }
                        }
                    }
                }

                if (msg.sources.isNotEmpty()) SourceList(msg.sources)
            }
        }

        if (selectMode && isUser) {
            Spacer(Modifier.width(8.dp))
            CheckDot(checked = checked, onToggle = onToggle)
        }
    }
}

/** 多选时的圆形勾选框 */
@Composable
private fun CheckDot(checked: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .size(22.dp)
            .background(
                if (checked) ChatColors.Accent else Color.Transparent,
                CircleShape
            )
            .border(
                1.5.dp,
                if (checked) ChatColors.Accent else ChatColors.Dim.copy(alpha = 0.6f),
                CircleShape
            )
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text("✓", color = Color.White, fontSize = 13.sp)
        }
    }
}

/** 思考过程：流式时强制展开，答完自动收起，用户也可以手动点开 */
@Composable
private fun ThinkingBlock(
    text: String,
    running: Boolean,
    hasAnswer: Boolean,
    userOpen: Boolean,
    onToggle: () -> Unit
) {
    val expanded = running || userOpen
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (hasAnswer || running) 10.dp else 0.dp)
            .background(ChatColors.CodeBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !running) { onToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (running) "思考过程 …" else "思考过程",
                    color = ChatColors.Accent2,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                if (!running) {
                    Text(
                        text = if (expanded) "收起" else "展开",
                        color = ChatColors.Dim,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }
            }

            if (text.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                CopyChip { copyText(ctx, text) }
            }
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                color = ChatColors.Dim,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }
    }
}

/** 小号「复制」按钮，正文和思考过程共用 */
@Composable
private fun CopyChip(text: String = "复制", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(ChatColors.Panel2, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = ChatColors.Dim, fontSize = 11.sp)
    }
}

/**
 * 拼出要复制的文本。思考块展开着才把思考过程带上——用户点开它，
 * 说明此刻在意这段推理；收起状态按默认理解就是只想拿结论。
 */
internal fun buildCopyText(msg: ChatMessage, withThinking: Boolean): String {
    val content = msg.content.trim()
    if (!withThinking || msg.thinking.isBlank()) return content
    return "【思考过程】\n${msg.thinking.trim()}\n\n【回复】\n$content"
}

/** 写系统剪贴板。Android 13 起系统会自己弹「已复制」，低版本补一个 Toast */
internal fun copyText(ctx: Context, text: String) {    try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dschat", text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(ctx, "复制失败", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 正文渲染。流式过程中走纯文本，因为每个 chunk 都重新解析整段 markdown
 * 在手机上会明显卡顿；结束之后再一次性排版。
 */
@Composable
private fun MarkdownBody(text: String, streaming: Boolean) {
    if (streaming) {
        Text(text = text, style = BodyStyle)
        return
    }

    val blocks = remember(text) { parseMd(text) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inlineMd(block.text),
                    fontSize = when (block.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        else -> 16.5.sp
                    },
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChatColors.Txt
                )

                is MdBlock.Para -> Text(
                    text = inlineMd(block.text),
                    style = BodyStyle
                )

                is MdBlock.Quote -> Row {
                    Spacer(
                        Modifier
                            .width(2.dp)
                            .height(20.dp)
                            .background(ChatColors.Line)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = inlineMd(block.text),
                        color = ChatColors.Dim,
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    )
                }

                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { i, item ->
                        Row {
                            Text(
                                text = if (block.ordered) "${i + 1}." else "·",
                                color = ChatColors.Accent2,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                modifier = Modifier.width(if (block.ordered) 22.dp else 14.dp)
                            )
                            Text(
                                text = inlineMd(item),
                                fontSize = 15.5.sp,
                                lineHeight = 24.sp,
                                color = ChatColors.Txt
                            )
                        }
                    }
                }

                is MdBlock.Code -> CodeBlock(block.code)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(
        color = ChatColors.CodeBg,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, ChatColors.Line)
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Color_CodeText,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

private val Color_CodeText = androidx.compose.ui.graphics.Color(0xFFD6E2F5)

/** 本轮用量与费用 */
@Composable
private fun UsageLine(u: Usage, modifier: Modifier = Modifier) {
    val parts = ArrayList<String>(5).apply {
        if (u.searches > 0) add("联网 ${u.searches} 次")
        add("入 ${Pricing.fmtNum(u.promptTokens)}")
        if (u.cacheHit > 0) add("缓存 ${Pricing.fmtNum(u.cacheHit)}")
        add("出 ${Pricing.fmtNum(u.completionTokens)}")
        if (u.reasoningTokens > 0) add("思考 ${Pricing.fmtNum(u.reasoningTokens)}")
    }
    Text(
        text = buildAnnotatedString {
            append(parts.joinToString(" · "))
            withStyle(SpanStyle(color = ChatColors.Ok)) {
                append(" · " + Pricing.fmtMoney(u.cost))
            }
        },
        color = ChatColors.Dim,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = modifier
    )
}

/** 联网搜索的参考来源，点一下直接开浏览器 */
@Composable
private fun SourceList(sources: List<Source>) {
    val opener = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ChatColors.Line)
        )
        Spacer(Modifier.height(8.dp))
        Text("参考来源（${sources.size}）", color = ChatColors.Dim, fontSize = 12.sp)
        sources.forEach { s ->
            Text(
                text = "· ${s.title}",
                color = ChatColors.Accent2,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { runCatching { opener.openUri(s.url) } }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

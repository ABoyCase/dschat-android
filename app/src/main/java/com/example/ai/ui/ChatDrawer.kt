package com.example.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Canvas
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ai.data.ConversationMeta
import com.example.ai.data.SearchHit
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatDrawer(
    conversations: List<ConversationMeta>,
    currentId: String,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (ConversationMeta) -> Unit,
    onRename: (ConversationMeta, String) -> Unit,
    onExport: (ConversationMeta) -> Unit,
    onSearch: suspend (String) -> List<SearchHit>
) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ConversationMeta?>(null) }

    // 输入停顿 220ms 再查，别每敲一个字都读一遍文件
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            hits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(220)
        hits = onSearch(q)
        searching = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatColors.Panel)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Text(
            text = "对话",
            color = ChatColors.Txt,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp)
        )

        SearchBox(query) { query = it }

        Spacer(Modifier.height(9.dp))

        // 新建
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(ChatColors.Accent, RoundedCornerShape(11.dp))
                .clickable { onNew() }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("＋", color = Color.White, fontSize = 15.sp)
            Spacer(Modifier.width(5.dp))
            Text(
                text = "新对话",
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(11.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ChatColors.Line)
        )

        val showSearch = query.isNotBlank()

        when {
            showSearch && searching -> {
                Text(
                    text = "搜索中…",
                    color = ChatColors.Dim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            showSearch && hits.isEmpty() -> {
                Text(
                    text = "没有找到包含「$query」的对话",
                    color = ChatColors.Dim,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            showSearch -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(hits, key = { it.meta.id }) { hit ->
                        SearchItem(
                            hit = hit,
                            onClick = {
                                query = ""
                                onOpen(hit.meta.id)
                            }
                        )
                    }
                }
            }

            conversations.isEmpty() -> {
                Text(
                    text = "还没有历史对话",
                    color = ChatColors.Dim,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(16.dp)
                )
                Spacer(Modifier.weight(1f))
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(conversations, key = { it.id }) { c ->
                        ConversationItem(
                            meta = c,
                            active = c.id == currentId,
                            onClick = { onOpen(c.id) },
                            onRenameClick = { renaming = c },
                            onDeleteClick = { onDelete(c) },
                            onExportClick = { onExport(c) }
                        )
                    }
                }
            }
        }

        Text(
            text = "对话保存在本机，不上传服务器",
            color = ChatColors.Dim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }

    renaming?.let { meta ->
        RenameDialog(
            initial = meta.title,
            onDismiss = { renaming = null },
            onConfirm = { newTitle ->
                onRename(meta, newTitle)
                renaming = null
            }
        )
    }
}

// ---------------- 搜索框 ----------------

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(ChatColors.Panel2, RoundedCornerShape(10.dp))
            .border(1.dp, ChatColors.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 放大镜：自己画，省得为两个图标拉一整个 icons 依赖
        // 画笔颜色先取出来，DrawScope 里不能调用 @Composable
        val iconColor = ChatColors.Dim
        Canvas(modifier = Modifier.size(14.dp)) {
            val r = size.minDimension * 0.34f
            val c = Offset(r + 0.8f, r + 0.8f)
            drawCircle(
                color = iconColor,
                radius = r,
                center = c,
                style = Stroke(width = 1.5f)
            )
            drawLine(
                color = iconColor,
                start = Offset(c.x + r * 0.62f, c.y + r * 0.62f),
                end = Offset(size.width - 0.4f, size.height - 0.4f),
                strokeWidth = 1.5f
            )
        }
        Spacer(Modifier.width(7.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("搜索标题或内容", color = ChatColors.Dim, fontSize = 13.5.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = ChatColors.Txt, fontSize = 13.5.sp),
                cursorBrush = SolidColor(ChatColors.Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onChange("") },
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = ChatColors.Dim, fontSize = 15.sp)
            }
        }
    }
}

// ---------------- 列表项 ----------------

@Composable
private fun ConversationItem(
    meta: ConversationMeta,
    active: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onExportClick: () -> Unit
) {
    var menuOpen by remember(meta.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp, vertical = 2.dp)
            .background(
                if (active) ChatColors.Panel2 else Color.Transparent,
                RoundedCornerShape(9.dp)
            )
            .clickable { onClick() }
            .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .background(
                    if (active) ChatColors.Accent else Color.Transparent,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = meta.title,
                color = if (active) ChatColors.Txt else ChatColors.Txt.copy(alpha = 0.82f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${fmtTime(meta.updatedAt)} · ${meta.count} 轮",
                color = ChatColors.Dim,
                fontSize = 11.sp
            )
        }

        // ⋯ 更多：重命名 / 删除
        Box {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Text("⋯", color = ChatColors.Dim.copy(alpha = 0.8f), fontSize = 17.sp)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("导出为 TXT", color = ChatColors.Txt, fontSize = 14.sp) },
                    onClick = {
                        menuOpen = false
                        onExportClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("重命名", color = ChatColors.Txt, fontSize = 14.sp) },
                    onClick = {
                        menuOpen = false
                        onRenameClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除", color = ChatColors.Err, fontSize = 14.sp) },
                    onClick = {
                        menuOpen = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchItem(hit: SearchHit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp, vertical = 2.dp)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = hit.meta.title,
            color = ChatColors.Txt,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (hit.snippet.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = hit.snippet,
                color = ChatColors.Dim,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = fmtTime(hit.meta.updatedAt),
            color = ChatColors.Dim.copy(alpha = 0.7f),
            fontSize = 10.5.sp
        )
    }
}

// ---------------- 重命名 ----------------

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChatColors.Panel, RoundedCornerShape(16.dp))
                .border(1.dp, ChatColors.Line, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text("重命名对话", color = ChatColors.Txt, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChatColors.Panel2, RoundedCornerShape(10.dp))
                    .border(1.dp, ChatColors.Line, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                if (text.isEmpty()) {
                    Text("给这段对话起个名字", color = ChatColors.Dim, fontSize = 14.sp)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    singleLine = true,
                    textStyle = TextStyle(color = ChatColors.Txt, fontSize = 14.sp),
                    cursorBrush = SolidColor(ChatColors.Accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "取消",
                    color = ChatColors.Dim,
                    fontSize = 14.5.sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "保存",
                    color = if (text.trim().isEmpty()) ChatColors.Dim else ChatColors.Accent2,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(enabled = text.trim().isNotEmpty()) { onConfirm(text) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun fmtTime(ts: Long): String {
    if (ts <= 0) return ""
    val now = System.currentTimeMillis()
    val d = now - ts
    return when {
        d < 60_000 -> "刚刚"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        d < 86_400_000 -> "${d / 3_600_000} 小时前"
        d < 7 * 86_400_000L -> "${d / 86_400_000} 天前"
        else -> SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(ts))
    }
}

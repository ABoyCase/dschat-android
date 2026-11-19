package com.example.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import com.example.ai.data.ConversationMeta
import com.example.ai.data.Efforts
import com.example.ai.data.Models
import com.example.ai.data.Pricing
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(ctrl: ChatController) {
    var input by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var showCost by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ConversationMeta?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }

    // 多选复制
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // 哪些消息的思考过程处于展开状态——复制时要不要带上思考，全看这个
    var thinkingOpen by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val ctx = androidx.compose.ui.platform.LocalContext.current

    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    BackHandler(enabled = selectMode) {
        selectMode = false
        selected = emptySet()
    }

    val scrollState = rememberScrollState()
    val focus = remember { FocusRequester() }
    var follow by remember { mutableStateOf(true) }

    // 第一次进来没 key，直接把设置框弹出来
    LaunchedEffect(Unit) {
        if (!ctrl.hasKey) showKey = true
    }

    // 内容变长就跟着滚到底；用户自己往上翻了就别打扰他
    LaunchedEffect(Unit) {
        var prevMax = 0
        snapshotFlow { scrollState.maxValue }.collect { max ->
            if (max > prevMax && follow) scrollState.scrollTo(max)
            prevMax = max
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.isScrollInProgress }.collect { dragging ->
            if (!dragging) {
                follow = scrollState.maxValue - scrollState.value < 160
            }
        }
    }

    // 换会话时重置滚动位置和思考展开状态
    LaunchedEffect(ctrl.currentId) {
        follow = true
        thinkingOpen = emptySet()
        scrollState.scrollTo(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatColors.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Header(
                ctrl = ctrl,
                onMenu = { drawerOpen = true },
                onSettings = { showKey = true },
                onCost = { showCost = true }
            )

                Box(modifier = Modifier.weight(1f)) {
                    if (ctrl.messages.isEmpty()) {
                        EmptyState(modifier = Modifier.fillMaxSize())
                    } else {
                        // 不用 SelectionContainer：它会抢长按，长按要留给「多选」。
                        // 想复制文字直接用气泡右下角的「复制」按钮。
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = Dimens.ScreenPad, vertical = 10.dp)
                        ) {
                            ctrl.messages.forEach { msg ->
                                MessageRow(
                                    msg = msg,
                                    selectMode = selectMode,
                                    checked = msg.id in selected,
                                    thinkingOpen = msg.id in thinkingOpen,
                                    onToggle = {
                                        selected = if (msg.id in selected) {
                                            selected - msg.id
                                        } else {
                                            selected + msg.id
                                        }
                                    },
                                    onToggleThinking = {
                                        thinkingOpen = if (msg.id in thinkingOpen) {
                                            thinkingOpen - msg.id
                                        } else {
                                            thinkingOpen + msg.id
                                        }
                                    },
                                    onEnterSelect = {
                                        selectMode = true
                                        selected = setOf(msg.id)
                                    }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

            if (selectMode) {
                SelectionBar(
                    count = selected.size,
                    onSelectAll = { selected = ctrl.messages.map { it.id }.toSet() },
                    onCopy = {
                        val picked = ctrl.messages
                            .filter { it.id in selected && it.content.isNotBlank() }
                            .sortedBy { it.id }
                        val body = picked.joinToString("\n\n") { m ->
                            val head = if (m.role == "user") "我：" else "AI："
                            val withThinking = m.role != "user" && m.id in thinkingOpen
                            if (withThinking) {
                                head + "\n" + buildCopyText(m, true)
                            } else {
                                head + m.content.trim()
                            }
                        }
                        if (body.isNotEmpty()) copyText(ctx, body)
                        selectMode = false
                        selected = emptySet()
                    },
                    onCancel = {
                        selectMode = false
                        selected = emptySet()
                    }
                )
            } else {
                Composer(
                    value = input,
                    onValueChange = { input = it },
                    busy = ctrl.busy,
                    focusRequester = focus,
                    onSend = {
                        follow = true
                        ctrl.send(input)
                        input = ""
                    },
                    onStop = { ctrl.stop() }
                )
            }
        }

        // ---- 左侧会话抽屉（自己画的，Material3 那个在真机上渲染不对） ----

        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000))
                    .pointerInput(Unit) {
                        detectTapGestures { drawerOpen = false }
                    }
            )
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(268.dp)
                    .background(ChatColors.Panel)
            ) {
                ChatDrawer(
                    conversations = ctrl.conversations,
                    currentId = ctrl.currentId,
                    onNew = {
                        drawerOpen = false
                        ctrl.newChat()
                    },
                    onOpen = { id ->
                        drawerOpen = false
                        ctrl.openChat(id)
                    },
                    onDelete = { meta -> pendingDelete = meta },
                    onRename = { meta, title -> ctrl.renameChat(meta.id, title) },
                    onSearch = { q -> ctrl.search(q) }
                )
            }
        }
    }

    if (showKey) {
        SettingsDialog(
            currentHint = ctrl.keyHint,
            config = ctrl.billing,
            holidays = ctrl.holidays,
            holidaySyncing = ctrl.holidaySyncing,
            holidayMsg = ctrl.holidayMsg,
            onSyncHolidays = { ctrl.syncHolidays() },
            onDismiss = { showKey = false },
            onSaved = { newKey, cfg ->
                newKey?.let { ctrl.saveKey(it) }
                ctrl.saveBilling(cfg)
                showKey = false
            }
        )
    }

    if (showCost) CostDialog(ctrl, onDismiss = { showCost = false })

    pendingDelete?.let { meta ->
        ConfirmDialog(
            title = "删除这段对话？",
            body = "「${meta.title}」将从本机删除，删了找不回来。",
            confirm = "删除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                ctrl.deleteChat(meta.id)
                pendingDelete = null
            }
        )
    }

    ctrl.notice?.let { text ->
        ConfirmDialog(
            title = "提示",
            body = text,
            confirm = "知道了",
            onDismiss = { ctrl.consumeNotice() },
            onConfirm = {
                ctrl.consumeNotice()
                showKey = true
            }
        )
    }
}

// ---------------- 顶栏 ----------------

@Composable
private fun Header(
    ctrl: ChatController,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    onCost: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChatColors.Panel)
            .statusBarsPadding()
            .padding(horizontal = Dimens.ScreenPad, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 抽屉开关
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable { onMenu() },
                contentAlignment = Alignment.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .width(17.dp)
                                .height(1.7.dp)
                                .background(ChatColors.Txt.copy(alpha = 0.85f), RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))

            Box(
                Modifier
                    .size(7.dp)
                    .background(if (ctrl.hasKey) ChatColors.Ok else ChatColors.Err, CircleShape)
            )
            Spacer(Modifier.width(7.dp))

            Text(
                text = ctrl.currentTitle,
                color = ChatColors.Txt,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))

            Text(
                text = if (ctrl.totals.turns == 0) "未花费" else Pricing.fmtMoney(ctrl.totals.cost),
                color = if (ctrl.totals.turns == 0) ChatColors.Dim else ChatColors.Ok,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onCost() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )

            IconText("⚙", onClick = onSettings)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModelMenu(ctrl)
            EffortMenu(ctrl)
            Chip(
                text = if (ctrl.webActive) "联网·开" else "联网",
                active = ctrl.webActive,
                enabled = ctrl.canUseWeb,
                onClick = { ctrl.toggleWeb() }
            )
            Spacer(Modifier.weight(1f))
            if (!ctrl.hasKey) {
                Text(
                    text = "缺 key",
                    color = ChatColors.Err,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onSettings() }
                )
            }
        }
    }
}

@Composable
private fun IconText(text: String, onClick: () -> Unit, small: Boolean = false) {
    Text(
        text = text,
        color = ChatColors.Dim,
        fontSize = if (small) 12.sp else 15.sp,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun ModelMenu(ctrl: ChatController) {
    var open by remember { mutableStateOf(false) }
    Box {
        Chip(
            text = Models.shortOf(ctrl.model),
            active = false,
            onClick = { open = true }
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false }
        ) {
            Models.ALL.forEach { o ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = o.label,
                            color = if (o.id == ctrl.model) ChatColors.Accent2 else ChatColors.Txt,
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        ctrl.chooseModel(o.id)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EffortMenu(ctrl: ChatController) {
    var open by remember { mutableStateOf(false) }
    Box {
        Chip(
            text = Efforts.labelOf(ctrl.effort),
            active = false,
            onClick = { open = true }
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false }
        ) {
            Efforts.ALL.forEach { (id, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = if (id == ctrl.effort) ChatColors.Accent2 else ChatColors.Txt,
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        ctrl.chooseEffort(id)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun Chip(
    text: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val fg = when {
        !enabled -> ChatColors.Dim.copy(alpha = 0.45f)
        active -> ChatColors.Txt
        else -> ChatColors.Accent2
    }
    Box(
        modifier = Modifier
            .background(
                if (active) ChatColors.Accent else ChatColors.Panel2,
                RoundedCornerShape(9.dp)
            )
            .border(
                1.dp,
                if (active) ChatColors.Accent else ChatColors.Line,
                RoundedCornerShape(9.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(text = text, color = fg, fontSize = 13.sp)
    }
}

// ---------------- 空状态 / 输入区 ----------------

// ---------------- 多选操作条 ----------------

@Composable
private fun SelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChatColors.Panel)
            .navigationBarsPadding()
            .padding(horizontal = Dimens.ScreenPad, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "全选",
                color = ChatColors.Accent2,
                fontSize = 13.5.sp,
                modifier = Modifier
                    .clickable { onSelectAll() }
                    .padding(vertical = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (count == 0) "点消息进行勾选" else "已选 $count 条",
                color = ChatColors.Dim,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(9.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            BarButton(
                text = "复制所选",
                primary = true,
                enabled = count > 0,
                modifier = Modifier.weight(1f),
                onClick = onCopy
            )
            BarButton(
                text = "取消",
                primary = false,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = onCancel
            )
        }
    }
}

@Composable
private fun BarButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                when {
                    !enabled -> ChatColors.Panel2
                    primary -> ChatColors.Accent
                    else -> ChatColors.Panel2
                },
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> ChatColors.Dim.copy(alpha = 0.55f)
                primary -> Color.White
                else -> ChatColors.Dim
            },
            fontSize = 15.sp,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "直接开始打字",
            color = ChatColors.Txt,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "下方选模型和思考强度\n回复里的思考过程可以点开看",
            color = ChatColors.Dim,
            fontSize = 14.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    focusRequester: FocusRequester,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChatColors.Panel)
            .navigationBarsPadding()
            .padding(horizontal = Dimens.ScreenPad, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(ChatColors.Panel2, RoundedCornerShape(14.dp))
                    .border(1.dp, ChatColors.Line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "说点什么…",
                        color = ChatColors.Dim,
                        fontSize = 16.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = ChatColors.Txt,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    ),
                    cursorBrush = SolidColor(ChatColors.Accent),
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 24.dp)
                        .focusRequester(focusRequester)
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (busy) ChatColors.Err else ChatColors.Accent,
                        CircleShape
                    )
                    .clickable {
                        if (busy) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (busy) "停" else "发",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "直连 api.deepseek.com · key 只存在本机",
            color = ChatColors.Dim,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

// ---------------- 弹窗 ----------------

@Composable
private fun CostDialog(ctrl: ChatController, onDismiss: () -> Unit) {
    val t = ctrl.totals
    val cfg = ctrl.billing
    val peak = Pricing.isPeak(cfg, ctrl.holidays)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChatColors.Panel, RoundedCornerShape(18.dp))
                .border(1.dp, ChatColors.Line, RoundedCornerShape(18.dp))
                .padding(22.dp)
        ) {
            Text("本次会话花费", color = ChatColors.Txt, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            CostLine("累计", Pricing.fmtMoney(t.cost), ChatColors.Ok)
            CostLine("轮数", "${t.turns} 轮")
            CostLine("输入", "${t.promptTokens} tokens")
            CostLine("其中缓存命中", "${t.cacheHit} tokens")
            CostLine("输出", "${t.completionTokens} tokens")
            CostLine("其中思考", "${t.reasoningTokens} tokens")
            CostLine(
                "当前价档",
                if (peak) "忙时 ×${fmtMult(cfg.peakMultiplier)}" else "闲时"
            )
            CostLine("忙时时段", cfg.peaksText())
            if (!cfg.weekdaysOnly) CostLine("适用", "每天（含周末）")
            val todayKey = com.example.ai.data.HolidayCalendar.keyOf()
            CostLine(
                "今天",
                when {
                    ctrl.holidays?.isOff(todayKey) == true -> "法定节假日 · 按闲时"
                    ctrl.holidays?.isWork(todayKey) == true -> "调休上班 · 按工作日"
                    com.example.ai.data.HolidayCalendar.isWeekend() -> "周末"
                    else -> "工作日"
                }
            )

            Spacer(Modifier.height(10.dp))
            val r = cfg.effective(ctrl.model, peak)
            Text(
                text = "${Models.shortOf(ctrl.model)} 当前单价：缓存 ¥${fmtP(r.hit)} · 未命中 ¥${fmtP(r.miss)} · 输出 ¥${fmtP(r.out)}（元/百万）",
                color = ChatColors.Accent2,
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "输出单价是输入的 4 倍，省钱主要看输出；\n联网搜索本身不收费，但搜来的网页正文按输入 token 计费。",
                color = ChatColors.Dim,
                fontSize = 12.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "单价可在设置里改",
                color = ChatColors.Dim.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "关闭",
                    color = ChatColors.Accent2,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun fmtP(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.2f", v)

private fun fmtMult(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)

@Composable
private fun CostLine(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = ChatColors.Txt) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(label, color = ChatColors.Dim, fontSize = 13.5.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 13.5.sp)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChatColors.Panel, RoundedCornerShape(18.dp))
                .border(1.dp, ChatColors.Line, RoundedCornerShape(18.dp))
                .padding(22.dp)
        ) {
            Text(title, color = ChatColors.Txt, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(body, color = ChatColors.Dim, fontSize = 14.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "取消",
                    color = ChatColors.Dim,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = confirm,
                    color = ChatColors.Accent2,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onConfirm() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

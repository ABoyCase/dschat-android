package com.example.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai.data.BillingConfig
import com.example.ai.data.HolidayCalendar
import com.example.ai.data.HolidayData
import com.example.ai.data.Models
import com.example.ai.data.PeakRange
import com.example.ai.data.PriceRule
import com.example.ai.data.Pricing
import com.example.ai.net.DeepSeekClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun fmtPrice(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

@Composable
fun SettingsDialog(
    currentHint: String,
    config: BillingConfig,
    holidays: HolidayData?,
    holidaySyncing: Boolean,
    holidayMsg: String,
    onSyncHolidays: () -> Unit,
    onDismiss: () -> Unit,
    onSaved: (newKey: String?, billing: BillingConfig) -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var msgIsError by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    var tab by remember { mutableStateOf(Models.FLASH) }

    // 六个价格框的文本，切换模型不会丢
    val priceText = remember {
        mutableStateMapOf(
            "flash_hit" to fmtPrice(config.flash.hit),
            "flash_miss" to fmtPrice(config.flash.miss),
            "flash_out" to fmtPrice(config.flash.out),
            "pro_hit" to fmtPrice(config.pro.hit),
            "pro_miss" to fmtPrice(config.pro.miss),
            "pro_out" to fmtPrice(config.pro.out)
        )
    }
    var multText by remember { mutableStateOf(fmtPrice(config.peakMultiplier)) }
    var peaks by remember { mutableStateOf(config.peaks) }
    var weekdaysOnly by remember { mutableStateOf(config.weekdaysOnly) }

    // 时段编辑弹窗：editingIndex = -1 表示新增
    var editingIndex by remember { mutableStateOf(-1) }
    var editorOpen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val client = remember { DeepSeekClient() }

    fun currentRule(): PriceRule {
        val p = if (tab == Models.PRO) "pro" else "flash"
        return PriceRule(
            hit = priceText["${p}_hit"]?.toDoubleOrNull() ?: -1.0,
            miss = priceText["${p}_miss"]?.toDoubleOrNull() ?: -1.0,
            out = priceText["${p}_out"]?.toDoubleOrNull() ?: -1.0
        )
    }

    /** 把界面上的文本收成一个配置；校验不过就返回 null 并写错误信息 */
    fun collect(): BillingConfig? {
        fun ruleOf(p: String): PriceRule? {
            val h = priceText["${p}_hit"]?.trim()?.toDoubleOrNull() ?: return null
            val m = priceText["${p}_miss"]?.trim()?.toDoubleOrNull() ?: return null
            val o = priceText["${p}_out"]?.trim()?.toDoubleOrNull() ?: return null
            if (h < 0 || m < 0 || o < 0) return null
            return PriceRule(h, m, o)
        }

        val flash = ruleOf("flash")
        val pro = ruleOf("pro")
        if (flash == null || pro == null) {
            msg = "单价要填不小于 0 的数字"
            msgIsError = true
            return null
        }

        val mult = multText.trim().toDoubleOrNull()
        if (mult == null || mult < 1.0 || mult > 100.0) {
            msg = "忙时倍率要在 1 ~ 100 之间"
            msgIsError = true
            return null
        }

        return BillingConfig(
            flash = flash,
            pro = pro,
            peakMultiplier = mult,
            peaks = peaks,
            weekdaysOnly = weekdaysOnly
        )
    }

    Dialog(
        onDismissRequest = { if (!checking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .imePadding()
                .background(ChatColors.Panel, RoundedCornerShape(18.dp))
                .border(1.dp, ChatColors.Line, RoundedCornerShape(18.dp))
        ) {
            // 标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("设置", color = ChatColors.Txt, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { if (!checking) onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = ChatColors.Dim, fontSize = 20.sp)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                // ---------- API Key ----------
                SectionTitle("API Key")
                FieldBox {
                    if (keyInput.isEmpty()) {
                        Text("留空则不修改", color = ChatColors.Dim, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.trim() },
                        singleLine = true,
                        enabled = !checking,
                        textStyle = TextStyle(color = ChatColors.Txt, fontSize = 14.sp),
                        cursorBrush = SolidColor(ChatColors.Accent),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (currentHint.isEmpty()) "还没配置" else "当前：$currentHint",
                    color = ChatColors.Dim,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(18.dp))

                // ---------- 计费 ----------
                SectionTitle("计费规则")
                Text(
                    text = "单位：元 / 百万 tokens。改完从下一轮开始生效，已有记录不重算。",
                    color = ChatColors.Dim,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(10.dp))

                // 模型切换
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Models.ALL.forEach { m ->
                        Chip(
                            text = m.short,
                            active = tab == m.id,
                            onClick = { tab = m.id }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))

                val p = if (tab == Models.PRO) "pro" else "flash"
                PriceRow("输入 · 缓存命中", priceText, "${p}_hit")
                PriceRow("输入 · 未命中", priceText, "${p}_miss")
                PriceRow("输出", priceText, "${p}_out")

                Spacer(Modifier.height(14.dp))
                Text("忙时", color = ChatColors.Accent2, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("忙时倍率", color = ChatColors.Dim, fontSize = 13.5.sp, modifier = Modifier.width(78.dp))
                    FieldBox(modifier = Modifier.width(84.dp)) {
                        BasicTextField(
                            value = multText,
                            onValueChange = { multText = it },
                            singleLine = true,
                            textStyle = TextStyle(color = ChatColors.Txt, fontSize = 14.sp),
                            cursorBrush = SolidColor(ChatColors.Accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "×（1.0 = 忙闲同价）",
                        color = ChatColors.Dim,
                        fontSize = 11.5.sp
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text("忙时时段", color = ChatColors.Dim, fontSize = 13.5.sp)
                Spacer(Modifier.height(6.dp))

                if (peaks.isEmpty()) {
                    Text(
                        text = "一个都没加，等于全天同价",
                        color = ChatColors.Dim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    peaks.forEachIndexed { i, p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(ChatColors.Panel2, RoundedCornerShape(9.dp))
                                .clickable {
                                    editingIndex = i
                                    editorOpen = true
                                }
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = p.label(),
                                color = ChatColors.Txt,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "修改",
                                color = ChatColors.Accent2,
                                fontSize = 11.5.sp,
                                modifier = Modifier
                                    .clickable {
                                        editingIndex = i
                                        editorOpen = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clickable {
                                        peaks = peaks.filterIndexed { j, _ -> j != i }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("×", color = ChatColors.Dim, fontSize = 17.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clickable {
                            editingIndex = -1
                            editorOpen = true
                        }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("＋", color = ChatColors.Accent2, fontSize = 14.sp)
                    Spacer(Modifier.width(5.dp))
                    Text("添加时段", color = ChatColors.Accent2, fontSize = 13.5.sp)
                }
                Text(
                    text = "北京时间。点某一段可修改，× 删除。",
                    color = ChatColors.Dim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("只在周一至周五生效", color = ChatColors.Txt, fontSize = 13.5.sp)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(
                                if (weekdaysOnly) ChatColors.Accent else ChatColors.Panel2,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { weekdaysOnly = !weekdaysOnly }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (weekdaysOnly) "已开启" else "已关闭",
                            color = if (weekdaysOnly) Color.White else ChatColors.Dim,
                            fontSize = 12.5.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("法定节假日", color = ChatColors.Txt, fontSize = 13.5.sp)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clickable(enabled = !holidaySyncing) { onSyncHolidays() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when {
                                holidaySyncing -> "同步中…"
                                holidays == null -> "立即同步"
                                else -> "重新同步"
                            },
                            color = if (holidaySyncing) ChatColors.Dim else ChatColors.Accent2,
                            fontSize = 12.5.sp
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = holidayStatusText(holidays, holidaySyncing, holidayMsg),
                    color = if (holidays == null && !holidaySyncing) ChatColors.Dim.copy(alpha = 0.85f) else ChatColors.Dim,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )

                // 实时折算提示，让用户改完就知道对不对
                val preview = currentRule()
                if (preview.hit >= 0 && preview.miss >= 0 && preview.out >= 0) {
                    val nowPeak = Pricing.isPeak(
                        BillingConfig(
                            flash = config.flash, pro = config.pro,
                            peakMultiplier = multText.toDoubleOrNull()?.takeIf { it >= 1.0 } ?: 2.0,
                            peaks = peaks,
                            weekdaysOnly = weekdaysOnly
                        ),
                        holidays
                    )
                    val m = multText.toDoubleOrNull()?.takeIf { it >= 1.0 } ?: 2.0
                    val k = if (nowPeak) m else 1.0
                    val todayKey = HolidayCalendar.keyOf()
                    val dayNote = when {
                        holidays?.isOff(todayKey) == true -> "法定节假日"
                        holidays?.isWork(todayKey) == true -> "调休上班"
                        HolidayCalendar.isWeekend() -> "周末"
                        else -> "工作日"
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "今天$dayNote · ${if (nowPeak) "忙时" else "闲时"}，" +
                            "${Models.shortOf(tab)} 实际单价：输入 ¥${fmtPrice(round2(preview.miss * k))} / " +
                            "输出 ¥${fmtPrice(round2(preview.out * k))}",
                        color = ChatColors.Accent2,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "恢复官方默认值",
                    color = ChatColors.Dim,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .clickable {
                            val d = BillingConfig.DEFAULT
                            priceText["flash_hit"] = fmtPrice(d.flash.hit)
                            priceText["flash_miss"] = fmtPrice(d.flash.miss)
                            priceText["flash_out"] = fmtPrice(d.flash.out)
                            priceText["pro_hit"] = fmtPrice(d.pro.hit)
                            priceText["pro_miss"] = fmtPrice(d.pro.miss)
                            priceText["pro_out"] = fmtPrice(d.pro.out)
                            multText = fmtPrice(d.peakMultiplier)
                            peaks = d.peaks
                            weekdaysOnly = d.weekdaysOnly
                            msg = "已填回默认值，点保存生效"
                            msgIsError = false
                        }
                        .padding(vertical = 6.dp)
                )

                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = if (msgIsError) ChatColors.Err else ChatColors.Ok,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }

                Spacer(Modifier.height(10.dp))
            }

            // 底部按钮
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ChatColors.Line)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "取消",
                    color = ChatColors.Dim,
                    fontSize = 14.5.sp,
                    modifier = Modifier
                        .clickable { if (!checking) onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (checking) "验证中…" else "保存",
                    color = if (checking) ChatColors.Dim else ChatColors.Accent2,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(enabled = !checking) {
                            val cfg = collect() ?: return@clickable
                            val k = keyInput.trim()

                            if (k.isEmpty()) {
                                msg = ""
                                onSaved(null, cfg)
                                return@clickable
                            }
                            if (!k.startsWith("sk-") || k.length < 20) {
                                msg = "格式不像 DeepSeek 的 key（应以 sk- 开头）"
                                msgIsError = true
                                return@clickable
                            }

                            checking = true
                            msgIsError = false
                            msg = "正在连 DeepSeek 验证…"
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { client.verifyKey(k) }
                                checking = false
                                r.onSuccess { onSaved(k, cfg) }
                                    .onFailure {
                                        msg = it.message ?: "验证失败"
                                        msgIsError = true
                                    }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (editorOpen) {
        PeakEditorDialog(
            initial = peaks.getOrNull(editingIndex),
            onDismiss = { editorOpen = false },
            onConfirm = { range ->
                peaks = if (editingIndex in peaks.indices) {
                    peaks.toMutableList().also { it[editingIndex] = range }
                } else {
                    (peaks + range).sortedBy { it.startMin }
                }
                editorOpen = false
            }
        )
    }
}

private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

private fun holidayStatusText(h: HolidayData?, syncing: Boolean, msg: String): String = when {
    syncing -> "正在从网络获取…"
    h == null && msg.isNotEmpty() -> "同步失败：$msg（不影响使用，只是节假日按忙时算）"
    h == null -> "还没同步，节假日会按忙时计算"
    else -> {
        val d = java.text.SimpleDateFormat("MM-dd", java.util.Locale.CHINA).format(java.util.Date(h.fetchedAt))
        "${h.year} 年 · 放假 ${h.off.size} 天 · 补班 ${h.work.size} 天 · $d 更新"
    }
}

/**
 * 时段编辑：两个时间按钮切换 + 一个 24 小时表盘。
 * 用选择器代替手输，省得用户写错格式还要被校验拦。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeakEditorDialog(
    initial: PeakRange?,
    onDismiss: () -> Unit,
    onConfirm: (PeakRange) -> Unit
) {
    var editingStart by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf("") }

    val startState = rememberTimePickerState(
        initialHour = (initial?.startMin ?: 9 * 60) / 60,
        initialMinute = (initial?.startMin ?: 9 * 60) % 60,
        is24Hour = true
    )
    val endState = rememberTimePickerState(
        initialHour = (initial?.endMin ?: 12 * 60) / 60,
        initialMinute = (initial?.endMin ?: 12 * 60) % 60,
        is24Hour = true
    )

    val startMin = startState.hour * 60 + startState.minute
    val endMin = endState.hour * 60 + endState.minute

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .background(ChatColors.Panel, RoundedCornerShape(18.dp))
                .border(1.dp, ChatColors.Line, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(
                text = if (initial == null) "添加忙时时段" else "修改忙时时段",
                color = ChatColors.Txt,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            // 开始 / 结束 切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeTag(
                    label = "开始",
                    minute = startMin,
                    selected = editingStart,
                    onClick = { editingStart = true }
                )
                Text("→", color = ChatColors.Dim, fontSize = 14.sp)
                TimeTag(
                    label = "结束",
                    minute = endMin,
                    selected = !editingStart,
                    onClick = { editingStart = false }
                )
                Spacer(Modifier.weight(1f))
                Text("24 小时制", color = ChatColors.Dim, fontSize = 11.sp)
            }

            // 表盘
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(
                    state = if (editingStart) startState else endState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = ChatColors.Panel2,
                        clockDialSelectedContentColor = Color.White,
                        selectorColor = ChatColors.Accent,
                        timeSelectorSelectedContainerColor = ChatColors.Accent,
                        timeSelectorUnselectedContainerColor = ChatColors.Panel2
                    )
                )
            }

            if (err.isNotEmpty()) {
                Text(
                    text = err,
                    color = ChatColors.Err,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "取消",
                    color = ChatColors.Dim,
                    fontSize = 14.5.sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "确定",
                    color = ChatColors.Accent2,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            if (startMin == endMin) {
                                err = "开始和结束不能是同一个时间"
                            } else if (startMin > endMin) {
                                err = "结束时间要晚于开始时间（不支持跨夜）"
                            } else {
                                onConfirm(PeakRange(startMin, endMin))
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun TimeTag(label: String, minute: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(
                if (selected) ChatColors.Accent.copy(alpha = 0.18f) else ChatColors.Panel2,
                RoundedCornerShape(9.dp)
            )
            .border(
                1.dp,
                if (selected) ChatColors.Accent else ChatColors.Line,
                RoundedCornerShape(9.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = ChatColors.Dim, fontSize = 11.5.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = String.format("%02d:%02d", minute / 60, minute % 60),
            color = if (selected) ChatColors.Accent2 else ChatColors.Txt,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = ChatColors.Accent2,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun FieldBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .background(ChatColors.Panel2, RoundedCornerShape(10.dp))
            .border(1.dp, ChatColors.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        content()
    }
}

@Composable
private fun PriceRow(label: String, map: MutableMap<String, String>, key: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = ChatColors.Dim, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        FieldBox(modifier = Modifier.width(96.dp)) {
            BasicTextField(
                value = map[key].orEmpty(),
                onValueChange = { map[key] = it },
                singleLine = true,
                textStyle = TextStyle(color = ChatColors.Txt, fontSize = 14.sp),
                cursorBrush = SolidColor(ChatColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
    }
}

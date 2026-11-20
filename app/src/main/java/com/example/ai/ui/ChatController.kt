package com.example.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ai.data.BillingConfig
import com.example.ai.data.ChatMessage
import com.example.ai.data.Conversation
import com.example.ai.data.ConversationMeta
import com.example.ai.data.ConversationStore
import com.example.ai.data.Efforts
import com.example.ai.data.HolidayCalendar
import com.example.ai.data.HolidayData
import com.example.ai.data.Models
import com.example.ai.data.Prefs
import com.example.ai.data.Pricing
import com.example.ai.data.SearchHit
import com.example.ai.data.Source
import com.example.ai.data.Totals
import com.example.ai.data.Usage
import com.example.ai.data.titleOf
import com.example.ai.net.DeepSeekClient
import com.example.ai.net.HolidayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * 界面状态与请求编排。会话存在本地，切走再回来接着聊。
 */
class ChatController(
    private val prefs: Prefs,
    private val store: ConversationStore
) {

    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    var totals by mutableStateOf(Totals())
        private set

    /** 左侧列表 */
    var conversations by mutableStateOf<List<ConversationMeta>>(emptyList())
        private set

    var currentId by mutableStateOf("")
        private set

    var currentTitle by mutableStateOf("新对话")
        private set

    var busy by mutableStateOf(false)
        private set

    /** 一次性提示，界面消费完置空 */
    var notice by mutableStateOf<String?>(null)

    var model by mutableStateOf(prefs.model)
        private set
    var effort by mutableStateOf(prefs.effort)
        private set
    var web by mutableStateOf(prefs.web)
        private set

    /** 计费规则，改完立刻影响后续每一轮的计算 */
    var billing by mutableStateOf(prefs.billing)
        private set

    /** 法定节假日安排，拿不到就是 null，按普通工作日算 */
    var holidays by mutableStateOf(prefs.holidays)
        private set

    var holidaySyncing by mutableStateOf(false)
        private set

    var holidayMsg by mutableStateOf("")
        private set

    /** key 是否存在要能被 Compose 观测到，否则保存完界面不刷新 */
    var keyPresent by mutableStateOf(prefs.apiKey.isNotEmpty())
        private set

    val hasKey: Boolean get() = keyPresent
    val keyHint: String get() = prefs.hint()
    val canUseWeb: Boolean get() = Models.supportsWeb(model)
    val webActive: Boolean get() = canUseWeb && web

    private val client = DeepSeekClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 送给接口的上下文，只收干净的问答对（失败的轮次不进上下文） */
    private val ctx = ArrayList<Pair<String, String>>()
    private val seq = AtomicLong(1)
    private var createdAt = System.currentTimeMillis()

    init {
        // 打开上次聊的那个；一个都没有就开新的
        val list = store.list()
        conversations = list
        val first = list.firstOrNull()
        if (first != null) loadInto(first.id) else freshSession()

        // 节假日数据会过期（跨年、或者官方中途调整），启动时顺手刷一下
        scope.launch { autoSyncHolidays() }
    }

    // ---------- 节假日 ----------

    private suspend fun autoSyncHolidays() {
        val now = System.currentTimeMillis()
        val year = HolidayCalendar.yearOf(now)
        val cur = holidays
        val stale = cur == null ||
            cur.year != year ||
            now - cur.fetchedAt > 15L * 86_400_000L
        if (stale) syncHolidays()
    }

    fun syncHolidays() {
        if (holidaySyncing) return
        holidaySyncing = true
        holidayMsg = ""

        val year = HolidayCalendar.yearOf()
        scope.launch {
            val r = withContext(Dispatchers.IO) { HolidayClient().fetch(year) }
            holidaySyncing = false
            r.onSuccess { data ->
                holidays = data
                prefs.holidays = data
                holidayMsg = "已同步 $year 年：放假 ${data.off.size} 天、补班 ${data.work.size} 天"
            }.onFailure { e ->
                holidayMsg = e.message ?: "同步失败"
            }
        }
    }

    // ---------- 会话管理 ----------

    fun newChat() {
        if (busy) client.cancel()
        persist()
        freshSession()
    }

    fun openChat(id: String) {
        if (id == currentId) return
        if (busy) client.cancel()
        persist()
        loadInto(id)
    }

    fun deleteChat(id: String) {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                store.delete(id)
                store.list()
            }
            conversations = list

            if (id == currentId) {
                if (busy) client.cancel()
                // 当前会话被删了，先断干净再切
                currentId = ""
                messages = emptyList()
                totals = Totals()
                ctx.clear()
                busy = false

                val next = list.firstOrNull()
                if (next != null) loadInto(next.id) else freshSession()
            }
        }
    }

    fun renameChat(id: String, newTitle: String) {
        val title = newTitle.trim()
        if (title.isEmpty()) return
        if (id == currentId) currentTitle = title

        scope.launch {
            val list = withContext(Dispatchers.IO) {
                store.rename(id, title)
                store.list()
            }
            conversations = list
        }
    }

    suspend fun search(query: String): List<SearchHit> =
        withContext(Dispatchers.IO) { store.search(query) }

    /**
     * 导出用的一份快照。当前会话从内存取（可能比磁盘上还新），
     * 其他会话从存档读。
     */
    suspend fun conversationForExport(id: String): Conversation? {
        if (id == currentId && messages.isNotEmpty()) {
            return Conversation(
                id = currentId,
                title = currentTitle,
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis(),
                messages = messages.map { it.copy(streaming = false) },
                totals = totals
            )
        }
        return withContext(Dispatchers.IO) { store.load(id) }
    }

    private fun freshSession() {
        currentId = UUID.randomUUID().toString()
        currentTitle = "新对话"
        createdAt = System.currentTimeMillis()
        messages = emptyList()
        totals = Totals()
        ctx.clear()
        seq.set(1)
        busy = false
    }

    private fun loadInto(id: String) {
        val c = store.load(id)
        if (c == null) {
            freshSession()
            return
        }
        currentId = c.id
        currentTitle = c.title
        createdAt = if (c.createdAt > 0) c.createdAt else System.currentTimeMillis()
        messages = c.messages
        totals = c.totals
        busy = false

        ctx.clear()
        c.messages.forEach { m ->
            if (m.error == null && m.content.isNotEmpty()) ctx.add(m.role to m.content)
        }
        seq.set((c.messages.maxOfOrNull { it.id } ?: 0L) + 1)
    }

    /** 把当前会话落盘。一条都没发的空会话不占列表，直接跳过 */
    private fun persist() {
        val id = currentId
        if (id.isEmpty()) return
        if (messages.none { it.role == "user" }) return

        val snapshot = Conversation(
            id = id,
            title = currentTitle,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            messages = messages.map { it.copy(streaming = false) },
            totals = totals
        )
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                store.save(snapshot)
                store.list()
            }
            conversations = list
        }
    }

    /** 切后台时兜底存一次 */
    fun persistNow() = persist()

    // ---------- 顶部设置 ----------

    fun chooseModel(id: String) {
        model = id
        prefs.model = id
        if (!Models.supportsWeb(id) && web) {
            web = false
            prefs.web = false
        }
    }

    fun chooseEffort(id: String) {
        effort = id
        prefs.effort = id
    }

    fun toggleWeb() {
        if (!canUseWeb) return
        web = !web
        prefs.web = web
    }

    fun saveBilling(cfg: BillingConfig) {
        billing = cfg
        prefs.billing = cfg
    }

    fun saveKey(key: String) {
        prefs.apiKey = key
        keyPresent = true
    }

    fun consumeNotice() {
        notice = null
    }

    fun stop() {
        client.cancel()
    }

    // ---------- 对话 ----------

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty() || busy) return

        val key = prefs.apiKey
        if (key.isEmpty()) {
            notice = "还没填 API key，点右上角 ⚙ 填一个"
            return
        }

        // 第一条消息顺手把会话标题定下来
        if (messages.isEmpty()) currentTitle = titleOf(body)

        val modelId = model
        val effortId = effort
        val webOn = webActive

        messages = messages + ChatMessage(id = seq.getAndIncrement(), role = "user", content = body)
        val aid = seq.getAndIncrement()
        messages = messages + ChatMessage(id = aid, role = "assistant", streaming = true)

        ctx.add("user" to body)
        val history = ctx.toList()

        busy = true
        // 先把用户这句存下来，万一进程被杀不至于丢标题和提问
        persist()

        scope.launch {
            val acc = StringBuilder()
            val thk = StringBuilder()
            val srcs = LinkedHashSet<Source>()
            var lastPaint = 0L

            fun paint(force: Boolean) {
                val now = System.currentTimeMillis()
                if (!force && now - lastPaint < 60) return
                lastPaint = now
                update(aid) {
                    it.copy(
                        content = acc.toString(),
                        thinking = thk.toString(),
                        sources = srcs.toList()
                    )
                }
            }

            try {
                withContext(Dispatchers.IO) {
                    client.stream(
                        apiKey = key,
                        model = modelId,
                        history = history,
                        effort = effortId,
                        web = webOn,
                        onDelta = { t, th, list ->
                            if (t != null) acc.append(t)
                            if (th != null) thk.append(th)
                            list.forEach { if (srcs.size < 8) srcs.add(it) }
                            paint(false)
                        },
                        onUsage = { u -> applyUsage(aid, modelId, u) }
                    )
                }
                paint(true)
                finish(aid, error = null, content = acc.toString())
            } catch (e: Exception) {
                paint(true)
                finish(aid, error = e.message ?: "请求失败", content = acc.toString())
            } finally {
                busy = false
                persist()
            }
        }
    }

    /** 收尾：补全内容、落上下文、算累计 */
    private fun finish(id: Long, error: String?, content: String) {
        update(id) { it.copy(streaming = false, error = error) }
        when {
            error == null && content.isNotEmpty() -> ctx.add("assistant" to content)
            error == null -> {
                ctx.add("assistant" to "（空回复）")
                update(id) { it.copy(content = "（空回复）") }
            }
            content.isNotEmpty() -> ctx.add("assistant" to content)
            else -> dropLastUser()
        }
    }

    private fun dropLastUser() {
        if (ctx.isNotEmpty() && ctx.last().first == "user") ctx.removeAt(ctx.lastIndex)
    }

    private fun applyUsage(id: Long, modelId: String, u: JSONObject) {
        val prompt = u.optInt("prompt_tokens", 0)
        val completion = u.optInt("completion_tokens", 0)
        val reasoning = u.optJSONObject("completion_tokens_details")?.optInt("reasoning_tokens", 0) ?: 0
        val cacheHit = u.optInt("prompt_cache_hit_tokens", 0)
        val searches = u.optJSONObject("server_tool_use")?.optInt("tool_search", 0) ?: 0
        val cost = Pricing.cost(billing, holidays, modelId, prompt, completion, cacheHit)

        val usage = Usage(
            promptTokens = prompt,
            completionTokens = completion,
            reasoningTokens = reasoning,
            cacheHit = cacheHit,
            searches = searches,
            cost = cost
        )
        update(id) { it.copy(usage = usage) }

        totals = totals.let {
            it.copy(
                promptTokens = it.promptTokens + prompt,
                completionTokens = it.completionTokens + completion,
                reasoningTokens = it.reasoningTokens + reasoning,
                cacheHit = it.cacheHit + cacheHit,
                cost = it.cost + cost,
                turns = it.turns + 1
            )
        }
    }

    private inline fun update(id: Long, crossinline block: (ChatMessage) -> ChatMessage) {
        messages = messages.map { if (it.id == id) block(it) else it }
    }

    fun dispose() {
        persist()
        client.cancel()
        scope.cancel()
    }
}

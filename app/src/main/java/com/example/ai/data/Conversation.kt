package com.example.ai.data

import org.json.JSONArray
import org.json.JSONObject

/** 列表里用的会话摘要 */
data class ConversationMeta(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val count: Int
)

/** 完整会话 */
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
    val totals: Totals
)

/** 搜索命中：会话摘要 + 命中的那一小段文字 */
data class SearchHit(val meta: ConversationMeta, val snippet: String)

// ---------------- 序列化 ----------------

fun Conversation.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
    put("totals", totals.toJson())
}

fun Conversation.toMeta(): ConversationMeta =
    ConversationMeta(id = id, title = title, updatedAt = updatedAt, count = messages.count { it.role == "user" })

private fun ChatMessage.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("role", role)
    put("content", content)
    if (thinking.isNotEmpty()) put("thinking", thinking)
    if (error != null) put("error", error)
    usage?.let { put("usage", it.toJson()) }
    if (sources.isNotEmpty()) {
        put("sources", JSONArray().apply {
            sources.forEach { s -> put(JSONObject().put("url", s.url).put("title", s.title)) }
        })
    }
}

private fun Usage.toJson(): JSONObject = JSONObject().apply {
    put("promptTokens", promptTokens)
    put("completionTokens", completionTokens)
    put("reasoningTokens", reasoningTokens)
    put("cacheHit", cacheHit)
    put("searches", searches)
    put("cost", cost)
}

private fun Totals.toJson(): JSONObject = JSONObject().apply {
    put("promptTokens", promptTokens)
    put("completionTokens", completionTokens)
    put("reasoningTokens", reasoningTokens)
    put("cacheHit", cacheHit)
    put("cost", cost)
    put("turns", turns)
}

fun conversationFromJson(o: JSONObject): Conversation {
    val msgs = o.optJSONArray("messages")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { m ->
                ChatMessage(
                    id = m.optLong("id", i.toLong()),
                    role = m.optString("role", "user"),
                    content = m.optString("content", ""),
                    thinking = m.optString("thinking", ""),
                    error = if (m.has("error") && !m.isNull("error")) m.getString("error") else null,
                    usage = m.optJSONObject("usage")?.let { u ->
                        Usage(
                            promptTokens = u.optInt("promptTokens"),
                            completionTokens = u.optInt("completionTokens"),
                            reasoningTokens = u.optInt("reasoningTokens"),
                            cacheHit = u.optInt("cacheHit"),
                            searches = u.optInt("searches"),
                            cost = u.optDouble("cost", 0.0)
                        )
                    },
                    sources = m.optJSONArray("sources")?.let { s ->
                        (0 until s.length()).mapNotNull { k ->
                            s.optJSONObject(k)?.let {
                                Source(it.optString("url"), it.optString("title"))
                            }
                        }
                    } ?: emptyList(),
                    streaming = false
                )
            }
        }
    } ?: emptyList()

    val t = o.optJSONObject("totals")
    return Conversation(
        id = o.optString("id"),
        title = o.optString("title", "新对话"),
        createdAt = o.optLong("createdAt"),
        updatedAt = o.optLong("updatedAt"),
        messages = msgs,
        totals = Totals(
            promptTokens = t?.optInt("promptTokens") ?: 0,
            completionTokens = t?.optInt("completionTokens") ?: 0,
            reasoningTokens = t?.optInt("reasoningTokens") ?: 0,
            cacheHit = t?.optInt("cacheHit") ?: 0,
            cost = t?.optDouble("cost", 0.0) ?: 0.0,
            turns = t?.optInt("turns") ?: 0
        )
    )
}

/** 用第一条用户消息当标题 */
fun titleOf(text: String): String {
    val one = text.replace(Regex("\\s+"), " ").trim()
    if (one.isEmpty()) return "新对话"
    return if (one.length <= 20) one else one.take(20) + "…"
}

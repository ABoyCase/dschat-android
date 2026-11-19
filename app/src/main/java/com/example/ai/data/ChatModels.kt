package com.example.ai.data

/** 联网搜索返回的引用来源 */
data class Source(val url: String, val title: String)

/** 单轮用量与费用 */
data class Usage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val cacheHit: Int = 0,
    val searches: Int = 0,
    val cost: Double = 0.0
)

/** 一条消息。assistant 的消息在流式过程中会不断被替换成新副本。 */
data class ChatMessage(
    val id: Long,
    val role: String,                       // "user" / "assistant"
    val content: String = "",
    val thinking: String = "",
    val usage: Usage? = null,
    val sources: List<Source> = emptyList(),
    val error: String? = null,
    val streaming: Boolean = false
)

/** 整个会话累计 */
data class Totals(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val cacheHit: Int = 0,
    val cost: Double = 0.0,
    val turns: Int = 0
)

/** 模型定义，跟桌面版保持一致 */
object Models {
    const val FLASH = "deepseek-flash"
    const val PRO = "deepseek-v4-pro"

    val ALL = listOf(
        Option(FLASH, "V4.1 Flash · 快", "Flash", true),
        Option(PRO, "V4 Pro · 更强", "Pro", false)
    )

    data class Option(val id: String, val label: String, val short: String, val supportWeb: Boolean)

    fun labelOf(id: String): String = ALL.firstOrNull { it.id == id }?.label ?: id
    fun shortOf(id: String): String = ALL.firstOrNull { it.id == id }?.short ?: id
    fun supportsWeb(id: String): Boolean = id == FLASH
}

object Efforts {
    val ALL = listOf("low" to "思考·少", "high" to "思考·标准", "max" to "思考·最深")
    fun labelOf(id: String): String = ALL.firstOrNull { it.first == id }?.second ?: id
}

package com.example.ai.data

import org.json.JSONArray
import org.json.JSONObject

/** 单个模型的三档单价，单位：元 / 百万 tokens */
data class PriceRule(
    val hit: Double,    // 输入·缓存命中
    val miss: Double,   // 输入·未命中
    val out: Double     // 输出
)

/** 忙时区间，按北京时间记，用分钟数表示 */
data class PeakRange(val startMin: Int, val endMin: Int) {

    fun label(): String = String.format(
        "%02d:%02d-%02d:%02d",
        startMin / 60, startMin % 60, endMin / 60, endMin % 60
    )

    fun contains(minuteOfDay: Int): Boolean = minuteOfDay >= startMin && minuteOfDay < endMin
}

/**
 * 计费规则。用「基准价 × 忙时倍率」表示忙闲两档：
 * 官方是忙时 = 标准价、闲时 = 半价，所以倍率默认 2.0，闲时价 = 基准价 ÷ 倍率。
 * 比直接填两套价格少一半字段，也不会出现「闲时比忙时还贵」这种自相矛盾的输入。
 */
data class BillingConfig(
    val flash: PriceRule = PriceRule(0.02, 1.00, 4.00),
    val pro: PriceRule = PriceRule(0.15, 4.50, 13.50),
    val peakMultiplier: Double = 2.0,
    val peaks: List<PeakRange> = DEFAULT_PEAKS,
    val weekdaysOnly: Boolean = true
) {

    fun ruleFor(model: String): PriceRule =
        if (model == Models.PRO) pro else flash

    /** 按忙闲取实际单价 */
    fun effective(model: String, peak: Boolean): PriceRule {
        val r = ruleFor(model)
        if (!peak) return r
        val m = if (peakMultiplier < 1.0) 1.0 else peakMultiplier
        return PriceRule(r.hit * m, r.miss * m, r.out * m)
    }

    fun peaksText(): String = peaks.joinToString(", ") { it.label() }

    fun toJson(): String = JSONObject().apply {
        put("flash", flash.toJson())
        put("pro", pro.toJson())
        put("mult", peakMultiplier)
        put("weekdays", weekdaysOnly)
        put("peaks", JSONArray().apply { peaks.forEach { put("${it.startMin}-${it.endMin}") } })
    }.toString()

    companion object {
        val DEFAULT_PEAKS = listOf(
            PeakRange(9 * 60, 12 * 60),
            PeakRange(14 * 60, 18 * 60)
        )

        val DEFAULT = BillingConfig()

        fun fromJson(raw: String?): BillingConfig {
            if (raw.isNullOrBlank()) return DEFAULT
            return try {
                val o = JSONObject(raw)
                val arr = o.optJSONArray("peaks")
                val peaks = ArrayList<PeakRange>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val raw = arr.optString(i, "")
                        val seg = raw.split('-')
                        if (seg.size != 2) continue
                        val start = seg[0].toIntOrNull() ?: continue
                        val end = seg[1].toIntOrNull() ?: continue
                        if (start in 0..1439 && end in 0..1440 && start < end) {
                            peaks.add(PeakRange(start, end))
                        }
                    }
                }
                BillingConfig(
                    flash = o.optJSONObject("flash")?.let { ruleFrom(it) } ?: DEFAULT.flash,
                    pro = o.optJSONObject("pro")?.let { ruleFrom(it) } ?: DEFAULT.pro,
                    peakMultiplier = o.optDouble("mult", 2.0).let { if (it < 1.0 || it > 100) 2.0 else it },
                    peaks = if (peaks.isEmpty()) DEFAULT_PEAKS else peaks,
                    weekdaysOnly = o.optBoolean("weekdays", true)
                )
            } catch (_: Exception) {
                DEFAULT
            }
        }

        private fun ruleFrom(o: JSONObject) = PriceRule(
            hit = o.optDouble("hit", 0.0).coerceAtLeast(0.0),
            miss = o.optDouble("miss", 0.0).coerceAtLeast(0.0),
            out = o.optDouble("out", 0.0).coerceAtLeast(0.0)
        )

        /** "09:00-12:00, 14:00-18:00" → 区间列表。格式不对返回 null，让界面报错 */
        fun parsePeaks(text: String): List<PeakRange>? {
            val cleaned = text.replace('，', ',').replace('；', ';').replace('、', ',')
            val parts = cleaned.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null

            val out = ArrayList<PeakRange>()
            val re = Regex("^(\\d{1,2}):(\\d{2})\\s*[-~～至到]\\s*(\\d{1,2}):(\\d{2})$")
            for (p in parts) {
                val m = re.find(p) ?: return null
                val h1 = m.groupValues[1].toIntOrNull() ?: return null
                val m1 = m.groupValues[2].toIntOrNull() ?: return null
                val h2 = m.groupValues[3].toIntOrNull() ?: return null
                val m2 = m.groupValues[4].toIntOrNull() ?: return null
                if (h1 !in 0..23 || h2 !in 0..24 || m1 !in 0..59 || m2 !in 0..59) return null

                val start = h1 * 60 + m1
                val end = h2 * 60 + m2
                if (start >= end) return null
                out.add(PeakRange(start, end))
            }
            return if (out.isEmpty()) null else out.sortedBy { it.startMin }
        }
    }
}

private fun PriceRule.toJson(): JSONObject = JSONObject().apply {
    put("hit", hit)
    put("miss", miss)
    put("out", out)
}

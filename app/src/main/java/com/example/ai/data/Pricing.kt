package com.example.ai.data

import java.util.Calendar
import java.util.TimeZone

/**
 * 计费计算。规则本身来自 [BillingConfig]（用户可改），这里只负责按规则算钱。
 * 时间一律按北京时间判断——用户填的时段就是看到的钟点，不用再心算 UTC。
 */
object Pricing {

    private val TZ: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    /**
     * 是否处于忙时。判断顺序：
     * 1. 今天在法定节假日里 → 闲时（官方规定节假日按闲时）
     * 2. 今天是调休补班 → 当工作日，继续看时段
     * 3. 周末且开启了「仅工作日」→ 闲时
     * 4. 落在忙时时段里 → 忙时
     */
    fun isPeak(
        cfg: BillingConfig,
        holidays: HolidayData? = null,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val dateKey = HolidayCalendar.keyOf(now)
        val today = holidays?.takeIf { it.year == HolidayCalendar.yearOf(now) }

        if (today != null && today.isOff(dateKey)) return false

        val makeupWorkday = today != null && today.isWork(dateKey)
        if (!makeupWorkday && cfg.weekdaysOnly && HolidayCalendar.isWeekend(now)) return false

        val c = Calendar.getInstance(TZ)
        c.timeInMillis = now
        val minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return cfg.peaks.any { it.contains(minute) }
    }

    /** 单轮费用（元）。completionTokens 已含思考 tokens，不重复加。 */
    fun cost(
        cfg: BillingConfig,
        holidays: HolidayData?,
        model: String,
        promptTokens: Int,
        completionTokens: Int,
        cacheHit: Int,
        at: Long = System.currentTimeMillis()
    ): Double {
        val peak = isPeak(cfg, holidays, at)
        val p = cfg.effective(model, peak)
        val hit = cacheHit.coerceAtLeast(0)
        val miss = (promptTokens - hit).coerceAtLeast(0)
        return (hit * p.hit + miss * p.miss + completionTokens * p.out) / 1e6
    }

    fun fmtNum(n: Int): String = when {
        n >= 10000 -> "${n / 1000}k"
        n >= 1000 -> String.format("%.1fk", n / 1000.0)
        else -> n.toString()
    }

    fun fmtMoney(v: Double): String = when {
        v < 0.001 -> String.format("¥%.5f", v)
        v < 0.1 -> String.format("¥%.4f", v)
        else -> String.format("¥%.3f", v)
    }
}

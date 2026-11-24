package com.example.ai.data

import android.content.Context

/**
 * 本地配置。API key 存在应用私有 SharedPreferences 里，
 * 只有本应用（以及 root 后的手机）能读到。
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("dschat", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString(KEY_API, "").orEmpty()
        set(v) = sp.edit().putString(KEY_API, v.trim()).apply()

    var model: String
        get() = sp.getString(KEY_MODEL, Models.FLASH) ?: Models.FLASH
        set(v) = sp.edit().putString(KEY_MODEL, v).apply()

    var effort: String
        get() = sp.getString(KEY_EFFORT, "high") ?: "high"
        set(v) = sp.edit().putString(KEY_EFFORT, v).apply()

    var web: Boolean
        get() = sp.getBoolean(KEY_WEB, false)
        set(v) = sp.edit().putBoolean(KEY_WEB, v).apply()

    /** 主题：dark / light / system。默认深色，保持老用户看到的样子 */
    var theme: String
        get() = sp.getString(KEY_THEME, ThemeMode.DARK) ?: ThemeMode.DARK
        set(v) = sp.edit().putString(KEY_THEME, v).apply()

    /** 计费规则（单价、忙时倍率、忙时时段） */
    var billing: BillingConfig
        get() = BillingConfig.fromJson(sp.getString(KEY_BILLING, null))
        set(v) = sp.edit().putString(KEY_BILLING, v.toJson()).apply()

    /** 节假日缓存。拿不到就一直是 null，按普通工作日算 */
    var holidays: HolidayData?
        get() = HolidayData.fromJson(sp.getString(KEY_HOLIDAY, null))
        set(v) {
            if (v == null) sp.edit().remove(KEY_HOLIDAY).apply()
            else sp.edit().putString(KEY_HOLIDAY, v.toJson()).apply()
        }

    /** 只在界面上露一小段，别把完整 key 摊在屏幕上 */
    fun hint(): String {
        val k = apiKey
        return when {
            k.isEmpty() -> ""
            k.length <= 12 -> k.take(3) + "…"
            else -> k.take(7) + "…" + k.takeLast(4)
        }
    }

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_EFFORT = "effort"
        const val KEY_WEB = "web_search"
        const val KEY_BILLING = "billing"
        const val KEY_HOLIDAY = "holidays"
        const val KEY_THEME = "theme"
    }
}

/** 主题模式。存的是字符串，方便以后加新选项不用改数据库 */
object ThemeMode {
    const val DARK = "dark"
    const val LIGHT = "light"
    const val SYSTEM = "system"

    val ALL = listOf(DARK, LIGHT, SYSTEM)

    fun label(v: String): String = when (v) {
        LIGHT -> "浅色"
        SYSTEM -> "跟随系统"
        else -> "深色"
    }
}

package com.example.ai.data

import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/**
 * 一年的法定节假日安排。
 *
 * - off：放假的日子（这些天全天按闲时算）
 * - work：调休补班的日子（周末但要上班，仍按工作日判断忙时）
 */
data class HolidayData(
    val year: Int,
    val off: Set<String>,
    val work: Set<String>,
    val fetchedAt: Long
) {

    fun isOff(dateKey: String): Boolean = off.contains(dateKey)

    fun isWork(dateKey: String): Boolean = work.contains(dateKey)

    fun toJson(): String = JSONObject().apply {
        put("year", year)
        put("fetchedAt", fetchedAt)
        put("off", org.json.JSONArray().apply { off.forEach { put(it) } })
        put("work", org.json.JSONArray().apply { work.forEach { put(it) } })
    }.toString()

    companion object {
        fun fromJson(raw: String?): HolidayData? {
            if (raw.isNullOrBlank()) return null
            return try {
                val o = JSONObject(raw)
                val y = o.optInt("year", 0)
                if (y <= 0) return null
                HolidayData(
                    year = y,
                    off = readSet(o, "off"),
                    work = readSet(o, "work"),
                    fetchedAt = o.optLong("fetchedAt", 0L)
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun readSet(o: JSONObject, key: String): Set<String> {
            val arr = o.optJSONArray(key) ?: return emptySet()
            val out = HashSet<String>(arr.length())
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotEmpty()) out.add(s)
            }
            return out
        }
    }
}

object HolidayCalendar {

    val TZ: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    /** yyyy-MM-dd */
    fun keyOf(now: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance(TZ)
        c.timeInMillis = now
        return String.format(
            "%04d-%02d-%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun yearOf(now: Long = System.currentTimeMillis()): Int {
        val c = Calendar.getInstance(TZ)
        c.timeInMillis = now
        return c.get(Calendar.YEAR)
    }

    fun isWeekend(now: Long = System.currentTimeMillis()): Boolean {
        val c = Calendar.getInstance(TZ)
        c.timeInMillis = now
        val wd = c.get(Calendar.DAY_OF_WEEK)
        return wd == Calendar.SATURDAY || wd == Calendar.SUNDAY
    }
}

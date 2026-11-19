package com.example.ai.net

import com.example.ai.data.HolidayData
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 拉取中国法定节假日安排。
 *
 * 用的是公开的 timor.tech 接口（免费、无需 key），返回形如：
 * { "code":0, "holiday": { "01-01": {"holiday":true,  "date":"2026-01-01"},
 *                          "01-04": {"holiday":false, "date":"2026-01-04"} } }
 * holiday=true 是放假，false 是调休补班。
 *
 * 拉不到就返回失败，界面保留上次缓存，绝不能因为第三方接口挂了用不了。
 */
class HolidayClient {

    fun fetch(year: Int): Result<HolidayData> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$BASE/$year").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) dschat")
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                return Result.failure(RuntimeException("节假日接口返回 HTTP $code"))
            }

            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)

            val apiCode = root.optInt("code", -1)
            if (apiCode != 0) {
                return Result.failure(RuntimeException("节假日接口返回 code=$apiCode"))
            }

            val map = root.optJSONObject("holiday")
                ?: return Result.failure(RuntimeException("节假日数据为空"))

            val off = HashSet<String>()
            val work = HashSet<String>()
            val keys = map.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = map.optJSONObject(key) ?: continue
                val date = item.optString("date", "").ifEmpty {
                    // 兜底：接口给的是 MM-dd
                    if (key.length == 5) "$year-$key" else ""
                }
                if (date.isEmpty()) continue

                if (item.optBoolean("holiday", false)) off.add(date) else work.add(date)
            }

            if (off.isEmpty() && work.isEmpty()) {
                return Result.failure(RuntimeException("节假日数据解析为空"))
            }

            Result.success(
                HolidayData(
                    year = year,
                    off = off,
                    work = work,
                    fetchedAt = System.currentTimeMillis()
                )
            )
        } catch (e: java.net.UnknownHostException) {
            Result.failure(RuntimeException("连不上节假日接口，检查网络"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(RuntimeException("节假日接口超时"))
        } catch (e: Exception) {
            Result.failure(RuntimeException("获取失败：${e.message ?: e.javaClass.simpleName}"))
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val BASE = "https://timor.tech/api/holiday/year"
    }
}

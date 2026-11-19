package com.example.ai.net

import com.example.ai.data.Models
import com.example.ai.data.Source
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * DeepSeek 接口直连。实测该服务对任意 Origin 都放行 CORS，
 * 所以安卓端不需要任何中间代理，key 只存在本机。
 */
class DeepSeekClient {

    @Volatile
    private var conn: HttpURLConnection? = null

    @Volatile
    private var cancelled = false

    /** 中断当前请求（停止按钮 / 新请求抢占） */
    fun cancel() {
        cancelled = true
        try {
            conn?.disconnect()
        } catch (_: Exception) {
        }
    }

    /**
     * 阻塞式流式对话，必须在 IO 线程调用。
     * 增量通过回调抛出，调用方负责切回主线程刷新界面。
     */
    fun stream(
        apiKey: String,
        model: String,
        history: List<Pair<String, String>>,
        effort: String,
        web: Boolean,
        onDelta: (text: String?, think: String?, sources: List<Source>) -> Unit,
        onUsage: (JSONObject) -> Unit
    ) {
        cancelled = false
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                history.forEach { (role, content) ->
                    put(JSONObject().put("role", role).put("content", content))
                }
            })
            put("stream", true)
            put("reasoning_effort", effort)
            put("stream_options", JSONObject().put("include_usage", true))
            // 联网：仅 V4.1 Flash 支持这两个服务端工具
            if (web && Models.supportsWeb(model)) {
                put("tools", JSONArray().apply {
                    put(JSONObject().put("type", "deepseek_web_search").put("name", "deepseek_web_search"))
                    put(JSONObject().put("type", "deepseek_read_url").put("name", "deepseek_read_url"))
                })
            }
        }

        val c = (URL(BASE + "/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 600_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "text/event-stream")
        }
        conn = c

        try {
            c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = c.responseCode
            if (code !in 200..299) {
                val raw = try {
                    c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } catch (_: Exception) {
                    ""
                }
                throw RuntimeException(describe(code, raw))
            }

            BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    if (cancelled) return
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue

                    val data = line.substring(5).trim()
                    if (data.isEmpty() || data == "[DONE]") continue

                    val obj = try {
                        JSONObject(data)
                    } catch (_: Exception) {
                        continue
                    }

                    if (obj.has("error") && !obj.isNull("error")) {
                        throw RuntimeException(errorText(obj))
                    }

                    if (obj.has("usage") && !obj.isNull("usage")) {
                        onUsage(obj.getJSONObject("usage"))
                    }

                    val choices = obj.optJSONArray("choices") ?: continue
                    val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: continue

                    val text = delta.optStringOrNull("content")
                    val think = delta.optStringOrNull("reasoning_content")
                    val srcs = delta.optJSONArray("annotations")?.let { parseSources(it) } ?: emptyList()

                    if (text != null || think != null || srcs.isNotEmpty()) {
                        onDelta(text, think, srcs)
                    }
                }
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            // 主动取消时 disconnect 会让读流抛异常，这种情况不算错误
            if (cancelled) return
            throw RuntimeException(friendlyNet(e), e)
        } finally {
            conn = null
            try {
                c.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /** 用 /models 验证 key 是否真能用，顺便区分「无效 / 欠费 / 无权限」 */
    fun verifyKey(apiKey: String): Result<Unit> {
        return try {
            val c = (URL(BASE + "/models").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            val code = c.responseCode
            c.inputStream?.close()
            c.disconnect()
            if (code in 200..299) Result.success(Unit)
            else Result.failure(RuntimeException(describe(code, "")))
        } catch (e: Exception) {
            Result.failure(RuntimeException(friendlyNet(e), e))
        }
    }

    private fun parseSources(arr: JSONArray): List<Source> {
        val out = ArrayList<Source>(arr.length())
        for (i in 0 until arr.length()) {
            val cite = arr.optJSONObject(i)?.optJSONObject("url_citation") ?: continue
            val url = cite.optStringOrNull("url") ?: continue
            out.add(Source(url = url, title = cite.optStringOrNull("title") ?: url))
        }
        return out
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun errorText(obj: JSONObject): String {
        val err = obj.opt("error")
        return when (err) {
            is JSONObject -> err.optString("message").ifEmpty { err.toString() }
            is String -> err
            else -> err?.toString() ?: "未知错误"
        }
    }

    private fun describe(code: Int, rawBody: String): String {
        val hint = when (code) {
            401 -> "（key 无效或已被删除）"
            402 -> "（余额不足，去 platform.deepseek.com 充值）"
            429 -> "（请求太频繁 / 并发超限）"
            503 -> "（DeepSeek 服务繁忙，稍后重试）"
            else -> ""
        }
        val detail = try {
            JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            rawBody.take(200)
        }
        return "DeepSeek $code$hint" + if (detail.isNotBlank()) "：$detail" else ""
    }

    private fun friendlyNet(e: Exception): String = when {
        e is java.net.UnknownHostException -> "连不上网，检查手机网络或代理"
        e is java.net.SocketTimeoutException -> "请求超时，DeepSeek 响应太慢或网络不稳"
        e is javax.net.ssl.SSLException -> "SSL 握手失败，可能有代理在中间拦截"
        else -> "网络错误：${e.message ?: e.javaClass.simpleName}"
    }

    companion object {
        const val BASE = "https://api.deepseek.com/v1"
    }
}

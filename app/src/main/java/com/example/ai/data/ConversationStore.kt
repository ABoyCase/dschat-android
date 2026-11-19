package com.example.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 会话存档。一个会话一个 json 文件，另有一份 index.json 记录标题和顺序，
 * 免得每次启动都要把所有会话完整读一遍。
 */
class ConversationStore(context: Context) {

    private val dir = File(context.filesDir, "chats").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    @Volatile
    private var index: MutableList<ConversationMeta>? = null

    @Synchronized
    fun list(): List<ConversationMeta> {
        val all = indexOrLoad()
        return all.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun newestId(): String? = list().firstOrNull()?.id

    @Synchronized
    fun load(id: String): Conversation? {
        val f = File(dir, "$id.json")
        if (!f.exists()) return null
        return try {
            conversationFromJson(JSONObject(f.readText(Charsets.UTF_8)))
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun save(conv: Conversation) {
        try {
            File(dir, "${conv.id}.json").writeText(conv.toJson().toString(), Charsets.UTF_8)
            val all = indexOrLoad()
            val meta = conv.toMeta()
            val i = all.indexOfFirst { it.id == conv.id }
            if (i >= 0) all[i] = meta else all.add(meta)
            writeIndex()
        } catch (_: Exception) {
            // 存不下就算了，不能因为落盘失败把聊天打断
        }
    }

    @Synchronized
    fun delete(id: String) {
        try {
            File(dir, "$id.json").delete()
        } catch (_: Exception) {
        }
        val all = indexOrLoad()
        all.removeAll { it.id == id }
        writeIndex()
    }

    @Synchronized
    fun rename(id: String, title: String) {
        val c = load(id) ?: return
        save(c.copy(title = title))
        val all = indexOrLoad()
        val i = all.indexOfFirst { it.id == id }
        if (i >= 0) all[i] = all[i].copy(title = title)
        writeIndex()
    }

    /** 搜标题和正文，返回命中的会话 + 一小段上下文 */
    @Synchronized
    fun search(query: String): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val out = ArrayList<SearchHit>()
        for (meta in list()) {
            val conv = load(meta.id) ?: continue
            var snippet: String? = null

            if (conv.title.contains(q, ignoreCase = true)) snippet = ""

            if (snippet == null) {
                for (m in conv.messages) {
                    val idx = m.content.indexOf(q, ignoreCase = true)
                    if (idx >= 0) {
                        val start = (idx - 10).coerceAtLeast(0)
                        val end = (idx + q.length + 26).coerceAtMost(m.content.length)
                        snippet = m.content.substring(start, end)
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        break
                    }
                }
            }

            if (snippet != null) out.add(SearchHit(meta, snippet))
        }
        return out
    }

    // ---------- index ----------

    private fun indexOrLoad(): MutableList<ConversationMeta> {
        index?.let { return it }

        val loaded = ArrayList<ConversationMeta>()
        if (indexFile.exists()) {
            try {
                val arr = JSONObject(indexFile.readText(Charsets.UTF_8)).optJSONArray("items")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id")
                        if (id.isNotEmpty() && File(dir, "$id.json").exists()) {
                            loaded.add(
                                ConversationMeta(
                                    id = id,
                                    title = o.optString("title", "新对话"),
                                    updatedAt = o.optLong("updatedAt"),
                                    count = o.optInt("count")
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // index 丢了或不全（比如手动删过文件），就扫目录重建
        val known = loaded.map { it.id }.toHashSet()
        val onDisk = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") && f.name != "index.json" }
            ?.map { it.nameWithoutExtension } ?: emptyList()

        if (onDisk.any { it !in known }) {
            for (id in onDisk) {
                if (id in known) continue
                load(id)?.let { c ->
                    loaded.add(c.toMeta())
                }
            }
        }

        index = loaded
        return loaded
    }

    private fun writeIndex() {
        val all = index ?: return
        try {
            val arr = JSONArray()
            all.forEach { m ->
                arr.put(
                    JSONObject()
                        .put("id", m.id)
                        .put("title", m.title)
                        .put("updatedAt", m.updatedAt)
                        .put("count", m.count)
                )
            }
            indexFile.writeText(JSONObject().put("items", arr).toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}

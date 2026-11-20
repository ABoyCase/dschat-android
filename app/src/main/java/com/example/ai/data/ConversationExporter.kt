package com.example.ai.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把一个会话导成纯文本。
 *
 * 走纯 txt 而不是 markdown/pdf：用户拿去存档、发人、贴进 Word 都不折腾，
 * 也不会因为字体缺字变成方块。思考过程也一并写进去，用 [思考过程] 包起来，
 * 方便事后区分哪句是推理、哪句是结论。
 */
object ConversationExporter {

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    fun buildText(conv: Conversation): String {
        val sb = StringBuilder(4096)

        sb.append("随身 AI · 对话导出").append("\n")
        sb.append("=".repeat(46)).append("\n")
        sb.append("标题：").append(conv.title).append("\n")
        sb.append("创建：").append(timeFmt.format(Date(conv.createdAt))).append("\n")
        sb.append("导出：").append(timeFmt.format(Date())).append("\n")
        sb.append("轮数：").append(conv.totals.turns).append("\n")
        sb.append("累计：").append(Pricing.fmtMoney(conv.totals.cost)).append("\n")
        sb.append("\n")

        conv.messages.forEach { m ->
            // 只有报错、没有内容的轮次没必要写进去
            if (m.content.isBlank() && m.thinking.isBlank()) return@forEach

            sb.append("-".repeat(46)).append("\n")
            sb.append(if (m.role == "user") "【我】" else "【AI】").append("\n")
            sb.append("\n")

            if (m.thinking.isNotBlank()) {
                sb.append("[思考过程]\n")
                sb.append(m.thinking.trim()).append("\n")
                sb.append("[/思考过程]\n")
                sb.append("\n")
            }

            if (m.content.isNotBlank()) {
                sb.append(m.content.trim()).append("\n")
            } else if (m.error != null) {
                sb.append("（无内容）\n")
            }

            m.usage?.let { u ->
                sb.append("\n")
                sb.append("用量：入 ").append(u.promptTokens)
                    .append(" · 出 ").append(u.completionTokens)
                if (u.reasoningTokens > 0) sb.append(" · 思考 ").append(u.reasoningTokens)
                if (u.cacheHit > 0) sb.append(" · 缓存 ").append(u.cacheHit)
                sb.append(" · ").append(Pricing.fmtMoney(u.cost)).append("\n")
            }

            if (m.error != null) {
                sb.append("\n")
                sb.append("调用失败：").append(m.error).append("\n")
            }

            sb.append("\n")
        }

        // Windows 记事本对 \n 兼容不好时才想起来要 \r\n，这里直接给全
        return sb.toString().replace("\n", "\r\n")
    }

    /** 文件名里不能出现的字符换成下划线，并限长，免得系统拒收 */
    fun fileNameOf(title: String): String {
        val safe = title
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
            .trim()
            .trimEnd('.')
            .take(40)
            .ifBlank { "对话" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date())
        return "${safe}_$stamp.txt"
    }

    /**
     * 存文件，返回给用户看的保存位置。
     *
     * Android 10 起写「下载」目录走 MediaStore，不需要任何权限。
     * 注意 RELATIVE_PATH 必须以标准目录名开头（Download/），只写子目录名会被系统拒绝。
     * 万一手厂商 ROM 上还是失败，退到应用自己的外部目录，保证导出这件事永远能成。
     */
    fun save(ctx: Context, fileName: String, text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = ctx.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
                    )
                    // 写完才让别的应用看见，避免读到写了一半的文件
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("系统拒绝创建文件")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IOException("无法写入文件")

                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null
                )
                return "下载/$FOLDER/$fileName"
            } catch (_: Exception) {
                // 落到下面的兜底
            }
        }

        @Suppress("DEPRECATION")
        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: ctx.filesDir
        val dir = File(base, "export").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private const val FOLDER = "随身AI"
}

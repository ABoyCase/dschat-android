package com.example.ai.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * 用 WebView 里跑的 KaTeX 渲染带公式的消息。
 *
 * 只有正文里真的出现 LaTeX 定界符时才用这个，普通消息一律走原生的 MarkdownBody，
 * 免得每条消息都背一个 WebView 的内存和初始化开销。
 *
 * 高度靠 JS 回报：网页里用 ResizeObserver 盯着 body 尺寸，一变就调 [AndroidBridge.onHeight]。
 * 不用 evaluateJavascript 轮询，那个既费电又慢半拍。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathWebView(
    content: String,
    fontSizeSp: Float,
    lineHeightSp: Float,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var heightDp by remember { mutableStateOf(48) }
    var pageReady by remember { mutableStateOf(false) }

    // 内容用 base64 传，绕开引号和换行的转义地狱
    val payload = remember(content) {
        Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    val themeJson = remember(fontSizeSp, lineHeightSp) {
        JSONObject().apply {
            put("fg", hex(ChatColors.Txt))
            put("dim", hex(ChatColors.Dim))
            put("line", hex(ChatColors.Line))
            put("panel2", hex(ChatColors.Panel2))
            put("codeBg", hex(ChatColors.CodeBg))
            put("codeFg", hex(ChatColors.CodeFg))
            put("accent", hex(ChatColors.Accent2))
            put("fontSize", fontSizeSp)
            put("lineHeight", lineHeightSp)
        }.toString()
    }

    val webView = remember {
        WebView(ctx).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = true
                allowContentAccess = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                // 字号完全由 CSS 决定，关掉 WebView 自己的缩放，否则会和系统字体缩放叠乘
                textZoom = 100
                loadWithOverviewMode = false
                useWideViewPort = true
                // 只加载本地资源；消息里的外链图片加载不了，但那本来就不支持
                blockNetworkLoads = true
            }

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onHeight(h: Int) {
                        post { if (h > 0) heightDp = h }
                    }
                },
                "AndroidBridge"
            )

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    pageReady = true
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    // 正文里的链接交给系统浏览器，WebView 里点开就没法返回了
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (_: Exception) {
                    }
                    return true
                }
            }

            loadUrl("file:///android_asset/katex/math.html")
        }
    }

    // 页面就绪、或者内容变了，重新渲染（不重新加载页面，省掉白屏）
    LaunchedEffect(pageReady, payload, themeJson) {
        if (pageReady) {
            webView.evaluateJavascript("renderMessage('$payload', '$themeJson');", null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webView.stopLoading()
                webView.destroy()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
    )
}

private fun hex(c: androidx.compose.ui.graphics.Color): String {
    val a = c.toArgb()
    return String.format("#%06X", 0xFFFFFF and a)
}

/**
 * 正文里有没有 LaTeX。宁可多判一点让 WebView 接手，也别漏掉让公式裸奔。
 * 单个 $ 不当公式（价格、变量名里常有），要求至少两个字符且不跨行。
 */
internal fun containsMath(text: String): Boolean {
    if (text.length < 3) return false
    if (text.contains("$$") || text.contains("\\(") || text.contains("\\[")) return true
    if (text.contains("\\begin{")) return true
    return Regex("""\$[^$\n]{2,120}\$""").containsMatchIn(text)
}

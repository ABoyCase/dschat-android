package com.example.ai

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.ai.data.ConversationStore
import com.example.ai.data.Prefs
import com.example.ai.data.ThemeMode
import com.example.ai.ui.ChatController
import com.example.ai.ui.ChatScreen
import com.example.ai.ui.theme.随身AITheme

class MainActivity : ComponentActivity() {

    private lateinit var ctrl: ChatController

    override fun onCreate(savedInstanceState: Bundle?) {
        // 首帧前先按当前设置铺好系统栏样式，避免启动时闪一下
        applySystemBars(sysInDark = isNightMode())
        super.onCreate(savedInstanceState)

        ctrl = ChatController(Prefs(this), ConversationStore(this))

        setContent {
            val dark = when (ctrl.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }

            // 用户在设置里切了主题：系统栏图标颜色跟着变
            LaunchedEffect(dark) { applySystemBars(sysInDark = dark) }

            随身AITheme(darkTheme = dark) {
                ChatScreen(ctrl)
            }
        }
    }

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * transparent 背景 + 图标明暗随主题。
     * light() 第二个参数是深色底上兜底用的 scrim，这里用同一透明色。
     */
    private fun applySystemBars(sysInDark: Boolean) {
        val transparent = Color.Transparent.toArgb()
        val status = if (sysInDark) SystemBarStyle.dark(transparent)
        else SystemBarStyle.light(transparent, transparent)
        val nav = if (sysInDark) SystemBarStyle.dark(transparent)
        else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = status, navigationBarStyle = nav)
    }

    override fun onStop() {
        // 切后台兜底存一次，防止进程被系统回收丢对话
        if (!isFinishing) ctrl.persistNow()
        super.onStop()
    }

    override fun onDestroy() {
        // 只有真正退出才释放；转屏之类的配置变化靠 configChanges 不重建
        if (isFinishing) ctrl.dispose()
        super.onDestroy()
    }
}

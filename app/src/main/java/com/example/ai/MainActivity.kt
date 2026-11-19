package com.example.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.ai.data.ConversationStore
import com.example.ai.data.Prefs
import com.example.ai.ui.ChatController
import com.example.ai.ui.ChatScreen
import com.example.ai.ui.theme.随身AITheme

class MainActivity : ComponentActivity() {

    private lateinit var ctrl: ChatController

    override fun onCreate(savedInstanceState: Bundle?) {
        // 深色背景 + 浅色系统图标，避免状态栏图标看不清
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )
        super.onCreate(savedInstanceState)

        ctrl = ChatController(Prefs(this), ConversationStore(this))

        setContent {
            随身AITheme {
                ChatScreen(ctrl)
            }
        }
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

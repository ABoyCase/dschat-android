# 随身 AI

> 一个原生 Android 的 DeepSeek 客户端。直连官方 API，按官方规则实时记账，会话只存在本机。

不是又一个网页套壳——界面是 Kotlin + Jetpack Compose 原生写的，网络层自己实现 SSE 逐行解析，没有后端、没有第三方网络库。唯一用到 WebView 的地方是数学公式排版（跑 KaTeX），而且只在正文真的出现 LaTeX 时才启用。

## 为什么做这个

用 API 聊 DeepSeek 有一个绕不开的问题：**不知道花了多少钱**。

官方 App 是包月/限额的，用 API 是按 token 计费，而且忙时闲时差一倍、缓存命中又是另一个价。等你月底看到账单，已经想不起来是哪几句话烧的钱。

所以这个客户端把每一轮的费用直接摆在消息下面：

```
入 303 · 出 919 · 思考 168 · ¥0.0077
```

## 功能

**对话**
- SSE 流式输出，思考过程和正文分开显示，可折叠
- 思考块展开时点击复制，连思考一起复制；收起时只复制结论
- 模型切换（Flash / Pro）、思考强度（少 / 标准 / 最深）
- 联网搜索（Flash 支持），回复下方列出引用的来源
- **数学公式排版**：行内和块级的 LaTeX 都能排，分式、角标、积分上下限、极限下标都是标准排版

**计费**
- 每条消息显示 token 明细与本次花费，顶栏累计整个会话的费用
- 单价可改，忙时倍率可改，忙时时段可改
- 法定节假日从网络自动同步，节假日当天按闲时计费
- 花费详情里能看到当前价档、生效时段、今日类型

**会话**
- 左侧抽屉列出历史会话，自动按首句话取名
- 新建 / 重命名 / 删除 / 搜索（标题和正文都搜）
- 存在应用私有目录，不上传服务器

**复制**
- 单条复制、多选复制
- 多选时按对话原序拼接，带上「我：」「AI：」标签

## 技术栈

Kotlin + Jetpack Compose + Material 3。**Gradle 层面没有引入任何第三方库**；公式排版用的是打包进 `assets` 的 KaTeX 静态资源（648KB），不是 Maven 依赖。

| 层 | 做法 |
|---|---|
| 网络 | `HttpURLConnection` 手写 SSE 逐行解析 |
| 存储 | 会话存 JSON 文件（一会话一文件 + 索引），设置存 SharedPreferences |
| 渲染 | 自己写的极简 Markdown 解析（标题、列表、代码块、加粗、行内代码）；含公式的消息交给 marked，额外支持表格 |
| 公式 | 正文含 LaTeX 时才切到内置 WebView 跑 KaTeX，其余消息保持原生 |
| 主题 | 深色固定，配色与桌面版保持一致 |

公式那层值得说一句实现细节：markdown 会把 `\(` 当成转义符吃掉反斜杠，所以必须先把公式和代码块抽成占位符，等 markdown 渲染完再放回去，否则 KaTeX 一个公式都找不到。WebView 的高度靠网页里 `ResizeObserver` 回调给原生，不用轮询。

## 构建

需要 JDK 17+ 和 Android SDK。

```bash
export JAVA_HOME="/path/to/Android Studio/jbr"
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

在 Android Studio 里直接 Run 即可。命令行构建如果卡在依赖下载，先 `unset HTTP_PROXY HTTPS_PROXY`。

## 和官方 chat-demo 的区别

DeepSeek 官方在 2026 年 11 月开源了 [chat-demo](https://github.com/deepseek-ai/chat-demo)（Web 版，MIT）。本项目不是它的替代，两者定位不同：

|  | 本项目 | 官方 chat-demo |
|---|---|---|
| 形态 | 原生 Android 应用 | 网页 / 加到主屏的 PWA |
| 计费 | 每条消息实时算钱，单价可自定义 | 无 |
| 存储 | 应用私有目录的 JSON 文件 | 浏览器 localStorage / IndexedDB |
| 数学公式 | 支持（KaTeX，和官方 App 观感一致） | 支持 |
| 附件、语音输入、Mermaid | 不支持 | 支持 |

要附件和语音，用官方 chat-demo。只想要一个轻快、随时知道花了多少钱的手机客户端，用这个。

## 已知限制

- 只支持 DeepSeek 官方 API，不接第三方中转
- 对话不跨设备同步（这是设计选择，不是缺陷）
- 切到后台时正在生成的回复会中断（Android 后台网络限制）
- 搜索是遍历本地文件，会话堆到几百条会变慢
- **流式输出期间公式显示 LaTeX 源码**，答完才排版——半截的 `\frac{a}{` 任何引擎都排不了
- 每条含公式的消息占一个 WebView，这类消息堆到几十条时内存会明显上去

## 免责声明

非官方项目，与 DeepSeek 官方无关联。

API Key 只保存在设备本地，不经过任何第三方服务器。App 内的费用数字按官方公开定价估算，**实际扣费以开放平台账单为准**。

## License

MIT

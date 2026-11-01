package com.liquidmate.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.liquidmate.data.AppState
import com.liquidmate.data.AppStore
import com.liquidmate.data.ChatApi
import com.liquidmate.data.AlbumEntry
import com.liquidmate.data.ChatMessage
import com.liquidmate.data.DiaryEntry
import com.liquidmate.data.MomentComment
import com.liquidmate.data.MomentEntry
import com.liquidmate.glass.LiquidBottomTab
import com.liquidmate.glass.LiquidBottomTabs
import com.liquidmate.ui.components.AvatarView
import com.liquidmate.ui.components.Glass
import com.liquidmate.ui.components.GlassDialog
import com.liquidmate.ui.components.GlassIconButton
import com.liquidmate.ui.components.LocalGlassParams
import com.liquidmate.ui.components.rememberImagePicker
import com.liquidmate.ui.icons.IconChat
import com.liquidmate.ui.icons.IconPerson
import com.liquidmate.ui.icons.IconDiary
import com.liquidmate.ui.icons.IconPlus
import com.liquidmate.ui.icons.IconSettings
import com.liquidmate.agent.AgentTools
import com.liquidmate.agent.CommandResult
import com.liquidmate.ui.screens.CharacterEditor
import com.liquidmate.ui.screens.CharacterUi
import com.liquidmate.ui.screens.ChatMsgUi
import com.liquidmate.ui.screens.ChatScreen
import com.liquidmate.ui.screens.AlbumScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.liquidmate.data.Conversation
import com.liquidmate.data.SearchApi
import com.liquidmate.ui.screens.CharactersScreen
import com.liquidmate.ui.screens.ConversationPanel
import com.liquidmate.ui.screens.DiaryScreen
import com.liquidmate.ui.screens.MomentsScreen
import com.liquidmate.ui.screens.ProfileScreen
import com.liquidmate.ui.screens.SettingsScreen
import com.liquidmate.ui.screens.AskData
import com.liquidmate.ui.screens.toBrief
import com.liquidmate.ui.screens.TogetherWebView
import com.liquidmate.ui.screens.Wallpaper
import com.liquidmate.ui.theme.LiquidMateTheme
import com.liquidmate.ui.theme.ThemeMode
import com.liquidmate.data.TtsPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_ASKS = 5

private data class TabItem(val label: String, val icon: ImageVector)

private val TAB_ITEMS = listOf(
    TabItem("聊天", IconChat),
    TabItem("伴侣", IconPerson),
    TabItem("日记", IconDiary),
    TabItem("设置", IconSettings),
)

/** AI 流式会话（记录正在回复的角色与已累积文本） */
private data class StreamingSession(
    val charId: String,
    val text: String,
    val reasoning: String = "",
)

/** 应用根：玻璃采样层（壁纸）+ UI 层 + 官方液态玻璃导航；完整版 = 本地持久化 + 真实 AI 对话 */
@Composable
fun LiquidMateApp() {
    val appContext = LocalContext.current.applicationContext
    val activityContext = LocalContext.current

    // 完整状态：启动时从本地恢复，任何变更自动落盘
    var appState by remember { mutableStateOf(AppStore.load(appContext)) }
    LaunchedEffect(appState) { AppStore.save(appContext, appState) }

    val themeMode = runCatching { ThemeMode.valueOf(appState.themeMode) }.getOrDefault(ThemeMode.System)
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    LiquidMateTheme(darkTheme = dark) {
        // 创建液态玻璃采样源（整个 App 共享一个 backdrop）
        val backdrop = rememberLayerBackdrop()

        var tab by rememberSaveable { mutableStateOf(0) }
        var editing by remember { mutableStateOf<CharacterUi?>(null) }
        // 待确认删除的角色（官方对话框样式确认弹窗）
        var confirmDelete by remember { mutableStateOf<CharacterUi?>(null) }
        var streaming by remember { mutableStateOf<StreamingSession?>(null) }
        var apiNotice by remember { mutableStateOf<String?>(null) }
        // 当前正在使用的角色（从伴侣列表「开始聊天」切换）
        var chatCharacterId by rememberSaveable { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        // 玻璃全局调参：独立于 appState，拖动滑块时只重组玻璃相关组件（不整树重组，避免卡顿）；
        // 松手后防抖 400ms 再合并进 appState 落盘。
        var glassParams by remember { mutableStateOf(appState.glass) }
        LaunchedEffect(glassParams) {
            delay(400)
            if (glassParams != appState.glass) {
                appState = appState.copy(glass = glassParams)
            }
        }

        val characters = appState.characters
        val current = characters.firstOrNull { it.id == chatCharacterId }
            ?: characters.firstOrNull()
            ?: return@LiquidMateTheme
        // 会话（每角色隔离）：当前角色的会话列表 + 当前会话消息
        val convs = appState.conversations[current.id].orEmpty()
        val activeConvId = appState.activeConv[current.id] ?: convs.firstOrNull()?.id ?: "conv_default"
        val messages = convs.firstOrNull { it.id == activeConvId }?.messages
            ?: appState.messages[current.id].orEmpty()
        val isStreaming = streaming?.charId == current.id

        // AI 一起听全屏页开关（进入时主界面整体下沉，退出后上升）
        var showTogether by rememberSaveable { mutableStateOf(false) }
        // 聊天输入时导航栏缩成小球下沉，发送后上升恢复
        var navMinimized by remember { mutableStateOf(false) }
        // AI 写日记进行中
        var aiWritingDiary by remember { mutableStateOf(false) }
        // 朋友圈：角色档案 / 相册 / 朋友圈 全屏页
        var showProfile by remember { mutableStateOf<CharacterUi?>(null) }
        var showAlbum by remember { mutableStateOf(false) }
        var showMoments by remember { mutableStateOf(false) }
        var aiFinding by remember { mutableStateOf(false) }
        var momentBusy by remember { mutableStateOf(false) }
        var commentBusy by remember { mutableStateOf(false) }
        var pendingMomentImage by remember { mutableStateOf<String?>(null) }
        val pickMomentImage = rememberImagePicker { path -> if (path != null) pendingMomentImage = path }
        // 会话列表面板（左滑打开）
        var showConvs by remember { mutableStateOf(false) }
        // 主聊天内置 Agent 工具执行状态：AI 正在执行命令时禁用输入
        var agentBusy by remember { mutableStateOf(false) }
        // AI 询问抽屉：非空时底部弹出毛玻璃抽屉等待用户回应
        var pendingAsk by remember { mutableStateOf<AskData?>(null) }
        // 已询问次数（整个会话最多 5 次）
        var askCount by remember { mutableStateOf(0) }
        // 双击退出：记录上次返回键时间戳
        var lastBackAt by remember { mutableStateOf(0L) }
        // 全屏页返回冷却：从 Agent / 一起听返回后 1 秒内，返回键只提示不计数（防误触退出）
        var backCooldown by remember { mutableStateOf(0L) }

        // 旧版单一聊天记录 → 迁移为会话（无损，只在首次升级时执行一次）
        LaunchedEffect(appState) {
            if (appState.conversations.isEmpty() && appState.messages.isNotEmpty()) {
                val migrated = appState.messages.mapValues { (_, msgs) ->
                    val now = System.currentTimeMillis()
                    listOf(Conversation("conv_default", "聊天", msgs, now, now))
                }
                appState = appState.copy(
                    conversations = migrated,
                    activeConv = migrated.keys.associateWith { "conv_default" },
                )
            }
        }

        fun updateMessages(charId: String, newList: List<ChatMsgUi>) {
            val now = System.currentTimeMillis()
            val convs = appState.conversations[charId].orEmpty()
            val act = appState.activeConv[charId] ?: convs.firstOrNull()?.id
            if (convs.isEmpty() || act == null) {
                val conv = Conversation("conv_default", "聊天", newList, now, now)
                appState = appState.copy(
                    conversations = appState.conversations + (charId to listOf(conv)),
                    activeConv = appState.activeConv + (charId to conv.id),
                )
            } else {
                appState = appState.copy(
                    conversations = appState.conversations + (charId to convs.map {
                        if (it.id == act) it.copy(messages = newList, updatedAt = now) else it
                    })
                )
            }
        }

        // 会话操作：新建 / 切换 / 重命名 / 删除（每角色隔离）
        fun newConversation() {
            val now = System.currentTimeMillis()
            val id = "conv${System.currentTimeMillis()}"
            val convs = appState.conversations[current.id].orEmpty()
            appState = appState.copy(
                conversations = appState.conversations +
                    (current.id to listOf(Conversation(id, "新对话", emptyList(), now, now)) + convs),
                activeConv = appState.activeConv + (current.id to id),
            )
            showConvs = false
            navMinimized = false
        }

        fun switchConversation(id: String) {
            appState = appState.copy(activeConv = appState.activeConv + (current.id to id))
            showConvs = false
            navMinimized = false
        }

        fun renameConversation(id: String, title: String) {
            appState = appState.copy(
                conversations = appState.conversations + (current.id to appState.conversations[current.id].orEmpty()
                    .map { if (it.id == id) it.copy(title = title) else it })
            )
        }

        fun deleteConversation(id: String) {
            val convs = appState.conversations[current.id].orEmpty().filterNot { it.id == id }
            val act = appState.activeConv[current.id]
            val newAct = if (act == id) (convs.firstOrNull()?.id ?: "conv_default") else act
            appState = appState.copy(
                conversations = appState.conversations + (current.id to convs),
                activeConv = appState.activeConv + (current.id to (newAct ?: "conv_default")),
            )
        }

        // ---------------- 主聊天内置 Agent 工具执行（shell / shizuku / accessibility / ask / read_file） ----------------
        val blockRegex = Regex("```(shell|shizuku|accessibility|ask|read_file)\\s*\\n([\\s\\S]*?)```")

        // 追加一条工具/终端消息到当前会话（命令/结果内嵌渲染）
        fun appendToolMsg(text: String) {
            val latest = appState.conversations[current.id].orEmpty()
                .firstOrNull { it.id == (appState.activeConv[current.id] ?: "") }?.messages
                ?: appState.messages[current.id].orEmpty()
            val isCmd = text.startsWith("执行 [") || text.startsWith("【工具结果】") ||
                text.startsWith("询问：") || text.startsWith("你的回答：") ||
                text.startsWith("已达到本次会话询问上限")
            updateMessages(
                current.id,
                latest + ChatMsgUi(
                    id = "t${System.currentTimeMillis()}",
                    text = text,
                    fromUser = false,
                    isCommandResult = isCmd,
                )
            )
        }

        // Agent 模式的系统提示词（角色人设 + 工具白名单 + 命令拼写要求）
        fun agentSystemPrompt(): String = buildString {
            append("你是「${current.name}」。")
            if (current.tagline.isNotBlank()) append("人设：${current.tagline}。")
            if (current.traits.isNotEmpty()) append("性格特质：${current.traits.joinToString("、")}。")
            append(
                "【重要】你运行在 Android 系统手机上（不是电脑、不是服务器）。用户让你操作手机/执行命令时，直接用 Android 命令（am start、input tap、input swipe、getprop、pm、dumpsys 等），绝不要用 Windows/macOS/Linux 桌面命令。\n" +
                    "你现在处于终端 Agent 模式，用户给你的指令你应当直接执行，而不是拒绝、推诿或说“我不能”。\n" +
                    "你拥有以下工具，通过输出代码块触发（一次回复可以多个）：\n" +
                    "1. ```shell\n命令\n```：普通 shell 命令（App 自身权限）。注意：运行环境是 Android 终端，只存在 Android 常用命令（ls、pwd、cat、echo、ps、df、free、top、du、getprop、settings、am、pm、dumpsys、wm、input、ping、curl、toybox 等），不要使用 apt、yum、ipconfig、python3、docker、systemctl 等不存在于 Android 的命令。\n" +
                    "2. ```shizuku\n命令\n```：Shizuku 特权命令（需 Shizuku 已通过 ADB 启动并授权；未授权时给出授权步骤，不要拒绝执行）。\n" +
                    "3. ```accessibility\ntap x y\n``` / ```accessibility\nswipe x1 y1 x2 y2\n```：无障碍操作（需用户在系统设置手动开启无障碍；未开启时给出开启步骤）。\n" +
                    "4. ```ask\n问题\n选项1\n选项2\n选项3\n```：需要向用户确认信息或让用户做选择时使用。第一行是问题，接下来每行一个选项（最多 3 个）。用户会从选项中选一个，也可以自定义输入回复，或选择跳过。注意：一次会话最多只能询问 5 次，能自己判断的事情就不要问。\n" +
                    "5. ```read_file\n绝对路径\n```：读取用户发送的本地文件内容（用户消息里会给出文件的绝对路径，用 read_file 工具读取后基于内容回答）。\n" +
                    "执行结果会由系统以【工具结果】回传给你。收到结果后继续分析并给出下一步，需要更多操作就继续输出代码块，任务完成后给出总结。\n" +
                    "命令必须拼写正确：写错命令会导致执行失败与反复重试，浪费大量资源。若某条命令执行失败，绝不重复尝试相同或相似的命令，立即换用其他可用命令，或直接总结告知用户。\n" +
                    "禁止说“我无法/我不能/我没有权限”这类话——权限问题一律给出明确的授权步骤，等用户完成后再继续。\n"
            )
        }

        // 回喂 AI 一次（Agent 工具上下文），返回 AI 的新回复；60 秒超时防卡死
        suspend fun chatOnceReply(userContent: String): String = withTimeoutOrNull(60_000) {
            try {
                ChatApi.chatOnce(
                    appState.api,
                    listOf(
                        ChatMessage("system", agentSystemPrompt()),
                        ChatMessage("user", userContent),
                    ),
                    maxTokens = appState.chatOptions.maxTokens,
                ).getOrDefault("")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ""
            }
        } ?: ""

        // 处理 ask 块：弹抽屉等用户回应（最多 5 次），回喂后返回 AI 回复里的新代码块
        suspend fun doAsk(cmd: String): List<MatchResult>? {
            val lines = cmd.lines().map { it.trim() }.filter { it.isNotBlank() }
            val question = lines.firstOrNull() ?: "需要向你确认一个问题"
            val options = lines.drop(1).take(3)
            appendToolMsg("询问：$question")
            askCount++
            val followUp: String
            if (askCount > MAX_ASKS) {
                appendToolMsg("已达到本次会话询问上限（$MAX_ASKS 次），不再询问")
                followUp = chatOnceReply(
                    "系统提示：你已向用户询问 $MAX_ASKS 次（达到上限）。请不要再使用 ask 工具，直接根据已有信息继续或总结。"
                )
            } else {
                val deferred = CompletableDeferred<String?>()
                pendingAsk = AskData(question, options, deferred)
                // 挂起等待用户在抽屉里选择/输入/跳过；2 分钟没回应按跳过处理，防止永久卡死
                val userAnswer = withTimeoutOrNull(120_000) { deferred.await() }
                pendingAsk = null
                val answerText = userAnswer?.takeIf { it.isNotBlank() } ?: "用户跳过了这个问题"
                appendToolMsg("你的回答：$answerText")
                followUp = chatOnceReply(
                    "你刚才向用户询问：$question\n用户的回应：$answerText\n请根据回应继续（需要更多操作就输出代码块，完成就总结）。"
                )
            }
            if (followUp.isBlank()) return null
            appendToolMsg(followUp)
            return blockRegex.findAll(followUp).toList().ifEmpty { null }
        }

        // 按队列处理代码块：主回复的块 + AI 回喂产生的新块，直至全部完成（最多 20 个块防失控）
        suspend fun drainBlocks(initial: List<MatchResult>) {
            val pending = initial.toMutableList()
            var guard = 0
            while (pending.isNotEmpty() && guard < 20) {
                guard++
                val b = pending.removeAt(0)
                val channel = b.groupValues[1]
                val cmd = b.groupValues[2].trim()
                if (channel == "ask") {
                    val next = doAsk(cmd)
                    if (next != null) pending.addAll(0, next)
                    continue
                }
                if (channel == "read_file") {
                    // 读取用户发送的文件：只显示文件名与字符数，不把全文贴进气泡
                    val fname = cmd.substringAfterLast('/').ifBlank { cmd }
                    appendToolMsg("执行 [read_file] $fname")
                    val result: CommandResult = withTimeoutOrNull(40_000) {
                        withContext(Dispatchers.IO) { AgentTools.execute(activityContext, "read_file:$cmd") }
                    } ?: CommandResult(-1, "", "读取超时（>40s），已中断", "read_file")
                    val ok = result.exitCode == 0 && result.stdout.isNotBlank()
                    appendToolMsg(if (ok) "【工具结果】已读取 $fname（${result.stdout.length} 字符），内容已交给 AI 分析"
                        else "【工具结果】读取失败：${result.stderr.ifBlank { result.stdout }}")
                    // 回喂：文件内容（长文件给足前 12000 字符），让 AI 基于内容回答
                    val followUp = chatOnceReply(
                        if (ok) {
                            "已读取用户文件「$fname」，内容如下（若被截断请基于已有部分分析）：\n${result.stdout.take(12000)}"
                        } else {
                            "读取用户文件「$fname」失败：${result.stderr.ifBlank { result.stdout }}。请直接告知用户读取失败，不要重试。"
                        }
                    )
                    if (followUp.isBlank()) continue
                    appendToolMsg(followUp)
                    val next = blockRegex.findAll(followUp).toList().ifEmpty { null }
                    if (next != null) pending.addAll(0, next)
                    continue
                }
                // 命令块执行（shell / shizuku / accessibility）
                appendToolMsg("执行 [$channel] $cmd")
                val result: CommandResult = withTimeoutOrNull(40_000) {
                    withContext(Dispatchers.IO) { AgentTools.execute(activityContext, "$channel:$cmd") }
                } ?: CommandResult(-1, "", "命令执行超时（>40s），已中断", channel)
                appendToolMsg("【工具结果】\n" + result.toDisplay())
                // 回喂 AI：只回传截断后的简要结果（大输出不完整回传，省 token）
                val followUp = chatOnceReply(
                    "上一条回复中的工具已执行，结果如下：\n${result.toBrief()}\n" +
                        "如果退出码是 127（命令不存在）或命令拼写错误，请只使用 Android 可用命令重试（白名单：ls、pwd、cat、echo、ps、df、free、top、du、getprop、settings、am、pm、dumpsys、wm、input、ping、curl、toybox），" +
                        "不要重复尝试相同或相似的命令；请继续（需要更多操作就输出代码块，完成就总结）。"
                )
                if (followUp.isBlank()) continue
                appendToolMsg(followUp)
                val next = blockRegex.findAll(followUp).toList().ifEmpty { null }
                if (next != null) pending.addAll(0, next)
            }
        }

        // 解析 AI 回复中的工具代码块并执行（主聊天内直接运行，不新开界面）
        fun runTools(reply: String) {
            val blocks = blockRegex.findAll(reply).toList()
            if (blocks.isEmpty()) return
            agentBusy = true
            scope.launch {
                try {
                    drainBlocks(blocks)
                } finally {
                    // 无论成功、异常还是中断，都复位 busy
                    agentBusy = false
                }
            }
        }
        // 发送消息 + 真实 AI 流式回复（imageDataUrl 非空时按多模态图片消息发送；attachmentName/Size 为真实附件）
        fun send(
            text: String,
            imageDataUrl: String? = null,
            attachmentName: String? = null,
            attachmentSize: Long = 0L,
            attachmentPath: String? = null,
        ) {
            if (isStreaming || agentBusy) return
            // 发文件：不再把文件内容解析成文本贴进气泡（大文件会被截断）。
            // 文件已复制到私有目录，AI 需要内容时用 read_file 工具自己读取。
            val userMsg = ChatMsgUi(
                id = "u${System.currentTimeMillis()}",
                text = text,
                fromUser = true,
                imageDataUrl = imageDataUrl,
                attachmentName = attachmentName,
                attachmentSize = attachmentSize,
                attachmentPath = attachmentPath,
            )
            val newMessages = messages + userMsg
            updateMessages(current.id, newMessages)
            apiNotice = null

            val cfg = appState.api
            if (cfg.baseUrl.isBlank() || cfg.model.isBlank()) {
                apiNotice = "尚未配置 AI 服务：请在「设置 → AI 服务」填写 API URL 与模型名"
                return
            }
            streaming = StreamingSession(current.id, "")
            scope.launch {
                val history = buildApiMessages(current, newMessages).toMutableList()
                val opts = appState.chatOptions
                // 联网搜索：用最后一条用户消息检索，把资料注入为系统上下文
                if (opts.webSearch) {
                    val q = newMessages.lastOrNull { it.fromUser }?.text?.take(128)
                    if (!q.isNullOrBlank()) {
                        val ctx = SearchApi.searchContext(cfg, q)
                        if (ctx != null) {
                            history.add(
                                ChatMessage(
                                    "system",
                                    ctx + "\n当前时间：" + java.text.SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm", java.util.Locale.CHINA
                                    ).format(java.util.Date())
                                )
                            )
                        }
                    }
                }
                val result = ChatApi.streamChat(
                    cfg,
                    history,
                    onDelta = { delta ->
                        streaming = streaming?.let { it.copy(text = it.text + delta) }
                    },
                    options = opts,
                    onReasoning = if (opts.deepThinking) { r ->
                        streaming = streaming?.let { it.copy(reasoning = it.reasoning + r) }
                    } else null,
                )
                val session = streaming
                streaming = null
                result.onSuccess {
                    if (session != null && session.text.isNotBlank()) {
                        updateMessages(
                            current.id,
                            newMessages + ChatMsgUi(
                                id = "c${System.currentTimeMillis()}",
                                text = session.text,
                                fromUser = false,
                                reasoning = session.reasoning.ifBlank { null },
                            )
                        )
                        // AI 自命名会话：新对话第一轮回复后生成标题
                        val curTitle = appState.conversations[current.id].orEmpty()
                            .firstOrNull { it.id == activeConvId }?.title
                        if (curTitle == "新对话") {
                            val firstUser = newMessages.firstOrNull { it.fromUser }?.text ?: ""
                            scope.launch {
                                val t = ChatApi.chatOnce(
                                    cfg,
                                    listOf(
                                        ChatMessage("system", "根据这段对话内容，为会话起一个 4~8 个字的标题，直接输出标题，不要引号、不要多余文字。"),
                                        ChatMessage("user", "对话内容：${firstUser.take(60)}"),
                                    ),
                                    maxTokens = 30,
                                ).getOrNull()?.trim()
                                if (!t.isNullOrBlank()) {
                                    appState = appState.copy(
                                        conversations = appState.conversations + (current.id to appState.conversations[current.id].orEmpty()
                                            .map { if (it.id == activeConvId) it.copy(title = t) else it })
                                    )
                                }
                            }
                        }
                        // 主聊天内置 Agent：解析 AI 回复里的工具代码块并执行（不新开界面）
                        runTools(session.text)
                    }
                }.onFailure { e ->
                    apiNotice = "对话失败：${e.message ?: "未知错误"}"
                }
            }
        }


        // 重新输出：删除指定 AI 消息及其命令子块后，以剩余历史重新请求（不新增用户消息）
        fun regenerate(charId: String, msgId: String) {
            if (isStreaming) return
            val convs = appState.conversations[charId].orEmpty()
            val act = appState.activeConv[charId] ?: convs.firstOrNull()?.id
            val list = convs.firstOrNull { it.id == act }?.messages ?: appState.messages[charId].orEmpty()
            // 删除整组：目标消息 + 其后紧跟的命令执行/终端返回消息
            val idsToDelete = buildList {
                val idx = list.indexOfFirst { it.id == msgId }
                if (idx >= 0) {
                    add(msgId)
                    var i = idx + 1
                    while (i < list.size && list[i].isCommandResult) {
                        add(list[i].id)
                        i++
                    }
                } else add(msgId)
            }
            val newList = list.filterNot { it.id in idsToDelete }
            updateMessages(charId, newList)
            if (newList.lastOrNull()?.fromUser != true) return
            apiNotice = null
            val cfg = appState.api
            if (cfg.baseUrl.isBlank() || cfg.model.isBlank()) {
                apiNotice = "尚未配置 AI 服务：请在「设置 → AI 服务」填写 API URL 与模型名"
                return
            }
            streaming = StreamingSession(charId, "")
            scope.launch {
                val history = buildApiMessages(current, newList)
                val opts = appState.chatOptions
                val result = ChatApi.streamChat(
                    cfg,
                    history,
                    onDelta = { delta ->
                        streaming = streaming?.let { it.copy(text = it.text + delta) }
                    },
                    options = opts,
                    onReasoning = if (opts.deepThinking) { r ->
                        streaming = streaming?.let { it.copy(reasoning = it.reasoning + r) }
                    } else null,
                )
                val session = streaming
                streaming = null
                result.onSuccess {
                    if (session != null && session.text.isNotBlank()) {
                        updateMessages(
                            charId,
                            newList + ChatMsgUi(
                                id = "c${System.currentTimeMillis()}",
                                text = session.text,
                                fromUser = false,
                                reasoning = session.reasoning.ifBlank { null },
                            )
                        )
                    }
                }.onFailure { e ->
                    apiNotice = "对话失败：${e.message ?: "未知错误"}"
                }
            }
        }

        // 用户手写日记（本地缓存）
        fun addDiary(content: String) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            appState = appState.copy(
                diaries = appState.diaries + DiaryEntry(
                    id = "d${System.currentTimeMillis()}",
                    date = today,
                    content = content,
                    author = "user",
                    createdAt = System.currentTimeMillis(),
                )
            )
        }

        // AI 代笔写今天的日记（复用全局 API 配置，一次性生成）
        fun aiWriteDiary() {
            val cfg = appState.api
            if (cfg.baseUrl.isBlank() || cfg.model.isBlank()) {
                apiNotice = "尚未配置 AI 服务：请在「设置 → AI 服务」填写 API URL 与模型名"
                return
            }
            if (aiWritingDiary) return
            aiWritingDiary = true
            scope.launch {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
                val sys = buildString {
                    append("你是 AI 伴侣「${current.name}」。")
                    if (current.tagline.isNotBlank()) append("人设：${current.tagline}。")
                    append("请以这个角色的身份写今天的日记：有生活细节和真实感，语气符合人设，100~200 字，直接写正文，不要标题、不要日期前缀。")
                }
                val result = ChatApi.chatOnce(
                    cfg,
                    listOf(
                        ChatMessage("system", sys),
                        ChatMessage("user", "请写今天的日记（日期：$today）"),
                    ),
                    maxTokens = 300,
                )
                result.onSuccess { reply ->
                    val r = (reply ?: "").trim()
                    if (r.isNotEmpty()) {
                        appState = appState.copy(
                            diaries = appState.diaries + DiaryEntry(
                                id = "d${System.currentTimeMillis()}",
                                date = today,
                                content = r,
                                author = "ai",
                                authorId = current.id,
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                    } else {
                        apiNotice = "AI 写日记失败：模型未返回内容"
                    }
                }.onFailure { e ->
                    apiNotice = "AI 写日记失败：${e.message ?: "未知错误"}"
                }
                aiWritingDiary = false
            }
        }

        // ---------------- 朋友圈：相册 / 动态 / 评论（AI 逻辑） ----------------

        // AI 找图：AI 生成关键词 → 本地图片搜索接口拿链接 → 下载到相册（用户立即可见）
        val imageHttp by remember { mutableStateOf(OkHttpClient.Builder().connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()) }
        // 朋友圈/相册共用：按关键词在本地图片搜索接口取一张图（直接返回远程 URL）
        fun findMomentImage(kw: String): String? = runCatching {
            val req = Request.Builder()
                .url("http://127.0.0.1:8080/api/images?q=" + java.net.URLEncoder.encode(kw, "UTF-8") + "&n=1")
                .build()
            imageHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val obj = org.json.JSONObject(resp.body?.string() ?: "{}")
                val list = obj.optJSONArray("list") ?: org.json.JSONArray()
                if (list.length() == 0) return@use null
                val u = list.getJSONObject(0).optString("url", "")
                if (u.isBlank()) null else u
            }
        }.getOrNull()

        fun downloadImage(url: String): String? = runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .header("Referer", "https://duckduckgo.com/")
                .build()
            imageHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                if (bytes.isEmpty()) return@use null
                val dir = java.io.File(appContext.filesDir, "albums").apply { mkdirs() }
                val f = java.io.File(dir, "img${System.currentTimeMillis()}.jpg")
                f.writeBytes(bytes)
                f.absolutePath
            }
        }.getOrNull()

        fun aiFindImages() {
            val ch = showProfile ?: return
            val cfg = appState.api
            if (cfg.baseUrl.isBlank() || cfg.model.isBlank()) {
                apiNotice = "尚未配置 AI 服务：请在「设置 → AI 服务」填写 API URL 与模型名"
                return
            }
            if (aiFinding) return
            aiFinding = true
            scope.launch {
                var added = 0
                val result = ChatApi.chatOnce(
                    cfg,
                    listOf(
                        ChatMessage(
                            "system",
                            "你是图片策划。请为角色「${ch.name}」（${ch.tagline}）的相册推荐 2 个图片主题关键词，用中文、逗号分隔输出，不要任何多余文字。示例：海边日落, 街角咖啡店",
                        ),
                        ChatMessage("user", "请给出关键词"),
                    ),
                    maxTokens = 60,
                )
                val kws = result.getOrNull()
                    ?.split(Regex("[,，、\n]"))
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.take(2)
                    ?: listOf(ch.name)
                for (kw in kws) {
                    runCatching {
                        val listReq = Request.Builder()
                            .url("http://127.0.0.1:8080/api/images?q=" + java.net.URLEncoder.encode(kw, "UTF-8") + "&n=2")
                            .build()
                        imageHttp.newCall(listReq).execute().use { resp ->
                            val obj = org.json.JSONObject(resp.body?.string() ?: "{}")
                            val list = obj.optJSONArray("list") ?: org.json.JSONArray()
                            for (i in 0 until list.length()) {
                                if (added >= 3) break
                                val url = list.getJSONObject(i).optString("url", "")
                                if (url.isBlank()) continue
                                val saved = downloadImage(url)
                                if (saved != null) {
                                    val prev = appState.albums[ch.id].orEmpty()
                                    appState = appState.copy(
                                        albums = appState.albums + (ch.id to prev + AlbumEntry(
                                            id = "a${System.currentTimeMillis()}",
                                            path = saved,
                                            caption = kw,
                                            createdAt = System.currentTimeMillis(),
                                        ))
                                    )
                                    added++
                                }
                            }
                        }
                    }
                }
                if (added == 0) apiNotice = "AI 找图失败：没有下载到图片（检查网络）"
                else apiNotice = "AI 找图完成：已添加 $added 张到相册"
                aiFinding = false
            }
        }

        // AI 发一条朋友圈（可指定任意角色 → 拉其他 AI 一起发）
        fun aiPostMoment(characterId: String) {
            val ch = characters.firstOrNull { it.id == characterId } ?: return
            val cfg = appState.api
            if (cfg.baseUrl.isBlank() || cfg.model.isBlank()) {
                apiNotice = "尚未配置 AI 服务：请在「设置 → AI 服务」填写 API URL 与模型名"
                return
            }
            if (momentBusy) return
            momentBusy = true
            scope.launch {
                val result = ChatApi.chatOnce(
                    cfg,
                    listOf(
                        ChatMessage(
                            "system",
                            "你是「${ch.name}」。${ch.tagline} 请以这个角色的身份发一条朋友圈动态：一句话或一小段，有生活感、符合人设，直接输出正文，不要引号包裹、不要表情符号之外的装饰。",
                        ),
                        ChatMessage("user", "发一条朋友圈"),
                    ),
                    maxTokens = 120,
                )
                result.onSuccess { r ->
                    val c = (r ?: "").trim()
                    if (c.isNotEmpty()) {
                        val mid = "m${System.currentTimeMillis()}"
                        appState = appState.copy(
                            moments = appState.moments + MomentEntry(
                                id = mid,
                                authorId = ch.id,
                                authorName = ch.name,
                                content = c,
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                        // AI 自动找 1 张图并下载到本地，挂到这条动态（不阻塞主流程）
                        scope.launch {
                            val url = findMomentImage(kw = ch.name + " " + ch.tagline) ?: return@launch
                            val saved = downloadImage(url)
                            if (saved != null) {
                                appState = appState.copy(
                                    moments = appState.moments.map {
                                        if (it.id == mid) it.copy(imagePaths = it.imagePaths + saved) else it
                                    }
                                )
                            }
                        }
                    } else apiNotice = "AI 发动态失败：模型未返回内容"
                }.onFailure { e ->
                    apiNotice = "AI 发动态失败：${e.message ?: "未知错误"}"
                }
                momentBusy = false
            }
        }

        // AI 评论某条朋友圈（当前角色身份；与 AI 发动态互不阻塞，保证"自动评论"不被吞）
        fun aiCommentMoment(momentId: String) {
            val m = appState.moments.firstOrNull { it.id == momentId } ?: return
            val ch = current
            val cfg = appState.api
            if (cfg.baseUrl.isBlank() || cfg.model.isBlank()) return
            if (commentBusy) return
            commentBusy = true
            scope.launch {
                val result = ChatApi.chatOnce(
                    cfg,
                    listOf(
                        ChatMessage(
                            "system",
                            "你是「${ch.name}」。${ch.tagline} 请以这个角色的身份，评论这条朋友圈（一句话，简短自然，符合人设，直接输出评论内容）。",
                        ),
                        ChatMessage("user", "朋友圈动态：${m.authorName} 说：${m.content}"),
                    ),
                    maxTokens = 80,
                )
                result.onSuccess { r ->
                    val c = (r ?: "").trim()
                    if (c.isNotEmpty()) {
                        val updated = appState.moments.map {
                            if (it.id == momentId) it.copy(
                                comments = it.comments + MomentComment(
                                    id = "mc${System.currentTimeMillis()}",
                                    authorId = ch.id,
                                    authorName = ch.name,
                                    content = c,
                                    createdAt = System.currentTimeMillis(),
                                )
                            ) else it
                        }
                        appState = appState.copy(moments = updated)
                    }
                }.onFailure { }
                commentBusy = false
            }
        }


        // 用户发朋友圈（可带图）；发完当前角色 AI 自动评论
        fun postMoment(content: String, imagePath: String?) {
            val id = "m${System.currentTimeMillis()}"
            appState = appState.copy(
                moments = appState.moments + MomentEntry(
                    id = id,
                    authorId = "user",
                    authorName = "我",
                    content = content,
                    imagePaths = if (imagePath != null) listOf(imagePath) else emptyList(),
                    createdAt = System.currentTimeMillis(),
                )
            )
            aiCommentMoment(id)
        }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // —— 玻璃采样层：壁纸（液态玻璃模糊/折射的来源）——
            WallpaperLayer(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                appState.wallpaper,
            )

            // —— UI 层（玻璃元素采样壁纸层）——
            // 全局液态玻璃调参（游乐场）作用于整个 UI 树（含底部导航）
            CompositionLocalProvider(LocalGlassParams provides glassParams) {
            // 一起听全屏页（主界面整体下沉让位）
            AnimatedVisibility(
                visible = showTogether,
                modifier = Modifier.fillMaxSize(),
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(320)) +
                    fadeIn(animationSpec = tween(320)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) +
                    fadeOut(animationSpec = tween(300)),
            ) {
                TogetherWebView(
                    characterName = current.name,
                    persona = buildString {
                        if (current.tagline.isNotBlank()) append(current.tagline)
                        if (current.traits.isNotEmpty()) {
                            if (isNotEmpty()) append("；")
                            append(current.traits.joinToString("、"))
                        }
                    },
                    configProvider = { appState.api },
                    onExit = { showTogether = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // 主界面（含底部导航栏，同一图层）：输入时导航栏缩成小球下沉，发送后恢复
            // 一起听全屏时主界面整体滑出销毁，避免被覆盖在下面
            AnimatedVisibility(
                visible = !showTogether,
                modifier = Modifier.fillMaxSize(),
                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(320)) +
                    fadeIn(animationSpec = tween(320)),
                exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(300)) +
                    fadeOut(animationSpec = tween(300)),
            ) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 4.dp)) {
                    // Tab 切换动画：滑入 + 淡入 + 轻微缩放（方向跟随 tab 序号）
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(animationSpec = tween(340, easing = FastOutSlowInEasing)) { it * dir } +
                                fadeIn(animationSpec = tween(240))) togetherWith
                                (slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { -it * dir } +
                                    fadeOut(animationSpec = tween(200)))
                        },
                        label = "tab",
                    ) { t ->
                    when (t) {
                        0 -> ChatScreen(
                            backdrop = backdrop,
                            character = current,
                            messages = messages,
                            isStreaming = isStreaming,
                            streamingText = streaming?.text ?: "",
                            streamingReasoning = streaming?.reasoning ?: "",
                            apiNotice = apiNotice,
                            onSend = { t, img, an, asz, ap -> send(t, img, an, asz, ap) },
                            onDeleteMessage = { msgId ->
                                // 删除整组：目标消息 + 其后紧跟的命令执行/终端返回消息
                                val idsToDelete = buildList {
                                    val idx = messages.indexOfFirst { it.id == msgId }
                                    if (idx >= 0) {
                                        add(msgId)
                                        var i = idx + 1
                                        while (i < messages.size && messages[i].isCommandResult) {
                                            add(messages[i].id)
                                            i++
                                        }
                                    } else add(msgId)
                                }
                                updateMessages(current.id, messages.filterNot { it.id in idsToDelete })
                            },
                            onRegenerate = { msgId -> regenerate(current.id, msgId) },
                            onTogetherClick = { showTogether = true },
                            onInputFocus = { navMinimized = it },
                            onOpenConversations = { showConvs = true },
                            tts = appState.tts,
                            onTtsChange = { appState = appState.copy(tts = it) },
                            chatOptions = appState.chatOptions,
                            onOptionsChange = { appState = appState.copy(chatOptions = it) },
                            agentBusy = agentBusy,
                            pendingAsk = pendingAsk,
                            onAskAnswer = { ans ->
                                pendingAsk?.deferred?.complete(ans)
                                pendingAsk = null
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        1 -> Column(Modifier.fillMaxSize()) {
                            // 伴侣页顶部：标题 + 当前角色头像（档案入口）+ 创建按钮
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "我的伴侣",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "${characters.size} 位 AI 伴侣 · 滑动卡片列表",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // 当前角色头像 → 点击进入全屏档案（相册 / 朋友圈）
                                Box(
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { showProfile = current },
                                    ),
                                ) {
                                    AvatarView(current.avatarPath, current.seed, current.name, 42.dp)
                                }
                                Spacer(Modifier.width(8.dp))
                                GlassIconButton(
                                    backdrop,
                                    IconPlus,
                                    "创建角色",
                                    onClick = { editing = CharacterUi.new() },
                                    size = 42.dp,
                                    tint = Glass.accent.copy(alpha = 0.25f),
                                )
                            }
                            // AndroidLiquidGlass 原生玻璃卡片列表（非 HTML 容器）
                            CharactersScreen(
                                backdrop = backdrop,
                                characters = characters,
                                activeId = current.id,
                                onUse = { c ->
                                    chatCharacterId = c.id
                                    tab = 0
                                },
                                onEdit = { editing = it },
                                onCreate = { editing = CharacterUi.new() },
                                onDelete = { confirmDelete = it },
                                onProfile = { showProfile = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                showHeader = false,
                            )
                        }

                        2 -> DiaryScreen(
                            backdrop = backdrop,
                            character = current,
                            characters = characters,
                            diaries = appState.diaries,
                            aiWriting = aiWritingDiary,
                            onAdd = { content -> addDiary(content) },
                            onDelete = { id ->
                                appState = appState.copy(diaries = appState.diaries.filterNot { it.id == id })
                            },
                            onAiWrite = { aiWriteDiary() },
                            modifier = Modifier.fillMaxSize(),
                        )

                        3 -> SettingsScreen(
                            backdrop = backdrop,
                            wallpaper = appState.wallpaper,
                            onWallpaperChange = { appState = appState.copy(wallpaper = it) },
                            themeMode = themeMode,
                            onThemeModeChange = { appState = appState.copy(themeMode = it.name) },
                            api = appState.api,
                            onApiChange = { appState = appState.copy(api = it) },
                            glass = glassParams,
                            onGlassChange = { glassParams = it },
                            tts = appState.tts,
                            onTtsChange = { appState = appState.copy(tts = it) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    }
                }

                // 角色编辑浮层（进入/退出动画）
                AnimatedVisibility(
                    visible = editing != null,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(animationSpec = tween(240)) +
                        scaleIn(initialScale = 0.92f, animationSpec = tween(240)),
                    exit = fadeOut(animationSpec = tween(200)) +
                        scaleOut(targetScale = 0.92f, animationSpec = tween(200)),
                ) {
                    editing?.let { e ->
                        CharacterEditor(
                            backdrop = backdrop,
                            initial = e,
                            onSave = { saved ->
                                appState = if (saved.id.isEmpty()) {
                                    appState.copy(
                                        characters = characters + saved.copy(id = "c${System.currentTimeMillis()}")
                                    )
                                } else {
                                    appState.copy(
                                        characters = characters.map { if (it.id == saved.id) saved else it }
                                    )
                                }
                                editing = null
                            },
                            onCancel = { editing = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // 删除角色确认对话框（开源项目官方 DialogContent 样式）
                AnimatedVisibility(
                    visible = confirmDelete != null,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(animationSpec = tween(240)) +
                        scaleIn(initialScale = 0.94f, animationSpec = tween(240)),
                    exit = fadeOut(animationSpec = tween(200)) +
                        scaleOut(targetScale = 0.94f, animationSpec = tween(200)),
                ) {
                    confirmDelete?.let { c ->
                        GlassDialog(
                            backdrop = backdrop,
                            title = "删除角色",
                            message = "确定删除「${c.name}」及其全部聊天记录？此操作不可恢复。",
                            confirmText = "删除",
                            confirmTint = Color(0xFFFF5252),
                            onConfirm = {
                                appState = appState.copy(
                                    characters = characters - c,
                                    messages = appState.messages - c.id,
                                )
                                confirmDelete = null
                            },
                            onDismiss = { confirmDelete = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // —— 底部液态玻璃导航栏（开源项目官方 LiquidBottomTabs 组件）——
                // 作为主界面 Box 的子元素（BoxScope），附着在主界面同一图层底部；
                // 聊天输入时缩成小球下沉，发送后小球上升恢复为导航栏
                val navScale by animateFloatAsState(
                    if (navMinimized) 0.15f else 1f,
                    tween(360, easing = FastOutSlowInEasing),
                    label = "navScale",
                )
                val navAlpha by animateFloatAsState(
                    if (navMinimized) 0.85f else 1f,
                    tween(300),
                    label = "navAlpha",
                )
                val navY by animateDpAsState(
                    if (navMinimized) 76.dp else 0.dp,
                    tween(360, easing = FastOutSlowInEasing),
                    label = "navY",
                )
                // 布局占位随动画真实收起/展开：输入时导航栏缩成小球并让出底部空间，
                // 聊天页（imePadding）随即占满全屏，输入栏紧贴键盘；发送后小球上升恢复导航栏
                AnimatedVisibility(
                    visible = !navMinimized,
                    enter = expandVertically(
                        animationSpec = tween(360, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Bottom,
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(
                        animationSpec = tween(360, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Bottom,
                    ) + fadeOut(animationSpec = tween(240)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = navScale
                                scaleY = navScale
                                alpha = navAlpha
                                translationY = navY.toPx()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        LiquidBottomTabs(
                            selectedTabIndex = { tab },
                            onTabSelected = { tab = it },
                            backdrop = backdrop,
                            tabsCount = TAB_ITEMS.size,
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            TAB_ITEMS.forEachIndexed { index, item ->
                                val selected = index == tab
                                LiquidBottomTab({ tab = index }) {
                                    Icon(
                                        item.icon,
                                        contentDescription = item.label,
                                        tint = if (selected) Glass.accent
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) Glass.accent
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
            // —— 角色档案 / 相册 / 朋友圈 全屏浮层（盖住导航栏）——
            AnimatedVisibility(
                visible = showProfile != null,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(animationSpec = tween(240)) + scaleIn(initialScale = 0.96f, animationSpec = tween(240)),
                exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.96f, animationSpec = tween(200)),
            ) {
                showProfile?.let { p ->
                    ProfileScreen(
                        backdrop = backdrop,
                        character = p,
                        albumCount = appState.albums[p.id].orEmpty().size,
                        momentCount = appState.moments.count { it.authorId == p.id },
                        onBack = { showProfile = null },
                        onOpenAlbum = { showAlbum = true },
                        onOpenMoments = { showMoments = true },
                        onEdit = {
                            showProfile = null
                            editing = p
                        },
                        onDelete = {
                            showProfile = null
                            confirmDelete = p
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            AnimatedVisibility(
                visible = showAlbum,
                modifier = Modifier.fillMaxSize(),
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(260)) + fadeOut(animationSpec = tween(260)),
            ) {
                showProfile?.let { p ->
                    AlbumScreen(
                        backdrop = backdrop,
                        character = p,
                        entries = appState.albums[p.id].orEmpty(),
                        aiFinding = aiFinding,
                        onAiFind = { aiFindImages() },
                        onDelete = { id ->
                            appState = appState.copy(
                                albums = appState.albums + (p.id to appState.albums[p.id].orEmpty().filterNot { it.id == id })
                            )
                        },
                        onBack = { showAlbum = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            AnimatedVisibility(
                visible = showMoments,
                modifier = Modifier.fillMaxSize(),
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(260)) + fadeOut(animationSpec = tween(260)),
            ) {
                MomentsScreen(
                    backdrop = backdrop,
                    character = current,
                    characters = characters,
                    moments = appState.moments,
                    busy = momentBusy,
                    pickedImage = pendingMomentImage,
                    onPickImage = { pickMomentImage() },
                    onPost = { c, img -> postMoment(c, img); pendingMomentImage = null },
                    onAiPost = { id -> aiPostMoment(id) },
                    onComment = { mid, c ->
                        appState = appState.copy(
                            moments = appState.moments.map {
                                if (it.id == mid) it.copy(
                                    comments = it.comments + MomentComment(
                                        id = "mc${System.currentTimeMillis()}",
                                        authorId = "user",
                                        authorName = "我",
                                        content = c,
                                        createdAt = System.currentTimeMillis(),
                                    )
                                ) else it
                            }
                        )
                    },
                    onAiComment = { mid -> aiCommentMoment(mid) },
                    onDeleteMoment = { mid ->
                        appState = appState.copy(moments = appState.moments.filterNot { it.id == mid })
                    },
                    onBack = { showMoments = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // —— 会话列表面板（左滑打开；返回键关闭）——
            AnimatedVisibility(
                visible = showConvs,
                modifier = Modifier.fillMaxSize(),
                enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(320)) +
                    fadeIn(animationSpec = tween(260)),
                exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(280)) +
                    fadeOut(animationSpec = tween(220)),
            ) {
                ConversationPanel(
                    backdrop = backdrop,
                    character = current,
                    conversations = appState.conversations[current.id].orEmpty(),
                    activeId = appState.activeConv[current.id]
                        ?: appState.conversations[current.id].orEmpty().firstOrNull()?.id,
                    onNew = { newConversation() },
                    onSwitch = { switchConversation(it) },
                    onRename = { id, t -> renameConversation(id, t) },
                    onDelete = { deleteConversation(it) },
                    onClose = { showConvs = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // —— 返回键分层：浮层逐层回退，最底层才退出应用 ——
            BackHandler(enabled = editing != null) { editing = null }
            BackHandler(enabled = confirmDelete != null) { confirmDelete = null }
            BackHandler(enabled = showProfile != null) { showProfile = null }
            BackHandler(enabled = showAlbum) { showAlbum = false }
            BackHandler(enabled = showMoments) { showMoments = false }
            BackHandler(enabled = showTogether) {
                showTogether = false
                backCooldown = System.currentTimeMillis()
            }
            BackHandler(enabled = showConvs && !showTogether) { showConvs = false }
            // 最底层：无任何浮层/全屏页时，再按一次退出（2 秒内双击退出，首次提示）
            BackHandler(
                enabled = !showTogether && editing == null &&
                    confirmDelete == null && showProfile == null &&
                    !showAlbum && !showMoments && !showConvs
            ) {
                val now = System.currentTimeMillis()
                when {
                    // 刚从全屏页返回（1 秒内）：只提示，不计数、不退出
                    now - backCooldown < 1000L -> {
                        Toast.makeText(activityContext, "已返回", Toast.LENGTH_SHORT).show()
                    }
                    // 2 秒内再次按返回：真正退出
                    now - lastBackAt < 2000L -> {
                        (activityContext as? Activity)?.finishAffinity()
                    }
                    // 首次按返回：提示"再按一次退出"
                    else -> {
                        lastBackAt = now
                        Toast.makeText(activityContext, "再按一次退出", Toast.LENGTH_LONG).show()
                    }
                }
            }
            }
            } // CompositionLocalProvider（全局玻璃调参）
        }
    }
}

/** 把角色人设 + 最近聊天记录组装成 API 消息（system 人设 + 最近 40 条历史） */
private fun buildApiMessages(character: CharacterUi, history: List<ChatMsgUi>): List<ChatMessage> {
    val system = buildString {
        append("你是 AI 伴侣「${character.name}」。")
        if (character.tagline.isNotBlank()) append("人设：${character.tagline}。")
        if (character.traits.isNotEmpty()) append("性格特质：${character.traits.joinToString("、")}。")
        append("请始终以这个角色身份自然地和用户聊天，语气符合人设，回复简洁、有温度。")
        append(
            "【重要】你运行在 Android 系统手机上（不是电脑、不是服务器、不是网页）。" +
                "用户让你打开应用、执行命令、操作手机时，直接用 Android 可用命令（am start、input tap、input swipe、getprop、pm、dumpsys 等），" +
                "绝不要使用 Windows/macOS/Linux 桌面命令（apt、yum、ipconfig、python3、docker、systemctl、open 等）。\n" +
                "你同时拥有手机操作工具：当用户让你打开应用、执行命令、查看系统信息、操作手机时，" +
                "不要拒绝、推诿或说“我不会/我不能/我做不到”，直接输出工具代码块让系统执行：\n" +
                "1. ```shell\n命令\n```：普通 shell 命令。运行环境是 Android 终端，只存在 Android 常用命令" +
                "（ls、pwd、cat、echo、ps、df、free、top、du、getprop、settings、am、pm、dumpsys、wm、input、ping、curl、toybox 等），" +
                "打开应用用 am start 或 am start -a android.intent.action.VIEW -d 应用URL，不要使用 apt、python3、docker、systemctl 等不存在于 Android 的命令。\n" +
                "2. ```shizuku\n命令\n```：Shizuku 特权命令（需 Shizuku 已通过 ADB 启动并授权；未授权时给出授权步骤，不要拒绝执行）。\n" +
                "3. ```accessibility\ntap x y\n``` / ```accessibility\nswipe x1 y1 x2 y2\n```：无障碍点击/滑动（需用户在系统设置手动开启无障碍；未开启时给出开启步骤）。\n" +
                "4. ```ask\n问题\n选项1\n选项2\n选项3\n```：需要向用户确认信息或让用户做选择时使用（第一行问题，后续每行一个选项，最多 3 个；一次会话最多问 5 次，能自己判断的事情就不要问）。\n" +
                "5. ```read_file\n绝对路径\n```：读取用户发送的本地文件（用户消息里会给出文件的绝对路径，需要内容时用 read_file 读取后基于内容回答；路径不要展示给用户）。\n" +
                "执行结果会由系统以【工具结果】回传给你，收到后继续分析，需要更多操作就继续输出代码块，任务完成后总结。\n" +
                "命令必须拼写正确：写错命令会导致执行失败与反复重试，浪费大量资源。若某条命令执行失败，绝不重复尝试相同或相似的命令，立即换用其他可用命令，或直接总结告知用户。\n" +
                "禁止说“我无法/我不能/我没有权限”这类话——权限问题一律给出明确的授权步骤，等用户完成后再继续。\n" +
                "日常闲聊时正常对话，不要强行使用工具。"
        )
    }
    val historyMessages = history.filterNot { it.isCommandResult }.takeLast(40).map {
        val base = if (it.fromUser && it.attachmentPath != null) {
            // 附件的真实路径只放进模型上下文（不显示在气泡里），AI 需要时用 read_file 工具读取
            it.text + "\n（用户发来文件：${it.attachmentName ?: "附件"}，绝对路径：${it.attachmentPath}。需要内容时用 read_file 工具读取，路径不要展示给用户看。）"
        } else it.text
        ChatMessage(
            role = if (it.fromUser) "user" else "assistant",
            content = base,
            imageDataUrl = if (it.fromUser) it.imageDataUrl else null,
        )
    }
    return listOf(ChatMessage("system", system)) + historyMessages
}

/** 壁纸层：渐变预设 或 本地导入图片，另附柔和光斑让液态玻璃折射更明显 */
@Composable
private fun WallpaperLayer(modifier: Modifier, wallpaper: Wallpaper) {
    Box(modifier) {
        when (wallpaper) {
            is Wallpaper.Gradient -> {
                val colors = Wallpaper.Presets[wallpaper.index % Wallpaper.Presets.size]
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)))
            }

            is Wallpaper.Local -> {
                val bmp = remember(wallpaper.path) {
                    BitmapFactory.decodeFile(wallpaper.path)?.asImageBitmap()
                }
                if (bmp != null) {
                    Image(
                        bmp,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(Wallpaper.Presets[0])
                        )
                    )
                }
            }
        }
        // 装饰光斑（让玻璃的模糊与折射更明显）
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-50).dp)
                .background(Brush.radialGradient(listOf(Color(0x663D8BFF), Color.Transparent)))
        )
        Box(
            Modifier
                .size(280.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-90).dp, y = 70.dp)
                .background(Brush.radialGradient(listOf(Color(0x667C6CFF), Color.Transparent)))
        )
        Box(
            Modifier
                .size(180.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 50.dp)
                .background(Brush.radialGradient(listOf(Color(0x33FFA63D), Color.Transparent)))
        )
    }
}

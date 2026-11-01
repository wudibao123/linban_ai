package com.liquidmate.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.provider.OpenableColumns
import com.kyant.backdrop.Backdrop
import com.liquidmate.agent.CommandResult
import com.liquidmate.data.ChatOptions
import com.liquidmate.data.TtsApi
import com.liquidmate.data.TtsConfig
import com.liquidmate.data.TtsPlayer
import com.liquidmate.ui.components.AvatarView
import com.liquidmate.ui.components.Glass
import com.liquidmate.ui.components.ImagePreviewOverlay
import com.liquidmate.ui.components.GlassChip
import com.liquidmate.ui.components.GlassIconButton
import com.liquidmate.ui.components.LiquidSlider
import com.liquidmate.ui.components.LiquidToggle
import com.liquidmate.ui.components.liquidGlass
import com.liquidmate.ui.icons.IconCopy
import com.liquidmate.ui.icons.IconDelete
import com.liquidmate.ui.icons.IconFile
import com.liquidmate.ui.icons.IconImage
import com.liquidmate.ui.icons.IconMusicNote
import com.liquidmate.ui.icons.IconPlus
import com.liquidmate.ui.icons.IconRefresh
import com.liquidmate.ui.icons.IconSend
import com.liquidmate.ui.icons.IconSparkles
import com.liquidmate.ui.icons.IconVolumeOff
import com.liquidmate.ui.icons.IconVolumeUp
import com.liquidmate.ui.icons.IconWarning
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** 角色（可序列化，随聊天记录一同保存在本地） */
@Serializable
data class CharacterUi(
    val id: String,
    val name: String,
    val seed: Int,
    val avatarPath: String?,
    val tagline: String,
    val traits: List<String>,
) {
    companion object {
        /** 新建角色：id 为空，保存时才分配 */
        fun new(): CharacterUi = CharacterUi(
            id = "",
            name = "",
            seed = 0,
            avatarPath = null,
            tagline = "",
            traits = emptyList(),
        )
    }
}

/** 单条聊天消息（可序列化）；imageDataUrl 为本地压缩后的 data URL（发图/PDF 预览给视觉模型），attachmentName/Size 为真实附件信息 */
@Serializable
data class ChatMsgUi(
    val id: String,
    val text: String,
    val fromUser: Boolean,
    val imageDataUrl: String? = null,
    val attachmentName: String? = null,
    val attachmentSize: Long = 0L,
    val attachmentPath: String? = null,
    val reasoning: String? = null,
    val isCommandResult: Boolean = false,
)

/** AI 向用户发起的一次询问：问题 + 选项，等待用户回应 */
data class AskData(
    val question: String,
    val options: List<String>,
    val deferred: kotlinx.coroutines.CompletableDeferred<String?>,
)

/** 解码消息里的 data URL 图片 */
private fun decodeImageDataUrl(url: String): ImageBitmap? = runCatching {
    val base64 = url.substringAfter("base64,", "")
    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

/** 压缩 Bitmap 为 JPEG data URL */
private fun compressBitmap(bmp: Bitmap, maxSide: Int = 1024, quality: Int = 85): String {
    val scale = minOf(1f, maxSide.toFloat() / max(bmp.width, bmp.height).toFloat())
    val out = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * scale).toInt().coerceAtLeast(1),
            (bmp.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else bmp
    val bos = java.io.ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.JPEG, quality, bos)
    if (scale < 1f && out !== bmp) bmp.recycle()
    return "data:image/jpeg;base64," +
        android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
}

/** 从 Uri 读图并压缩 */
private fun compressUriToDataUrl(cr: android.content.ContentResolver, uri: android.net.Uri): String? = runCatching {
    val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    compressBitmap(bmp)
}.getOrNull()

/** 渲染 PDF 第一页为图片（视觉模型可看） */
private fun renderPdfFirstPage(cr: android.content.ContentResolver, uri: android.net.Uri): String? = runCatching {
    val fd = cr.openFileDescriptor(uri, "r") ?: return null
    fd.use { pfd ->
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        if (renderer.pageCount <= 0) {
            renderer.close()
            return null
        }
        val page = renderer.openPage(0)
        val w = minOf(page.width, 1024)
        val h = (page.height * w.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        compressBitmap(bmp)
    }
}.getOrNull()

private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "xml", "csv", "log", "kt", "kts", "java", "py",
    "js", "ts", "html", "css", "sql", "yml", "yaml", "ini", "sh", "bat", "toml",
    "gradle", "properties", "cfg", "conf", "env", "gitignore",
)

private fun isTextFile(name: String, mime: String): Boolean =
    mime.startsWith("text/") || name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS

private fun formatSize(bytes: Long): String =
    if (bytes > 1024 * 1024) String.format("%.1f MB", bytes / 1024f / 1024f)
    else "${max(1, bytes / 1024)} KB"

/** 气泡内容：附件卡片（如有）+ 图片（如有）+ 文字 */
@Composable
private fun MessageContent(msg: ChatMsgUi, onPreview: (ImageBitmap) -> Unit = {}) {
    Column {
        if (msg.attachmentName != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.09f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    IconFile,
                    contentDescription = null,
                    tint = Glass.accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        msg.attachmentName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (msg.attachmentSize > 0) {
                        Text(
                            formatSize(msg.attachmentSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (msg.imageDataUrl != null) {
            val img = remember(msg.imageDataUrl) { decodeImageDataUrl(msg.imageDataUrl) }
            if (img != null) {
                Image(
                    img,
                    contentDescription = "图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onPreview(img) },
                        ),
                    contentScale = ContentScale.Crop,
                )
            }
            if (msg.text.isNotBlank()) Spacer(Modifier.height(6.dp))
        }
        if (msg.text.isNotBlank()) {
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (msg.attachmentName != null) 12 else Int.MAX_VALUE,
                overflow = if (msg.attachmentName != null) TextOverflow.Ellipsis else TextOverflow.Clip,
            )
        }
    }
}

/** 聊天页：真实 AI 对话（流式），消息随角色保存到本地；长按气泡弹出操作菜单 */
@Composable
fun ChatScreen(
    backdrop: Backdrop,
    character: CharacterUi,
    messages: List<ChatMsgUi>,
    isStreaming: Boolean,
    streamingText: String,
    streamingReasoning: String = "",
    apiNotice: String?,
    onSend: (String, String?, String?, Long, String?) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onTogetherClick: () -> Unit,
    onInputFocus: (Boolean) -> Unit = {},
    onOpenConversations: () -> Unit = {},
    tts: TtsConfig = TtsConfig(),
    onTtsChange: (TtsConfig) -> Unit = {},
    chatOptions: ChatOptions = ChatOptions(),
    onOptionsChange: (ChatOptions) -> Unit = {},
    agentBusy: Boolean = false,
    pendingAsk: AskData? = null,
    onAskAnswer: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState: LazyListState = rememberLazyListState()
    LaunchedEffect(messages.size, isStreaming) {
        val count = messages.size + 1
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    // 当前长按打开菜单的消息 id
    var menuFor by remember { mutableStateOf<String?>(null) }
    // 当前正在朗读的消息 id（喇叭高亮）
    var speakingId by remember { mutableStateOf<String?>(null) }
    // 图片放大预览
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    // 附件面板（加号弹出）
    var attachOpen by remember { mutableStateOf(false) }
    var pickingImage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 选择图片：压缩到最长边 1024 / JPEG 85，转 data URL 发给视觉模型
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachOpen = false
            pickingImage = true
            scope.launch {
                val dataUrl = withContext(Dispatchers.IO) {
                    compressUriToDataUrl(context.contentResolver, uri)
                }
                pickingImage = false
                if (dataUrl != null) onSend("", dataUrl, null, 0L, null)
            }
        }
    }
    // 选择文件：按类型真实发送内容（图片→视觉、文本→全文、PDF→首页预览、其他→说明）
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            attachOpen = false
            pickingImage = true
            scope.launch {
                val handled = withContext(Dispatchers.IO) {
                    runCatching {
                        val cr = context.contentResolver
                        var nm = "文件"
                        var sz = 0L
                        cr.query(uri, null, null, null, null)?.use { c ->
                            if (c.moveToFirst()) {
                                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                val si = c.getColumnIndex(OpenableColumns.SIZE)
                                if (ni >= 0) nm = c.getString(ni) ?: "文件"
                                if (si >= 0) sz = c.getLong(si)
                            }
                        }
                        val mime = cr.getType(uri) ?: "*/*"
                        when {
                            // 图片 → 视觉内容（data URL 直接给视觉模型）
                            mime.startsWith("image/") -> {
                                val dataUrl = compressUriToDataUrl(cr, uri)
                                if (dataUrl != null) {
                                    onSend("", dataUrl, nm, sz, null)
                                    true
                                } else false
                            }
                            // 其余文件（文本 / PDF / 其他）→ 复制到私有目录，
                            // 把本地路径告诉 AI，AI 通过 read_file 工具自己读取内容
                            else -> {
                                val saved = runCatching {
                                    val dir = java.io.File(context.filesDir, "attachments").apply { mkdirs() }
                                    val safe = nm.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_")
                                    val f = java.io.File(dir, "${System.currentTimeMillis()}_$safe")
                                    cr.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                                    f.absolutePath
                                }.getOrNull()
                                if (saved != null) {
                                    // 只发文件名：气泡不显示路径、不显示文件内容（AI 需要时用 read_file 工具读取）
                                    onSend(
                                        "我发了一个文件：$nm（${formatSize(sz)}）",
                                        null, nm, sz, saved,
                                    )
                                } else {
                                    onSend("我发了一个文件：$nm（${formatSize(sz)}）", null, nm, sz, null)
                                }
                                true
                            }
                        }
                    }.getOrNull() ?: false
                }
                pickingImage = false
                if (!handled) {
                    onSend("我发了一个文件（已选择）", null, null, 0L, null)
                }
            }
        }
    }
    val clipboard = LocalClipboardManager.current

    // 朗读一条 AI 消息：合成并播放，喇叭高亮，播完取消高亮
    // （朗读时去掉 ```shell 等代码块原文，避免把命令念出来）
    fun speakMessage(m: ChatMsgUi) {
        val speakText = m.text.replace(Regex("```[a-z]*\\s*\\n[\\s\\S]*?```"), "")
            .trim()
        if (speakText.isBlank()) return
        speakingId = m.id
        scope.launch {
            val r = TtsPlayer.playText(context, tts, speakText)
            r.onFailure { speakingId = null }
            // 正常播完：completion 后由播放器释放，这里延迟取消高亮
            if (r.isSuccess) {
                kotlinx.coroutines.delay(300)
                speakingId = null
            }
        }
    }

    // 自动朗读：流式结束（isStreaming 由 true→false）且最后一条是 AI 消息时自动播放
    var wasStreaming by remember { mutableStateOf(false) }
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            wasStreaming = true
        } else if (wasStreaming) {
            wasStreaming = false
            if (tts.autoRead && TtsApi.ready(tts)) {
                messages.lastOrNull { !it.fromUser && !it.isCommandResult && it.text.isNotBlank() }?.let { m ->
                    speakMessage(m)
                }
            }
        }
    }

    // imePadding：输入法弹出时整个聊天页（消息列表 + 输入栏）随之上移，不被键盘遮挡
    Column(modifier.fillMaxSize().imePadding()) {
        // 顶部液态玻璃信息栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .liquidGlass(
                    backdrop,
                    shape = { RoundedCornerShape(22.dp) },
                    blurRadius = 18.dp,
                    tint = Glass.containerTint(),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarView(character.avatarPath, character.seed, character.name, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        character.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3DDC84))
                    )
                }
                Text(
                    if (isStreaming) "正在回应…" else "AI 伴侣 · 已接入真实模型",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                IconSparkles,
                contentDescription = "AI",
                tint = Glass.accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            // 自动朗读开关：开启=喇叭正常显示，关闭=喇叭带斜线（不自动播放，气泡仍可手动点）
            if (TtsApi.ready(tts)) {
                GlassIconButton(
                    backdrop,
                    if (tts.autoRead) IconVolumeUp else IconVolumeOff,
                    if (tts.autoRead) "关闭自动朗读" else "开启自动朗读",
                    onClick = { onTtsChange(tts.copy(autoRead = !tts.autoRead)) },
                    size = 38.dp,
                    tint = if (tts.autoRead) Glass.accent.copy(alpha = 0.25f)
                    else Color(0x808080).copy(alpha = 0.18f),
                )
                Spacer(Modifier.width(4.dp))
            }
            // AI 一起听入口：点击进入一起听页（导航栏下沉）
            GlassIconButton(
                backdrop,
                IconMusicNote,
                "AI 一起听",
                onClick = onTogetherClick,
                size = 38.dp,
                tint = Glass.accent.copy(alpha = 0.25f),
            )
        }

        // AI 服务提示条（未配置 / 请求失败时显示）
        if (apiNotice != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .liquidGlass(
                        backdrop,
                        shape = { RoundedCornerShape(14.dp) },
                        blurRadius = 12.dp,
                        tint = Color(0xFFFFF3E0).copy(alpha = 0.40f),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    IconWarning,
                    contentDescription = null,
                    tint = Color(0xFFE8A13D),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    apiNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 消息列表（真实消息 + 流式气泡 / 打字动画）
        // 分组：命令执行/终端返回（isCommandResult）并入前一条 AI 消息，显示在同一聊天气泡内部
        val groupedMsgs = remember(messages) {
            val groups = mutableListOf<MutableList<ChatMsgUi>>()
            for (m in messages) {
                if (m.isCommandResult && groups.isNotEmpty()) {
                    groups.last().add(m)
                } else {
                    groups.add(mutableListOf(m))
                }
            }
            groups
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // 屏幕中间往左滑 → 打开会话列表
                .pointerInput(Unit) {
                    var totalX = 0f
                    var started = false
                    detectHorizontalDragGestures(
                        onDragStart = { started = true; totalX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (started) totalX += dragAmount
                        },
                        onDragEnd = {
                            if (started && totalX < -180f) onOpenConversations()
                            started = false
                        },
                        onDragCancel = { started = false },
                    )
                },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(groupedMsgs, key = { it.first().id }) { group ->
                MessageBubble(
                    backdrop = backdrop,
                    group = group,
                    character = character,
                    menuOpen = menuFor == group.first().id,
                    onLongPress = { menuFor = group.first().id },
                    onCopy = {
                        clipboard.setText(AnnotatedString(group.first().text))
                        menuFor = null
                    },
                    onDelete = {
                        menuFor = null
                        onDeleteMessage(group.first().id)
                    },
                    onRegenerate = {
                        menuFor = null
                        onRegenerate(group.first().id)
                    },
                    onPreview = { preview = it },
                    tts = tts,
                    onSpeaker = { m -> speakMessage(m) },
                    speakingId = speakingId,
                )
            }
            if (isStreaming) {
                if (streamingText.isEmpty() && streamingReasoning.isEmpty()) {
                    item(key = "typing") { TypingBubble(backdrop, character) }
                } else {
                    item(key = "streaming") {
                        StreamingBubble(backdrop, streamingText, streamingReasoning, character)
                    }
                }
            }
            item { Spacer(Modifier.height(2.dp)) }
        }

        // 底部液态玻璃输入栏
        ChatInputBar(
            backdrop,
            character.name,
            isStreaming,
            agentBusy,
            pickingImage,
            attachOpen,
            chatOptions,
            onOptionsChange = onOptionsChange,
            onToggleAttach = { attachOpen = !attachOpen },
            onPickImage = {
                attachOpen = false
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickFile = {
                attachOpen = false
                filePicker.launch(arrayOf("*/*"))
            },
            onSend,
            onInputFocus,
        )
    }
    // 点图片缩略图放大预览，点 X 关闭
    ImagePreviewOverlay(preview, onClose = { preview = null })

    // AI 询问抽屉（毛玻璃）：AI 输出 ask 代码块时弹出
    pendingAsk?.let { ask ->
        AskSheet(
            ask = ask,
            onAnswer = onAskAnswer,
        )
    }

    // 加号附件面板：全屏遮罩（点空白关闭）+ 底部液态玻璃选项（发图片 / 发文件）
    AnimatedVisibility(
        visible = attachOpen,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(160)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { attachOpen = false },
                ),
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .liquidGlass(
                        backdrop,
                        shape = { RoundedCornerShape(24.dp) },
                        blurRadius = 20.dp,
                        tint = Glass.containerTint(),
                    )
                    .padding(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "发送内容",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AttachmentOption(
                            backdrop, IconImage, "发图片",
                            onClick = {
                                attachOpen = false
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        )
                        AttachmentOption(
                            backdrop, IconFile, "发文件",
                            onClick = {
                                attachOpen = false
                                filePicker.launch(arrayOf("*/*"))
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 单条消息气泡：长按后从气泡上方弹出玻璃操作菜单（用户：复制/删除；AI：重新输出/复制）
 *  命令执行与终端返回（isCommandResult 子消息）内嵌在 AI 气泡内部 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    backdrop: Backdrop,
    group: List<ChatMsgUi>,
    character: CharacterUi,
    menuOpen: Boolean,
    onLongPress: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onPreview: (ImageBitmap) -> Unit = {},
    tts: TtsConfig = TtsConfig(),
    onSpeaker: (ChatMsgUi) -> Unit = {},
    speakingId: String? = null,
) {
    val msg = group.first()
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.fromUser) Alignment.End else Alignment.Start,
    ) {
        // 从气泡上方弹出的操作菜单
        AnimatedVisibility(
            visible = menuOpen,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Column(horizontalAlignment = if (msg.fromUser) Alignment.End else Alignment.Start) {
                BubbleMenu(backdrop, msg.fromUser, onCopy, onDelete, onRegenerate)
                Spacer(Modifier.height(6.dp))
            }
        }

        if (msg.fromUser) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .widthIn(max = 280.dp)
                        .combinedClickable(onClick = {}, onLongClick = onLongPress)
                        .liquidGlass(
                            backdrop,
                            shape = { RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) },
                            blurRadius = 14.dp,
                            tint = Glass.accent.copy(alpha = 0.22f),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    MessageContent(msg, onPreview)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.Start) {
            // 思考过程块（深度思考开启时，AI 完成态消息上方展示）
            if (!msg.reasoning.isNullOrBlank()) {
                Box(
                    Modifier
                        .padding(start = 38.dp)
                        .widthIn(max = 280.dp)
                        .liquidGlass(
                            backdrop,
                            shape = { RoundedCornerShape(12.dp) },
                            blurRadius = 10.dp,
                            tint = Glass.containerTint(),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        msg.reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                AvatarView(character.avatarPath, character.seed, character.name, 30.dp)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .widthIn(max = 280.dp)
                        .combinedClickable(onClick = {}, onLongClick = onLongPress)
                        .liquidGlass(
                            backdrop,
                            shape = { RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp) },
                            blurRadius = 14.dp,
                            tint = Glass.containerTint(),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    MessageContent(msg)
                }
            }
            // 命令执行与终端返回：内嵌在同一 AI 气泡下方（等宽小字，命令强调色 / 结果浅色）
            if (group.size > 1) {
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .padding(start = 38.dp)
                        .widthIn(max = 280.dp)
                        .liquidGlass(
                            backdrop,
                            shape = { RoundedCornerShape(12.dp) },
                            blurRadius = 10.dp,
                            tint = Glass.containerTint(),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        for (sub in group.drop(1)) {
                            val isCmd = !sub.text.startsWith("【工具结果】")
                            Text(
                                sub.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (isCmd) Glass.accent
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (sub != group.last()) Spacer(Modifier.height(5.dp))
                        }
                    }
                }
            }
            // AI 气泡下方小喇叭：配置 TTS 且该消息有文本时显示，点击朗读
            if (TtsApi.ready(tts) && msg.text.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.padding(start = 38.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isSpeaking = speakingId == msg.id
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSpeaking) Glass.accent.copy(alpha = 0.28f)
                                else Glass.barTint()
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSpeaker(msg) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isSpeaking) IconVolumeUp else IconVolumeUp,
                            contentDescription = "朗读",
                            tint = if (isSpeaking) Glass.accent
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            }
        }
    }
}

/** 气泡操作菜单（玻璃胶囊容器，官方 LiquidButton 同款胶囊风格） */
@Composable
private fun BubbleMenu(
    backdrop: Backdrop,
    fromUser: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Row(
        Modifier
            .liquidGlass(
                backdrop,
                shape = { RoundedCornerShape(percent = 50) },
                blurRadius = 12.dp,
                tint = Glass.barTint(),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (fromUser) {
            GlassChip(backdrop, "复制", compact = true, leading = IconCopy, onClick = onCopy)
            GlassChip(backdrop, "删除", compact = true, leading = IconDelete, onClick = onDelete)
        } else {
            GlassChip(backdrop, "重新输出", compact = true, leading = IconRefresh, onClick = onRegenerate)
            GlassChip(backdrop, "复制", compact = true, leading = IconCopy, onClick = onCopy)
        }
    }
}

@Composable
private fun StreamingBubble(backdrop: Backdrop, text: String, reasoning: String, character: CharacterUi) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AvatarView(character.avatarPath, character.seed, character.name, 30.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.widthIn(max = 280.dp)) {
            // 思考过程块（深度思考开启时流式展示）
            if (reasoning.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            backdrop,
                            shape = { RoundedCornerShape(12.dp) },
                            blurRadius = 10.dp,
                            tint = Glass.containerTint(),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            if (text.isNotBlank()) {
                Box(
                    Modifier
                        .liquidGlass(
                            backdrop,
                            shape = { RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp) },
                            blurRadius = 14.dp,
                            tint = Glass.containerTint(),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TypingBubble(backdrop: Backdrop, character: CharacterUi) {
    val transition = rememberInfiniteTransition(label = "typing")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "typingDot",
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AvatarView(character.avatarPath, character.seed, character.name, 30.dp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .liquidGlass(
                    backdrop,
                    shape = { RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp) },
                    blurRadius = 14.dp,
                    tint = Glass.containerTint(),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { i ->
                    val alpha = when (i) {
                        0 -> 1f - progress * 0.6f
                        1 -> 0.4f + progress * 0.6f
                        else -> progress * 0.6f
                    }
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    backdrop: Backdrop,
    name: String,
    isStreaming: Boolean,
    agentBusy: Boolean = false,
    pickingImage: Boolean,
    attachOpen: Boolean,
    chatOptions: ChatOptions = ChatOptions(),
    onOptionsChange: (ChatOptions) -> Unit = {},
    onToggleAttach: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onSend: (String, String?, String?, Long, String?) -> Unit,
    onInputFocus: (Boolean) -> Unit = {},
) {
    var text by rememberSaveable { mutableStateOf("") }
    val inputEnabled = !isStreaming && !agentBusy

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // 深度思考 / 联网搜索 / 消耗速度（官方液态玻璃开关与滑块）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidToggle(
                selected = { chatOptions.deepThinking },
                onSelect = { onOptionsChange(chatOptions.copy(deepThinking = it)) },
                backdrop = backdrop,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "深度思考",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            LiquidToggle(
                selected = { chatOptions.webSearch },
                onSelect = { onOptionsChange(chatOptions.copy(webSearch = it)) },
                backdrop = backdrop,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "联网搜索",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "消耗速度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            LiquidSlider(
                value = {
                    (chatOptions.maxTokens - ChatOptions.MIN_TOKENS).toFloat() /
                        (ChatOptions.MAX_TOKENS - ChatOptions.MIN_TOKENS)
                },
                onValueChange = { f ->
                    val tokens = (ChatOptions.MIN_TOKENS +
                        f * (ChatOptions.MAX_TOKENS - ChatOptions.MIN_TOKENS))
                        .toInt().coerceIn(ChatOptions.MIN_TOKENS, ChatOptions.MAX_TOKENS)
                    onOptionsChange(chatOptions.copy(maxTokens = tokens))
                },
                valueRange = 0f..1f,
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                chatOptions.maxTokens.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .liquidGlass(
                    backdrop,
                    shape = { RoundedCornerShape(percent = 50) },
                    blurRadius = 16.dp,
                    tint = Glass.containerTint(),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        // + 号：打开底部附件面板（发图片 / 发文件）
        GlassIconButton(
            backdrop,
            IconPlus,
            contentDescription = "添加附件",
            onClick = onToggleAttach,
            size = 40.dp,
            tint = Glass.accent.copy(alpha = 0.25f),
            enabled = inputEnabled,
        )
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .onFocusChanged { onInputFocus(it.isFocused) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(Glass.accent),
            singleLine = true,
            enabled = inputEnabled,
            decorationBox = { innerTextField ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            if (pickingImage) "正在压缩图片…"
                            else if (agentBusy) "AI 正在执行命令…"
                            else if (isStreaming) "TA 正在回复…"
                            else "和 $name 聊点什么…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    innerTextField()
                }
            },
        )
        GlassIconButton(
            backdrop,
            IconSend,
            contentDescription = "发送",
            onClick = {
                val trimmed = text.trim()
                if (trimmed.isNotEmpty()) {
                    onSend(trimmed, null, null, 0L, null)
                    text = ""
                    onInputFocus(false)
                }
            },
            size = 40.dp,
            tint = Glass.accent.copy(alpha = 0.25f),
            enabled = text.isNotBlank() && inputEnabled,
        )
    }
    }
}

/** 附件面板选项：圆形玻璃图标 + 文字 */
@Composable
private fun AttachmentOption(
    backdrop: Backdrop,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassIconButton(
            backdrop,
            icon,
            label,
            onClick = onClick,
            size = 52.dp,
            tint = Glass.accent.copy(alpha = 0.25f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** AI 询问底部抽屉：展示问题 + AI 写的选项 + 自定义回复输入框 + 确定/跳过（半透明毛玻璃，无遮罩黑闪） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AskSheet(
    ask: AskData,
    onAnswer: (String?) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    // 半透明毛玻璃背景：截取底层画面并缩小采样（放大后天然模糊，不依赖 RenderEffect，兼容安卓 10 以下）
    val frostedBg = remember { captureFrostedBackground(context) }
    ModalBottomSheet(
        onDismissRequest = { onAnswer(null) },
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        containerColor = Color.Transparent,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.18f))
        ) {
            // 模糊后的背景画面
            frostedBg?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // 半透明盖层：保证内容可读性，形成毛玻璃观感
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color(0xFF141420).copy(alpha = 0.42f))
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    ask.question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(14.dp))
                // AI 写的选项
                ask.options.forEach { opt ->
                    OutlinedButton(
                        onClick = {
                            selected = opt
                            input = opt
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected == opt) Glass.accent else Glass.barTint(),
                        ),
                    ) {
                        Text(
                            opt,
                            color = if (selected == opt) Glass.accent else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected == opt) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 自定义回复
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义回复（可选）") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onAnswer(null) }) {
                        Text("跳过", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val answer = input.trim().ifEmpty { selected }
                            onAnswer(answer)
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

/** 截取当前界面画面并缩小采样，用作抽屉毛玻璃背景（低分辨率放大即模糊，全版本可用） */
private fun captureFrostedBackground(context: Context): Bitmap? = runCatching {
    val activity = context.findActivity() ?: return null
    val decor = activity.window.decorView
    if (decor.width <= 0 || decor.height <= 0) return null
    // 缩小到约 1/6 采样：既省内存，放大显示时天然呈现模糊效果
    val sw = (decor.width / 6).coerceAtLeast(1)
    val sh = (decor.height / 6).coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.scale(sw / decor.width.toFloat(), sh / decor.height.toFloat())
    decor.draw(canvas)
    bmp
}.getOrNull()

/** 从任意 Context 向上找到宿主 Activity */
private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** 回喂给 AI 的简短结果：超长输出截断到 600 字符，避免大段终端输出浪费 token */
fun CommandResult.toBrief(): String {
    val full = toDisplay()
    return if (full.length > 600) full.take(600) + "\n…（输出过长，已截断）" else full
}

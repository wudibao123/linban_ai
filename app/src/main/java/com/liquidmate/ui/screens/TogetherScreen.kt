package com.liquidmate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.liquidmate.data.MusicApi
import com.liquidmate.data.MusicTrack
import com.liquidmate.data.PlayerController
import com.liquidmate.data.QueueTrack
import com.liquidmate.data.TogetherController
import com.liquidmate.ui.components.AvatarView
import com.liquidmate.ui.components.Glass
import com.liquidmate.ui.components.GlassChip
import com.liquidmate.ui.components.GlassIconButton
import com.liquidmate.ui.components.liquidGlass
import com.liquidmate.ui.icons.IconBack
import com.liquidmate.ui.icons.IconCrown
import com.liquidmate.ui.icons.IconMusicNote
import com.liquidmate.ui.icons.IconPause
import com.liquidmate.ui.icons.IconPlay
import com.liquidmate.ui.icons.IconSearch
import com.liquidmate.ui.icons.IconSend
import com.liquidmate.ui.icons.IconSkipNext
import com.liquidmate.ui.icons.IconSkipPrevious
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** AI 一起听：轮流点歌播放页（全屏，退出后导航栏自动恢复） */
@Composable
fun TogetherScreen(
    backdrop: Backdrop,
    character: CharacterUi,
    controller: TogetherController,
    player: PlayerController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val st by player.state.collectAsState()
    val scope = rememberCoroutineScope()

    // 进入页面：AI 先点第一首歌；之后每 20 秒向 AI 汇报一次播放进度
    LaunchedEffect(Unit) {
        controller.enter()
        while (true) {
            delay(20_000)
            controller.maybeReportProgress()
        }
    }

    // 搜索状态
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("kugou") }
    var results by remember { mutableStateOf<List<MusicTrack>?>(null) }
    var searching by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // —— 顶部：返回 + 标题 ——
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(backdrop, IconBack, "返回", onClick = onBack, size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "AI 一起听",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "与 ${character.name} 轮流点歌 · 酷狗 / 网易云",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AvatarView(character.avatarPath, character.seed, character.name, 38.dp)
        }

        // —— 播放器卡 ——
        val current = st.queue.getOrNull(st.currentIndex)
        PlayerCard(
            backdrop = backdrop,
            current = current,
            playing = st.playing,
            positionMs = st.positionMs,
            durationMs = st.durationMs,
            onPlayPause = { player.playPause() },
            onNext = { player.next() },
            onPrev = { player.previous() },
            onSeek = { player.seekTo(it) },
        )

        // —— 轮次 + 排除 VIP 开关 ——
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .liquidGlass(
                        backdrop,
                        shape = { RoundedCornerShape(percent = 50) },
                        blurRadius = 12.dp,
                        tint = if (st.turn == "ai") Color(0xFFFFB74D).copy(alpha = 0.25f)
                        else Glass.accent.copy(alpha = 0.22f),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        st.aiPicking -> "轮到 AI 点歌… AI 正在挑 🎶"
                        st.turn == "ai" -> "轮到 AI 点歌…"
                        else -> "轮到你了，搜一首点吧 👉"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            GlassChip(
                backdrop,
                "排除VIP",
                selected = st.excludeVipUser,
                compact = true,
                leading = IconCrown,
                onClick = { controller.toggleExcludeVipUser(!st.excludeVipUser) },
            )
            GlassChip(
                backdrop,
                "AI排除VIP",
                selected = st.excludeVipAi,
                compact = true,
                leading = IconCrown,
                onClick = { controller.toggleExcludeVipAi(!st.excludeVipAi) },
            )
        }

        // —— 搜索点歌 ——
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        backdrop,
                        shape = { RoundedCornerShape(16.dp) },
                        blurRadius = 12.dp,
                        tint = Glass.containerTint(),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    IconSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(Glass.accent),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    "搜索歌曲…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            inner()
                        }
                    },
                )
                GlassIconButton(
                    backdrop,
                    IconSearch,
                    "搜索",
                    onClick = {
                        if (query.isNotBlank()) {
                            searching = true
                            scope.launch {
                                val r = if (source == "kugou") {
                                    MusicApi.searchKugou(query)
                                } else {
                                    MusicApi.searchNetease(query)
                                }
                                var list = r.getOrNull().orEmpty()
                                // 用户开启排除 VIP → 搜索结果隐藏 VIP
                                if (st.excludeVipUser) list = list.filter { !it.isVip }
                                results = list
                                searching = false
                            }
                        }
                    },
                    size = 34.dp,
                    tint = Glass.accent.copy(alpha = 0.25f),
                )
            }
            // 来源切换
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassChip(backdrop, "酷狗", selected = source == "kugou", compact = true,
                    onClick = { source = "kugou" })
                GlassChip(backdrop, "网易云", selected = source == "netease", compact = true,
                    onClick = { source = "netease" })
                if (searching) {
                    Text(
                        "搜索中…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
            // 搜索结果
            if (results != null) {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(results!!, key = { "${it.source}-${it.id}" }) { t ->
                        SearchResultRow(backdrop, t, onClick = { controller.userPick(t) })
                    }
                    if (results!!.isEmpty()) {
                        item {
                            Text(
                                "没有结果",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        // —— 点歌对话（与 AI 的实时互动） ——
        val chatState = rememberLazyListState()
        LaunchedEffect(st.chatLog.size) {
            if (st.chatLog.isNotEmpty()) chatState.scrollToItem(st.chatLog.size - 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "点歌对话",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (st.chatLog.isEmpty()) {
                Text(
                    "点歌记录会显示在这里：你点一首、AI 点一首，播放进度每 20 秒汇报给 AI。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(backdrop, shape = { RoundedCornerShape(16.dp) }, blurRadius = 12.dp, tint = Glass.containerTint())
                        .padding(12.dp),
                )
            } else {
                LazyColumn(
                    state = chatState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(st.chatLog.size) { i ->
                        val (role, text) = st.chatLog[i]
                        ChatLogRow(backdrop, role, text, character)
                    }
                }
            }
        }

        // —— 播放队列 ——
        if (st.queue.isNotEmpty()) {
            Text(
                "播放队列（${st.queue.size}）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val curId = st.queue.getOrNull(st.currentIndex)?.track?.let { "${it.source}:${it.id}" }
                st.queue.forEach { q ->
                    QueueRow(
                        backdrop,
                        q,
                        isCurrent = "${q.track.source}:${q.track.id}" == curId,
                    )
                }
            }
        }

        // —— 跟 AI 说话 ——
        ChatInput(backdrop, st, controller)

        Spacer(Modifier.height(4.dp))
    }
}

/** 播放器卡片 */
@Composable
private fun PlayerCard(
    backdrop: Backdrop,
    current: QueueTrack?,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop,
                shape = { RoundedCornerShape(24.dp) },
                blurRadius = 20.dp,
                tint = Glass.containerTint(),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 封面（渐变占位，不加载网络图）
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF3D8BFF),
                                Color(0xFF7C4DFF),
                                Color(0xFFFF6EA9),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    IconMusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (current == null) {
                    Text(
                        "还没有在放歌",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "AI 正在点今天的第一首歌…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            current.track.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (current.track.isVip) {
                            Spacer(Modifier.width(6.dp))
                            VipBadge()
                        }
                    }
                    Text(
                        current.track.artist.ifBlank { "未知歌手" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when (current.state) {
                            "failed" -> "播放失败（可能为 VIP/付费）"
                            else -> "${if (current.pickedBy == "user") "你点的" else "AI 点的"} · ${current.track.displayTime}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (current.state == "failed") Color(0xFFFF6B6B)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 进度条（点击跳转）
        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val trackWidthPx = constraints.maxWidth
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (durationMs > 0) {
                                val ratio = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                                onSeek((durationMs * ratio).toLong())
                            }
                        }
                    },
            )
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Glass.accent)
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (durationMs > 0) "${positionMs / 1000 / 60}:${(positionMs / 1000 % 60).toString().padStart(2, '0')} / ${durationMs / 1000 / 60}:${(durationMs / 1000 % 60).toString().padStart(2, '0')}"
                else "0:00",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(backdrop, IconSkipPrevious, "上一首", onClick = onPrev, size = 40.dp)
                GlassIconButton(
                    backdrop,
                    if (playing) IconPause else IconPlay,
                    if (playing) "暂停" else "播放",
                    onClick = onPlayPause,
                    size = 52.dp,
                    tint = Glass.accent.copy(alpha = 0.3f),
                )
                GlassIconButton(backdrop, IconSkipNext, "下一首", onClick = onNext, size = 40.dp)
            }
        }
    }
}

@Composable
private fun VipBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0xFFFFB300).copy(alpha = 0.22f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "VIP",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE8A13D),
        )
    }
}

/** 搜索结果行 */
@Composable
private fun SearchResultRow(backdrop: Backdrop, t: MusicTrack, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop,
                shape = { RoundedCornerShape(14.dp) },
                blurRadius = 10.dp,
                tint = if (t.isVip) Color(0xFFFFF3E0).copy(alpha = 0.3f) else Glass.containerTint(),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            IconMusicNote,
            contentDescription = null,
            tint = if (t.isVip) Color(0xFFE8A13D) else Glass.accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (t.isVip) {
                    Spacer(Modifier.width(5.dp))
                    VipBadge()
                }
            }
            Text(
                "${t.artist.ifBlank { "未知歌手" }} · ${t.displayTime} · ${if (t.source == "kugou") "酷狗" else "网易云"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 队列行 */
@Composable
private fun QueueRow(backdrop: Backdrop, q: QueueTrack, isCurrent: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop,
                shape = { RoundedCornerShape(12.dp) },
                blurRadius = 10.dp,
                tint = if (isCurrent) Glass.accent.copy(alpha = 0.18f) else Glass.containerTint(),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (q.pickedBy == "user") "你" else "AI",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (q.pickedBy == "user") Glass.accent else Color(0xFFE8A13D),
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(if (q.pickedBy == "user") Glass.accent.copy(alpha = 0.15f) else Color(0xFFE8A13D).copy(alpha = 0.15f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            q.track.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (q.track.isVip) {
            Spacer(Modifier.width(5.dp))
            VipBadge()
        }
        Spacer(Modifier.width(6.dp))
        Text(
            when (q.state) {
                "playing" -> "▶ 播放中"
                "failed" -> "✕ 失败"
                else -> ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (q.state == "failed") Color(0xFFFF6B6B)
            else if (q.state == "playing") Glass.accent
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 点歌对话行 */
@Composable
private fun ChatLogRow(backdrop: Backdrop, role: String, text: String, character: CharacterUi) {
    if (role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .liquidGlass(
                        backdrop,
                        shape = { RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp) },
                        blurRadius = 10.dp,
                        tint = Glass.accent.copy(alpha = 0.2f),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AvatarView(character.avatarPath, character.seed, character.name, 22.dp)
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .liquidGlass(
                        backdrop,
                        shape = { RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp) },
                        blurRadius = 10.dp,
                        tint = Glass.containerTint(),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 跟 AI 说话输入框 */
@Composable
private fun ChatInput(backdrop: Backdrop, st: com.liquidmate.data.TogetherState, controller: TogetherController) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop,
                shape = { RoundedCornerShape(percent = 50) },
                blurRadius = 14.dp,
                tint = Glass.containerTint(),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(Glass.accent),
            singleLine = true,
            enabled = !st.aiBusy,
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            if (st.aiBusy) "AI 正在回复…" else "和 AI 聊聊这首歌…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    inner()
                }
            },
        )
        GlassIconButton(
            backdrop,
            IconSend,
            "发送",
            onClick = {
                val t = text.trim()
                if (t.isNotEmpty()) {
                    controller.userChat(t)
                    text = ""
                }
            },
            size = 36.dp,
            tint = Glass.accent.copy(alpha = 0.25f),
            enabled = text.isNotBlank() && !st.aiBusy,
        )
    }
}

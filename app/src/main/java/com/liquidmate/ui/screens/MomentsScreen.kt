package com.liquidmate.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.liquidmate.data.MomentEntry
import com.liquidmate.ui.components.AvatarView
import com.liquidmate.ui.components.Glass
import com.liquidmate.ui.components.GlassButton
import com.liquidmate.ui.components.ImagePreviewOverlay
import com.liquidmate.ui.components.GlassCard
import com.liquidmate.ui.components.GlassIconButton
import com.liquidmate.ui.components.liquidGlass
import com.liquidmate.ui.icons.IconBack
import com.liquidmate.ui.icons.IconComment
import com.liquidmate.ui.icons.IconImage
import com.liquidmate.ui.icons.IconMoment
import com.liquidmate.ui.icons.IconPersonAdd
import com.liquidmate.ui.icons.IconSend2

/**
 * 朋友圈：AI 发动态、用户发动态（可带图）、AI 评论、可拉其他角色进来一起发。
 */
@Composable
fun MomentsScreen(
    backdrop: Backdrop,
    character: CharacterUi,
    characters: List<CharacterUi>,
    moments: List<MomentEntry>,
    busy: Boolean,
    pickedImage: String?,
    onPickImage: () -> Unit,
    onPost: (content: String, imagePath: String?) -> Unit,
    onAiPost: (characterId: String) -> Unit,
    onComment: (momentId: String, content: String) -> Unit,
    onAiComment: (momentId: String) -> Unit,
    onDeleteMoment: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var commentFor by remember { mutableStateOf<String?>(null) }
    var commentText by rememberSaveable { mutableStateOf("") }
    var showPickOther by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    val sorted = remember(moments) { moments.sortedByDescending { it.createdAt } }

    Box(
        modifier
            .fillMaxSize()
            .drawBackdrop(
                    backdrop = backdrop,
                    shape = { androidx.compose.ui.graphics.RectangleShape },
                    effects = {
                        vibrancy()
                        blur(22f.dp.toPx())
                    },
                )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(backdrop, IconBack, "返回", onClick = onBack, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "朋友圈",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "看看 ${character.name} 的动态，也可以自己发",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassIconButton(
                    backdrop,
                    IconPersonAdd,
                    "拉其他 AI",
                    onClick = { showPickOther = true },
                    size = 40.dp,
                    tint = Glass.accent.copy(alpha = 0.25f),
                )
                Spacer(Modifier.width(6.dp))
                GlassIconButton(
                    backdrop,
                    IconMoment,
                    "AI 发一条",
                    onClick = { onAiPost(character.id) },
                    size = 40.dp,
                    tint = Glass.accent.copy(alpha = 0.35f),
                )
            }

            // 拉其他 AI 选择器（自写 UI，非官方对话框）
            if (showPickOther) {
                val others = characters.filter { it.id != character.id }
                GlassCard(
                    backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        "让谁发一条？",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (others.isEmpty()) {
                        Text(
                            "还没有其他角色，先去「伴侣」创建一个吧",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        others.forEach { o ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        onAiPost(o.id)
                                        showPickOther = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AvatarView(o.avatarPath, o.seed, o.name, 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(o.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    GlassButton(backdrop, "取消", onClick = { showPickOther = false }, modifier = Modifier.fillMaxWidth())
                }
            }

            if (busy) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .liquidGlass(
                            backdrop,
                            shape = { RoundedCornerShape(14.dp) },
                            blurRadius = 14.dp,
                            tint = Glass.containerTint(),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("…", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "AI 正在想…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 时间线
            if (sorted.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有动态\n让 AI 发一条，或自己发一条吧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 6.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(sorted, key = { it.id }) { m ->
                        MomentCard(
                            backdrop,
                            m,
                            characters,
                            onPreview = { preview = it },
                            commentFor = commentFor,
                            commentText = commentText,
                            onCommentText = { commentText = it },
                            onStartComment = { commentFor = if (commentFor == m.id) null else m.id },
                            onSendComment = { c ->
                                onComment(m.id, c)
                                commentText = ""
                                commentFor = null
                            },
                            onAiComment = { onAiComment(m.id) },
                            onDelete = { onDeleteMoment(m.id) },
                        )
                    }
                }
            }

            // 底部发动态栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .liquidGlass(
                        backdrop,
                        shape = { RoundedCornerShape(percent = 50) },
                        blurRadius = 16.dp,
                        tint = Glass.containerTint(),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    backdrop,
                    IconImage,
                    "发图片",
                    onClick = { onPickImage() },
                    size = 40.dp,
                    tint = Glass.accent.copy(alpha = 0.25f),
                )
                Spacer(Modifier.width(8.dp))
                if (pickedImage != null) {
                    val bmp = remember(pickedImage) {
                        pickedImage?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
                    }
                    if (bmp != null) {
                        Image(
                            bmp,
                            contentDescription = "已选图片",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(Glass.accent),
                    maxLines = 3,
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    if (pickedImage != null) "配点文字…" else "发一条朋友圈…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                GlassIconButton(
                    backdrop,
                    IconSend2,
                    "发布",
                    onClick = {
                        val t = text.trim()
                        if (t.isNotEmpty() || pickedImage != null) {
                            onPost(t, pickedImage)
                            text = "" 
                        }
                    },
                    size = 40.dp,
                    tint = Glass.accent.copy(alpha = 0.4f),
                    enabled = text.isNotBlank() || pickedImage != null,
                )
            }
        }

        // 点动态图片放大预览，点 X 关闭
        ImagePreviewOverlay(preview, onClose = { preview = null })
    }
}

@Composable
private fun MomentCard(
    backdrop: Backdrop,
    m: MomentEntry,
    characters: List<CharacterUi>,
    onPreview: (androidx.compose.ui.graphics.ImageBitmap) -> Unit = {},
    commentFor: String?,
    commentText: String,
    onCommentText: (String) -> Unit,
    onStartComment: () -> Unit,
    onSendComment: (String) -> Unit,
    onAiComment: () -> Unit,
    onDelete: () -> Unit,
) {
    val author = characters.firstOrNull { it.id == m.authorId }
    GlassCard(
        backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        blurRadius = 16.dp,
        tint = Glass.containerTint(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.authorId == "user") {
                AvatarView(null, 99, "我", 40.dp)
            } else {
                AvatarView(author?.avatarPath, author?.seed ?: 0, m.authorName, 40.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    m.createdAt.let { fmtTime(it) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GlassIconButton(
                backdrop,
                com.liquidmate.ui.icons.IconDelete,
                "删除",
                onClick = onDelete,
                size = 28.dp,
                tint = Color(0xFFFF5252).copy(alpha = 0.4f),
            )
        }
        if (m.content.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                m.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (m.imagePaths.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                m.imagePaths.forEach { path ->
                    val bmp = remember(path) {
                        BitmapFactory.decodeFile(path)?.asImageBitmap()
                    }
                    if (bmp != null) {
                        Image(
                            bmp,
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onPreview(bmp) },
                                ),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
        // 评论区
        if (m.comments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                m.comments.forEach { c ->
                    Row {
                        Text(
                            c.authorName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Glass.accent,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            c.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 操作行
        Row {
            GlassIconButton(
                backdrop,
                IconComment,
                "评论",
                onClick = onStartComment,
                size = 30.dp,
            )
            Spacer(Modifier.width(4.dp))
            GlassButton(
                backdrop,
                "AI 评论",
                onClick = onAiComment,
                tint = Glass.accent.copy(alpha = 0.2f),
            )
        }
        if (commentFor == m.id) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = commentText,
                    onValueChange = onCommentText,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(Glass.accent),
                    maxLines = 2,
                    decorationBox = { inner ->
                        Box {
                            if (commentText.isEmpty()) {
                                Text(
                                    "说点什么…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                GlassIconButton(
                    backdrop,
                    IconSend2,
                    "发送评论",
                    onClick = { if (commentText.isNotBlank()) onSendComment(commentText.trim()) },
                    size = 32.dp,
                    tint = Glass.accent.copy(alpha = 0.35f),
                    enabled = commentText.isNotBlank(),
                )
            }
        }
    }
}

private fun fmtTime(ts: Long): String {
    val d = java.util.Date(ts)
    val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
    return sdf.format(d)
}

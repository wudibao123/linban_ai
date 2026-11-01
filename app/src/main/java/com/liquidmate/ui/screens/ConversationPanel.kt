package com.liquidmate.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.liquidmate.data.Conversation
import com.liquidmate.ui.components.AvatarView
import com.liquidmate.ui.components.Glass
import com.liquidmate.ui.components.GlassButton
import com.liquidmate.ui.components.GlassCard
import com.liquidmate.ui.components.GlassIconButton
import com.liquidmate.ui.components.liquidGlass
import com.liquidmate.ui.icons.IconChat
import com.liquidmate.ui.icons.IconClose
import com.liquidmate.ui.icons.IconDelete
import com.liquidmate.ui.icons.IconEdit
import com.liquidmate.ui.icons.IconPlus

/**
 * 会话列表面板（每角色隔离）：左滑聊天页打开。
 * 新建对话 / 点击切换 / 长按重命名或删除 / 返回键关闭。
 */
@Composable
fun ConversationPanel(
    backdrop: Backdrop,
    character: CharacterUi,
    conversations: List<Conversation>,
    activeId: String?,
    onNew: () -> Unit,
    onSwitch: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    val sorted = remember(conversations) {
        conversations.sortedByDescending { it.updatedAt }
    }

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
            ) { onClose() },
    ) {
        // 会话面板只占屏幕左半边（液态玻璃抽屉）
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.62f)
                .liquidGlass(
                    backdrop,
                    shape = { RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp) },
                    blurRadius = 24.dp,
                    tint = Glass.containerTint(),
                )
                .padding(16.dp),
        ) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarView(character.avatarPath, character.seed, character.name, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "对话",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${character.name} · ${sorted.size} 个会话",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GlassIconButton(
                    backdrop,
                    IconPlus,
                    "新建对话",
                    onClick = onNew,
                    size = 40.dp,
                    tint = Glass.accent.copy(alpha = 0.25f),
                )
                Spacer(Modifier.width(6.dp))
                GlassIconButton(backdrop, IconClose, "关闭", onClick = onClose, size = 40.dp)
            }

            Spacer(Modifier.height(12.dp))

            if (sorted.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有对话\n点 + 新建一个",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sorted, key = { it.id }) { conv ->
                        val active = conv.id == activeId
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .liquidGlass(
                                    backdrop,
                                    shape = { RoundedCornerShape(18.dp) },
                                    blurRadius = 14.dp,
                                    tint = if (active) Glass.accent.copy(alpha = 0.22f) else Glass.containerTint(),
                                )
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onSwitch(conv.id) },
                                    onLongClick = { menuFor = conv.id },
                                )
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    IconChat,
                                    contentDescription = null,
                                    tint = if (active) Glass.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        conv.title.ifBlank { "未命名对话" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${conv.messages.size} 条消息 · ${fmtConvTime(conv.updatedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (active) {
                                    Text(
                                        "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Glass.accent,
                                    )
                                }
                            }
                            // 长按操作菜单（自写 UI）：重命名 / 删除
                            if (menuFor == conv.id && renameFor != conv.id) {
                                Spacer(Modifier.height(8.dp))
                                Row {
                                    GlassButton(
                                        backdrop,
                                        "重命名",
                                        onClick = {
                                            renameFor = conv.id
                                            renameText = conv.title
                                            menuFor = null
                                        },
                                        tint = Glass.accent.copy(alpha = 0.2f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    GlassButton(
                                        backdrop,
                                        "删除",
                                        onClick = {
                                            onDelete(conv.id)
                                            menuFor = null
                                        },
                                        tint = Color(0xFFFF5252).copy(alpha = 0.2f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    GlassButton(backdrop, "取消", onClick = { menuFor = null })
                                }
                            }
                            if (renameFor == conv.id) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BasicTextField(
                                        value = renameText,
                                        onValueChange = { renameText = it },
                                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        cursorBrush = SolidColor(Glass.accent),
                                        singleLine = true,
                                        decorationBox = { inner ->
                                            Box {
                                                if (renameText.isEmpty()) {
                                                    Text(
                                                        "输入新标题…",
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
                                        IconEdit,
                                        "确认重命名",
                                        onClick = {
                                            val t = renameText.trim()
                                            if (t.isNotEmpty()) onRename(conv.id, t)
                                            renameFor = null
                                        },
                                        size = 32.dp,
                                        tint = Glass.accent.copy(alpha = 0.3f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "提示：聊天页中间往左滑打开本面板 · 按返回键关闭",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun fmtConvTime(ts: Long): String {
    val d = java.util.Date(ts)
    val now = System.currentTimeMillis()
    return if (now - ts < 60000L) "刚刚"
    else if (now - ts < 3600000L) "${(now - ts) / 60000L} 分钟前"
    else {
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
        sdf.format(d)
    }
}

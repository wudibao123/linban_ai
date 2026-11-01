package com.liquidmate.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.liquidmate.data.DiaryEntry
import com.liquidmate.ui.components.Glass
import com.liquidmate.ui.components.GlassButton
import com.liquidmate.ui.components.GlassCard
import com.liquidmate.ui.components.GlassIconButton
import com.liquidmate.ui.components.liquidGlass
import com.liquidmate.ui.icons.IconDelete
import com.liquidmate.ui.icons.IconDiary
import com.liquidmate.ui.icons.IconSparkles

/**
 * 日记：用户手写 + AI 代笔，全部缓存本地（AppStore 持久化）。
 * 顶部可让当前角色「AI 写今天」；列表按时间倒序；底部输入框自己写。
 */
@Composable
fun DiaryScreen(
    backdrop: Backdrop,
    character: CharacterUi,
    characters: List<CharacterUi> = emptyList(),
    diaries: List<DiaryEntry>,
    aiWriting: Boolean,
    onAdd: (content: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onAiWrite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val sorted = remember(diaries) { diaries.sortedByDescending { it.createdAt } }

    Column(modifier.fillMaxSize()) {
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
            Icon(
                IconDiary,
                contentDescription = "日记",
                tint = Glass.accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "日记",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (aiWriting) "${character.name} 正在写今天…"
                    else "共 ${sorted.size} 篇 · ${character.name} 也能替你写",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GlassIconButton(
                backdrop,
                IconSparkles,
                "AI 写今天",
                onClick = onAiWrite,
                size = 40.dp,
                tint = Glass.accent.copy(alpha = 0.25f),
                enabled = !aiWriting,
            )
        }

        // 日记列表
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有日记\n点右上角让 AI 写今天，或在下方自己写",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 6.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sorted, key = { it.id }) { d ->
                    DiaryRow(backdrop, d, character.name, characters, onDelete = { onDelete(d.id) })
                }
            }
        }

        // 底部输入：自己写日记
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .liquidGlass(
                    backdrop,
                    shape = { RoundedCornerShape(percent = 50) },
                    blurRadius = 16.dp,
                    tint = Glass.containerTint(),
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(Glass.accent),
                singleLine = false,
                maxLines = 4,
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                "写点今天的事…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Spacer(Modifier.width(10.dp))
            GlassButton(
                backdrop,
                "保存",
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty()) {
                        onAdd(trimmed)
                        text = ""
                    }
                },
                tint = Glass.accent.copy(alpha = 0.25f),
                enabled = text.isNotBlank(),
            )
        }
    }
}

@Composable
private fun DiaryRow(
    backdrop: Backdrop,
    entry: DiaryEntry,
    characterName: String,
    characters: List<CharacterUi>,
    onDelete: () -> Unit,
) {
    GlassCard(
        backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        blurRadius = 16.dp,
        tint = Glass.containerTint(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 作者标签
            val isAi = entry.author == "ai"
            Box(
                Modifier
                    .size(28.dp)
                    .padding(4.dp)
                    .let { m ->
                        if (isAi) m else m
                    },
            ) {
                Icon(
                    IconSparkles,
                    contentDescription = null,
                    tint = if (isAi) Glass.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isAi) {
                            val authorName = characters.firstOrNull { it.id == entry.authorId }?.name
                            "${authorName ?: characterName} 写的"
                        } else "我写的",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAi) Glass.accent else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        entry.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            GlassIconButton(
                backdrop,
                IconDelete,
                "删除",
                onClick = onDelete,
                size = 30.dp,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            entry.content,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.25f,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

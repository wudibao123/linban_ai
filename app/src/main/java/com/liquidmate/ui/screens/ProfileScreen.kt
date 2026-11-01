package com.liquidmate.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.liquidmate.ui.components.AvatarView
import com.liquidmate.ui.components.Glass
import com.liquidmate.ui.components.GlassCard
import com.liquidmate.ui.components.GlassChip
import com.liquidmate.ui.components.GlassIconButton
import com.liquidmate.ui.components.liquidGlass
import com.liquidmate.ui.icons.IconAlbum
import com.liquidmate.ui.icons.IconBack
import com.liquidmate.ui.icons.IconDelete
import com.liquidmate.ui.icons.IconEdit
import com.liquidmate.ui.icons.IconMoment

/**
 * 角色全屏信息页：点击角色卡片头像进入。
 * 展示头像 / 姓名 / 性格，并提供「相册」「朋友圈」两个入口。
 */
@Composable
fun ProfileScreen(
    backdrop: Backdrop,
    character: CharacterUi,
    albumCount: Int,
    momentCount: Int,
    onBack: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenMoments: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶部：返回
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(backdrop, IconBack, "返回", onClick = onBack, size = 40.dp)
                Spacer(Modifier.weight(1f))
                Text(
                    "角色档案",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                GlassIconButton(backdrop, IconEdit, "编辑角色", onClick = onEdit, size = 36.dp)
                Spacer(Modifier.width(6.dp))
                GlassIconButton(
                    backdrop,
                    IconDelete,
                    "删除角色",
                    onClick = onDelete,
                    size = 36.dp,
                    tint = Color(0xFFFF5252).copy(alpha = 0.18f),
                )
            }

            Spacer(Modifier.height(24.dp))

            // 大头像（液态玻璃圈）
            Box(
                Modifier
                    .liquidGlass(
                        backdrop,
                        shape = { androidx.compose.foundation.shape.CircleShape },
                        blurRadius = 18.dp,
                        tint = Glass.containerTint(),
                    )
                    .padding(6.dp),
            ) {
                AvatarView(character.avatarPath, character.seed, character.name, 108.dp)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                character.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            if (character.tagline.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    character.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (character.traits.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    character.traits.take(6).forEach { t ->
                        GlassChip(backdrop, t, compact = true)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // 相册入口
            GlassCard(
                backdrop,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                tint = Glass.containerTint(),
                onClick = onOpenAlbum,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        IconAlbum,
                        contentDescription = null,
                        tint = Glass.accent,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "相册",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (albumCount > 0) "共 $albumCount 张 · 让 AI 去网上找图片珍藏"
                            else "还没有照片 · 点进来让 AI 去网上找图片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "$albumCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Glass.accent,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 朋友圈入口
            GlassCard(
                backdrop,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                tint = Glass.containerTint(),
                onClick = onOpenMoments,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        IconMoment,
                        contentDescription = null,
                        tint = Glass.accent,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "朋友圈",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (momentCount > 0) "共 $momentCount 条动态 · AI 会发动态、评论你"
                            else "看看 ${character.name} 今天发了什么",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "$momentCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Glass.accent,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

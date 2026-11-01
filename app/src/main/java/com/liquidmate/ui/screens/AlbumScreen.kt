package com.liquidmate.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.liquidmate.data.AlbumEntry
import com.liquidmate.ui.components.Glass
import com.liquidmate.ui.components.GlassIconButton
import com.liquidmate.ui.components.ImagePreviewOverlay
import com.liquidmate.ui.components.liquidGlass
import com.liquidmate.ui.icons.IconAlbum
import com.liquidmate.ui.icons.IconBack
import com.liquidmate.ui.icons.IconDelete
import com.liquidmate.ui.icons.IconWifi

/**
 * 相册页：展示角色相册（网格），支持「AI 找图」从网上搜索并下载图片加入相册，可删除。
 */
@Composable
fun AlbumScreen(
    backdrop: Backdrop,
    character: CharacterUi,
    entries: List<AlbumEntry>,
    aiFinding: Boolean,
    onAiFind: () -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(entries) { entries.sortedByDescending { it.createdAt } }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
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
                        "${character.name} 的相册",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "共 ${entries.size} 张",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GlassIconButton(
                    backdrop,
                    IconWifi,
                    "AI 找图",
                    onClick = onAiFind,
                    size = 40.dp,
                    tint = Glass.accent.copy(alpha = 0.25f),
                )
            }

            if (aiFinding) {
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
                        "AI 正在网上找图并下载…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (sorted.isEmpty() && !aiFinding) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "相册还是空的\n点右上角让 AI 去网上找图片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sorted, key = { it.id }) { e ->
                        AlbumCell(backdrop, e, onDelete = { onDelete(e.id) }, onClick = { preview = it })
                    }
                }
            }
        }

        // 点缩略图放大预览，点 X 关闭
        ImagePreviewOverlay(preview, onClose = { preview = null })
    }
}

@Composable
private fun AlbumCell(
    backdrop: Backdrop,
    entry: AlbumEntry,
    onDelete: () -> Unit,
    onClick: (androidx.compose.ui.graphics.ImageBitmap) -> Unit = {},
) {
    val bmp = remember(entry.path) {
        BitmapFactory.decodeFile(entry.path)?.asImageBitmap()
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        if (bmp != null) {
            Image(
                bmp,
                contentDescription = entry.caption,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { onClick(bmp) },
                    ),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IconAlbum, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 删除角标
        GlassIconButton(
            backdrop,
            IconDelete,
            "删除",
            onClick = onDelete,
            size = 26.dp,
            tint = Color(0xFFFF5252).copy(alpha = 0.5f),
        )
    }
}

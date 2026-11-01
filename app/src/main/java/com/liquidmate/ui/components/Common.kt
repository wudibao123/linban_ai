package com.liquidmate.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import java.io.File
import java.io.FileOutputStream

/** 角色头像渐变配色盘 */
val AvatarGradients: List<List<Color>> = listOf(
    listOf(Color(0xFF3D8BFF), Color(0xFF7FC4FF)),
    listOf(Color(0xFF7C6CFF), Color(0xFFB5A8FF)),
    listOf(Color(0xFFFF6B9D), Color(0xFFFFB3C7)),
    listOf(Color(0xFFFFA63D), Color(0xFFFFD27F)),
    listOf(Color(0xFF22C58F), Color(0xFF7FE3C2)),
    listOf(Color(0xFFB06AB3), Color(0xFFE0A6E3)),
    listOf(Color(0xFF4FC3F7), Color(0xFF9BE7FF)),
    listOf(Color(0xFFFF8A65), Color(0xFFFFC9B0)),
)

/** 玻璃卡片 */
@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    blurRadius: Dp = 20.dp,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
            } else Modifier
        )
        .liquidGlass(backdrop, shape = { shape }, blurRadius = blurRadius, tint = tint)
    Box(base) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** 玻璃胶囊按钮 */
@Composable
fun GlassButton(
    backdrop: Backdrop,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .liquidGlass(
                backdrop,
                shape = { RoundedCornerShape(percent = 50) },
                blurRadius = 12.dp,
                lensHeight = 12.dp,
                lensAmount = 14.dp,
                tint = tint,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

/** 玻璃圆形图标按钮 */
@Composable
fun GlassIconButton(
    backdrop: Backdrop,
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .liquidGlass(
                backdrop,
                shape = { CircleShape },
                blurRadius = 12.dp,
                lensHeight = 10.dp,
                lensAmount = 12.dp,
                tint = tint,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) Glass.accent
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

/** 玻璃小胶囊（标签/选项） */
@Composable
fun GlassChip(
    backdrop: Backdrop,
    text: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    selected: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .liquidGlass(
                backdrop,
                shape = { RoundedCornerShape(percent = 50) },
                blurRadius = 10.dp,
                lensHeight = 8.dp,
                lensAmount = 10.dp,
                tint = if (selected) Glass.accent.copy(alpha = 0.22f) else null,
            )
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 5.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (leading != null) {
                Icon(
                    leading,
                    contentDescription = null,
                    tint = if (selected) Glass.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Glass.accent else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 头像（本地图片 或 渐变首字母占位） */
@Composable
fun AvatarView(
    avatarPath: String?,
    seed: Int,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    AvatarGradients[((seed % AvatarGradients.size) + AvatarGradients.size) % AvatarGradients.size]
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = remember(avatarPath) {
            avatarPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
        }
        if (bmp != null) {
            Image(
                bmp,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/** 区块标题 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/**
 * 从本地相册导入图片（系统照片选择器，无需存储权限）。
 * 返回选中图片复制到应用私有目录后的路径（随应用数据长期保存）；取消选择返回 null。
 */
@Composable
fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onPicked(uri?.let { copyUriToFiles(context, it) })
    }
    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}

private fun copyUriToFiles(context: Context, uri: Uri): String {
    val dir = File(context.filesDir, "imports").apply { mkdirs() }
    val out = File(dir, "img_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(out).use { output -> input.copyTo(output) }
    }
    return out.absolutePath
}

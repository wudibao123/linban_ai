package com.liquidmate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * 确认对话框 —— 开源项目 AndroidLiquidGlass (Backdrop) 官方 DialogContent 原样移植（通用化）。
 *
 * 样式与官方对话框一致：全屏暗色遮罩 + 大圆角玻璃卡片
 * （colorControls 增亮提饱和 + blur + lens(depthEffect) + Highlight.Plain）+ 底部两颗胶囊按钮。
 */
@Composable
fun GlassDialog(
    backdrop: Backdrop,
    title: String,
    message: String,
    confirmText: String,
    cancelText: String = "取消",
    confirmTint: Color = Color(0xFF0088FF),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.6f)
        else Color(0xFF121212).copy(0.4f)
    val dimColor =
        if (isLightTheme) Color(0xFF29293A).copy(0.23f)
        else Color(0xFF121212).copy(0.56f)

    Box(
        modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                drawRect(dimColor)
            }
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(40.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(48f.dp) },
                    effects = {
                        colorControls(
                            brightness = if (isLightTheme) 0.2f else 0f,
                            saturation = 1.5f,
                        )
                        blur(if (isLightTheme) 16f.dp.toPx() else 8f.dp.toPx())
                        lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .fillMaxWidth()
        ) {
            Text(
                title,
                modifier = Modifier.padding(28.dp, 24.dp, 28.dp, 12.dp),
                style = TextStyle(contentColor, 22.sp, FontWeight.Medium),
            )
            Text(
                message,
                modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 16.dp),
                style = TextStyle(contentColor.copy(alpha = 0.68f), 15.sp),
            )
            Row(
                Modifier
                    .padding(24.dp, 12.dp, 24.dp, 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DialogCapsuleButton(
                    text = cancelText,
                    bg = containerColor.copy(alpha = 0.2f),
                    textColor = contentColor,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                DialogCapsuleButton(
                    text = confirmText,
                    bg = confirmTint,
                    textColor = Color.White,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 官方对话框同款胶囊按钮 */
@Composable
private fun DialogCapsuleButton(
    text: String,
    bg: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(Capsule())
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .height(48.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = TextStyle(textColor, 16.sp, FontWeight.Medium),
        )
    }
}

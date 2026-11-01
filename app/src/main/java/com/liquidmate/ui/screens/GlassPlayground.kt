package com.liquidmate.ui.screens

import androidx.compose.animation.core.Animatable
import android.os.Build
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import com.liquidmate.data.GlassParams
import com.liquidmate.glass.LiquidButton
import com.liquidmate.glass.LiquidSlider
import com.liquidmate.ui.components.GlassIconButton
import com.liquidmate.ui.icons.IconClose
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 液态玻璃游乐场 —— 开源项目 AndroidLiquidGlass (Backdrop) 官方 GlassPlaygroundContent 移植。
 *
 * 上方：可拖拽 / 缩放 / 旋转的玻璃预览方块（实时反映参数）。
 * 下方：官方 LiquidSlider 控制 5 个参数 —— 模糊 / 折射高度 / 折射强度 / 色散会
 * **实时应用到全 App 的所有玻璃 UI**（含底部导航栏）；圆角仅作用于预览方块。
 */
@Composable
fun GlassPlayground(
    backdrop: Backdrop,
    params: GlassParams,
    onParamsChange: (GlassParams) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationScope = rememberCoroutineScope()
    // 玻璃游乐场自动检测：Android 12 (API 31) 以下 AGSL Shader 不可用，
    // 液态玻璃会退化为透明 —— 此时调参不生效，直接禁用滑块并提示
    val isAndroid12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val offsetAnimation = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val zoomAnimation = remember { Animatable(1f) }
    val rotationAnimation = remember { Animatable(0f) }

    var isSheetExpanded by remember { mutableStateOf(true) }

    // 预览专用：圆角系数（其余参数来自全局 params）
    var cornerRadiusFrac by remember { mutableFloatStateOf(0.5f) }

    // 用引用捕获（而非值捕获）包装全局参数：
    // 官方 LiquidSlider / drawBackdrop 内部通过闭包读取 value/effects，
    // 若直接捕获 data class 实例会永远读到旧值，导致滑块一拖就被拉回（滑不动）。
    val currentParams by rememberUpdatedState(params)

    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // 标题行
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "液态玻璃游乐场",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                GlassIconButton(backdrop, IconClose, "关闭", onClick = onClose, size = 40.dp)
            }

            if (!isAndroid12Plus) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFFFFF3E0),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "⚠",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "当前设备低于 Android 12：液态玻璃不可用（显示为透明），下方调参不会生效。升级系统或换设备后即可体验。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8A5A00),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 玻璃预览方块（官方实现：拖拽 / 双指缩放 / 旋转）
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(256f.dp / 2f * cornerRadiusFrac) },
                            effects = {
                                val minDimension = size.minDimension
                                vibrancy()
                                blur(currentParams.blurRadiusDp.dp.toPx())
                                lens(
                                    refractionHeight = currentParams.refractionHeightFrac * minDimension * 0.5f,
                                    refractionAmount = currentParams.refractionAmountFrac * minDimension,
                                    depthEffect = true,
                                    chromaticAberration = currentParams.chromaticAberration > 0f
                                )
                            },
                            highlight = { Highlight.Plain },
                            layerBlock = {
                                val offset = offsetAnimation.value
                                val zoom = zoomAnimation.value
                                val rotation = rotationAnimation.value
                                translationX = offset.x
                                translationY = offset.y
                                scaleX = zoom
                                scaleY = zoom
                                rotationZ = rotation
                            }
                        )
                        .pointerInput(animationScope) {
                            fun Offset.rotateBy(angle: Float): Offset {
                                val angleInRadians = angle * (PI / 180)
                                val cos = cos(angleInRadians)
                                val sin = sin(angleInRadians)
                                return Offset((x * cos - y * sin).toFloat(), (x * sin + y * cos).toFloat())
                            }

                            detectTransformGestures { centroid, pan, gestureZoom, gestureRotate ->
                                val offset = offsetAnimation.value
                                val zoom = zoomAnimation.value
                                val rotation = rotationAnimation.value

                                val targetZoom = zoom * gestureZoom
                                val targetRotation = rotation + gestureRotate
                                val targetOffset = offset + pan.rotateBy(targetRotation) * targetZoom

                                animationScope.launch {
                                    offsetAnimation.snapTo(targetOffset)
                                    zoomAnimation.snapTo(targetZoom)
                                    rotationAnimation.snapTo(targetRotation)
                                }
                            }
                        }
                        .size(256f.dp)
                        .align(Alignment.TopCenter)
                )
            }

            Spacer(Modifier.height(24.dp))

            // 玻璃控制面板：官方 LiquidSlider 实时调参
            val sheetBackdrop = rememberLayerBackdrop()
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(32f.dp) },
                        effects = {
                            vibrancy()
                            blur(4f.dp.toPx())
                            lens(16f.dp.toPx(), 32f.dp.toPx())
                        },
                        highlight = { Highlight.Plain },
                        exportedBackdrop = sheetBackdrop,
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.5f)) }
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isSheetExpanded) {
                    SliderRow(
                        sheetBackdrop,
                        "圆角（仅预览）",
                        { cornerRadiusFrac },
                        { cornerRadiusFrac = it },
                        0f..1f,
                        0.001f,
                        enabled = isAndroid12Plus,
                    )
                    SliderRow(
                        sheetBackdrop,
                        "模糊半径（全局）",
                        { currentParams.blurRadiusDp },
                        { onParamsChange(currentParams.copy(blurRadiusDp = it)) },
                        0f..32f,
                        0.01f,
                        enabled = isAndroid12Plus,
                    )
                    SliderRow(
                        sheetBackdrop,
                        "折射高度（全局）",
                        { currentParams.refractionHeightFrac },
                        { onParamsChange(currentParams.copy(refractionHeightFrac = it)) },
                        0f..1f,
                        0.001f,
                        enabled = isAndroid12Plus,
                    )
                    SliderRow(
                        sheetBackdrop,
                        "折射强度（全局）",
                        { currentParams.refractionAmountFrac },
                        { onParamsChange(currentParams.copy(refractionAmountFrac = it)) },
                        0f..1f,
                        0.001f,
                        enabled = isAndroid12Plus,
                    )
                    SliderRow(
                        sheetBackdrop,
                        "色散（全局）",
                        { currentParams.chromaticAberration },
                        { onParamsChange(currentParams.copy(chromaticAberration = it)) },
                        0f..1f,
                        0.001f,
                        enabled = isAndroid12Plus,
                    )
                    Text(
                        "「全局」参数会实时应用到全 App 的所有玻璃 UI（含底部导航）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "控制面板已折叠",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 官方 LiquidButton：折叠 / 重置
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LiquidButton(
                    { isSheetExpanded = !isSheetExpanded },
                    backdrop,
                    tint = Color(0xFFFF8D28),
                ) {
                    Text(
                        if (isSheetExpanded) "收起" else "展开",
                        style = TextStyle(Color.White, 15f.sp),
                    )
                }
                LiquidButton(
                    {
                        animationScope.launch {
                            launch { offsetAnimation.animateTo(Offset.Zero) }
                            launch { zoomAnimation.animateTo(1f) }
                            launch { rotationAnimation.animateTo(0f) }
                        }
                        cornerRadiusFrac = 0.5f
                        onParamsChange(GlassParams())
                    },
                    backdrop,
                    tint = Color(0xFFFF8D28),
                ) {
                    Text(
                        "重置",
                        style = TextStyle(Color.White, 15f.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    backdrop: Backdrop,
    label: String,
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    threshold: Float,
    enabled: Boolean = true,
) {
    val consumeBlock = Modifier.pointerInput(enabled) {
        if (!enabled) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
        }
    }
    Column(
        Modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(consumeBlock),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LiquidSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            visibilityThreshold = threshold,
            backdrop = backdrop,
        )
    }
}

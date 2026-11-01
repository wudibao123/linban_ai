package com.liquidmate.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.liquidmate.data.GlassParams

/**
 * 液态玻璃全局调参（由「液态玻璃游乐场」实时调节）。
 * 在应用根用 [androidx.compose.runtime.CompositionLocalProvider] 提供，
 * 所有玻璃 UI 都会叠加这些参数。
 */
val LocalGlassParams = staticCompositionLocalOf { GlassParams() }

/** 液态玻璃相关颜色与常量 */
object Glass {
    val accent = Color(0xFF3D8BFF)

    /** 玻璃面板底色（浅色/深色自适应） */
    @Composable
    fun containerTint(): Color =
        if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.10f)
        else Color.White.copy(alpha = 0.46f)

    /** 更强的不透明玻璃底色（用于大面板） */
    @Composable
    fun barTint(): Color =
        if (isSystemInDarkTheme()) Color(0xFF141927).copy(alpha = 0.55f)
        else Color(0xFFF6F8FE).copy(alpha = 0.55f)
}

/**
 * 液态玻璃修饰器 —— 基于开源项目 AndroidLiquidGlass (Backdrop)。
 *
 * 用法：先在根布局用 [com.kyant.backdrop.backdrops.rememberLayerBackdrop] 创建 backdrop，
 * 把壁纸画进 `Modifier.layerBackdrop(backdrop)` 采样层，再对任意 UI 元素应用本修饰器，
 * 即可获得对壁纸的模糊 + 折射 + 边缘色散效果。
 *
 * 全局调参（[LocalGlassParams]）会叠加到 blur / lens / 色散上：游乐场里拖滑块，全 App 实时变化。
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: () -> Shape = { RoundedCornerShape(28.dp) },
    blurRadius: Dp = 16.dp,
    lensHeight: Dp = 18.dp,
    lensAmount: Dp = 22.dp,
    chromaticAberration: Boolean = false,
    tint: Color? = null,
    showHighlight: Boolean = true,
): Modifier {
    val density = LocalDensity.current
    val global = LocalGlassParams.current
    // 全局增量：模糊直接叠加 dp；折射高度/强度映射为 0..48dp 叠加；色散 >0 即开启
    val blurPx = with(density) { (blurRadius + global.blurRadiusDp.dp).toPx() }
    val lensHPx = with(density) { (lensHeight + (global.refractionHeightFrac * 48f).dp).toPx() }
    val lensAPx = with(density) { (lensAmount + (global.refractionAmountFrac * 48f).dp).toPx() }
    val chroma = chromaticAberration || global.chromaticAberration > 0f

    val effects: BackdropEffectScope.() -> Unit = {
        vibrancy()
        blur(blurPx)
        lens(lensHPx, lensAPx, chromaticAberration = chroma)
    }
    val highlight: () -> Highlight? = { if (showHighlight) Highlight.Default else null }

    return if (tint != null) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = shape,
            effects = effects,
            highlight = highlight,
            onDrawSurface = { drawRect(tint) },
        )
    } else {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = shape,
            effects = effects,
            highlight = highlight,
        )
    }
}

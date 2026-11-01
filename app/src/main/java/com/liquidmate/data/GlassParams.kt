package com.liquidmate.data

import kotlinx.serialization.Serializable

/**
 * 液态玻璃全局调参（由「液态玻璃游乐场」实时调节，作用于整个 App 的所有玻璃 UI）。
 * 默认全 0 = 不叠加，保持各组件的原始观感。
 */
@Serializable
data class GlassParams(
    val blurRadiusDp: Float = 0f,           // 全局模糊半径增量（dp）
    val refractionHeightFrac: Float = 0f,   // 全局折射高度系数 0..1
    val refractionAmountFrac: Float = 0f,   // 全局折射强度系数 0..1
    val chromaticAberration: Float = 0f,    // 全局色散强度 0..1（>0 即开启）
)

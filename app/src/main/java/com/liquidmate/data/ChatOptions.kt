package com.liquidmate.data

import kotlinx.serialization.Serializable

/**
 * 聊天增强选项（主页面与 Agent 共用）。
 *
 * @param deepThinking 深度思考：开启后解析并展示模型的 reasoning_content（思考过程）
 * @param webSearch    联网搜索：DeepSeek 模型走官方 /v1/tools/search，其他模型走第三方
 * @param maxTokens    输出预算（“消耗速度”）：值越大回复越长、消耗越快；越小越省
 */
@Serializable
data class ChatOptions(
    val deepThinking: Boolean = false,
    val webSearch: Boolean = false,
    val maxTokens: Int = 2048,
) {
    companion object {
        /** 消耗速度滑块范围 */
        const val MIN_TOKENS = 256
        const val MAX_TOKENS = 8192

        /** 判断模型是否为 DeepSeek（决定搜索走官方还是第三方） */
        fun isDeepSeek(model: String): Boolean =
            model.lowercase().contains("deepseek")
    }
}

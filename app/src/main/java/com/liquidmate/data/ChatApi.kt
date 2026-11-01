package com.liquidmate.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** AI 服务配置：兼容任意 OpenAI Chat Completions 协议的端点 */
@Serializable
data class ApiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)

/** 一条消息；imageDataUrl 非空时按多模态 content 数组发送（支持视觉模型看图） */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val imageDataUrl: String? = null,
)

@Serializable
private data class ChatResponse(
    val choices: List<Choice>? = null,
    val error: ApiError? = null,
)

@Serializable
private data class Choice(
    val delta: Delta? = null,
    val message: ChatMessage? = null,
)

@Serializable
private data class Delta(
    val content: String? = null,
    @kotlinx.serialization.SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
private data class ApiError(val message: String? = null)

/**
 * OpenAI 兼容 Chat Completions 客户端。
 * - 支持自定义 Base URL（自建 / 中转 / OpenAI / DeepSeek / 通义等任意兼容服务）
 * - 支持 Bearer API Key
 * - 支持 SSE 流式输出（逐字回调）
 */
object ChatApi {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** 端点规整：自动补 /v1 前缀与 /chat/completions 后缀 */
    private fun endpoint(baseUrl: String): String {
        var url = baseUrl.trim().trimEnd('/')
        if (url.isBlank()) url = "https://api.openai.com/v1"
        if (!url.endsWith("/v1")) url += "/v1"
        return "$url/chat/completions"
    }

    private fun buildRequest(config: ApiConfig, bodyJson: String): Request =
        Request.Builder()
            .url(endpoint(config.baseUrl))
            .addHeader("Content-Type", "application/json")
            .apply {
                if (config.apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

    /** 消息序列化：带图的 user 消息按多模态 content 数组输出，文本消息保持字符串 */
    private fun messagesJson(messages: List<ChatMessage>): JsonElement =
        buildJsonArray {
            messages.forEach { m ->
                addJsonObject {
                    put("role", m.role)
                    if (m.imageDataUrl.isNullOrBlank()) {
                        put("content", m.content)
                    } else {
                        putJsonArray("content") {
                            addJsonObject { put("type", "text"); put("text", m.content) }
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", m.imageDataUrl) }
                            }
                        }
                    }
                }
            }
        }

    private fun buildBody(
        config: ApiConfig,
        messages: List<ChatMessage>,
        stream: Boolean,
        maxTokens: Int? = null,
    ): String = buildJsonObject {
        put("model", config.model)
        put("messages", messagesJson(messages))
        put("stream", stream)
        if (maxTokens != null) put("max_tokens", maxTokens)
    }.toString()

    /** 流式对话：每个增量片段回调 [onDelta]；思考过程回调 [onReasoning]；失败时 Result.failure 带可读错误信息 */
    suspend fun streamChat(
        config: ApiConfig,
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
        options: ChatOptions = ChatOptions(),
        onReasoning: ((String) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildBody(config, messages, stream = true, maxTokens = options.maxTokens)
            client.newCall(buildRequest(config, body)).execute().use { resp ->
                val code = resp.code
                if (code !in 200..299) {
                    val errText = resp.body?.string().orEmpty()
                    val apiMsg = runCatching {
                        json.decodeFromString<ChatResponse>(errText).error?.message
                    }.getOrNull()
                    error(apiMsg ?: "HTTP $code")
                }
                val source = resp.body ?: error("响应为空")
                source.source().use { s ->
                    var line: String?
                    while (s.readUtf8Line().also { line = it } != null) {
                        val l = line!!
                        val data = if (l.startsWith("data:")) l.removePrefix("data:").trim() else l.trim()
                        if (data.isEmpty()) continue
                        if (data == "[DONE]") break
                        val delta = runCatching {
                            json.decodeFromString<ChatResponse>(data)
                                .choices?.firstOrNull()?.delta
                        }.getOrNull()
                        if (delta != null) {
                            if (!delta.reasoningContent.isNullOrEmpty()) {
                                onReasoning?.invoke(delta.reasoningContent)
                            }
                            if (!delta.content.isNullOrEmpty()) onDelta(delta.content)
                        }
                    }
                }
            }
        }
    }

    /** 非流式对话：一次性返回完整回复（用于 AI 点歌 / 进度汇报等快速响应） */
    suspend fun chatOnce(
        config: ApiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int = 120,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildBody(config, messages, stream = false, maxTokens = maxTokens)
            client.newCall(buildRequest(config, body)).execute().use { resp ->
                val code = resp.code
                if (code !in 200..299) {
                    val errText = resp.body?.string().orEmpty()
                    val apiMsg = runCatching {
                        json.decodeFromString<ChatResponse>(errText).error?.message
                    }.getOrNull()
                    error(apiMsg ?: "HTTP $code")
                }
                val text = resp.body?.string().orEmpty()
                json.decodeFromString<ChatResponse>(text).choices
                    ?.firstOrNull()?.message?.content
                    ?: error("模型未返回内容")
            }
        }
    }

    /**
     * 工具调用对话：向模型暴露 read_file 工具，让 AI 自己读取用户发送的本地文件。
     * 循环处理 tool_calls（最多 5 轮），返回最终文本。
     */
    suspend fun chatWithTools(
        config: ApiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int = 300,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // 用 org.json 全程维护消息数组（支持 tool_calls / tool 结果消息）
            val arr = org.json.JSONArray(messagesJson(messages).toString())
            repeat(5) {
                val toolsArr = org.json.JSONArray().put(
                    org.json.JSONObject()
                        .put("type", "function")
                        .put(
                            "function",
                            org.json.JSONObject()
                                .put("name", "read_file")
                                .put("description", "读取用户发送的本地文件内容，路径由用户消息给出。支持 txt/md/json/csv/log/代码等文本文件；若为 pdf 或二进制则返回文件信息。")
                                .put(
                                    "parameters",
                                    org.json.JSONObject()
                                        .put("type", "object")
                                        .put(
                                            "properties",
                                            org.json.JSONObject().put(
                                                "path",
                                                org.json.JSONObject()
                                                    .put("type", "string")
                                                    .put("description", "文件的绝对路径"),
                                            ),
                                        )
                                        .put("required", org.json.JSONArray().put("path")),
                                ),
                        ),
                )
                val body = org.json.JSONObject()
                    .put("model", config.model)
                    .put("messages", arr)
                    .put("stream", false)
                    .put("max_tokens", maxTokens)
                    .put("tools", toolsArr)
                    .toString()
                val respText = client.newCall(buildRequest(config, body)).execute().use { resp ->
                    val code = resp.code
                    if (code !in 200..299) {
                        val errText = resp.body?.string().orEmpty()
                        val apiMsg = runCatching {
                            json.decodeFromString<ChatResponse>(errText).error?.message
                        }.getOrNull()
                        error(apiMsg ?: "HTTP $code")
                    }
                    resp.body?.string().orEmpty()
                }
                val obj = org.json.JSONObject(respText)
                val msg = obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                val content = msg.optString("content", "")
                val toolCalls = msg.optJSONArray("tool_calls")
                if (toolCalls == null || toolCalls.length() == 0) {
                    return@runCatching content
                }
                // 1) assistant 消息（含 tool_calls）
                val assistant = org.json.JSONObject()
                assistant.put("role", "assistant")
                assistant.put("content", content)
                val tcs = org.json.JSONArray()
                for (i in 0 until toolCalls.length()) {
                    val tc = toolCalls.getJSONObject(i)
                    val fn = tc.getJSONObject("function")
                    tcs.put(
                        org.json.JSONObject()
                            .put("id", tc.getString("id"))
                            .put("type", "function")
                            .put("function", org.json.JSONObject().put("name", fn.getString("name")).put("arguments", fn.getString("arguments")))
                    )
                }
                assistant.put("tool_calls", tcs)
                arr.put(assistant)
                // 2) 执行工具 → tool 结果消息
                for (i in 0 until toolCalls.length()) {
                    val tc = toolCalls.getJSONObject(i)
                    val id = tc.getString("id")
                    val fn = tc.getJSONObject("function")
                    var resultText = "（无参数）"
                    if (fn.has("arguments")) {
                        val args = runCatching { org.json.JSONObject(fn.getString("arguments")) }.getOrNull()
                        val path = args?.optString("path", "") ?: ""
                        resultText = if (path.isBlank()) "缺少 path 参数"
                        else runCatching { readLocalFile(path) }.getOrElse { "读取失败：${it.message}" }
                    }
                    arr.put(
                        org.json.JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", id)
                            .put("content", resultText)
                    )
                }
            }
            error("工具调用轮次过多（超过 5 轮）")
        }
    }

    /** 读取本地文件内容（read_file 工具的实现） */
    private fun readLocalFile(path: String): String {
        val f = java.io.File(path)
        if (!f.exists()) return "文件不存在：$path"
        val ext = f.extension.lowercase()
        if (ext == "pdf" || ext == "doc" || ext == "docx" || ext == "xls" || ext == "xlsx" || ext == "zip" || ext == "apk" || ext == "mp3" || ext == "mp4") {
            return "文件：${f.name}，${f.length()} 字节。这是二进制/富格式文件，无法直接读取文本，请基于文件名与用户说明回应。"
        }
        if (f.length() > 800_000L) {
            return "文件较大（${f.length()} 字节），仅显示开头：\n" + f.readText(Charsets.UTF_8).take(30_000)
        }
        return f.readText(Charsets.UTF_8).take(200_000)
    }

    /** 测试连接：发送一条极小请求，返回服务端回复片段或错误信息 */
    suspend fun testConnection(config: ApiConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildBody(
                config,
                listOf(ChatMessage("user", "ping")),
                stream = false,
                maxTokens = 8,
            )
            client.newCall(buildRequest(config, body)).execute().use { resp ->
                val code = resp.code
                if (code !in 200..299) {
                    val errText = resp.body?.string().orEmpty()
                    val apiMsg = runCatching {
                        json.decodeFromString<ChatResponse>(errText).error?.message
                    }.getOrNull()
                    error(apiMsg ?: "HTTP $code")
                }
                val text = resp.body?.string().orEmpty()
                val reply = runCatching {
                    json.decodeFromString<ChatResponse>(text).choices
                        ?.firstOrNull()?.message?.content
                }.getOrNull()
                "连接成功" + (reply?.let { "，模型回复：${it.trim().take(60)}" } ?: "")
            }
        }
    }
}

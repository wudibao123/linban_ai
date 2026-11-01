package com.liquidmate.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 语音朗读（TTS）配置。
 *
 * @param baseUrl  TTS 服务地址（如 https://api.openai.com 或自建中转）
 * @param apiKey   API 密钥
 * @param model    语音模型（如 tts-1 / tts-1-hd / gpt-4o-mini-tts）
 * @param voice    音色（alloy / echo / fable / onyx / nova / shimmer 等）
 * @param requestMode 请求方式：
 *   "openai"  → OpenAI /audio/speech：直接返回音频流；
 *   "custom"  → 通用 JSON：POST 后返回 JSON，取 audio/url/result 字段（链接或 base64）
 * @param format   音频格式（mp3 / wav / opus / aac / flac）
 * @param autoRead 自动朗读：AI 回复完成后自动播放
 */
@Serializable
data class TtsConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val voice: String = "alloy",
    val requestMode: String = "openai",
    val format: String = "mp3",
    val autoRead: Boolean = false,
)

/**
 * 语音合成客户端。
 *  - OpenAI 模式：POST <baseUrl>/v1/audio/speech，body {model, input, voice}，响应即音频
 *  - 通用 JSON 模式：POST 自定义完整 URL，body {text, model, voice}，
 *    从 JSON 的 audio / url / result / audio_url 字段取音频链接或 base64
 */
object TtsApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** 是否已完成基础配置（URL 非空即可用；Key 视服务而定） */
    fun ready(c: TtsConfig): Boolean = c.baseUrl.isNotBlank()

    /** 规整 baseUrl，返回 OpenAI 音频接口地址 */
    private fun speechEndpoint(baseUrl: String): String {
        var url = baseUrl.trim().trimEnd('/')
        if (url.endsWith("/audio/speech")) return url
        if (!url.endsWith("/v1")) url += "/v1"
        return "$url/audio/speech"
    }

    /** 合成语音，返回音频字节；测试时传入 "Hello World" */
    suspend fun synthesize(config: TtsConfig, text: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = text.trim()
                require(trimmed.isNotEmpty()) { "合成文本为空" }
                if (config.requestMode == "openai") {
                    synthesizeOpenAi(config, trimmed)
                } else {
                    synthesizeCustom(config, trimmed)
                }
            }
        }

    /** OpenAI /audio/speech 模式 */
    private fun synthesizeOpenAi(config: TtsConfig, text: String): ByteArray {
        val body = org.json.JSONObject()
            .put("model", config.model.ifBlank { "tts-1" })
            .put("input", text)
            .put("voice", config.voice.ifBlank { "alloy" })
            .put("response_format", config.format.ifBlank { "mp3" })
            .toString()
        val req = Request.Builder()
            .url(speechEndpoint(config.baseUrl))
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply {
                if (config.apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${config.apiKey}")
            }
            .build()
        client.newCall(req).execute().use { resp ->
            val data = resp.body?.bytes()
            if (resp.code !in 200..299 || data == null) {
                error(readError(resp.body?.string(), resp.code))
            }
            return data
        }
    }

    /** 通用 JSON 模式：返回 JSON 中携带音频链接或 base64 */
    private fun synthesizeCustom(config: TtsConfig, text: String): ByteArray {
        // 通用模式：baseUrl 视为完整端点；若仅填到根，尝试 /v1/audio/speech
        val endpoint = config.baseUrl.trim().let { u ->
            if (u.endsWith("/audio/speech") || u.contains("tts") || u.contains("speech")) u
            else speechEndpoint(u)
        }
        val body = org.json.JSONObject()
            .put("text", text)
            .put("model", config.model)
            .put("voice", config.voice)
            .put("format", config.format)
            .toString()
        val req = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply {
                if (config.apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${config.apiKey}")
            }
            .build()
        val raw = client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (resp.code !in 200..299) error(readError(txt, resp.code))
            txt
        }
        // 解析音频：链接 → 下载；data:base64 → 解码
        val field = runCatching {
            val obj = json.parseToJsonElement(raw) as JsonObject
            listOf("audio", "audio_url", "url", "result", "data", "speech").firstNotNullOf { k ->
                obj[k]?.jsonPrimitive?.content
            }
        }.getOrNull() ?: error("响应中未找到音频字段（audio/url/result 等）")
        return when {
            field.startsWith("data:") -> {
                val base64 = field.substringAfter("base64,", field)
                android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            }
            field.startsWith("http://") || field.startsWith("https://") -> {
                val r2 = Request.Builder().url(field).build()
                client.newCall(r2).execute().use { resp ->
                    val d = resp.body?.bytes()
                    if (resp.code !in 200..299 || d == null) error("音频下载失败 HTTP ${resp.code}")
                    d
                }
            }
            else -> android.util.Base64.decode(field, android.util.Base64.DEFAULT)
        }
    }

    private fun readError(text: String?, code: Int): String {
        val msg = runCatching {
            val obj = json.parseToJsonElement(text.orEmpty()) as JsonObject
            obj["error"]?.let {
                (it as? JsonObject)?.get("message")?.jsonPrimitive?.content
                    ?: it.jsonPrimitive.content
            }
        }.getOrNull()
        return msg ?: "HTTP $code"
    }
}

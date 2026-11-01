package com.liquidmate.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 联网搜索：
 *  - DeepSeek 模型 → 官方 POST /v1/tools/search（自研搜索引擎，结构化结果）
 *  - 其他模型 → 第三方（Bing 网页结果解析）
 * 结果统一整理成可注入上下文的文本。
 */
object SearchApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 搜索并返回整理好的「联网资料」文本；失败返回 null（不阻断主对话） */
    suspend fun searchContext(config: ApiConfig, query: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (ChatOptions.isDeepSeek(config.model)) deepSeekSearch(config, query)
                else bingSearch(query)
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }

    /** DeepSeek 官方搜索 */
    private fun deepSeekSearch(config: ApiConfig, query: String): String {
        var base = config.baseUrl.trim().trimEnd('/')
        if (base.endsWith("/chat/completions")) base = base.removeSuffix("/chat/completions")
        if (!base.endsWith("/v1")) base += "/v1"
        val url = "$base/tools/search"
        val body = org.json.JSONObject()
            .put("query", query.take(128))
            .put("count", 6)
            .put("need_content", false)
            .toString()
        val req = Request.Builder()
            .url(url)
            .post(body.toJsonBody())
            .addHeader("Content-Type", "application/json")
            .apply {
                if (config.apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${config.apiKey}")
            }
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (resp.code !in 200..299) error("HTTP ${resp.code}")
            val results = org.json.JSONObject(txt).optJSONArray("results") ?: return ""
            val sb = StringBuilder("以下是联网搜索到的最新资料（请据此回答，并标注来源）：\n\n")
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                sb.append("【${i + 1}】").append(r.optString("title", "")).append("\n")
                sb.append("来源：").append(r.optString("url", "")).append("\n")
                val summary = r.optString("summary").ifBlank { r.optString("snippet") }
                sb.append("摘要：").append(summary).append("\n\n")
            }
            return sb.toString()
        }
    }

    /** 第三方搜索：Bing 网页结果解析（国内可达） */
    private fun bingSearch(query: String): String {
        val url = "https://cn.bing.com/search?q=" +
            java.net.URLEncoder.encode(query, "UTF-8") + "&setlang=zh-CN&mkt=zh-CN"
        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
            )
            .build()
        client.newCall(req).execute().use { resp ->
            val html = resp.body?.string().orEmpty()
            if (resp.code !in 200..299) error("HTTP ${resp.code}")
            // 提取 b_algo 结果块：标题链接 + 摘要
            val blocks = Regex("<li class=\"b_algo\".*?</li>", RegexOption.DOT_MATCHES_ALL)
                .findAll(html).take(6).toList()
            val sb = StringBuilder("以下是联网搜索到的最新资料（请据此回答，并标注来源）：\n\n")
            var n = 0
            for (b in blocks) {
                val frag = b.value
                val href = Regex("<h2[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"").find(frag)?.groupValues?.get(1)
                    ?: continue
                val title = Regex("<h2[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL)
                    .find(frag)?.groupValues?.get(1)?.stripHtml()?.trim().orEmpty()
                val snippet = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                    .find(frag)?.groupValues?.get(1)?.stripHtml()?.trim().orEmpty()
                n++
                sb.append("【$n】").append(title).append("\n")
                sb.append("来源：").append(href).append("\n")
                sb.append("摘要：").append(snippet).append("\n\n")
            }
            return if (n == 0) "" else sb.toString()
        }
    }

    private fun String.stripHtml(): String =
        Regex("<[^>]+>").replace(this, "")
            .replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&nbsp;", " ")

    private fun String.toJsonBody() =
        toRequestBody("application/json".toMediaType())
}

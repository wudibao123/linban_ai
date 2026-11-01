package com.liquidmate.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.liquidmate.MusicServer
import com.liquidmate.data.ApiConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * AI 一起听 —— WebView 内嵌 HTML 实现（assets/together.html）。
 *
 * 页面通过应用内本地 HTTP 服务器（MusicServer, NanoHTTPD）以
 * http://127.0.0.1:<port>/together.html 加载：
 *  - 页面 JS 用相对路径 /api/ 接口调用搜索 / 解析 / AI 聊天，同源无 CORS；
 *  - 音乐接口由服务器在原生层用 OkHttp 代理（UA + Referer，绕开防盗链）；
 *  - AI 聊天复用主应用全局 API 配置。
 * 音频 CDN 请求由 shouldInterceptRequest 补 Referer/UA，避免防盗链 403。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TogetherWebView(
    characterName: String,
    persona: String,
    configProvider: () -> ApiConfig,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configRef = rememberUpdatedState(configProvider)
    val nameRef = rememberUpdatedState(characterName)
    val personaRef = rememberUpdatedState(persona)
    val exitRef = rememberUpdatedState(onExit)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MusicServer.configProvider = configRef.value
            MusicServer.characterName = nameRef.value
            MusicServer.characterPersona = personaRef.value
            MusicServer.ensureStarted(ctx)
            val wv = WebView(ctx)
            wv.apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.setAllowContentAccess(true)
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun exit() {
                            // 先让页面停掉音频，再退出（页面销毁由 onRelease 完成）
                            runCatching {
                                wv.evaluateJavascript(
                                    "try{audio&&audio.pause();audio&&(audio.src='');}catch(e){}",
                                    null,
                                )
                            }
                            exitRef.value()
                        }
                    },
                    "TogetherBridge",
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url?.toString() ?: return null
                        // 音频 CDN 防盗链兜底：给酷狗 / 网易播放请求补 Referer + UA
                        val isMusicHost = url.contains("kugou.com") || url.contains("music.163.com") ||
                            url.contains("126.net") || url.contains("kugou.")
                        if (!isMusicHost) return null
                        return try {
                            val client = OkHttpClient.Builder()
                                .connectTimeout(12, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS)
                                .build()
                            val rb = Request.Builder().url(url)
                            if (url.contains("kugou")) {
                                rb.header("Referer", "http://m.kugou.com/")
                                rb.header("User-Agent", UA_IPHONE)
                            } else {
                                rb.header("Referer", "https://music.163.com/")
                                rb.header("User-Agent", UA_CHROME)
                            }
                            val resp = client.newCall(rb.build()).execute()
                            val headers = resp.headers.toMultimap()
                                .mapValues { e -> e.value.joinToString(", ") }
                                .toMutableMap()
                            headers["Access-Control-Allow-Origin"] = "*"
                            WebResourceResponse(
                                resp.header("Content-Type") ?: "application/octet-stream",
                                resp.header("Content-Encoding"),
                                resp.code,
                                resp.message,
                                headers,
                                resp.body?.byteStream(),
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                loadUrl("http://127.0.0.1:${MusicServer.PORT}/index.html")
            }
            wv
        },
        update = { _ ->
            // 换角色后再进一起听：每次重组都同步最新角色名与人设
            MusicServer.characterName = nameRef.value
            MusicServer.characterPersona = personaRef.value
        },
        onRelease = { released ->
            // 退出一起听：销毁 WebView，停止一切音频与页面渲染
            runCatching {
                released.stopLoading()
                released.loadUrl("about:blank")
                released.onPause()
                released.destroy()
            }
        },
    )
}

private const val UA_CHROME =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
private const val UA_IPHONE =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"

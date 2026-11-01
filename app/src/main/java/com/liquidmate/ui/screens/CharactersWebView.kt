package com.liquidmate.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.liquidmate.MusicServer

/**
 * 伴侣 Tab —— WebView 内嵌开源项目 GooseHyperGlass 的液态玻璃滚动容器
 * （assets/characters.html，lazy-scroll-container 模式，玻璃卡片列表）。
 *
 * 页面经应用内本地服务器（MusicServer）以 http://127.0.0.1:<port>/characters.html
 * 加载，角色数据走同源 /api/characters；点击卡片链接通过 JS Bridge 回调原生。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CharactersWebView(
    charactersJson: String,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val jsonRef = rememberUpdatedState(charactersJson)
    val openChatRef = rememberUpdatedState(onOpenChat)
    var lastJson by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MusicServer.charactersProvider = { jsonRef.value }
            MusicServer.ensureStarted(ctx)
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun openChat(href: String) {
                            openChatRef.value(href)
                        }
                    },
                    "CharacterBridge",
                )
                webViewClient = WebViewClient()
                loadUrl("http://127.0.0.1:${MusicServer.PORT}/characters.html")
            }
        },
        update = { wv ->
            // 角色列表变化时（新建/编辑/删除后）才重载页面
            if (lastJson != jsonRef.value) {
                lastJson = jsonRef.value
                wv.loadUrl("http://127.0.0.1:${MusicServer.PORT}/characters.html")
            }
        },
    )
}

package com.liquidmate

import android.content.Context
import com.liquidmate.data.ApiConfig
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * 应用内本地 HTTP 服务器（NanoHTTPD）。
 *
 * 一起听页面用 http://127.0.0.1:<port>/together.html 打开（而不是 file://），
 * 页面里的 JS 全部走相对路径 /api/ 系列接口（同源、无 CORS 限制），由本服务器在原生层
 * 用 OkHttp 代理音乐接口与 AI 聊天接口——与 Mineradio 的 Node 服务端代理同思路：
 *  - 酷狗 / 网易云请求携带真实 UA + Referer，彻底绕开 CORS 与防盗链；
 *  - AI 聊天复用主应用全局 API 配置（baseUrl / apiKey / model），页面不再持有密钥。
 */
object MusicServer {

    const val PORT = 8080

    /** 由 TogetherWebView 注入，实时读取主应用最新 API 配置 */
    @Volatile
    var configProvider: (() -> ApiConfig)? = null

    /** 当前角色名（由 TogetherWebView 注入） */
    @Volatile
    var characterName: String = "灵伴"
    var characterPersona: String = ""

    /** 角色列表 JSON（伴侣 Tab 玻璃滚动容器由 CharactersWebView 注入） */
    @Volatile
    var charactersProvider: (() -> String)? = null

    private var server: NanoHTTPD? = null

    @Synchronized
    fun ensureStarted(ctx: Context) {
        if (server != null) return
        val app = ctx.applicationContext
        var started: NanoHTTPD? = null
        for (port in PORT..PORT + 10) {
            try {
                val s = object : NanoHTTPD("127.0.0.1", port) {
                    override fun serve(session: IHTTPSession): Response {
                        fun asset(name: String, mime: String): Response {
                            val bytes = app.assets.open(name).readBytes()
                            return newFixedLengthResponse(
                                Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong()
                            )
                        }
                        fun json(s: String): Response =
                            newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", s)

                        val uri = session.uri
                        return try {
                            when {
                                uri == "/" || uri == "/index.html" || uri == "/together.html" -> {
                                    // 每次请求实时写入最新 API 配置（内嵌密钥/URL/模型，修改设置后自动生效）
                                    val cfg = configProvider?.invoke()
                                    val raw = app.assets.open("together.html").readBytes().toString(Charsets.UTF_8)
                                    // JSON 字面量直接嵌入（自动转义引号/反斜杠，防止破坏页面 JS）
                                    val cfgJson = JSONObject()
                                        .put("key", cfg?.apiKey ?: "")
                                        .put("url", cfg?.baseUrl ?: "")
                                        .put("model", cfg?.model ?: "")
                                        .toString()
                                    val html = raw.replace("__LQ_API_JSON__", cfgJson)
                                    newFixedLengthResponse(
                                        Response.Status.OK, "text/html; charset=utf-8", html
                                    )
                                }

                                uri == "/characters.html" ->
                                    asset("characters.html", "text/html; charset=utf-8")

                                uri == "/api/characters" ->
                                    json(charactersProvider?.invoke() ?: "{\"list\":[]}")

                                uri == "/glasskit.js" ->
                                    asset("glasskit.js", "application/javascript; charset=utf-8")

                                uri == "/api/config" -> {
                                    val c = configProvider?.invoke()
                                    json(
                                        JSONObject()
                                            .put("characterName", characterName)
                                            .put("persona", characterPersona)
                                            .put("model", c?.model ?: "")
                                            .put(
                                                "configured",
                                                c != null && c.baseUrl.isNotBlank() && c.model.isNotBlank()
                                            )
                                            .toString()
                                    )
                                }

                                uri.startsWith("/api/search") -> {
                                    val q = session.parms?.get("q") ?: ""
                                    val src = session.parms?.get("source") ?: "kugou"
                                    val limit = session.parms?.get("limit")?.toIntOrNull() ?: 30
                                    if (q.isBlank()) json(JSONObject().put("error", "关键词为空").toString())
                                    else json(
                                        JSONObject().put(
                                            "list",
                                            if (src == "netease") searchNetease(q, limit) else searchKugou(q, limit)
                                        ).toString()
                                    )
                                }

                                uri.startsWith("/api/resolve") -> {
                                    val token = session.parms?.get("token") ?: ""
                                    val src = session.parms?.get("source") ?: "kugou"
                                    val albumId = session.parms?.get("album_id") ?: "0"
                                    val o = JSONObject()
                                    try {
                                        // 与用户自研播放器一致：直接返回 CDN 直链给页面 audio 播放
                                        //（WebView audio 直连会被 CDN 放行；代理转发反而被拒）
                                        if (src == "netease") {
                                            primeNetease()
                                            o.put(
                                                "url",
                                                "https://music.163.com/song/media/outer/url?id=" +
                                                    java.net.URLEncoder.encode(token, "UTF-8") + ".mp3"
                                            )
                                        } else {
                                            val u = kugouUrl(token, albumId)
                                            if (u.isNullOrBlank()) {
                                                o.put("error", "无法获取播放地址（可能为 VIP/付费或需登录）")
                                            } else o.put("url", u)
                                        }
                                    } catch (e: Exception) {
                                        o.put("error", e.message ?: "解析播放地址失败")
                                    }
                                    json(o.toString())
                                }

                                uri.startsWith("/api/audio") -> {
                                    val target = session.parms?.get("url") ?: ""
                                    val ref = session.parms?.get("ref") ?: ""
                                    val ua = session.parms?.get("ua") ?: "Mozilla/5.0"
                                    if (target.isBlank()) {
                                        json(JSONObject().put("error", "缺少 url").toString())
                                    } else {
                                        if (target.contains("music.163.com")) primeNetease()
                                        audioProxy(target, ref, ua)
                                    }
                                }

                                uri.startsWith("/api/images") -> {
                                    val q = session.parms?.get("q") ?: ""
                                    val n = session.parms?.get("n")?.toIntOrNull() ?: 4
                                    if (q.isBlank()) json(JSONObject().put("error", "关键词为空").toString())
                                    else json(JSONObject().put("list", imageSearch(q, n)).toString())
                                }

                                uri.startsWith("/api/chat") -> {
                                    // 必须用 parseBody 按 content-length 精确读取，不能 readBytes()
                                    // （readBytes 在 keep-alive 连接上会阻塞到 socket 超时 → 全部 AI 调用假"超时"）
                                    val files = HashMap<String, String>()
                                    runCatching { session.parseBody(files) }
                                    json(chat(files["postData"] ?: ""))
                                }

                                else -> newFixedLengthResponse(
                                    Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "404"
                                )
                            }
                        } catch (e: Exception) {
                            json(JSONObject().put("error", e.message ?: "服务器异常").toString())
                        }
                    }
                }
                s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                started = s
                break
            } catch (e: Exception) {
                started = null
            }
        }
        server = started
    }

    // ---------------- AI 聊天（复用主应用 API 配置） ----------------
    // 图片搜索：DuckDuckGo 图片接口（无 key），失败降级 LoremFlickr 随机图
private fun imageSearch(q: String, n: Int): JSONArray {
    val arr = JSONArray()
    val enc = java.net.URLEncoder.encode(q, "UTF-8")
    try {
        val req = Request.Builder()
            .url("https://duckduckgo.com/?q=$enc&iax=images&ia=images")
            .header("User-Agent", UA_CHROME)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val html = resp.body?.string() ?: ""
            val vqd = Regex("vqd=['\"]([^'\"]+)['\"]").find(html)?.groupValues?.get(1)
            if (!vqd.isNullOrBlank()) {
                val req2 = Request.Builder()
                    .url("https://duckduckgo.com/i.js?l=us-en&o=json&q=$enc&vqd=$vqd")
                    .header("User-Agent", UA_CHROME)
                    .build()
                httpClient.newCall(req2).execute().use { resp2 ->
                    val js = resp2.body?.string() ?: ""
                    val obj = JSONObject(js)
                    val results = obj.optJSONArray("results") ?: JSONArray()
                    for (i in 0 until results.length()) {
                        if (arr.length() >= n) break
                        val r = results.getJSONObject(i)
                        val img = r.optString("image", "")
                        if (img.isNotBlank() && (img.startsWith("http://") || img.startsWith("https://"))) {
                            arr.put(JSONObject().put("url", img).put("title", r.optString("title", q)))
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {
    }
    if (arr.length() == 0) {
        // 降级：LoremFlickr 按关键词返回随机图（免费无 key）
        for (i in 0 until n) {
            arr.put(JSONObject().put("url", "https://loremflickr.com/800/800/$enc?lock=$i").put("title", q))
        }
    }
    return arr
}

private fun chat(bodyStr: String): String {
        return runCatching {
            val body = JSONObject(bodyStr)
            val c = configProvider?.invoke()
            if (c == null || c.baseUrl.isBlank() || c.model.isBlank()) {
                return@runCatching JSONObject()
                    .put("error", "尚未配置 AI 服务：请在「设置 → AI 服务」填写 API URL 与模型名").toString()
            }
            var base = c.baseUrl.trim().trimEnd('/')
            if (base.isEmpty()) base = "https://api.openai.com/v1"
            if (!base.endsWith("/v1")) base += "/v1"
            val url = base + "/chat/completions"
            val payload = JSONObject()
            payload.put("model", c.model)
            payload.put("messages", body.optJSONArray("messages") ?: JSONArray())
            payload.put("stream", false)
            payload.put("max_tokens", body.optInt("maxTokens", 120))
            val req = Request.Builder().url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            if (c.apiKey.isNotBlank()) req.header("Authorization", "Bearer " + c.apiKey)
            chatClient.newCall(req.build()).execute().use { resp ->
                val txt = resp.body?.string().orEmpty()
                if (resp.code < 200 || resp.code >= 300) {
                    val msg = runCatching {
                        JSONObject(txt).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    return@use JSONObject().put("error", msg ?: "HTTP " + resp.code).toString()
                }
                val j = JSONObject(txt)
                val choices = j.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val m = choices.getJSONObject(0).optJSONObject("message")
                    if (m != null) {
                        return@use JSONObject().put("reply", m.optString("content", "")).toString()
                    }
                }
                JSONObject().put("error", "模型未返回内容").toString()
            }
        }.getOrElse { e ->
            JSONObject().put("error", e.message ?: "请求失败").toString()
        }
    }
}

// ---------------- 音乐 API 代理（Mineradio 同款端点） ----------------

/** 内存 CookieJar：记住网易云/酷狗匿名 cookie，跟随重定向时携带（防盗链与登录态关键） */
private class MemCookieJar : CookieJar {
    private val store = HashMap<String, List<Cookie>>()
    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store[url.host] = cookies
    }
    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
}

private val jar = MemCookieJar()

private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cookieJar(jar)
        .build()
}

/** 首次访问 music.163.com 拿匿名 cookie（NMTID 等），后续接口请求自动携带 */
@Volatile
private var neteasePrimed = false
private fun primeNetease() {
    if (neteasePrimed) return
    neteasePrimed = true
    runCatching {
        httpClient.newCall(
            Request.Builder()
                .url("https://music.163.com/")
                .header("User-Agent", UA_CHROME)
                .build()
        ).execute().use { }
    }
}

/** 音频流代理：服务端带 UA/Referer/cookie 拉取上游，流式转发给前端（NanoHTTPD chunked） */
private fun audioProxy(target: String, ref: String, ua: String? = null): Response {
    return try {
        val rb = Request.Builder().url(target).header("User-Agent", ua ?: "Mozilla/5.0")
        if (ref.isNotBlank()) rb.header("Referer", ref)
        val resp = httpClient.newCall(rb.build()).execute()
        val body = resp.body
        if (!resp.isSuccessful || body == null) {
            val code = resp.code
            resp.close()
            val msg = if (target.contains("music.163.com")) {
                "网易云该歌曲需登录或 VIP 才能播放"
            } else {
                "音频源请求失败（HTTP $code）"
            }
            return err(msg)
        }
        val mime = resp.header("Content-Type") ?: "audio/mpeg"
        // 流式转发（chunked）；OkHttp 的 body 流在 NanoHTTPD 写完并 close 时释放
        NanoHTTPD.newChunkedResponse(Response.Status.OK, mime, body.byteStream())
    } catch (e: Exception) {
        err(e.message ?: "音频代理失败")
    }
}

private fun err(msg: String): Response {
    val bytes = JSONObject().put("error", msg).toString().toByteArray(Charsets.UTF_8)
    return NanoHTTPD.newFixedLengthResponse(
        Response.Status.BAD_REQUEST,
        "application/json; charset=utf-8",
        ByteArrayInputStream(bytes),
        bytes.size.toLong(),
    )
}

/** AI 聊天专用客户端：与主页 ChatApi 完全对齐（connect 20s / read 150s，非流式请求更慢需留足余量） */
private val chatClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .build()
}

private const val UA_CHROME =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
private const val UA_IPHONE =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"

private fun stripHtml(s: String?): String = (s ?: "").replace(Regex("<[^>]*>"), "").trim()

// 酷狗搜索（参数与用户自研播放器一致：只带 page/pagesize/platform，避免酷狗拒绝）
private fun searchKugou(kw: String, limit: Int): JSONArray {
    val url = "https://songsearch.kugou.com/song_search_v2?keyword=" +
        java.net.URLEncoder.encode(kw, "UTF-8") +
        "&page=1&pagesize=$limit&platform=WebFilter"
    val req = Request.Builder().url(url)
        .header("User-Agent", UA_IPHONE)
        .header("Referer", "http://m.kugou.com/")
        .build()
    httpClient.newCall(req).execute().use { resp ->
        val j = JSONObject(resp.body?.string().orEmpty())
        val lists = j.optJSONObject("data")?.optJSONArray("lists") ?: JSONArray()
        val out = JSONArray()
        for (i in 0 until lists.length()) {
            val o = lists.getJSONObject(i)
            val hash = o.optString("FileHash")
            if (hash.isEmpty()) continue
            val item = JSONObject()
            item.put("id", hash)
            item.put("albumId", o.optString("AlbumID", "0"))
            item.put("name", stripHtml(o.optString("SongName")))
            item.put("artist", stripHtml(o.optString("SingerName")))
            item.put("album", stripHtml(o.optString("AlbumName")))
            item.put("durationMs", o.optLong("Duration", 0) * 1000L)
            item.put("source", "kugou")
            val pay = o.optInt("PayType", 0)
            item.put("isVip", pay != 0)
            item.put("payType", pay)
            out.put(item)
        }
        return out
    }
}

// 酷狗播放地址（与用户播放器一致：key=md5(hash+"kgcloud") 签名 + album_id + vip 参数）
private fun kugouUrl(hash: String, albumId: String): String? {
    val key = md5(hash + "kgcloud")
    val url = "https://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=" +
        java.net.URLEncoder.encode(hash, "UTF-8") +
        "&key=$key&album_id=${java.net.URLEncoder.encode(albumId, "UTF-8")}&pid=1&forceDown=0&vip=65530"
    val req = Request.Builder().url(url)
        .header("User-Agent", UA_IPHONE)
        .header("Referer", "http://m.kugou.com/")
        .build()
    httpClient.newCall(req).execute().use { resp ->
        val j = JSONObject(resp.body?.string().orEmpty())
        val d = j.optJSONObject("data") ?: JSONObject()
        val u = pickUrl(j.opt("url"))
            ?: pickUrl(j.opt("play_url"))
            ?: pickUrl(j.opt("backupUrl"))
            ?: pickUrl(j.opt("backup_url"))
            ?: pickUrl(d.opt("url"))
            ?: pickUrl(d.opt("play_url"))
            ?: pickUrl(d.opt("backupUrl"))
            ?: pickUrl(d.opt("backup_url"))
        return u
    }
}

private fun md5(s: String): String {
    val md = java.security.MessageDigest.getInstance("MD5")
    val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

/** 从 url 字段中挑选可播放的 http(s) 链接（支持数组 / \/ 转义） */
private fun pickUrl(v: Any?): String? {
    return when (v) {
        null -> null
        is JSONArray -> {
            for (i in 0 until v.length()) {
                val r = pickUrl(v.opt(i))
                if (r != null) return r
            }
            null
        }
        is String -> {
            val s = v.replace("\\/", "/").trim()
            if (s.startsWith("http://") || s.startsWith("https://")) s else null
        }
        else -> null
    }
}

// 网易云搜索（GET 参数与用户自研播放器一致）
private fun searchNetease(kw: String, limit: Int): JSONArray {
    primeNetease()
    val url = "https://music.163.com/api/cloudsearch/pc?s=" +
        java.net.URLEncoder.encode(kw, "UTF-8") + "&type=1&offset=0&limit=$limit"
    val req = Request.Builder().url(url)
        .header("User-Agent", UA_CHROME)
        .header("Referer", "https://music.163.com/")
        .build()
    val out = JSONArray()
    var searched = false
    httpClient.newCall(req).execute().use { resp ->
        val j = JSONObject(resp.body?.string().orEmpty())
        val songs = j.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
        searched = songs.length() > 0
        for (i in 0 until songs.length()) {
            val o = songs.getJSONObject(i)
            if (!o.has("id")) continue
            val priv = o.optJSONObject("privilege")
            val fee = o.optInt("fee", 0)
            // VIP 只看 fee（0=免费，1=VIP，8=付费专辑等）；pl 是"可播放性"不是 VIP，误标会害免费歌
            val vip = fee > 0 || (priv != null && priv.optInt("fee", 0) > 0)
            val ars = o.optJSONArray("ar") ?: JSONArray()
            val artists = buildList {
                for (k in 0 until ars.length()) {
                    add(stripHtml(ars.getJSONObject(k).optString("name")))
                }
            }.joinToString(" / ")
            val item = JSONObject()
            item.put("id", o.optLong("id"))
            item.put("name", stripHtml(o.optString("name")))
            item.put("artist", artists)
            item.put("album", stripHtml(o.optJSONObject("al")?.optString("name") ?: ""))
            item.put("durationMs", o.optLong("dt", 0L))
            item.put("source", "netease")
            item.put("isVip", vip)
            item.put("payType", fee)
            out.put(item)
        }
        return out
    }
}

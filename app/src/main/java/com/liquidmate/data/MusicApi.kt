package com.liquidmate.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 一首歌（搜索 / 队列 / 播放共用），isVip 用于显示 VIP 标识与排除 VIP */
@Serializable
data class MusicTrack(
    val id: String,          // 平台内 id：酷狗 FileHash / 网易云 songId
    val name: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?,
    val source: String,      // "kugou" / "netease"
    val isVip: Boolean = false,
    val playUrl: String? = null, // 解析后填入
) {
    val displayTime: String
        get() = if (durationMs > 0) {
            val s = durationMs / 1000
            "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
        } else ""

    companion object {
        /** 从酷狗 song_search_v2 结果构造 */
        fun fromKugou(o: JsonObject): MusicTrack {
            val pay = o["PayType"]?.jsonPrimitive?.intOrNull ?: 0
            return MusicTrack(
                id = o["FileHash"]?.jsonPrimitive?.contentOrNull ?: "",
                name = o["SongName"]?.jsonPrimitive?.contentOrNull ?: "未知歌曲",
                artist = o["SingerName"]?.jsonPrimitive?.contentOrNull ?: "",
                album = o["AlbumName"]?.jsonPrimitive?.contentOrNull ?: "",
                durationMs = (o["TimeLength"]?.jsonPrimitive?.contentOrNull ?: o["Duration"]?.jsonPrimitive?.intOrNull?.toString() ?: "").toLongOrNull() ?: 0L,
                coverUrl = null,
                source = "kugou",
                isVip = pay != 0,
            )
        }

        /** 从网易云 cloudsearch 结果构造 */
        fun fromNetease(o: JsonObject): MusicTrack {
            val fee = o["fee"]?.jsonPrimitive?.intOrNull ?: 0
            val priv = o["privilege"]?.jsonObject
            val privFee = priv?.get("fee")?.jsonPrimitive?.intOrNull ?: 0
            val pl = priv?.get("pl")?.jsonPrimitive?.intOrNull ?: 0
            val vip = fee != 0 || privFee != 0 || pl != 0
            val artists = (o["ar"] as? JsonArray)?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                ?: emptyList()
            val albumObj = o["al"]?.jsonObject
            return MusicTrack(
                id = o["id"]?.jsonPrimitive?.intOrNull?.toString() ?: "",
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: "未知歌曲",
                artist = artists.joinToString(" / "),
                album = albumObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "",
                durationMs = o["dt"]?.jsonPrimitive?.longOrNull ?: 0L,
                coverUrl = albumObj?.get("picUrl")?.jsonPrimitive?.contentOrNull,
                source = "netease",
                isVip = vip,
            )
        }
    }
}

/**
 * 音乐 API —— 复刻 Mineradio (github.com/XxHuberrr/Mineradio) 项目内使用的
 * 酷狗（kugou-api.js）与网易云（server.js / NeteaseCloudMusicApi）接口请求。
 * Android 端用 Kotlin 同构调用相同的 HTTP 接口。
 */
object MusicApi {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** 酷狗搜索：songsearch.kugou.com/song_search_v2（Mineradio kugou-api.js 同款接口） */
    suspend fun searchKugou(keyword: String, limit: Int = 30): Result<List<MusicTrack>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://songsearch.kugou.com/song_search_v2?" +
                    "keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}" +
                    "&page=1&pagesize=$limit&userid=-1&clientver=2000&platform=WebFilter" +
                    "&tag=em&filter=2&iscorrection=1&privilege_filter=0"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", "https://www.kugou.com/")
                    .build()
                val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
                val root = json.parseToJsonElement(body).jsonObject
                val lists = root["data"]?.jsonObject?.get("lists") as? JsonArray ?: JsonArray(emptyList())
                lists.mapNotNull { it.jsonObject.takeIf { o -> !o["FileHash"]?.jsonPrimitive?.contentOrNull.isNullOrEmpty() } }
                    .map { MusicTrack.fromKugou(it) }
            }
        }

    /** 网易云搜索：/api/cloudsearch/pc（Mineradio server.js 基于 NeteaseCloudMusicApi 同款底层接口） */
    suspend fun searchNetease(keyword: String, limit: Int = 30): Result<List<MusicTrack>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("https://music.163.com/api/cloudsearch/pc")
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", "https://music.163.com/")
                    .addHeader("Content-Type", "application/json")
                    .post(
                        """{"s":${json.encodeToString(keyword)},"type":1,"limit":$limit,"offset":0,"total":true}"""
                            .toRequestBody("application/json; charset=utf-8".toMediaType())
                    )
                    .build()
                val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
                val root = json.parseToJsonElement(body).jsonObject
                val songs = root["result"]?.jsonObject?.get("songs") as? JsonArray ?: JsonArray(emptyList())
                songs.mapNotNull { it.jsonObject.takeIf { o -> o["id"]?.jsonPrimitive?.intOrNull != null } }
                    .map { MusicTrack.fromNetease(it) }
            }
        }

    /** 酷狗播放地址：m.kugou.com getSongInfo.php（Mineradio kugou-api.js 的移动端 playInfo 接口） */
    suspend fun kugouPlayUrl(hash: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=$hash"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", "https://www.kugou.com/")
                    .build()
                val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonObject
                data?.get("url")?.jsonPrimitive?.contentOrNull
                    ?: data?.get("play_url")?.jsonPrimitive?.contentOrNull
                    ?: throw IllegalStateException("该歌曲无法获取播放地址（可能为 VIP/付费）")
            }
        }

    /** 网易云播放地址：/api/song/enhance/player/url（Mineradio server.js 同款 song_url 底层接口） */
    suspend fun neteasePlayUrl(songId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("https://music.163.com/api/song/enhance/player/url")
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", "https://music.163.com/")
                    .addHeader("Content-Type", "application/json")
                    .post(
                        """{"ids":"[$songId]","br":3200000,"csrf_token":""}"""
                            .toRequestBody("application/json; charset=utf-8".toMediaType())
                    )
                    .build()
                val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"] as? JsonArray ?: JsonArray(emptyList())
                val first = data.firstOrNull()?.jsonObject
                first?.get("url")?.jsonPrimitive?.contentOrNull
                    ?: throw IllegalStateException("该歌曲无法获取播放地址（可能为 VIP/付费或需登录）")
            }
        }

    /** 按来源获取播放地址 */
    suspend fun resolvePlayUrl(track: MusicTrack): Result<String> =
        when (track.source) {
            "kugou" -> kugouPlayUrl(track.id)
            "netease" -> neteasePlayUrl(track.id)
            else -> Result.failure(IllegalStateException("未知音乐来源"))
        }
}

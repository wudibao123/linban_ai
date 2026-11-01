package com.liquidmate.data

import android.content.Context
import com.liquidmate.ui.screens.CharacterUi
import com.liquidmate.ui.screens.ChatMsgUi
import com.liquidmate.ui.screens.Wallpaper
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 应用完整状态（角色 / 聊天记录 / 壁纸 / 主题 / AI 配置 / 玻璃全局调参 / 日记）—— 可序列化落盘 */
@Serializable
data class AppState(
    val characters: List<CharacterUi> = emptyList(),
    val messages: Map<String, List<ChatMsgUi>> = emptyMap(),
    val wallpaper: Wallpaper = Wallpaper.Gradient(0),
    val themeMode: String = "System",
    val api: ApiConfig = ApiConfig(),
    val tts: TtsConfig = TtsConfig(),
    val chatOptions: ChatOptions = ChatOptions(),
    val seeded: Boolean = false,
    val glass: GlassParams = GlassParams(),
    val diaries: List<DiaryEntry> = emptyList(),
    val albums: Map<String, List<AlbumEntry>> = emptyMap(),   // characterId -> 相册
    val moments: List<MomentEntry> = emptyList(),             // 朋友圈时间线
    val conversations: Map<String, List<Conversation>> = emptyMap(), // characterId -> 会话列表（每角色隔离）
    val activeConv: Map<String, String> = emptyMap(),         // characterId -> 当前会话 id
)

/** 一条日记：author = "user"（用户手写）或 "ai"（AI 代笔），本地持久化 */
@Serializable
data class DiaryEntry(
    val id: String,
    val date: String,      // yyyy-MM-dd
    val content: String,
    val author: String,    // "user" | "ai"
    val authorId: String? = null,  // 写这篇日记的角色 id（ai 日记归属）
    val createdAt: Long,
)

/** 一个会话（对话）：每角色隔离，AI 会自动命名标题；含自己的消息列表 */
@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMsgUi> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

/** 相册条目：AI 网上找图 / 用户添加，图片复制到私有目录后存本地路径 */
@Serializable
data class AlbumEntry(
    val id: String,
    val path: String,          // 本地文件路径（filesDir/albums/ 下）
    val caption: String = "",  // AI 配的一句话描述
    val createdAt: Long,
)

/** 朋友圈评论 */
@Serializable
data class MomentComment(
    val id: String,
    val authorId: String,      // 角色 id 或 "user"
    val authorName: String,
    val content: String,
    val createdAt: Long,
)

/** 朋友圈动态：AI 发 / 用户发，可带图，可被评论 */
@Serializable
data class MomentEntry(
    val id: String,
    val authorId: String,      // 角色 id 或 "user"
    val authorName: String,
    val content: String,
    val imagePaths: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val comments: List<MomentComment> = emptyList(),
    val createdAt: Long,
)

/** 首次启动预置角色（只种入一次，用户删除后不会再出现） */
internal val SeedCharacters = listOf(
    CharacterUi("c1", "小璃", 0, null, "温柔元气，永远接住你的情绪。", listOf("温柔", "元气", "倾听")),
    CharacterUi("c2", "阿澈", 3, null, "冷静毒舌，逻辑控，帮你把事情想清楚。", listOf("理性", "毒舌", "高效")),
    CharacterUi("c3", "Luna", 5, null, "神秘浪漫，陪你数星星的深夜搭子。", listOf("神秘", "浪漫", "治愈")),
)

/**
 * 本地持久化：所有状态 JSON 序列化后写入应用私有目录（无需任何权限），
 * 应用重启后自动恢复 —— 角色、聊天记录、设置全部保存在本地。
 */
object AppStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun storeFile(context: Context): File =
        File(context.filesDir, "liquidmate_store.json")

    fun load(context: Context): AppState {
        val file = storeFile(context)
        val state = runCatching {
            if (file.exists()) json.decodeFromString<AppState>(file.readText()) else null
        }.getOrNull() ?: AppState()
        // 首次启动（或旧版本无记录）时种入预置角色
        return if (!state.seeded) {
            state.copy(characters = SeedCharacters, seeded = true).also { save(context, it) }
        } else {
            state
        }
    }

    fun save(context: Context, state: AppState) {
        runCatching {
            storeFile(context).apply {
                parentFile?.mkdirs()
                writeText(json.encodeToString(state))
            }
        }
    }
}

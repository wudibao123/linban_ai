package com.liquidmate.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 一起听：轮流点歌 + VIP 标识/排除 + 每 20 秒向 AI 汇报播放进度。
 * - 进入页面 AI 先点一首；用户点完轮到 AI（自动），AI 点完轮到用户，如此轮流。
 * - 搜索结果与播放结果都会带上 VIP 标识反馈给 AI。
 * - 「排除 VIP」：用户开关（搜索过滤 + 点歌拦截）与 AI 开关（AI 只点免费歌）独立。
 */
class TogetherController(
    private val context: Context,
    private val player: PlayerController,
    private val configProvider: () -> ApiConfig,
    private val characterNameProvider: () -> String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastReportedMs = -1L

    /** 进入一起听：AI 先点一首 */
    fun enter() {
        aiPick()
    }

    /** 用户点歌（UI 搜索列表点击） */
    fun userPick(track: MusicTrack, onDone: () -> Unit = {}) {
        if (player.state.value.excludeVipUser && track.isVip) {
            player.setNotice("已开启「排除 VIP」，请选择免费歌曲")
            onDone()
            return
        }
        player.setNotice(null)
        scope.launch {
            player.appendChat("user", "我点一首：${track.name} - ${track.artist}")
            val urlResult = MusicApi.resolvePlayUrl(track)
            val resolved = urlResult.getOrNull()
            if (resolved == null) {
                player.setNotice("播放失败：${urlResult.exceptionOrNull()?.message ?: "未知错误"}")
                onDone()
                return@launch
            }
            val playing = track.copy(playUrl = resolved)
            val queue = player.state.value.queue + QueueTrack(playing, "user", "playing")
            player.playQueue(queue, queue.lastIndex)
            // 反馈给 AI：歌名 + 歌手 + VIP 标识
            val vipTag = if (track.isVip) "（VIP 歌曲）" else "（免费歌曲）"
            player.appendChat("ai", "已开始播放《${track.name}》- ${track.artist} $vipTag，轮到 AI 点歌了。")
            player.setTurn("ai")
            onDone()
            // 自动触发 AI 点歌
            aiPick()
        }
    }

    /** AI 点歌：让 AI 推荐一首 → 搜索 → 按「AI 排除 VIP」过滤 → 解析播放地址 → 入队播放 */
    fun aiPick() {
        val st = player.state.value
        if (st.aiPicking || st.aiBusy) return
        val cfg = configProvider()
        if (cfg.baseUrl.isBlank() || cfg.model.isBlank()) {
            player.setNotice("尚未配置 AI 服务：请在「设置 → AI 服务」填写 API URL 与模型名")
            return
        }
        player.setAiPicking(true)
        player.appendChat("ai", "AI 正在点歌…")
        scope.launch {
            val last = st.queue.lastOrNull()?.track
            val exclude = st.excludeVipAi
            val sys = buildString {
                append("你是「${characterNameProvider()}」的 AI 一起听点歌员，正在和用户玩「轮流点歌」：用户点一首，你点一首。\n")
                append("规则：轮到你时，只回复一首歌，格式严格为：歌名-歌手（例如：晴天-周杰伦），不要任何多余文字。\n")
                if (exclude) append("你被要求排除 VIP 歌曲：只选免费可播的歌曲。\n")
            }
            val userMsg = buildString {
                append("轮到你点歌了。")
                if (last != null) {
                    append("上一首播放的是《${last.name}》- ${last.artist}${if (last.isVip) "（VIP 歌曲）" else "（免费歌曲）"}。")
                }
                append("请点一首歌（格式：歌名-歌手）")
            }
            val result = ChatApi.chatOnce(cfg, listOf(ChatMessage("system", sys), ChatMessage("user", userMsg)))
            val reply = result.getOrNull()?.trim() ?: ""
            if (reply.isBlank()) {
                player.setAiPicking(false)
                player.setNotice("AI 点歌失败：${result.exceptionOrNull()?.message ?: "模型无回复"}")
                return@launch
            }
            // 解析「歌名-歌手」
            val parsed = parseSongSuggestion(reply)
            if (parsed == null) {
                player.setAiPicking(false)
                player.appendChat("ai", reply)
                player.setNotice("AI 回复格式不对，请手动点歌")
                return@launch
            }
            // 搜索（酷狗优先，失败用网易云）
            val tracks = withContext(Dispatchers.IO) {
                MusicApi.searchKugou("${parsed.first} ${parsed.second}", 10)
                    .getOrElse { MusicApi.searchNetease("${parsed.first} ${parsed.second}", 10).getOrNull() ?: emptyList() }
            }
            var candidate = tracks.firstOrNull { it.name.contains(parsed.first, ignoreCase = true) }
                ?: tracks.firstOrNull()
            if (candidate == null) {
                player.setAiPicking(false)
                player.appendChat("ai", "没搜到《${parsed.first}》- ${parsed.second}，换一首吧～")
                player.setNotice("搜索无结果：${parsed.first} ${parsed.second}")
                player.setTurn("user")
                return@launch
            }
            // AI 排除 VIP：从结果里挑第一个免费歌曲
            if (exclude) {
                candidate = tracks.firstOrNull { !it.isVip }
                if (candidate == null) {
                    player.setAiPicking(false)
                    player.appendChat("ai", "搜索结果都是 VIP，我先放不了，你点一首免费的吧～")
                    player.setNotice("AI 开启排除 VIP，但结果都是 VIP，请手动点歌")
                    player.setTurn("user")
                    return@launch
                }
            }
            val urlResult = MusicApi.resolvePlayUrl(candidate)
            val resolved = urlResult.getOrNull()
            if (resolved == null) {
                player.setAiPicking(false)
                player.appendChat("ai", "《${candidate.name}》播放不了${if (candidate.isVip) "（VIP）" else ""}，换一首吧～")
                player.setNotice("AI 点的《${candidate.name}》播放失败：${urlResult.exceptionOrNull()?.message ?: ""}")
                player.setTurn("user")
                return@launch
            }
            val playing = candidate.copy(playUrl = resolved)
            val queue = player.state.value.queue + QueueTrack(playing, "ai", "playing")
            player.playQueue(queue, queue.lastIndex)
            player.setAiPicking(false)
            val vipTag = if (playing.isVip) "（VIP 歌曲）" else "（免费歌曲）"
            player.appendChat("ai", "我点一首：${playing.name} - ${playing.artist} $vipTag，轮到你了～")
            player.setTurn("user")
        }
    }

    /** 每 20 秒向 AI 汇报一次播放进度（由 UI 层定时调用） */
    fun maybeReportProgress() {
        val st = player.state.value
        if (st.aiPicking || st.aiBusy) return
        val current = st.queue.getOrNull(st.currentIndex) ?: return
        if (!st.playing) return
        // 已播放时长（从 0 开始累计，避免整首重复汇报）
        val elapsedMs = st.positionMs
        if (elapsedMs < 0) return
        // 每 20 秒汇报一次：按 20s 整数刻度触发
        val tick = elapsedMs / 20_000
        if (tick == lastReportedMs / 20_000) return
        lastReportedMs = elapsedMs
        val cfg = configProvider()
        if (cfg.baseUrl.isBlank() || cfg.model.isBlank()) return
        player.setAiBusy(true)
        scope.launch {
            val sys = "你是「${characterNameProvider()}」的 AI 一起听伴侣。用户会每 20 秒向你汇报播放进度，你可以简短回应一两句话，或推荐换歌（若想说歌名，格式：歌名-歌手）。"
            val cur = st.queue.getOrNull(st.currentIndex)?.track
            val name = cur?.name ?: "未知歌曲"
            val artist = cur?.artist ?: ""
            val vipTag = if (cur?.isVip == true) "（VIP 歌曲）" else "（免费歌曲）"
            val msg = "正在播放《$name》- $artist $vipTag，已播放 ${elapsedMs / 1000} 秒。"
            val result = ChatApi.chatOnce(cfg, listOf(ChatMessage("system", sys), ChatMessage("user", msg)), maxTokens = 60)
            player.setAiBusy(false)
            val reply = result.getOrNull()?.trim()
            if (!reply.isNullOrEmpty()) {
                player.appendChat("ai", reply)
            }
        }
    }

    /** 用户在点歌页跟 AI 说话 */
    fun userChat(text: String) {
        if (text.isBlank()) return
        val cfg = configProvider()
        if (cfg.baseUrl.isBlank() || cfg.model.isBlank()) {
            player.setNotice("尚未配置 AI 服务：请在「设置 → AI 服务」填写 API URL 与模型名")
            return
        }
        if (player.state.value.aiBusy) return
        player.appendChat("user", text)
        player.setAiBusy(true)
        scope.launch {
            val sys = "你是「${characterNameProvider()}」的 AI 一起听伴侣，正在陪用户听歌。可以聊音乐、氛围、心情，可以简短点。若想点歌，格式：歌名-歌手。"
            val result = ChatApi.chatOnce(cfg, listOf(ChatMessage("system", sys), ChatMessage("user", text)), maxTokens = 120)
            player.setAiBusy(false)
            val reply = result.getOrNull()?.trim()
            if (!reply.isNullOrEmpty()) {
                player.appendChat("ai", reply)
            } else {
                player.setNotice("AI 回复失败：${result.exceptionOrNull()?.message ?: "未知错误"}")
            }
        }
    }

    /** 用户 / AI 各自的排除 VIP 开关 */
    fun toggleExcludeVipUser(v: Boolean) {
        player.setExcludeVipUser(v)
        if (v) player.setNotice("已开启「排除 VIP」：搜索结果将隐藏 VIP 歌曲")
        else player.setNotice("已关闭「排除 VIP」")
    }

    fun toggleExcludeVipAi(v: Boolean) {
        player.setExcludeVipAi(v)
        if (v) player.setNotice("已开启「AI 排除 VIP」：AI 点歌只选免费歌曲")
        else player.setNotice("已关闭「AI 排除 VIP」")
    }

    private fun parseSongSuggestion(text: String): Pair<String, String>? {
        // 去掉常见包裹字符
        var t = text.trim().trim('《', '》', '"', '\'', '「', '」')
        // 支持「歌名-歌手」「歌名 - 歌手」「歌名- 歌手」
        val dash = Regex("[-–—]")
        val parts = dash.split(t)
        if (parts.size >= 2 && parts[0].isNotBlank()) {
            val name = parts[0].trim()
            val artist = parts.drop(1).joinToString("-").trim().trim('《', '》', '"', '\'', '。', '.')
            if (artist.isNotBlank()) return name to artist
        }
        return null
    }
}

package com.liquidmate.data

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 队列里的一首歌：谁点的 + 播放状态 */
data class QueueTrack(
    val track: MusicTrack,
    val pickedBy: String, // "user" / "ai"
    val state: String = "pending", // pending / playing / ok / failed(vip或地址解析失败)
)

/** 一起听页面完整状态（播放队列 / 轮次 / 开关 / 点歌对话） */
data class TogetherState(
    val queue: List<QueueTrack> = emptyList(),
    val currentIndex: Int = -1,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    // 轮流点歌：当前轮到谁（"ai" / "user"），进入页面时 AI 先点
    val turn: String = "ai",
    val aiPicking: Boolean = false,     // AI 正在点歌中
    val aiBusy: Boolean = false,        // AI 正在回复（进度汇报/闲聊）
    // 开关：排除 VIP（用户）/ 排除 VIP（AI 点歌偏好）
    val excludeVipUser: Boolean = false,
    val excludeVipAi: Boolean = false,
    // 点歌对话（与 AI 的实时互动记录，仅在此页展示）
    val chatLog: List<Pair<String, String>> = emptyList(), // (role: user/ai, text)
    val notice: String? = null,
)

/** 播放控制器：Media3 ExoPlayer + 队列播放 + 播放进度（一起听） */
class PlayerController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
    }

    private val _state = MutableStateFlow(TogetherState())
    val state: StateFlow<TogetherState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    init {
        // 播放器状态 → 一起听状态
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                _state.update { st ->
                    st.copy(
                        currentIndex = idx,
                        positionMs = player.currentPosition,
                        durationMs = player.duration.coerceAtLeast(0),
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // 播完自动切下一首（无下一首则停在最后）
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                        player.play()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // 单曲播放失败（常见：VIP / 地址失效）→ 标失败并尝试下一首
                val idx = player.currentMediaItemIndex
                _state.update { st ->
                    val q = st.queue.toMutableList()
                    if (idx in q.indices) q[idx] = q[idx].copy(state = "failed")
                    st.copy(queue = q, notice = "「${q.getOrNull(idx)?.track?.name ?: "该歌曲"}」播放失败，已自动跳过（可能为 VIP/付费或链接失效）")
                }
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.play()
                }
            }
        })
    }

    /** 播放指定队列（从 index 开始） */
    fun playQueue(tracks: List<QueueTrack>, startIndex: Int = 0) {
        _state.update {
            it.copy(
                queue = tracks,
                currentIndex = startIndex,
                positionMs = 0,
                durationMs = 0,
                notice = null,
            )
        }
        val mediaItems = tracks.map { q ->
            MediaItem.Builder()
                .setUri(q.track.playUrl ?: "")
                .setMediaId("${q.track.source}:${q.track.id}")
                .build()
        }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
        markPlaying(startIndex)
        startProgressPolling()
    }

    private fun markPlaying(index: Int) {
        _state.update { st ->
            val q = st.queue.toMutableList()
            q.forEachIndexed { i, t -> q[i] = if (i == index) t.copy(state = "playing") else if (t.state == "playing") t.copy(state = "ok") else t }
            st.copy(queue = q)
        }
    }

    fun playPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
            markPlaying(player.currentMediaItemIndex)
        }
    }

    fun previous() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
            markPlaying(player.currentMediaItemIndex)
        }
    }

    /** 拖动进度 */
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0, player.duration.coerceAtLeast(0)))
        _state.update { it.copy(positionMs = player.currentPosition) }
    }

    fun setExcludeVipUser(v: Boolean) = _state.update { it.copy(excludeVipUser = v) }
    fun setExcludeVipAi(v: Boolean) = _state.update { it.copy(excludeVipAi = v) }

    /** AI 点歌状态 */
    fun setAiPicking(v: Boolean) = _state.update { it.copy(aiPicking = v) }
    fun setAiBusy(v: Boolean) = _state.update { it.copy(aiBusy = v) }

    /** 追加一条点歌对话 */
    fun appendChat(role: String, text: String) = _state.update {
        it.copy(chatLog = (it.chatLog + (role to text)).takeLast(80))
    }

    fun setTurn(t: String) = _state.update { it.copy(turn = t) }
    fun setNotice(msg: String?) = _state.update { it.copy(notice = msg) }

    /** 每 20 秒向 AI 汇报一次播放进度（由外部在轮询中触发回调） */
    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(1000)
                if (player.duration > 0) {
                    _state.update {
                        it.copy(positionMs = player.currentPosition, durationMs = player.duration)
                    }
                }
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        player.release()
    }
}

package com.liquidmate.data

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * TTS 播放器：合成结果写入缓存文件，由 MediaPlayer 播放。
 * 全局单例，切换播放时自动停掉上一条。
 */
object TtsPlayer {

    private var player: MediaPlayer? = null

    /** 直接播放音频字节（测试合成 / 已合成好的内容） */
    fun playBytes(context: Context, bytes: ByteArray) {
        stop()
        val file = File(context.cacheDir, "tts_play_${System.currentTimeMillis()}.mp3")
        file.writeBytes(bytes)
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { releaseCurrent() }
            setOnErrorListener { mp, _, _ -> mp.release(); player = null; true }
            prepare()
            start()
        }
    }

    /** 合成并播放：根据文本请求 TTS，成功后播放 */
    suspend fun playText(context: Context, config: TtsConfig, text: String): Result<Unit> =
        withContext(Dispatchers.Main) {
            val bytes = TtsApi.synthesize(config, text)
            bytes.mapCatching { data ->
                playBytes(context, data)
            }
        }

    private fun releaseCurrent() {
        runCatching { player?.release() }
        player = null
    }

    /** 停止并释放（退出页面 / 手动停止时调用） */
    fun stop() {
        releaseCurrent()
    }
}

package com.liquidmate.agent

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 灵伴 Agent 终端工具执行器。
 * 三类命令通道（都不静默提权）：
 *  - shell：Runtime.exec，App 自身权限（普通 shell 命令）
 *  - shizuku：Shizuku 特权命令，需用户先安装 Shizuku 并用 ADB 启动 + 在 App 内手动授权
 *  - accessibility：无障碍操作，需用户到系统设置手动开启
 */
object AgentTools {

    private const val SHIZUKU_REQUEST_CODE = 2333

    /** 执行一条命令，返回 (stdout, stderr, 提示)。按前缀路由到对应通道 */
    suspend fun execute(context: Context, command: String): CommandResult {
        val trimmed = command.trim()
        return when {
            trimmed.startsWith("shizuku:") -> execShizuku(trimmed.removePrefix("shizuku:").trim())
            trimmed.startsWith("shizuku ") -> execShizuku(trimmed.removePrefix("shizuku ").trim())
            trimmed.startsWith("accessibility:") ->
                execAccessibility(context, trimmed.removePrefix("accessibility:").trim())
            trimmed.startsWith("accessibility ") ->
                execAccessibility(context, trimmed.removePrefix("accessibility ").trim())
            trimmed.startsWith("shell:") -> execShell(trimmed.removePrefix("shell:").trim())
            trimmed.startsWith("shell ") -> execShell(trimmed.removePrefix("shell ").trim())
            trimmed.startsWith("read_file:") -> readLocalFile(trimmed.removePrefix("read_file:").trim())
            trimmed.startsWith("read_file ") -> readLocalFile(trimmed.removePrefix("read_file ").trim())
            else -> execShell(trimmed)
        }
    }

    // ---------- read_file（读取用户发送到私有目录的文件） ----------

    /** 读取 App 私有目录下的文件内容（用户发文件时复制到 filesDir/attachments/）。限制大小防爆 token / 卡死 */
    private fun readLocalFile(path: String): CommandResult {
        val f = java.io.File(path)
        if (!f.exists()) return CommandResult(-1, "", "文件不存在：$path", "read_file")
        if (f.length() > 2 * 1024 * 1024L) {
            return CommandResult(
                0,
                "文件较大（${f.length()} 字节），为保护性能不完整读取，请基于文件名与用户说明回应。",
                "",
                "read_file",
            )
        }
        return try {
            val content = f.readText(Charsets.UTF_8)
            CommandResult(0, content, "", "read_file")
        } catch (e: Exception) {
            CommandResult(-1, "", "读取失败：${e.message ?: "未知错误"}", "read_file")
        }
    }

    // ---------- Shell（App 自身权限） ----------

    /** 单条命令最长执行时间：超过即强制结束，避免 ping/top 等命令挂起卡死 */
    private const val CMD_TIMEOUT_SECONDS = 30L
    /** 输出最大字符数：超过截断，避免大输出撑爆内存/刷屏 */
    private const val MAX_OUTPUT_CHARS = 200_000

    private suspend fun execShell(cmd: String): CommandResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val proc = ProcessBuilder("sh", "-c", cmd)
                    .redirectErrorStream(false)
                    .start()
                // 先限量读输出，再带超时等待结束
                val out = readLimited(proc.inputStream)
                val err = readLimited(proc.errorStream)
                val finished = proc.waitFor(CMD_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                if (!finished) {
                    proc.destroyForcibly()
                    return@withContext CommandResult(-1, out.trim(), err.trim() + "命令执行超时（>${CMD_TIMEOUT_SECONDS}s），已强制结束", "shell")
                }
                CommandResult(proc.exitValue(), out.trim(), err.trim(), "shell")
            }.getOrElse { e ->
                CommandResult(-1, "", "执行失败：${e.message ?: "未知错误"}", "shell")
            }
        }

    private fun readAll(stream: java.io.InputStream): String =
        BufferedReader(InputStreamReader(stream)).use { it.readText() }

    /** 限量读取流：最多 MAX_OUTPUT_CHARS 字符，超长截断（防大输出撑爆内存） */
    private fun readLimited(stream: java.io.InputStream): String {
        val reader = BufferedReader(InputStreamReader(stream))
        val sb = StringBuilder()
        val buf = CharArray(4096)
        var total = 0
        try {
            while (total < MAX_OUTPUT_CHARS) {
                val n = reader.read(buf)
                if (n < 0) break
                val take = minOf(n, MAX_OUTPUT_CHARS - total)
                sb.append(buf, 0, take)
                total += take
                if (total >= MAX_OUTPUT_CHARS) {
                    sb.append("\n…（输出过长，已截断）")
                    break
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { stream.close() }
        }
        return sb.toString()
    }

    // ---------- Shizuku（特权） ----------

    /**
     * 官方流程（不静默提权）：
     * 1. 用户安装 Shizuku 应用（shizuku.rikka.app/download/）
     * 2. 非 root：Android 11+ 用「无线调试」在本机启动 Shizuku；低版本用电脑 ADB 执行
     *    `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/files/start.sh`
     * 3. App 内请求 API_V23 授权（Shizuku.requestPermission）
     */
    fun shizukuStatus(context: Context): String {
        if (!Shizuku.pingBinder()) return "未运行（请先安装 Shizuku 并启动服务）"
        return when (Shizuku.checkSelfPermission()) {
            PackageManager.PERMISSION_GRANTED -> "已授权"
            else -> "待授权（点击请求授权）"
        }
    }

    fun requestShizukuPermission() {
        if (Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        ) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    private suspend fun execShizuku(cmd: String): CommandResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (!Shizuku.pingBinder()) {
                    return@withContext CommandResult(
                        -1, "", "Shizuku 未运行：请先安装 Shizuku，并用 ADB 启动服务后重试",
                        "shizuku"
                    )
                }
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    return@withContext CommandResult(
                        -1, "", "Shizuku 未授权：请在界面上点击「请求授权」完成 API_V23 授权",
                        "shizuku"
                    )
                }
                // Shizuku 13.1.5 起 newProcess 为 private，官方推荐反射调用（见 Shizuku-API README）
                val proc = shizukuNewProcess(arrayOf("sh", "-c", cmd), null, null)
                    ?: return@withContext CommandResult(-1, "", "无法创建 Shizuku 进程", "shizuku")
                val out = readLimited(proc.inputStream)
                val err = readLimited(proc.errorStream)
                val finished = proc.waitFor(CMD_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                if (!finished) {
                    proc.destroy()
                    return@withContext CommandResult(-1, out.trim(), err.trim() + "命令执行超时（>${CMD_TIMEOUT_SECONDS}s），已强制结束", "shizuku")
                }
                CommandResult(proc.exitValue(), out.trim(), err.trim(), "shizuku")
            }.getOrElse { e ->
                CommandResult(
                    -1, "", "Shizuku 执行失败：${e.message ?: "未知错误"}", "shizuku"
                )
            }
        }

    /** 反射调用 Shizuku.newProcess（13.1.5 起为 private static，官方 README 亦用反射） */
    private fun shizukuNewProcess(
        cmd: Array<String>,
        env: Array<String>?,
        processName: String?,
    ): ShizukuRemoteProcess? = runCatching {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        m.isAccessible = true
        m.invoke(null, cmd, env, processName) as ShizukuRemoteProcess
    }.getOrNull()

    // ---------- 无障碍（用户手动开启） ----------

    private fun execAccessibility(context: Context, cmd: String): CommandResult {
        if (!AgentAccessibilityService.isEnabled(context)) {
            return CommandResult(
                -1, "", "无障碍未开启：请在系统设置 → 无障碍 中手动开启「灵伴 Agent 无障碍」",
                "accessibility"
            )
        }
        val parts = cmd.split("\\s+".toRegex())
        return when (parts.firstOrNull()) {
            "tap" -> {
                if (parts.size < 3) return CommandResult(-1, "", "用法：tap x y", "accessibility")
                val ok = AgentAccessibilityService.tap(
                    parts[1].toFloatOrNull() ?: 0f,
                    parts[2].toFloatOrNull() ?: 0f
                )
                CommandResult(if (ok) 0 else -1, if (ok) "已点击 (${parts[1]}, ${parts[2]})" else "", if (ok) "" else "点击失败", "accessibility")
            }
            "swipe" -> {
                if (parts.size < 5) return CommandResult(-1, "", "用法：swipe x1 y1 x2 y2", "accessibility")
                val ok = AgentAccessibilityService.swipe(
                    parts[1].toFloatOrNull() ?: 0f,
                    parts[2].toFloatOrNull() ?: 0f,
                    parts[3].toFloatOrNull() ?: 0f,
                    parts[4].toFloatOrNull() ?: 0f,
                )
                CommandResult(if (ok) 0 else -1, if (ok) "已滑动" else "", if (ok) "" else "滑动失败", "accessibility")
            }
            else -> CommandResult(
                -1, "", "不支持的无障碍操作：$cmd（支持 tap x y / swipe x1 y1 x2 y2）",
                "accessibility"
            )
        }
    }

    /** 无障碍开启状态（界面顶部提示用） */
    fun accessibilityEnabled(context: Context): Boolean =
        AgentAccessibilityService.isEnabled(context)
}

/** 一条命令的执行结果 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val channel: String,
) {
    fun toDisplay(): String {
        val sb = StringBuilder()
        sb.append("[$channel] 退出码 $exitCode")
        if (stdout.isNotBlank()) sb.append("\n").append(stdout)
        if (stderr.isNotBlank()) sb.append("\n").append(stderr)
        return sb.toString()
    }
}

package com.liquidmate.ui.screens

import androidx.compose.animation.AnimatedVisibility
import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.liquidmate.data.ApiConfig
import com.liquidmate.data.ChatApi
import com.liquidmate.data.GlassParams
import com.liquidmate.data.TtsApi
import com.liquidmate.data.TtsConfig
import com.liquidmate.data.TtsPlayer
import com.liquidmate.ui.components.Glass
import com.liquidmate.ui.components.GlassCard
import com.liquidmate.ui.components.GlassChip
import com.liquidmate.ui.components.GlassIconButton
import com.liquidmate.ui.components.SectionTitle
import com.liquidmate.ui.components.liquidGlass
import com.liquidmate.ui.components.rememberImagePicker
import com.liquidmate.ui.icons.IconBolt
import com.liquidmate.ui.icons.IconCheck
import com.liquidmate.ui.icons.IconChevronRight
import com.liquidmate.ui.icons.IconGlobe
import com.liquidmate.ui.icons.IconImage
import com.liquidmate.ui.icons.IconKey
import com.liquidmate.ui.icons.IconPalette
import com.liquidmate.ui.icons.IconPerson
import com.liquidmate.ui.icons.IconTerminal
import com.liquidmate.ui.icons.IconVolumeUp
import com.liquidmate.ui.icons.IconVisibility
import com.liquidmate.ui.icons.IconVisibilityOff
import com.liquidmate.ui.icons.IconWarning
import com.liquidmate.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/** 设置页：液态玻璃游乐场、AI 服务、背景、主题、兼容性声明、关于 */
@Composable
fun SettingsScreen(
    backdrop: Backdrop,
    wallpaper: Wallpaper,
    onWallpaperChange: (Wallpaper) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    api: ApiConfig,
    onApiChange: (ApiConfig) -> Unit,
    tts: TtsConfig,
    onTtsChange: (TtsConfig) -> Unit,
    glass: GlassParams,
    onGlassChange: (GlassParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickWallpaper = rememberImagePicker { path ->
        if (path != null) onWallpaperChange(Wallpaper.Local(path))
    }
    var showPlayground by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )

        // 液态玻璃游乐场（官方 GlassPlaygroundContent 移植）
        SectionTitle("液态玻璃游乐场")
        GlassCard(backdrop, tint = Glass.containerTint(), onClick = { showPlayground = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    IconPalette,
                    contentDescription = null,
                    tint = Glass.accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "玻璃效果实时调参",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            "官方 LiquidSlider 滑块 · 预览实时变化，参数应用到全 App"
                        else
                            "当前设备低于 Android 12：液态玻璃不可用（透明），调参不会生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    IconChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        SectionTitle("外观")

        // 聊天背景
        GlassCard(backdrop, tint = Glass.containerTint()) {
            Text(
                "聊天背景",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Wallpaper.Presets.indices.forEach { i ->
                    val colors = Wallpaper.Presets[i]
                    val selected = wallpaper is Wallpaper.Gradient && wallpaper.index == i
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(colors))
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, Glass.accent, RoundedCornerShape(14.dp))
                                } else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onWallpaperChange(Wallpaper.Gradient(i)) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(
                    backdrop,
                    "从本地导入背景",
                    leading = IconImage,
                    onClick = { pickWallpaper() },
                )
                GlassChip(backdrop, "恢复默认", onClick = { onWallpaperChange(Wallpaper.Gradient(0)) })
            }
        }

        // 主题模式
        GlassCard(backdrop, tint = Glass.containerTint()) {
            Text(
                "主题模式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    GlassChip(
                        backdrop,
                        when (mode) {
                            ThemeMode.System -> "跟随系统"
                            ThemeMode.Light -> "浅色"
                            ThemeMode.Dark -> "深色"
                        },
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                    )
                }
            }
        }

        SectionTitle("AI 服务")

        // AI 服务配置：任意 OpenAI 兼容 API（可自建 / 中转 / 官方）
        GlassCard(backdrop, tint = Glass.containerTint()) {
            Text(
                "对话模型接口",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "兼容任意 OpenAI Chat Completions 服务，如 OpenAI / DeepSeek / 通义 / 自建中转等",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            var baseUrl by rememberSaveable { mutableStateOf(api.baseUrl) }
            var apiKey by rememberSaveable { mutableStateOf(api.apiKey) }
            var model by rememberSaveable { mutableStateOf(api.model) }
            var keyVisible by rememberSaveable { mutableStateOf(false) }
            var testing by remember { mutableStateOf(false) }
            var testResult by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            fun pushChange() {
                onApiChange(ApiConfig(baseUrl = baseUrl.trim(), apiKey = apiKey.trim(), model = model.trim()))
                testResult = null
            }

            // API URL
            GlassField(
                backdrop,
                label = "API URL（Base URL）",
                value = baseUrl,
                onValueChange = { baseUrl = it; pushChange() },
                placeholder = "https://api.openai.com 或 http://192.168.x.x:8080/v1",
                leading = IconGlobe,
            )
            // API Key
            GlassField(
                backdrop,
                label = "API Key",
                value = apiKey,
                onValueChange = { apiKey = it; pushChange() },
                placeholder = "sk-...",
                leading = IconKey,
                password = !keyVisible,
                trailing = {
                    GlassIconButton(
                        backdrop,
                        if (keyVisible) IconVisibilityOff else IconVisibility,
                        "显示/隐藏",
                        onClick = { keyVisible = !keyVisible },
                        size = 32.dp,
                    )
                },
            )
            // 模型名
            GlassField(
                backdrop,
                label = "模型名",
                value = model,
                onValueChange = { model = it; pushChange() },
                placeholder = "如 gpt-4o-mini / deepseek-chat / qwen-plus",
                leading = IconBolt,
            )

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassChip(
                    backdrop,
                    if (testing) "测试中…" else "测试连接",
                    leading = IconBolt,
                    onClick = {
                        if (testing) return@GlassChip
                        testing = true
                        testResult = null
                        scope.launch {
                            val result = ChatApi.testConnection(
                                ApiConfig(baseUrl = baseUrl.trim(), apiKey = apiKey.trim(), model = model.trim())
                            )
                            testResult = result.fold(
                                onSuccess = { "✓ $it" },
                                onFailure = { "✗ ${it.message ?: "连接失败"}" },
                            )
                            testing = false
                        }
                    },
                )
                testResult?.let { r ->
                    Text(
                        r,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.startsWith("✓")) Color(0xFF22C58F)
                        else Color(0xFFFF6B6B),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (api.baseUrl.isBlank() || api.model.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        IconWarning,
                        contentDescription = null,
                        tint = Color(0xFFE8A13D),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "填好 URL 与模型名后即可开始对话；Key 可选（部分本地服务无需鉴权）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionTitle("语音朗读")

        // TTS 配置：API URL / Key / 模型 / 音色 / 请求方式 / 测试合成
        GlassCard(backdrop, tint = Glass.containerTint()) {
            Text(
                "语音朗读模型",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "配置后 AI 回复气泡下方出现小喇叭，可朗读；顶部可开自动朗读",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            var tBaseUrl by rememberSaveable { mutableStateOf(tts.baseUrl) }
            var tKey by rememberSaveable { mutableStateOf(tts.apiKey) }
            var tModel by rememberSaveable { mutableStateOf(tts.model) }
            var tVoice by rememberSaveable { mutableStateOf(tts.voice) }
            var tMode by rememberSaveable { mutableStateOf(tts.requestMode) }
            var tTesting by remember { mutableStateOf(false) }
            var tResult by remember { mutableStateOf<String?>(null) }
            val tScope = rememberCoroutineScope()

            fun pushTts(extra: TtsConfig.() -> TtsConfig = { this }) {
                onTtsChange(
                    TtsConfig(
                        baseUrl = tBaseUrl.trim(),
                        apiKey = tKey.trim(),
                        model = tModel.trim(),
                        voice = tVoice.trim(),
                        requestMode = tMode,
                        autoRead = tts.autoRead,
                    ).extra()
                )
                tResult = null
            }

            GlassField(
                backdrop,
                label = "TTS API URL",
                value = tBaseUrl,
                onValueChange = { tBaseUrl = it; pushTts() },
                placeholder = "https://api.openai.com 或自建中转",
                leading = IconGlobe,
            )
            Spacer(Modifier.height(10.dp))
            GlassField(
                backdrop,
                label = "TTS API Key",
                value = tKey,
                onValueChange = { tKey = it; pushTts() },
                placeholder = "sk-...（部分本地服务无需）",
                leading = IconKey,
            )
            Spacer(Modifier.height(10.dp))
            GlassField(
                backdrop,
                label = "语音模型",
                value = tModel,
                onValueChange = { tModel = it; pushTts() },
                placeholder = "如 tts-1 / tts-1-hd / gpt-4o-mini-tts",
                leading = IconBolt,
            )
            Spacer(Modifier.height(10.dp))
            GlassField(
                backdrop,
                label = "音色（voice）",
                value = tVoice,
                onValueChange = { tVoice = it; pushTts() },
                placeholder = "alloy / echo / fable / onyx / nova / shimmer",
                leading = IconPerson,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "请求方式",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(
                    backdrop,
                    "OpenAI 音频请求",
                    leading = IconBolt,
                    selected = tMode == "openai",
                    onClick = { tMode = "openai"; pushTts() },
                )
                GlassChip(
                    backdrop,
                    "通用 JSON 请求",
                    leading = IconTerminal,
                    selected = tMode == "custom",
                    onClick = { tMode = "custom"; pushTts() },
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassChip(
                    backdrop,
                    if (tTesting) "合成中…" else "测试合成（Hello World）",
                    leading = IconVolumeUp,
                    onClick = {
                        if (tTesting || tBaseUrl.isBlank()) return@GlassChip
                        tTesting = true
                        tResult = null
                        tScope.launch {
                            val cfg = TtsConfig(
                                baseUrl = tBaseUrl.trim(),
                                apiKey = tKey.trim(),
                                model = tModel.trim(),
                                voice = tVoice.trim(),
                                requestMode = tMode,
                            )
                            val r = TtsApi.synthesize(cfg, "Hello World")
                            r.onSuccess { bytes ->
                                // 测试：直接播放合成结果
                                TtsPlayer.playBytes(context, bytes)
                                tResult = "✓ 合成成功，正在播放（${bytes.size} 字节）"
                            }.onFailure { e ->
                                tResult = "✗ ${e.message ?: "合成失败"}"
                            }
                            tTesting = false
                        }
                    },
                )
            }
            tResult?.let { r ->
                Spacer(Modifier.height(8.dp))
                Text(
                    r,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (r.startsWith("✓")) Color(0xFF22C58F)
                    else Color(0xFFFF6B6B),
                )
            }
        }

        SectionTitle("关于")

        // 兼容性声明
        GlassCard(
            backdrop,
            tint = Color(0xFFFFF3E0).copy(alpha = 0.35f),
            blurRadius = 16.dp,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    IconWarning,
                    contentDescription = null,
                    tint = Color(0xFFE8A13D),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "兼容性声明：液态玻璃效果基于 AGSL Shader（Android 13+ / API 33+）。" +
                        "在 Android 12 及以下的设备上，液态玻璃可能退化为透明或简化显示，属正常现象，不影响功能使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 关于
        GlassCard(backdrop, tint = Glass.containerTint()) {
            Text(
                "开源项目：Backdrop（AndroidLiquidGlass）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "github.com/Kyant0/AndroidLiquidGlass\n坐标：io.github.kyant0:backdrop",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "灵伴 LiquidMate · v1.0.0 完整版",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
    }

    // 液态玻璃游乐场浮层（覆盖全屏，进入/退出动画）
    AnimatedVisibility(
        visible = showPlayground,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(260)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(260)),
        exit = fadeOut(animationSpec = tween(220)) +
            scaleOut(targetScale = 0.96f, animationSpec = tween(220)),
    ) {
        GlassPlayground(
            backdrop = backdrop,
            params = glass,
            onParamsChange = onGlassChange,
            onClose = { showPlayground = false },
            modifier = Modifier.fillMaxSize(),
        )
    }
    }
}

/** 玻璃输入框（支持前缀图标 / 密码态 / 尾随操作） */
@Composable
private fun GlassField(
    backdrop: Backdrop,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    password: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .liquidGlass(
                    backdrop,
                    shape = { RoundedCornerShape(16.dp) },
                    blurRadius = 12.dp,
                    tint = Glass.containerTint(),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Icon(
                    leading,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(Glass.accent),
                singleLine = true,
                visualTransformation = if (password) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (password) {
                        androidx.compose.ui.text.input.KeyboardType.Password
                    } else {
                        androidx.compose.ui.text.input.KeyboardType.Uri
                    }
                ),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        inner()
                    }
                },
            )
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
        }
    }
}

package com.liquidmate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import com.liquidmate.ui.components.AvatarGradients
import com.liquidmate.ui.components.AvatarView
import com.liquidmate.ui.components.Glass
import com.liquidmate.ui.components.GlassButton
import com.liquidmate.ui.components.LiquidButton
import com.liquidmate.ui.components.GlassCard
import com.liquidmate.ui.components.GlassChip
import com.liquidmate.ui.components.GlassIconButton
import com.liquidmate.ui.components.liquidGlass
import com.liquidmate.ui.components.rememberImagePicker
import com.liquidmate.ui.icons.IconAddPhoto
import com.liquidmate.ui.icons.IconDelete
import com.liquidmate.ui.icons.IconEdit
import com.liquidmate.ui.icons.IconPlus

/** 伴侣列表页（多角色管理：使用 / 创建 / 编辑 / 删除 / 本地导入头像）
 *  @param showHeader 是否显示内置标题行；外部已有增强标题时传 false */
@Composable
fun CharactersScreen(
    backdrop: Backdrop,
    characters: List<CharacterUi>,
    activeId: String?,
    onUse: (CharacterUi) -> Unit,
    onEdit: (CharacterUi) -> Unit,
    onCreate: () -> Unit,
    onDelete: (CharacterUi) -> Unit,
    onProfile: (CharacterUi) -> Unit = {},
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    Column(modifier.fillMaxSize()) {
        if (showHeader) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "我的伴侣",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                GlassIconButton(backdrop, IconPlus, "创建角色", onClick = onCreate, size = 44.dp)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(characters, key = { it.id }) { c ->
                CharacterCard(
                    backdrop,
                    c,
                    active = c.id == activeId,
                    onUse = { onUse(c) },
                    onEdit = { onEdit(c) },
                    onDelete = { onDelete(c) },
                    onProfile = { onProfile(c) },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CharacterCard(
    backdrop: Backdrop,
    c: CharacterUi,
    active: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onProfile: () -> Unit = {},
) {
    // 官方 AndroidLiquidGlass 滚动容器卡片：drawBackdrop(vibrancy + lens)，无 tint 覆盖
    // 注意：容器必须用 Column（垂直排列），用 Box 会让按钮与上部内容堆叠错位
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 148.dp)   // 饱满高度，避免内容挤压显得"扁"
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(32f.dp) },
                effects = {
                    vibrancy()
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onUse,
            )
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 点头像 → 全屏角色档案（相册 / 朋友圈入口）
            Box(
                Modifier
                    .size(58.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onProfile,
                    ),
            ) {
                AvatarView(c.avatarPath, c.seed, c.name, 58.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "● 聊天中",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF22C58F),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    c.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.traits.take(4).forEach { t ->
                        GlassChip(backdrop, t, compact = true)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                GlassIconButton(backdrop, IconEdit, "编辑", onClick = onEdit, size = 38.dp)
                Spacer(Modifier.height(8.dp))
                GlassIconButton(
                    backdrop,
                    IconDelete,
                    "删除",
                    onClick = onDelete,
                    size = 38.dp,
                    tint = Color(0xFFFF6B6B).copy(alpha = 0.18f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // 使用入口：当前角色显示「聊天中」，其余显示「聊天」（官方液态玻璃按钮，独立一行在卡片底部）
        LiquidButton(
            onClick = onUse,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            tint = if (active) Color(0xFF22C58F).copy(alpha = 0.22f)
            else Glass.accent.copy(alpha = 0.25f),
            isInteractive = !active,
        ) {
            Text(
                if (active) "聊天中" else "聊天",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
/** 角色编辑器（创建 / 编辑共用），支持自定义名字、渐变头像、从本地导入头像、人设与特质 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterEditor(
    backdrop: Backdrop,
    initial: CharacterUi,
    onSave: (CharacterUi) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var seed by rememberSaveable { mutableIntStateOf(initial.seed) }
    var avatarPath by remember { mutableStateOf(initial.avatarPath) }
    var tagline by rememberSaveable { mutableStateOf(initial.tagline) }
    var traits by rememberSaveable { mutableStateOf(initial.traits.toList()) }
    var traitInput by rememberSaveable { mutableStateOf("") }
    val pickAvatar = rememberImagePicker { path -> if (path != null) avatarPath = path }

    Box(
        modifier
            .fillMaxSize()
            .drawBackdrop(
                    backdrop = backdrop,
                    shape = { androidx.compose.ui.graphics.RectangleShape },
                    effects = {
                        vibrancy()
                        blur(22f.dp.toPx())
                    },
                )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .liquidGlass(
                    backdrop,
                    shape = { RoundedCornerShape(28.dp) },
                    blurRadius = 26.dp,
                    tint = Glass.barTint(),
                )
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (initial.id.isEmpty()) "创建角色" else "编辑角色",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // 头像：渐变皮肤 + 本地导入
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarView(avatarPath, seed, name, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AvatarGradients.indices.take(8).forEach { idx ->
                            val colors = AvatarGradients[idx]
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(colors))
                                    .then(
                                        if (seed == idx) {
                                            Modifier.border(2.dp, Glass.accent, CircleShape)
                                        } else Modifier
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { seed = idx }
                            )
                        }
                    }
                    GlassChip(
                        backdrop,
                        "从本地导入头像",
                        leading = IconAddPhoto,
                        onClick = { pickAvatar() },
                    )
                }
            }

            LabeledGlassField(
                backdrop,
                label = "名字",
                value = name,
                onValueChange = { name = it },
                placeholder = "给 TA 起个名字",
            )
            LabeledGlassField(
                backdrop,
                label = "人设描述",
                value = tagline,
                onValueChange = { tagline = it },
                placeholder = "性格、背景、说话风格……",
                singleLine = false,
                maxLines = 3,
            )

            Text(
                "特质（点击可移除）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            fun addTrait() {
                val t = traitInput.trim()
                if (t.isNotEmpty() && t !in traits) traits = traits + t
                traitInput = ""
            }
            // 已添加特质：自动换行（FlowRow），特质再多也不会把输入框挤掉
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                traits.forEach { t ->
                    GlassChip(backdrop, t, compact = true, onClick = { traits = traits - t })
                }
            }
            // 特质输入行：输入框 + 加号独立一行，始终可见可点，支持回车提交
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .liquidGlass(
                            backdrop,
                            shape = { RoundedCornerShape(percent = 50) },
                            blurRadius = 10.dp,
                            tint = Glass.containerTint(),
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    BasicTextField(
                        value = traitInput,
                        onValueChange = { traitInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(Glass.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTrait() }),
                        decorationBox = { inner ->
                            Box {
                                if (traitInput.isEmpty()) {
                                    Text(
                                        "添加特质（回车或点 +）",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
                GlassIconButton(
                    backdrop,
                    IconPlus,
                    "添加特质",
                    onClick = { addTrait() },
                    size = 32.dp,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassButton(
                    backdrop,
                    "取消",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    backdrop,
                    "保存",
                    onClick = {
                        onSave(
                            initial.copy(
                                name = name.trim().ifEmpty { "未命名" },
                                seed = seed,
                                avatarPath = avatarPath,
                                tagline = tagline.trim(),
                                traits = traits.filter { it.isNotBlank() },
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    tint = Glass.accent.copy(alpha = 0.25f),
                )
            }
        }
    }
}

@Composable
private fun LabeledGlassField(
    backdrop: Backdrop,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    maxLines: Int = 1,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .liquidGlass(
                    backdrop,
                    shape = { RoundedCornerShape(16.dp) },
                    blurRadius = 12.dp,
                    tint = Glass.containerTint(),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(Glass.accent),
                singleLine = singleLine,
                maxLines = maxLines,
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
        }
    }
}

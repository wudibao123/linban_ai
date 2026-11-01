package com.liquidmate.ui.screens

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/** 聊天背景：渐变预设 或 本地导入图片（可持久化保存） */
@Serializable
sealed interface Wallpaper {
    @Serializable
    data class Gradient(val index: Int) : Wallpaper

    @Serializable
    data class Local(val path: String) : Wallpaper

    companion object {
        val Presets: List<List<Color>> = listOf(
            listOf(Color(0xFF0B1026), Color(0xFF16324F), Color(0xFF1F5E5E)),
            listOf(Color(0xFF120A2E), Color(0xFF2B1055), Color(0xFF4C1D95)),
            listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B)),
            listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
            listOf(Color(0xFFFFE0EC), Color(0xFFFFC1D9), Color(0xFFF8BBD0)),
            listOf(Color(0xFF0F344D), Color(0xFF1A6B8A), Color(0xFF35A7C4)),
        )
    }
}

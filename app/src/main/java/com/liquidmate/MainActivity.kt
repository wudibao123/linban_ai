package com.liquidmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.liquidmate.ui.LiquidMateApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 本地服务器：提供图片搜索(/api/images)、音乐 API、HTML 页面等
        MusicServer.ensureStarted(applicationContext)
        setContent {
            LiquidMateApp()
        }
    }
}

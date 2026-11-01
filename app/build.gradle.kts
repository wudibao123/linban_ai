plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.liquidmate"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.liquidmate"
        minSdk = 26
        targetSdk = 37
        versionCode = 33
        versionName = "1.6.14"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "META-INF/*.version",
                "META-INF/**/LICENSE.txt"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    // 液态玻璃：开源项目 AndroidLiquidGlass (Backdrop)
    implementation(libs.backdrop)
    // 官方液态玻璃组件依赖的 Capsule 形状库
    implementation(libs.kyant.shapes)
    // AI 对话：OpenAI 兼容 API（流式）
    implementation(libs.okhttp)
    // 一起听：音乐播放（Media3 ExoPlayer）
    implementation(libs.media3.exoplayer)
    // 一起听：应用内本地 HTTP 服务器（NanoHTTPD），用 http://127.0.0.1 加载页面并代理音乐/AI 请求
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // 本地持久化：角色 / 聊天记录 / 设置 序列化
    implementation(libs.kotlinx.serialization.json)
    // 灵伴 Agent：Shizuku 特权命令（需用户手动 ADB 授权，不静默提权）
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

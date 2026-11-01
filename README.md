# LiquidMate 灵伴 · AI 伴侣（壳预览版）

基于 **Jetpack Compose** 的 AI 伴侣应用 UI 壳子。液态玻璃效果全部来自开源项目
**[AndroidLiquidGlass (Backdrop)](https://github.com/Kyant0/AndroidLiquidGlass)**（`io.github.kyant0:backdrop:2.0.1`）。

## 当前壳版包含

- **底部液态玻璃导航栏**：玻璃胶囊容器 + 滑动指示胶囊（边缘色散 / 高光 / 阴影），布局与动效参考官方 `LiquidBottomTabs`
- **聊天页**：液态玻璃顶部信息栏、消息气泡（用户=强调色玻璃，伴侣=玻璃）、打字动画、液态玻璃输入栏
- **伴侣页（多角色）**：角色玻璃卡片、创建 / 编辑 / 删除；支持**自定义名字、自定义人设描述、特质标签、渐变头像皮肤、从本地导入头像**（系统照片选择器，无需存储权限）
- **设置页**：聊天背景（6 套渐变预设 + **从本地导入背景**）、主题模式（跟随系统 / 浅色 / 深色）、AI 引擎占位、**兼容性声明**、开源项目信息
- 所有玻璃元素共用同一个 backdrop 采样层（壁纸），保证模糊/折射效果一致

## 液态玻璃架构（关键）

```
Box {
    val backdrop = rememberLayerBackdrop()          // 采样源
    WallpaperLayer(Modifier.layerBackdrop(backdrop)) // ① 壁纸画进采样层
    ...UI 层（所有玻璃元素 drawBackdrop(backdrop)）    // ② UI 采样壁纸
    LiquidBottomBar(backdrop, ...)                    // ③ 底部玻璃导航
}
```

任何控件想变玻璃：`Modifier.liquidGlass(backdrop, shape, blur, lens, tint)`，
见 `ui/components/Glass.kt`。

## 构建运行（你的本地环境）

要求：JDK 17+、Android SDK（compileSdk 37 / build-tools 37.0.0）。

```bash
# 方式一：命令行
cd LiquidMate
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # 按你本机路径调整
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 方式二：Android Studio（推荐，可直接连真机/模拟器预览）
# File → Open → 选择 LiquidMate 目录 → 等待 Sync 完成 → Run
```

> 本工程已在开发环境验证 `./gradlew :app:assembleDebug` 构建通过（APK ~15MB）。
> 版本栈与官方 demo 完全一致：AGP 9.3.2 / Kotlin 2.4.10 / Compose 1.12.0 / Backdrop 2.0.1。
> 若你的 SDK 没有 `platforms;android-37`，在 SDK Manager 中安装，或把 `app/build.gradle.kts` 的 `compileSdk` 改为已有版本（≥36）。

直接安装预览：把 `app/build/outputs/apk/debug/app-debug.apk` 传到手机（`adb install` 或文件管理器安装）。
**建议用 Android 13+ 真机预览完整液态玻璃效果**；Android 12 及以下会退化为透明/简化样式（设置页有声明）。

## 兼容性说明

液态玻璃效果基于 AGSL Shader（**Android 13+ / API 33+**）。在 **Android 12 及以下**设备上，
液态玻璃会退化为透明/简化显示（设置页已内置此声明文案）。完整效果建议 Android 13+ 设备预览。

## 后续路线（非本次壳版）

- DataStore 本地持久化：多角色、每个角色的聊天记录、设置项
- 真实 AI 引擎：本地规则引擎 + 可选 OpenAI 兼容远程 API（在设置中配置 baseUrl/key/model）
- 更多液态玻璃组件（滑杆、开关、弹窗、控制中心式面板）

## 目录结构

```
LiquidMate/
├── app/src/main/java/com/liquidmate/
│   ├── MainActivity.kt              # 入口（edge-to-edge）
│   ├── ui/LiquidMateApp.kt          # 根布局：采样层 + UI + 玻璃导航
│   ├── ui/theme/Theme.kt            # Material3 主题（浅/深）
│   ├── ui/icons/AppIcons.kt         # 自绘 Material 图标
│   ├── ui/components/Glass.kt       # 液态玻璃修饰器 liquidGlass()
│   ├── ui/components/Common.kt      # 玻璃卡片/按钮/胶囊/头像/图片选择器
│   ├── ui/components/LiquidBottomBar.kt  # 底部液态玻璃导航
│   └── ui/screens/                  # Chat / Characters(含编辑器) / Settings / Wallpaper
└── gradle/libs.versions.toml        # 版本目录（与官方 demo 对齐）
```

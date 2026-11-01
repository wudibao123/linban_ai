package com.liquidmate.agent

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path

/**
 * 灵伴 Agent 无障碍服务。
 * 用户必须到「系统设置 → 无障碍」手动开启（App 不静默提权）。
 * AI 通过 ```accessibility ...``` 代码块触发点击/滑动/输入。
 */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        /** 是否已开启（系统设置里勾选了本服务） */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            return enabled.split(':').any { it.contains("com.liquidmate/.agent.AgentAccessibilityService") }
        }

        /** 打开系统无障碍设置页，引导用户手动开启 */
        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        /** 全局点击 */
        fun tap(x: Float, y: Float): Boolean {
            val svc = instance ?: return false
            return svc.dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(
                            Path().apply { moveTo(x, y) }, 0, 60
                        )
                    )
                    .build(),
                null, null
            )
        }

        /** 全局滑动 */
        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300): Boolean {
            val svc = instance ?: return false
            return svc.dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(
                            Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, 0, durationMs
                        )
                    )
                    .build(),
                null, null
            )
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}

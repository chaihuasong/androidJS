package com.example.androidjs.ai.conversation

import com.example.androidjs.ai.tools.ToolRegistry

/**
 * Dynamically generates the system prompt based on available tools.
 */
class SystemPromptBuilder(
    private val toolRegistry: ToolRegistry
) {
    fun build(): String = buildString {
        appendLine("你是 Clawdbot，运行在 Android 设备上的 AI 助手。")
        appendLine("你可以通过工具与设备交互。")
        appendLine()

        val definitions = toolRegistry.getDefinitions()
        if (definitions.isNotEmpty()) {
            appendLine("## 可用设备能力")
            for (def in definitions) {
                appendLine("- ${def.name}: ${def.description}")
            }
            appendLine()
        }

        appendLine("## 使用指南")
        appendLine("- 需要操作设备时使用工具")
        appendLine("- 操作前向用户说明你要做什么")
        appendLine("- 以用户友好的方式展示工具结果")
        appendLine("- 使用中文回复用户（除非用户使用其他语言）")
    }
}

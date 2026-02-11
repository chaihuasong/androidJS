package com.example.androidjs.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Chat request to send to AI service.
 */
data class ChatRequest(
    val messages: List<Message>,
    val systemPrompt: String? = null,
    val tools: List<ToolSpec>? = null,
    val maxTokens: Int = 4096
)

/**
 * Tool specification for Clawdbot API tools parameter.
 */
@Serializable
data class ToolSpec(
    val name: String,
    val description: String,
    @SerialName("input_schema")
    val inputSchema: JsonObject
)

/**
 * Message in a conversation.
 */
sealed class Message {
    abstract val role: String

    data class UserMessage(
        val content: String
    ) : Message() {
        override val role = "user"
    }

    data class AssistantMessage(
        val content: String = "",
        val toolUse: List<ToolUseBlock> = emptyList()
    ) : Message() {
        override val role = "assistant"
    }

    data class ToolResultMessage(
        val toolResults: List<ToolResult>
    ) : Message() {
        override val role = "user"
    }
}

/**
 * A tool use block returned by the AI.
 */
@Serializable
data class ToolUseBlock(
    val id: String,
    val name: String,
    val input: JsonObject
)

/**
 * Result of executing a tool.
 */
data class ToolResult(
    val toolUseId: String,
    val content: String,
    val isError: Boolean = false
)

/**
 * Stream events from SSE.
 */
sealed class StreamEvent {
    data class TextDelta(val text: String) : StreamEvent()
    data class ToolUseStart(val id: String, val name: String) : StreamEvent()
    data class ToolUseDelta(val partialJson: String) : StreamEvent()
    data object ToolUseEnd : StreamEvent()
    data class MessageComplete(val response: ChatResponse) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

/**
 * Complete response from AI service.
 */
data class ChatResponse(
    val content: String = "",
    val toolUse: List<ToolUseBlock> = emptyList(),
    val stopReason: String = "",
    val usage: TokenUsage = TokenUsage()
)

/**
 * Token usage statistics.
 */
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)

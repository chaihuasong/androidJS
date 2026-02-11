package com.example.androidjs.ai.tools

import android.util.Log
import com.example.androidjs.ai.model.*
import com.example.androidjs.ai.service.AIService
import kotlinx.coroutines.flow.*

/**
 * Orchestrates the AI ↔ Tool execution loop.
 * Handles tool_use responses by executing tools and re-sending results to AI.
 */
class ToolOrchestrator(
    private val aiService: AIService,
    private val toolRegistry: ToolRegistry
) {
    /**
     * Send a chat request and handle tool use loops until a final text response.
     * Emits stream events including tool execution status.
     * @param maxToolRounds Maximum number of tool use rounds to prevent infinite loops
     */
    fun chat(
        request: ChatRequest,
        maxToolRounds: Int = MAX_TOOL_ROUNDS
    ): Flow<OrchestratorEvent> = flow {
        var currentMessages = request.messages.toMutableList()
        var rounds = 0

        while (rounds < maxToolRounds) {
            val toolSpecs = toolRegistry.getToolSpecs()
            val chatRequest = request.copy(
                messages = currentMessages,
                tools = toolSpecs.ifEmpty { null }
            )

            var response: ChatResponse? = null

            aiService.sendMessageStream(chatRequest).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> emit(OrchestratorEvent.TextDelta(event.text))
                    is StreamEvent.ToolUseStart -> emit(OrchestratorEvent.ToolCallStart(event.id, event.name))
                    is StreamEvent.ToolUseDelta -> { /* accumulating internally */ }
                    is StreamEvent.ToolUseEnd -> { /* handled in MessageComplete */ }
                    is StreamEvent.MessageComplete -> response = event.response
                    is StreamEvent.Error -> emit(OrchestratorEvent.Error(event.message))
                }
            }

            val resp = response ?: break

            if (resp.stopReason == "tool_use" && resp.toolUse.isNotEmpty()) {
                // Add assistant message with tool use to conversation
                currentMessages.add(
                    Message.AssistantMessage(
                        content = resp.content,
                        toolUse = resp.toolUse
                    )
                )

                // Execute each tool and collect results
                val toolResults = mutableListOf<ToolResult>()
                for (toolUse in resp.toolUse) {
                    emit(OrchestratorEvent.ToolExecuting(toolUse.id, toolUse.name))
                    val result = try {
                        val output = toolRegistry.execute(toolUse.name, toolUse.input)
                        ToolResult(toolUseId = toolUse.id, content = output)
                    } catch (e: Exception) {
                        Log.e(TAG, "Tool execution error: ${toolUse.name}", e)
                        ToolResult(
                            toolUseId = toolUse.id,
                            content = "Error: ${e.message}",
                            isError = true
                        )
                    }
                    toolResults.add(result)
                    emit(OrchestratorEvent.ToolResult(toolUse.id, toolUse.name, result.content, result.isError))
                }

                // Add tool results to conversation
                currentMessages.add(Message.ToolResultMessage(toolResults))
                rounds++
            } else {
                // Final response - no more tool calls
                emit(OrchestratorEvent.Complete(resp))
                break
            }
        }

        if (rounds >= maxToolRounds) {
            emit(OrchestratorEvent.Error("Maximum tool call rounds ($maxToolRounds) exceeded"))
        }
    }

    companion object {
        private const val TAG = "ToolOrchestrator"
        private const val MAX_TOOL_ROUNDS = 10
    }
}

/**
 * Events emitted by the orchestrator during a chat round.
 */
sealed class OrchestratorEvent {
    data class TextDelta(val text: String) : OrchestratorEvent()
    data class ToolCallStart(val id: String, val name: String) : OrchestratorEvent()
    data class ToolExecuting(val id: String, val name: String) : OrchestratorEvent()
    data class ToolResult(val id: String, val name: String, val result: String, val isError: Boolean) : OrchestratorEvent()
    data class Complete(val response: ChatResponse) : OrchestratorEvent()
    data class Error(val message: String) : OrchestratorEvent()
}

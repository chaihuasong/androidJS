package com.example.androidjs.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidjs.ai.conversation.ConversationManager
import com.example.androidjs.ai.conversation.SystemPromptBuilder
import com.example.androidjs.ai.model.ChatRequest
import com.example.androidjs.ai.model.Message
import com.example.androidjs.ai.persistence.ChatDatabase
import com.example.androidjs.ai.service.ApiKeyManager
import com.example.androidjs.ai.service.ClawdbotAIService
import com.example.androidjs.ai.tools.OrchestratorEvent
import com.example.androidjs.ai.tools.ToolOrchestrator
import com.example.androidjs.ai.tools.ToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyManager = ApiKeyManager(application)
    private val aiService = ClawdbotAIService { apiKeyManager.getApiKey() }
    val toolRegistry = ToolRegistry()
    private val orchestrator = ToolOrchestrator(aiService, toolRegistry)
    private val systemPromptBuilder = SystemPromptBuilder(toolRegistry)
    private val database = ChatDatabase.getInstance(application)
    private val conversationManager = ConversationManager(database)

    private val _chatItems = MutableStateFlow<List<ChatItem>>(emptyList())
    val chatItems: StateFlow<List<ChatItem>> = _chatItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _needsApiKey = MutableStateFlow(!apiKeyManager.hasApiKey())
    val needsApiKey: StateFlow<Boolean> = _needsApiKey.asStateFlow()

    private val conversationMessages = mutableListOf<Message>()
    private var conversationId: Long = -1

    fun setApiKey(key: String) {
        apiKeyManager.setApiKey(key)
        _needsApiKey.value = false
    }

    fun hasApiKey(): Boolean = apiKeyManager.hasApiKey()

    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return

        viewModelScope.launch {
            // Create conversation if needed
            if (conversationId == -1L) {
                conversationId = conversationManager.createConversation(
                    text.take(50)
                )
            }

            // Add user message
            val userMessage = Message.UserMessage(text)
            conversationMessages.add(userMessage)
            conversationManager.addMessage(conversationId, userMessage)

            val items = _chatItems.value.toMutableList()
            items.add(ChatItem.UserMsg(text))
            _chatItems.value = items
            _isLoading.value = true

            // Start streaming response
            val request = ChatRequest(
                messages = conversationMessages.toList(),
                systemPrompt = systemPromptBuilder.build()
            )

            var assistantText = StringBuilder()
            var assistantIndex = -1
            val toolCallItems = mutableMapOf<String, Int>() // tool id -> index in items

            try {
                orchestrator.chat(request).collect { event ->
                    val currentItems = _chatItems.value.toMutableList()

                    when (event) {
                        is OrchestratorEvent.TextDelta -> {
                            assistantText.append(event.text)
                            if (assistantIndex == -1) {
                                assistantIndex = currentItems.size
                                currentItems.add(ChatItem.AssistantMsg(assistantText.toString(), isStreaming = true))
                            } else {
                                currentItems[assistantIndex] = ChatItem.AssistantMsg(assistantText.toString(), isStreaming = true)
                            }
                            _chatItems.value = currentItems
                        }

                        is OrchestratorEvent.ToolCallStart -> {
                            val idx = currentItems.size
                            toolCallItems[event.id] = idx
                            currentItems.add(ChatItem.ToolCall(id = event.id, name = event.name))
                            _chatItems.value = currentItems
                        }

                        is OrchestratorEvent.ToolExecuting -> {
                            val idx = toolCallItems[event.id]
                            if (idx != null) {
                                val existing = currentItems[idx] as ChatItem.ToolCall
                                currentItems[idx] = existing.copy(name = "${event.name}")
                                _chatItems.value = currentItems
                            }
                        }

                        is OrchestratorEvent.ToolResult -> {
                            val idx = toolCallItems[event.id]
                            if (idx != null) {
                                currentItems[idx] = ChatItem.ToolCall(
                                    id = event.id,
                                    name = event.name,
                                    result = event.result.take(500),
                                    isError = event.isError
                                )
                                _chatItems.value = currentItems
                            }
                            // Reset for next AI response after tool results
                            assistantText = StringBuilder()
                            assistantIndex = -1
                        }

                        is OrchestratorEvent.Complete -> {
                            // Finalize assistant message
                            if (assistantIndex != -1) {
                                currentItems[assistantIndex] = ChatItem.AssistantMsg(assistantText.toString(), isStreaming = false)
                            } else if (assistantText.isNotEmpty()) {
                                currentItems.add(ChatItem.AssistantMsg(assistantText.toString(), isStreaming = false))
                            }
                            _chatItems.value = currentItems

                            // Save assistant message to conversation
                            val assistantMsg = Message.AssistantMessage(
                                content = event.response.content,
                                toolUse = event.response.toolUse
                            )
                            conversationMessages.add(assistantMsg)
                            conversationManager.addMessage(conversationId, assistantMsg)
                        }

                        is OrchestratorEvent.Error -> {
                            currentItems.add(ChatItem.ErrorMsg(event.message))
                            _chatItems.value = currentItems
                        }
                    }
                }
            } catch (e: Exception) {
                val currentItems = _chatItems.value.toMutableList()
                currentItems.add(ChatItem.ErrorMsg(e.message ?: "Unknown error"))
                _chatItems.value = currentItems
            }

            _isLoading.value = false
        }
    }

    fun clearConversation() {
        conversationMessages.clear()
        _chatItems.value = emptyList()
        conversationId = -1
    }
}

package com.example.androidjs.ai.conversation

import com.example.androidjs.ai.model.Message
import com.example.androidjs.ai.model.ToolUseBlock
import com.example.androidjs.ai.model.ToolResult
import com.example.androidjs.ai.persistence.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Manages conversations and their persistence.
 * Handles CRUD, context window management, and message serialization.
 */
class ConversationManager(private val database: ChatDatabase) {

    private val json = Json { ignoreUnknownKeys = true }
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()

    suspend fun createConversation(title: String): Long {
        return conversationDao.insert(ConversationEntity(title = title))
    }

    suspend fun getConversations(): List<ConversationEntity> {
        return conversationDao.getAll()
    }

    suspend fun getConversation(id: Long): ConversationEntity? {
        return conversationDao.getById(id)
    }

    suspend fun deleteConversation(id: Long) {
        conversationDao.deleteById(id)
    }

    suspend fun updateTitle(id: Long, title: String) {
        val conv = conversationDao.getById(id) ?: return
        conversationDao.update(conv.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun addMessage(conversationId: Long, message: Message) {
        val entity = when (message) {
            is Message.UserMessage -> MessageEntity(
                conversationId = conversationId,
                role = "user",
                content = message.content
            )
            is Message.AssistantMessage -> MessageEntity(
                conversationId = conversationId,
                role = "assistant",
                content = message.content,
                toolUseJson = if (message.toolUse.isNotEmpty()) {
                    json.encodeToString(message.toolUse)
                } else null
            )
            is Message.ToolResultMessage -> MessageEntity(
                conversationId = conversationId,
                role = "tool_result",
                content = json.encodeToString(message.toolResults.map {
                    mapOf(
                        "toolUseId" to it.toolUseId,
                        "content" to it.content,
                        "isError" to it.isError.toString()
                    )
                })
            )
        }
        messageDao.insert(entity)

        // Update conversation timestamp
        val conv = conversationDao.getById(conversationId) ?: return
        conversationDao.update(conv.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun getMessages(conversationId: Long): List<Message> {
        return messageDao.getByConversation(conversationId).map { entity ->
            when (entity.role) {
                "user" -> Message.UserMessage(entity.content)
                "assistant" -> {
                    val toolUse = if (entity.toolUseJson != null) {
                        try {
                            json.decodeFromString<List<ToolUseBlock>>(entity.toolUseJson)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else emptyList()
                    Message.AssistantMessage(entity.content, toolUse)
                }
                "tool_result" -> {
                    val results = try {
                        val list = json.decodeFromString<List<Map<String, String>>>(entity.content)
                        list.map {
                            ToolResult(
                                toolUseId = it["toolUseId"] ?: "",
                                content = it["content"] ?: "",
                                isError = it["isError"] == "true"
                            )
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    Message.ToolResultMessage(results)
                }
                else -> Message.UserMessage(entity.content)
            }
        }
    }

    /**
     * Get messages with context window management.
     * Keeps total estimated tokens under budget.
     */
    suspend fun getMessagesWithinBudget(
        conversationId: Long,
        maxTokens: Int = MAX_CONTEXT_TOKENS
    ): List<Message> {
        val allMessages = getMessages(conversationId)
        if (allMessages.isEmpty()) return allMessages

        var tokenCount = 0
        val result = mutableListOf<Message>()

        // Work backwards from the most recent message
        for (msg in allMessages.reversed()) {
            val msgTokens = estimateTokens(msg)
            if (tokenCount + msgTokens > maxTokens && result.isNotEmpty()) break
            result.add(0, msg)
            tokenCount += msgTokens
        }

        return result
    }

    private fun estimateTokens(message: Message): Int {
        val text = when (message) {
            is Message.UserMessage -> message.content
            is Message.AssistantMessage -> message.content + message.toolUse.toString()
            is Message.ToolResultMessage -> message.toolResults.joinToString { it.content }
        }
        // Rough estimate: ~3 chars per token for mixed CJK/English
        return (text.length / 3).coerceAtLeast(1)
    }

    companion object {
        private const val MAX_CONTEXT_TOKENS = 100_000
    }
}

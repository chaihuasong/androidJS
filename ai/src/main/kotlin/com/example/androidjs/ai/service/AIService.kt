package com.example.androidjs.ai.service

import com.example.androidjs.ai.model.ChatRequest
import com.example.androidjs.ai.model.ChatResponse
import com.example.androidjs.ai.model.StreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Abstract interface for AI service providers.
 * Supports both Clawdbot cloud API and future local model implementations.
 */
interface AIService {
    /** Unique identifier for this provider (e.g., "clawdbot", "local") */
    val providerId: String

    /** Check if the service is available and configured */
    suspend fun isAvailable(): Boolean

    /** Send a message and get a complete response */
    suspend fun sendMessage(request: ChatRequest): ChatResponse

    /** Send a message and get a streaming response */
    fun sendMessageStream(request: ChatRequest): Flow<StreamEvent>
}

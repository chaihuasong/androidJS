package com.example.androidjs.ai.service

import android.util.Log
import com.example.androidjs.ai.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * DeepSeek API implementation of AIService.
 * Uses OpenAI-compatible format with OkHttp SSE streaming.
 */
class ClawdbotAIService(
    private val apiKeyProvider: () -> String?
) : AIService {

    override val providerId = "clawdbot"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override suspend fun isAvailable(): Boolean {
        return !apiKeyProvider().isNullOrBlank()
    }

    override suspend fun sendMessage(request: ChatRequest): ChatResponse {
        return withContext(Dispatchers.IO) {
            val apiKey = apiKeyProvider()
                ?: throw IllegalStateException("API key not configured")

            val requestBody = buildRequestJson(request, stream = false)
            val httpRequest = Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string()
                ?: throw IOException("Empty response body")

            if (!response.isSuccessful) {
                throw IOException("API error ${response.code}: $body")
            }

            parseFullResponse(body)
        }
    }

    override fun sendMessageStream(request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        val apiKey = apiKeyProvider()
            ?: throw IllegalStateException("API key not configured")

        val requestBody = buildRequestJson(request, stream = true)
        val httpRequest = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val sseFactory = EventSources.createFactory(client)

        val contentText = StringBuilder()
        val toolCalls = mutableMapOf<Int, ToolCallAccumulator>()
        var inputTokens = 0
        var outputTokens = 0
        var finishReason = ""

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                try {
                    if (data == "[DONE]") {
                        // Build final response
                        val toolUseBlocks = toolCalls.values.map { acc ->
                            val inputJson = try {
                                json.parseToJsonElement(acc.arguments.toString()).jsonObject
                            } catch (e: Exception) {
                                JsonObject(emptyMap())
                            }
                            ToolUseBlock(acc.id, acc.name, inputJson)
                        }

                        trySend(
                            StreamEvent.MessageComplete(
                                ChatResponse(
                                    content = contentText.toString(),
                                    toolUse = toolUseBlocks,
                                    stopReason = if (finishReason == "tool_calls") "tool_use" else finishReason,
                                    usage = TokenUsage(inputTokens, outputTokens)
                                )
                            )
                        )
                        close()
                        return
                    }

                    val jsonData = json.parseToJsonElement(data).jsonObject
                    val choices = jsonData["choices"]?.jsonArray
                    val choice = choices?.firstOrNull()?.jsonObject ?: return
                    val delta = choice["delta"]?.jsonObject ?: return

                    // Check finish_reason
                    choice["finish_reason"]?.let {
                        if (it !is JsonNull) {
                            finishReason = it.jsonPrimitive.content
                        }
                    }

                    // Text content delta
                    delta["content"]?.let {
                        if (it !is JsonNull) {
                            val text = it.jsonPrimitive.content
                            contentText.append(text)
                            trySend(StreamEvent.TextDelta(text))
                        }
                    }

                    // Tool calls delta
                    delta["tool_calls"]?.jsonArray?.forEach { tcElement ->
                        val tc = tcElement.jsonObject
                        val index = tc["index"]?.jsonPrimitive?.int ?: 0
                        val function = tc["function"]?.jsonObject

                        if (tc.containsKey("id")) {
                            // New tool call start
                            val toolId = tc["id"]?.jsonPrimitive?.content ?: "call_$index"
                            val toolName = function?.get("name")?.jsonPrimitive?.content ?: ""
                            toolCalls[index] = ToolCallAccumulator(toolId, toolName)
                            trySend(StreamEvent.ToolUseStart(toolId, toolName))
                        }

                        // Accumulate arguments
                        function?.get("arguments")?.let {
                            if (it !is JsonNull) {
                                val argChunk = it.jsonPrimitive.content
                                toolCalls[index]?.arguments?.append(argChunk)
                                trySend(StreamEvent.ToolUseDelta(argChunk))
                            }
                        }
                    }

                    // Usage info (DeepSeek sends usage in the last chunk)
                    jsonData["usage"]?.jsonObject?.let { usage ->
                        inputTokens = usage["prompt_tokens"]?.jsonPrimitive?.int ?: inputTokens
                        outputTokens = usage["completion_tokens"]?.jsonPrimitive?.int ?: outputTokens
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing SSE event: $data", e)
                    trySend(StreamEvent.Error(e.message ?: "Parse error"))
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                val errorMsg = t?.message ?: response?.body?.string() ?: "Connection failed"
                Log.e(TAG, "SSE failure: $errorMsg", t)
                trySend(StreamEvent.Error(errorMsg))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = sseFactory.newEventSource(httpRequest, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    /**
     * Build OpenAI-compatible request JSON for DeepSeek API.
     */
    private fun buildRequestJson(request: ChatRequest, stream: Boolean): String {
        val root = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", request.maxTokens)
            put("stream", stream)

            putJsonArray("messages") {
                // System prompt as first message
                if (request.systemPrompt != null) {
                    addJsonObject {
                        put("role", "system")
                        put("content", request.systemPrompt)
                    }
                }

                for (msg in request.messages) {
                    when (msg) {
                        is Message.UserMessage -> {
                            addJsonObject {
                                put("role", "user")
                                put("content", msg.content)
                            }
                        }
                        is Message.AssistantMessage -> {
                            addJsonObject {
                                put("role", "assistant")
                                if (msg.toolUse.isNotEmpty()) {
                                    // Assistant message with tool calls
                                    if (msg.content.isNotEmpty()) {
                                        put("content", msg.content)
                                    } else {
                                        put("content", JsonNull)
                                    }
                                    putJsonArray("tool_calls") {
                                        for (tool in msg.toolUse) {
                                            addJsonObject {
                                                put("id", tool.id)
                                                put("type", "function")
                                                putJsonObject("function") {
                                                    put("name", tool.name)
                                                    put("arguments", tool.input.toString())
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    put("content", msg.content)
                                }
                            }
                        }
                        is Message.ToolResultMessage -> {
                            // Each tool result is a separate "tool" role message
                            for (result in msg.toolResults) {
                                addJsonObject {
                                    put("role", "tool")
                                    put("tool_call_id", result.toolUseId)
                                    put("content", result.content)
                                }
                            }
                        }
                    }
                }
            }

            // Tools in OpenAI format
            if (!request.tools.isNullOrEmpty()) {
                putJsonArray("tools") {
                    for (tool in request.tools) {
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.inputSchema)
                            }
                        }
                    }
                }
            }
        }
        return root.toString()
    }

    /**
     * Parse non-streaming response in OpenAI format.
     */
    private fun parseFullResponse(body: String): ChatResponse {
        val root = json.parseToJsonElement(body).jsonObject
        val choices = root["choices"]?.jsonArray
        val choice = choices?.firstOrNull()?.jsonObject ?: return ChatResponse()
        val message = choice["message"]?.jsonObject ?: return ChatResponse()

        val text = message["content"]?.let {
            if (it is JsonNull) "" else it.jsonPrimitive.content
        } ?: ""

        val toolUses = mutableListOf<ToolUseBlock>()
        message["tool_calls"]?.jsonArray?.forEach { tcElement ->
            val tc = tcElement.jsonObject
            val function = tc["function"]?.jsonObject
            val argsStr = function?.get("arguments")?.jsonPrimitive?.content ?: "{}"
            val argsJson = try {
                json.parseToJsonElement(argsStr).jsonObject
            } catch (e: Exception) {
                JsonObject(emptyMap())
            }
            toolUses.add(
                ToolUseBlock(
                    id = tc["id"]?.jsonPrimitive?.content ?: "",
                    name = function?.get("name")?.jsonPrimitive?.content ?: "",
                    input = argsJson
                )
            )
        }

        val finishReason = choice["finish_reason"]?.jsonPrimitive?.content ?: ""
        val usage = root["usage"]?.jsonObject
        val tokenUsage = TokenUsage(
            inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.int ?: 0,
            outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.int ?: 0
        )

        return ChatResponse(
            content = text,
            toolUse = toolUses,
            stopReason = if (finishReason == "tool_calls") "tool_use" else finishReason,
            usage = tokenUsage
        )
    }

    /**
     * Accumulator for streaming tool call chunks.
     */
    private data class ToolCallAccumulator(
        val id: String,
        val name: String,
        val arguments: StringBuilder = StringBuilder()
    )

    companion object {
        private const val TAG = "ClawdbotAIService"
        private const val API_URL = "https://api.deepseek.com/chat/completions"
        private const val MODEL = "deepseek-chat"
    }
}

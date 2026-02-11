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
 * Clawdbot API implementation of AIService.
 * Uses OkHttp for HTTP requests and SSE streaming.
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
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
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
            .header("x-api-key", apiKey)
            .header("anthropic-version", API_VERSION)
            .header("content-type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val sseFactory = EventSources.createFactory(client)

        var contentText = StringBuilder()
        var toolUseBlocks = mutableListOf<ToolUseBlock>()
        var currentToolId = ""
        var currentToolName = ""
        var currentToolInput = StringBuilder()
        var inputTokens = 0
        var outputTokens = 0

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                try {
                    if (data == "[DONE]") return

                    val jsonData = json.parseToJsonElement(data).jsonObject
                    when (type) {
                        "message_start" -> {
                            val usage = jsonData["message"]?.jsonObject?.get("usage")?.jsonObject
                            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.int ?: 0
                        }
                        "content_block_start" -> {
                            val block = jsonData["content_block"]?.jsonObject ?: return
                            when (block["type"]?.jsonPrimitive?.content) {
                                "text" -> { /* text block started */ }
                                "tool_use" -> {
                                    currentToolId = block["id"]?.jsonPrimitive?.content ?: ""
                                    currentToolName = block["name"]?.jsonPrimitive?.content ?: ""
                                    currentToolInput = StringBuilder()
                                    trySend(StreamEvent.ToolUseStart(currentToolId, currentToolName))
                                }
                            }
                        }
                        "content_block_delta" -> {
                            val delta = jsonData["delta"]?.jsonObject ?: return
                            when (delta["type"]?.jsonPrimitive?.content) {
                                "text_delta" -> {
                                    val text = delta["text"]?.jsonPrimitive?.content ?: ""
                                    contentText.append(text)
                                    trySend(StreamEvent.TextDelta(text))
                                }
                                "input_json_delta" -> {
                                    val partial = delta["partial_json"]?.jsonPrimitive?.content ?: ""
                                    currentToolInput.append(partial)
                                    trySend(StreamEvent.ToolUseDelta(partial))
                                }
                            }
                        }
                        "content_block_stop" -> {
                            if (currentToolId.isNotEmpty()) {
                                val inputJson = try {
                                    json.parseToJsonElement(currentToolInput.toString()).jsonObject
                                } catch (e: Exception) {
                                    JsonObject(emptyMap())
                                }
                                toolUseBlocks.add(
                                    ToolUseBlock(currentToolId, currentToolName, inputJson)
                                )
                                currentToolId = ""
                                currentToolName = ""
                                trySend(StreamEvent.ToolUseEnd)
                            }
                        }
                        "message_delta" -> {
                            val delta = jsonData["delta"]?.jsonObject
                            val stopReason = delta?.get("stop_reason")?.jsonPrimitive?.content ?: ""
                            val usage = jsonData["usage"]?.jsonObject
                            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.int ?: 0

                            trySend(
                                StreamEvent.MessageComplete(
                                    ChatResponse(
                                        content = contentText.toString(),
                                        toolUse = toolUseBlocks.toList(),
                                        stopReason = stopReason,
                                        usage = TokenUsage(inputTokens, outputTokens)
                                    )
                                )
                            )
                        }
                        "message_stop" -> {
                            close()
                        }
                        "error" -> {
                            val error = jsonData["error"]?.jsonObject
                            val message = error?.get("message")?.jsonPrimitive?.content
                                ?: "Unknown error"
                            trySend(StreamEvent.Error(message))
                            close()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing SSE event", e)
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

    private fun buildRequestJson(request: ChatRequest, stream: Boolean): String {
        val root = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", request.maxTokens)
            put("stream", stream)

            if (request.systemPrompt != null) {
                put("system", request.systemPrompt)
            }

            putJsonArray("messages") {
                for (msg in request.messages) {
                    addJsonObject {
                        when (msg) {
                            is Message.UserMessage -> {
                                put("role", "user")
                                put("content", msg.content)
                            }
                            is Message.AssistantMessage -> {
                                put("role", "assistant")
                                putJsonArray("content") {
                                    if (msg.content.isNotEmpty()) {
                                        addJsonObject {
                                            put("type", "text")
                                            put("text", msg.content)
                                        }
                                    }
                                    for (tool in msg.toolUse) {
                                        addJsonObject {
                                            put("type", "tool_use")
                                            put("id", tool.id)
                                            put("name", tool.name)
                                            put("input", tool.input)
                                        }
                                    }
                                }
                            }
                            is Message.ToolResultMessage -> {
                                put("role", "user")
                                putJsonArray("content") {
                                    for (result in msg.toolResults) {
                                        addJsonObject {
                                            put("type", "tool_result")
                                            put("tool_use_id", result.toolUseId)
                                            put("content", result.content)
                                            if (result.isError) {
                                                put("is_error", true)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!request.tools.isNullOrEmpty()) {
                putJsonArray("tools") {
                    for (tool in request.tools) {
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.inputSchema)
                        }
                    }
                }
            }
        }
        return root.toString()
    }

    private fun parseFullResponse(body: String): ChatResponse {
        val root = json.parseToJsonElement(body).jsonObject

        val content = root["content"]?.jsonArray ?: return ChatResponse()
        var text = ""
        val toolUses = mutableListOf<ToolUseBlock>()

        for (block in content) {
            val obj = block.jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "text" -> text = obj["text"]?.jsonPrimitive?.content ?: ""
                "tool_use" -> {
                    toolUses.add(
                        ToolUseBlock(
                            id = obj["id"]?.jsonPrimitive?.content ?: "",
                            name = obj["name"]?.jsonPrimitive?.content ?: "",
                            input = obj["input"]?.jsonObject ?: JsonObject(emptyMap())
                        )
                    )
                }
            }
        }

        val stopReason = root["stop_reason"]?.jsonPrimitive?.content ?: ""
        val usage = root["usage"]?.jsonObject
        val tokenUsage = TokenUsage(
            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.int ?: 0,
            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.int ?: 0
        )

        return ChatResponse(
            content = text,
            toolUse = toolUses,
            stopReason = stopReason,
            usage = tokenUsage
        )
    }

    companion object {
        private const val TAG = "ClawdbotAIService"
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val MODEL = "claude-sonnet-4-5-20250929"
    }
}

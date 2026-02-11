package com.example.androidjs.ai.tools

import kotlinx.serialization.json.JsonObject

/**
 * Interface for executing a tool.
 */
interface ToolExecutor {
    val definition: ToolDefinition

    /**
     * Execute the tool with the given input.
     * @param input JSON object with tool parameters
     * @return Result string (JSON or plain text)
     */
    suspend fun execute(input: JsonObject): String
}

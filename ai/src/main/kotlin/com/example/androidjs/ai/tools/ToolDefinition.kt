package com.example.androidjs.ai.tools

import com.example.androidjs.ai.model.ToolSpec
import kotlinx.serialization.json.JsonObject

/**
 * Definition of a tool that AI can invoke.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
) {
    fun toToolSpec(): ToolSpec = ToolSpec(
        name = name,
        description = description,
        inputSchema = inputSchema
    )
}

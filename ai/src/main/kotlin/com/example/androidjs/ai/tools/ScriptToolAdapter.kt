package com.example.androidjs.ai.tools

import com.example.androidjs.core.AndroidJSEngine
import kotlinx.serialization.json.*

/**
 * Adapts a JS script into a ToolExecutor that the AI can invoke.
 * The script is loaded and executed with the given input text.
 */
class ScriptToolAdapter(
    private val engine: AndroidJSEngine,
    private val scriptId: String,
    private val scriptName: String,
    private val scriptDescription: String,
    private val scriptPath: String,
    private val isAsset: Boolean = false
) : ToolExecutor {

    override val definition = ToolDefinition(
        name = "script_$scriptId",
        description = "$scriptName: $scriptDescription",
        inputSchema = buildInputSchema()
    )

    override suspend fun execute(input: JsonObject): String {
        val inputText = input["input"]?.jsonPrimitive?.content ?: ""

        val setupScript = """
            var __scriptInput = ${Json.encodeToString(kotlinx.serialization.serializer<String>(), inputText)};
        """.trimIndent()

        engine.executeScript(setupScript)

        val result = if (isAsset) {
            engine.executeAssetScript(scriptPath)
        } else {
            engine.executeFileScript(scriptPath)
        }

        val onInputResult = engine.executeScript("""
            if (typeof onInput === 'function') {
                JSON.stringify(onInput(__scriptInput));
            } else {
                __scriptInput;
            }
        """.trimIndent())

        return onInputResult ?: result ?: """{"result": "Script executed successfully"}"""
    }

    private fun buildInputSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("input") {
                put("type", "string")
                put("description", "Input text for the script")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("input"))
        }
    }
}

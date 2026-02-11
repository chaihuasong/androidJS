package com.example.androidjs.ai.tools

import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.json.*

/**
 * JSON Schema definitions for all native module tools.
 */
object ToolSchemas {

    /**
     * Get all tool definitions for a given native module.
     */
    fun getToolDefinitions(module: NativeModule): List<ToolDefinition> {
        return when (module.name) {
            "storage" -> storageTools()
            "network" -> networkTools()
            "database" -> databaseTools()
            "voice" -> voiceTools()
            "widget" -> widgetTools()
            "clipboard" -> clipboardTools()
            "device" -> deviceTools()
            "notification" -> notificationTools()
            "file" -> fileTools()
            "alarm" -> alarmTools()
            "adb" -> adbTools()
            else -> emptyList()
        }
    }

    private fun storageTools() = listOf(
        ToolDefinition(
            name = "storage_get",
            description = "Read a value from device key-value storage",
            inputSchema = objectSchema(
                "key" to stringProp("The storage key to read")
            )
        ),
        ToolDefinition(
            name = "storage_set",
            description = "Write a value to device key-value storage",
            inputSchema = objectSchema(
                "key" to stringProp("The storage key"),
                "value" to stringProp("The value to store"),
                required = listOf("key", "value")
            )
        ),
        ToolDefinition(
            name = "storage_remove",
            description = "Delete a key from device storage",
            inputSchema = objectSchema(
                "key" to stringProp("The storage key to remove")
            )
        ),
        ToolDefinition(
            name = "storage_getAll",
            description = "Get all key-value pairs from device storage",
            inputSchema = emptyObjectSchema()
        ),
        ToolDefinition(
            name = "storage_has",
            description = "Check if a key exists in device storage",
            inputSchema = objectSchema(
                "key" to stringProp("The storage key to check")
            )
        ),
        ToolDefinition(
            name = "storage_keys",
            description = "Get all keys in device storage",
            inputSchema = emptyObjectSchema()
        )
    )

    private fun networkTools() = listOf(
        ToolDefinition(
            name = "network_get",
            description = "Send an HTTP GET request",
            inputSchema = objectSchema(
                "url" to stringProp("The URL to request"),
                "headers" to objectPropOptional("Optional HTTP headers"),
                required = listOf("url")
            )
        ),
        ToolDefinition(
            name = "network_post",
            description = "Send an HTTP POST request",
            inputSchema = objectSchema(
                "url" to stringProp("The URL to request"),
                "body" to stringPropOptional("Request body"),
                "contentType" to stringPropOptional("Content type (default: application/json)"),
                "headers" to objectPropOptional("Optional HTTP headers"),
                required = listOf("url")
            )
        ),
        ToolDefinition(
            name = "network_put",
            description = "Send an HTTP PUT request",
            inputSchema = objectSchema(
                "url" to stringProp("The URL to request"),
                "body" to stringPropOptional("Request body"),
                "contentType" to stringPropOptional("Content type (default: application/json)"),
                "headers" to objectPropOptional("Optional HTTP headers"),
                required = listOf("url")
            )
        ),
        ToolDefinition(
            name = "network_delete",
            description = "Send an HTTP DELETE request",
            inputSchema = objectSchema(
                "url" to stringProp("The URL to request"),
                "headers" to objectPropOptional("Optional HTTP headers"),
                required = listOf("url")
            )
        )
    )

    private fun databaseTools() = listOf(
        ToolDefinition(
            name = "database_exec",
            description = "Execute a SQL statement (CREATE, INSERT, UPDATE, DELETE)",
            inputSchema = objectSchema(
                "sql" to stringProp("SQL statement to execute"),
                "params" to arrayPropOptional("Optional bind parameters"),
                required = listOf("sql")
            )
        ),
        ToolDefinition(
            name = "database_query",
            description = "Query the database and return rows",
            inputSchema = objectSchema(
                "sql" to stringProp("SQL SELECT query"),
                "params" to arrayPropOptional("Optional bind parameters"),
                required = listOf("sql")
            )
        ),
        ToolDefinition(
            name = "database_listTables",
            description = "List all tables in the database",
            inputSchema = emptyObjectSchema()
        ),
        ToolDefinition(
            name = "database_describeTable",
            description = "Get column info for a table",
            inputSchema = objectSchema(
                "name" to stringProp("Table name to describe")
            )
        )
    )

    private fun voiceTools() = listOf(
        ToolDefinition(
            name = "voice_isAvailable",
            description = "Check if speech recognition is available on this device",
            inputSchema = emptyObjectSchema()
        ),
        ToolDefinition(
            name = "voice_startListening",
            description = "Start speech recognition and return the recognized text",
            inputSchema = objectSchema(
                "locale" to stringPropOptional("Language locale (e.g., 'en-US', 'zh-CN')")
            )
        )
    )

    private fun widgetTools() = listOf(
        ToolDefinition(
            name = "widget_updateText",
            description = "Update text fields on a home screen widget",
            inputSchema = objectSchema(
                "fields" to objectProp("Map of view names to text values"),
                required = listOf("fields")
            )
        )
    )

    private fun clipboardTools() = listOf(
        ToolDefinition(
            name = "clipboard_getText",
            description = "Read text from the device clipboard",
            inputSchema = emptyObjectSchema()
        ),
        ToolDefinition(
            name = "clipboard_setText",
            description = "Copy text to the device clipboard",
            inputSchema = objectSchema(
                "text" to stringProp("Text to copy to clipboard")
            )
        )
    )

    private fun deviceTools() = listOf(
        ToolDefinition(
            name = "device_getInfo",
            description = "Get device information (model, manufacturer, OS version)",
            inputSchema = emptyObjectSchema()
        ),
        ToolDefinition(
            name = "device_getTime",
            description = "Get the current device date and time",
            inputSchema = emptyObjectSchema()
        ),
        ToolDefinition(
            name = "device_getConnectivity",
            description = "Get network connectivity status (WiFi, cellular, etc.)",
            inputSchema = emptyObjectSchema()
        )
    )

    private fun notificationTools() = listOf(
        ToolDefinition(
            name = "notification_show",
            description = "Show a local notification on the device",
            inputSchema = objectSchema(
                "title" to stringProp("Notification title"),
                "message" to stringProp("Notification message"),
                required = listOf("title", "message")
            )
        )
    )

    private fun fileTools() = listOf(
        ToolDefinition(
            name = "file_readText",
            description = "Read text content from a file in app internal storage",
            inputSchema = objectSchema(
                "path" to stringProp("Relative file path within app storage")
            )
        ),
        ToolDefinition(
            name = "file_writeText",
            description = "Write text content to a file in app internal storage",
            inputSchema = objectSchema(
                "path" to stringProp("Relative file path within app storage"),
                "content" to stringProp("Text content to write"),
                required = listOf("path", "content")
            )
        ),
        ToolDefinition(
            name = "file_listFiles",
            description = "List files in a directory within app internal storage",
            inputSchema = objectSchema(
                "dir" to stringPropOptional("Relative directory path (default: root)")
            )
        ),
        ToolDefinition(
            name = "file_delete",
            description = "Delete a file from app internal storage",
            inputSchema = objectSchema(
                "path" to stringProp("Relative file path to delete")
            )
        )
    )

    private fun alarmTools() = listOf(
        ToolDefinition(
            name = "alarm_setAlarm",
            description = "Set an alarm at a specific time",
            inputSchema = objectSchema(
                "hour" to intProp("Hour (0-23)"),
                "minute" to intProp("Minute (0-59)"),
                "message" to stringPropOptional("Alarm message"),
                required = listOf("hour", "minute")
            )
        ),
        ToolDefinition(
            name = "alarm_setReminder",
            description = "Set a reminder after a delay",
            inputSchema = objectSchema(
                "delayMinutes" to intProp("Delay in minutes"),
                "message" to stringProp("Reminder message"),
                required = listOf("delayMinutes", "message")
            )
        )
    )

    private fun adbTools() = listOf(
        ToolDefinition(
            name = "adb_shell",
            description = "Execute a shell command on the device (like adb shell). Returns stdout, stderr, and exitCode. Useful for getting system info, listing processes, checking properties, etc.",
            inputSchema = objectSchema(
                "command" to stringProp("Shell command to execute (e.g., 'getprop ro.product.model', 'ps', 'ls /data/local/tmp')"),
                "timeout" to intPropOptional("Command timeout in seconds (default: 30, max: 120)"),
                required = listOf("command")
            )
        )
    )

    // --- Schema builder helpers ---

    private fun emptyObjectSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
    }

    private fun objectSchema(
        vararg props: Pair<String, JsonObject>,
        required: List<String>? = null
    ): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            for ((name, schema) in props) {
                put(name, schema)
            }
        }
        val req = required ?: props.map { it.first }
        if (req.isNotEmpty()) {
            putJsonArray("required") { req.forEach { add(it) } }
        }
    }

    private fun stringProp(desc: String) = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    private fun stringPropOptional(desc: String) = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    private fun intProp(desc: String) = buildJsonObject {
        put("type", "integer")
        put("description", desc)
    }

    private fun intPropOptional(desc: String) = buildJsonObject {
        put("type", "integer")
        put("description", desc)
    }

    private fun objectProp(desc: String) = buildJsonObject {
        put("type", "object")
        put("description", desc)
    }

    private fun objectPropOptional(desc: String) = buildJsonObject {
        put("type", "object")
        put("description", desc)
    }

    private fun arrayPropOptional(desc: String) = buildJsonObject {
        put("type", "array")
        put("description", desc)
    }
}

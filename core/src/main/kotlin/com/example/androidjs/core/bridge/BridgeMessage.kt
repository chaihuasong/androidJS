package com.example.androidjs.core.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BridgeRequest(
    val module: String,
    val method: String,
    val args: String = "{}",
    val callbackId: String? = null
)

@Serializable
data class BridgeResponse(
    val success: Boolean,
    val data: String? = null,
    val error: String? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun success(data: String?): BridgeResponse =
            BridgeResponse(success = true, data = data)

        fun error(message: String): BridgeResponse =
            BridgeResponse(success = false, error = message)

        fun BridgeResponse.toJson(): String = json.encodeToString(serializer(), this)
    }
}

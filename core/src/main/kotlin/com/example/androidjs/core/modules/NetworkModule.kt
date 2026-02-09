package com.example.androidjs.core.modules

import android.util.Log
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Built-in module for HTTP network requests from JS.
 * Provides GET and POST methods using OkHttp.
 */
class NetworkModule : NativeModule {

    override val name: String = "network"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "get" -> {
                val args = json.decodeFromString<NetworkGetArgs>(argsJson)
                executeGet(args)
            }
            "post" -> {
                val args = json.decodeFromString<NetworkPostArgs>(argsJson)
                executePost(args)
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    override fun invokeAsync(method: String, argsJson: String, callback: (String) -> Unit) {
        Thread {
            val result = invoke(method, argsJson)
            callback(result ?: "{}")
        }.start()
    }

    private fun executeGet(args: NetworkGetArgs): String {
        return try {
            val requestBuilder = Request.Builder().url(args.url)
            args.headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: ""
            json.encodeToString(
                NetworkResponse.serializer(),
                NetworkResponse(response.code, body)
            )
        } catch (e: Exception) {
            Log.e(TAG, "GET request failed", e)
            json.encodeToString(
                NetworkResponse.serializer(),
                NetworkResponse(-1, "", e.message)
            )
        }
    }

    private fun executePost(args: NetworkPostArgs): String {
        return try {
            val mediaType = (args.contentType ?: "application/json").toMediaTypeOrNull()
            val body = (args.body ?: "").toRequestBody(mediaType)
            val requestBuilder = Request.Builder().url(args.url).post(body)
            args.headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""
            json.encodeToString(
                NetworkResponse.serializer(),
                NetworkResponse(response.code, responseBody)
            )
        } catch (e: Exception) {
            Log.e(TAG, "POST request failed", e)
            json.encodeToString(
                NetworkResponse.serializer(),
                NetworkResponse(-1, "", e.message)
            )
        }
    }

    @Serializable
    private data class NetworkGetArgs(
        val url: String,
        val headers: Map<String, String>? = null
    )

    @Serializable
    private data class NetworkPostArgs(
        val url: String,
        val body: String? = null,
        val contentType: String? = null,
        val headers: Map<String, String>? = null
    )

    @Serializable
    private data class NetworkResponse(
        val statusCode: Int,
        val body: String,
        val error: String? = null
    )

    companion object {
        private const val TAG = "NetworkModule"
    }
}

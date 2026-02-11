package com.example.androidjs.core.modules

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Built-in module for key-value storage via SharedPreferences.
 * Provides get/set/remove/clear operations to JS.
 */
class StorageModule(context: Context) : NativeModule {

    override val name: String = "storage"

    private val prefs: SharedPreferences =
        context.getSharedPreferences("androidjs_storage", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "get" -> {
                val args = json.decodeFromString<StorageKeyArgs>(argsJson)
                val value = prefs.getString(args.key, null)
                json.encodeToString(StorageValueResult.serializer(), StorageValueResult(value))
            }
            "set" -> {
                val args = json.decodeFromString<StorageSetArgs>(argsJson)
                prefs.edit().putString(args.key, args.value).apply()
                null
            }
            "remove" -> {
                val args = json.decodeFromString<StorageKeyArgs>(argsJson)
                prefs.edit().remove(args.key).apply()
                null
            }
            "clear" -> {
                prefs.edit().clear().apply()
                null
            }
            "getAll" -> {
                val all = prefs.all
                    .filterValues { it != null }
                    .mapValues { it.value.toString() }
                json.encodeToString(StorageAllResult.serializer(), StorageAllResult(all))
            }
            "has" -> {
                val args = json.decodeFromString<StorageKeyArgs>(argsJson)
                val exists = prefs.contains(args.key)
                json.encodeToString(StorageHasResult.serializer(), StorageHasResult(exists))
            }
            "keys" -> {
                val keys = prefs.all.keys.toList()
                json.encodeToString(StorageKeysResult.serializer(), StorageKeysResult(keys))
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    @Serializable
    private data class StorageKeyArgs(val key: String)

    @Serializable
    private data class StorageSetArgs(val key: String, val value: String)

    @Serializable
    private data class StorageValueResult(val value: String?)

    @Serializable
    private data class StorageAllResult(val entries: Map<String, String>)

    @Serializable
    private data class StorageHasResult(val exists: Boolean)

    @Serializable
    private data class StorageKeysResult(val keys: List<String>)

    companion object {
        private const val TAG = "StorageModule"
    }
}

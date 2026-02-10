package com.example.androidjs.core.script

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages downloading, caching, and version control of JS scripts.
 *
 * Supports `mock://` URLs for development: `mock://quran_widget` reads from
 * `assets/js/quran_widget.js`. Replace with real URLs for production.
 */
class ScriptManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scriptsDir: File
        get() = File(context.filesDir, "scripts").also { it.mkdirs() }

    /**
     * Fetch a script manifest from a URL.
     * Supports `mock://` — reads from `assets/mock/manifest.json`.
     */
    suspend fun fetchManifest(url: String): ScriptManifest = withContext(Dispatchers.IO) {
        val content = if (url.startsWith("mock://")) {
            context.assets.open("mock/manifest.json").bufferedReader().use { it.readText() }
        } else {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("Failed to fetch manifest: ${response.code}")
                }
                response.body?.string() ?: throw RuntimeException("Empty manifest response")
            }
        }
        json.decodeFromString<ScriptManifest>(content)
    }

    /**
     * Download a script and cache it locally.
     * Returns the local file path.
     *
     * For `mock://` URLs, reads from assets/js/{scriptId}.js.
     * For real URLs, downloads via OkHttp.
     */
    suspend fun downloadScript(info: ScriptInfo): String = withContext(Dispatchers.IO) {
        val fileName = "${info.id}_v${info.version}.js"
        val targetFile = File(scriptsDir, fileName)

        val content = if (info.url.startsWith("mock://")) {
            val assetName = info.url.removePrefix("mock://")
            context.assets.open("js/$assetName.js").bufferedReader().use { it.readText() }
        } else {
            val request = Request.Builder().url(info.url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("Failed to download script: ${response.code}")
                }
                response.body?.string() ?: throw RuntimeException("Empty script response")
            }
        }

        targetFile.writeText(content)

        // Clean up old versions of this script
        scriptsDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("${info.id}_v") && file.name != fileName) {
                file.delete()
                Log.d(TAG, "Deleted old version: ${file.name}")
            }
        }

        // Save cache metadata
        val cached = CachedScript(
            info = info,
            localPath = targetFile.absolutePath,
            cachedAt = System.currentTimeMillis()
        )
        prefs.edit().putString(keyFor(info.id), json.encodeToString(cached)).apply()

        Log.d(TAG, "Script cached: ${info.id} v${info.version} → ${targetFile.absolutePath}")
        targetFile.absolutePath
    }

    /**
     * Get the local file path for a cached script, or null if not cached.
     */
    fun getCachedScriptPath(scriptId: String): String? {
        val cached = getCachedScript(scriptId) ?: return null
        val file = File(cached.localPath)
        return if (file.exists()) cached.localPath else null
    }

    /**
     * Get full cache metadata for a script.
     */
    fun getCachedScript(scriptId: String): CachedScript? {
        val jsonStr = prefs.getString(keyFor(scriptId), null) ?: return null
        return try {
            json.decodeFromString<CachedScript>(jsonStr)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse cached script metadata for $scriptId", e)
            null
        }
    }

    /**
     * Check if a newer version is available compared to the cached version.
     */
    fun isUpdateAvailable(info: ScriptInfo): Boolean {
        val cached = getCachedScript(info.id) ?: return true
        return info.version > cached.info.version
    }

    /**
     * List all cached scripts.
     */
    fun getAllCachedScripts(): List<CachedScript> {
        return prefs.all.entries
            .filter { it.key.startsWith(KEY_PREFIX) }
            .mapNotNull { (_, value) ->
                try {
                    json.decodeFromString<CachedScript>(value as String)
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Delete a cached script and its file.
     */
    fun deleteScript(scriptId: String) {
        val cached = getCachedScript(scriptId)
        if (cached != null) {
            File(cached.localPath).delete()
        }
        prefs.edit().remove(keyFor(scriptId)).apply()
        Log.d(TAG, "Script deleted: $scriptId")
    }

    private fun keyFor(scriptId: String) = "$KEY_PREFIX$scriptId"

    companion object {
        private const val TAG = "ScriptManager"
        private const val PREFS_NAME = "androidjs_scripts"
        private const val KEY_PREFIX = "script_"
    }
}

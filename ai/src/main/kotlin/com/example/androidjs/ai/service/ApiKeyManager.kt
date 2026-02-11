package com.example.androidjs.ai.service

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages the Clawdbot API key with encrypted storage.
 */
class ApiKeyManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    companion object {
        private const val PREFS_FILE = "clawdbot_secure_prefs"
        private const val KEY_API_KEY = "clawdbot_api_key"
    }
}

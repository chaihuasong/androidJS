package com.example.androidjs.demo

import android.app.Application
import android.util.Log
import com.example.androidjs.ai.persistence.ChatDatabase
import com.example.androidjs.ai.service.ApiKeyManager

class ClawdbotApplication : Application() {

    lateinit var chatDatabase: ChatDatabase
        private set
    lateinit var apiKeyManager: ApiKeyManager
        private set

    override fun onCreate() {
        super.onCreate()
        chatDatabase = ChatDatabase.getInstance(this)
        apiKeyManager = ApiKeyManager(this)
        Log.d(TAG, "Clawdbot Application initialized")
    }

    companion object {
        private const val TAG = "Clawdbot"
    }
}

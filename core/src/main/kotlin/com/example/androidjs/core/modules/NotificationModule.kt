package com.example.androidjs.core.modules

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NotificationModule(private val context: Context) : NativeModule {

    override val name: String = "notification"

    private val json = Json { ignoreUnknownKeys = true }
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var notificationId = 1000

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clawdbot Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "show" -> {
                val args = json.decodeFromString<ShowArgs>(argsJson)
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(args.title)
                    .setContentText(args.message)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
                val id = notificationId++
                notificationManager.notify(id, notification)
                """{"success": true, "notificationId": $id}"""
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    @Serializable
    private data class ShowArgs(val title: String, val message: String)

    companion object {
        private const val TAG = "NotificationModule"
        private const val CHANNEL_ID = "clawdbot_default"
    }
}

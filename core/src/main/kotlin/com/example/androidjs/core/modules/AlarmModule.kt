package com.example.androidjs.core.modules

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar

class AlarmModule(private val context: Context) : NativeModule {

    override val name: String = "alarm"

    private val json = Json { ignoreUnknownKeys = true }
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "setAlarm" -> {
                val args = json.decodeFromString<AlarmArgs>(argsJson)
                setAlarm(args)
            }
            "setReminder" -> {
                val args = json.decodeFromString<ReminderArgs>(argsJson)
                setReminder(args)
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    private fun setAlarm(args: AlarmArgs): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, args.hour)
            set(Calendar.MINUTE, args.minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1) // next day if time already passed
            }
        }

        val requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("message", args.message ?: "Alarm")
            putExtra("id", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        return """{"success": true, "alarmId": $requestCode, "triggerTime": "${calendar.time}"}"""
    }

    private fun setReminder(args: ReminderArgs): String {
        val triggerTime = System.currentTimeMillis() + args.delayMinutes * 60 * 1000L
        val requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("message", args.message)
            putExtra("id", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )

        return """{"success": true, "alarmId": $requestCode, "delayMinutes": ${args.delayMinutes}}"""
    }

    @Serializable
    private data class AlarmArgs(val hour: Int, val minute: Int, val message: String? = null)

    @Serializable
    private data class ReminderArgs(val delayMinutes: Int, val message: String)

    companion object {
        private const val TAG = "AlarmModule"
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "Reminder"
        val id = intent.getIntExtra("id", 0)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "clawdbot_alarm",
                "Clawdbot Alarms",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "clawdbot_alarm")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Clawdbot")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(id, notification)
    }
}

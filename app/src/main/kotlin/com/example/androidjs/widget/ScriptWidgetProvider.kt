package com.example.androidjs.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Generic AppWidgetProvider for script-driven widgets.
 * Manages widget lifecycle and schedules periodic updates via WorkManager.
 */
class ScriptWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")

        // Trigger an immediate update
        val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)

        // Schedule periodic updates
        schedulePeriodicUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled, scheduling periodic updates")
        schedulePeriodicUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Widget disabled, cancelling periodic updates")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun schedulePeriodicUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            24, TimeUnit.HOURS,
            1, TimeUnit.HOURS  // flex interval
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }

    companion object {
        private const val TAG = "ScriptWidgetProvider"
        private const val WORK_NAME = "script_widget_periodic_update"
    }
}

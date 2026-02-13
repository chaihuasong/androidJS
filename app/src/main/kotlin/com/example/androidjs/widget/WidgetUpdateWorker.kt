package com.example.androidjs.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager Worker for periodic widget updates.
 * Executes JS script and applies result to widget via shared helper.
 */
class WidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting widget update work")

        return try {
            val result = ScriptWidgetProvider.executeWidgetScript(applicationContext)
            Log.d(TAG, "JS result: $result")

            if (result != null && result != "null") {
                val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(applicationContext, ScriptWidgetProvider::class.java)
                )

                if (widgetIds.isNotEmpty()) {
                    ScriptWidgetProvider.applyResultToWidgets(
                        applicationContext, appWidgetManager, widgetIds, result
                    )
                } else {
                    Log.d(TAG, "No widget instances found")
                }
            }

            Log.d(TAG, "Widget update completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Widget update failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WidgetUpdateWorker"
    }
}

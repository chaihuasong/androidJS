package com.example.androidjs.demo

import android.app.Application
import android.util.Log

class DemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AndroidJS Demo Application initialized")
    }

    companion object {
        private const val TAG = "DemoApp"
    }
}

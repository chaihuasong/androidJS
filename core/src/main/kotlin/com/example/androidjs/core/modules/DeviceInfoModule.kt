package com.example.androidjs.core.modules

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class DeviceInfoModule(private val context: Context) : NativeModule {

    override val name: String = "device"

    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "getInfo" -> {
                json.encodeToString(
                    DeviceInfo.serializer(),
                    DeviceInfo(
                        model = Build.MODEL,
                        manufacturer = Build.MANUFACTURER,
                        brand = Build.BRAND,
                        osVersion = Build.VERSION.RELEASE,
                        sdkVersion = Build.VERSION.SDK_INT
                    )
                )
            }
            "getTime" -> {
                val now = Date()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                json.encodeToString(
                    TimeInfo.serializer(),
                    TimeInfo(
                        formatted = dateFormat.format(now),
                        iso = isoFormat.format(now),
                        timestamp = now.time,
                        timezone = TimeZone.getDefault().id
                    )
                )
            }
            "getConnectivity" -> {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = cm.activeNetwork
                val capabilities = network?.let { cm.getNetworkCapabilities(it) }
                val type = when {
                    capabilities == null -> "none"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                    else -> "other"
                }
                json.encodeToString(
                    ConnectivityInfo.serializer(),
                    ConnectivityInfo(
                        connected = network != null,
                        type = type
                    )
                )
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    @Serializable
    private data class DeviceInfo(
        val model: String,
        val manufacturer: String,
        val brand: String,
        val osVersion: String,
        val sdkVersion: Int
    )

    @Serializable
    private data class TimeInfo(
        val formatted: String,
        val iso: String,
        val timestamp: Long,
        val timezone: String
    )

    @Serializable
    private data class ConnectivityInfo(
        val connected: Boolean,
        val type: String
    )

    companion object {
        private const val TAG = "DeviceInfoModule"
    }
}

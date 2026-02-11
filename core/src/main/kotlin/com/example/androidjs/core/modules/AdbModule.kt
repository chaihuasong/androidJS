package com.example.androidjs.core.modules

import android.util.Log
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * ADB shell command execution module.
 * Executes shell commands via ProcessBuilder on the device.
 */
class AdbModule : NativeModule {

    override val name: String = "adb"

    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "shell" -> {
                val args = json.decodeFromString<ShellArgs>(argsJson)
                shell(args.command, args.timeout)
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    private fun shell(command: String, timeout: Int): String {
        val effectiveTimeout = timeout.coerceIn(1, MAX_TIMEOUT_SECONDS)

        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(effectiveTimeout.toLong(), TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return json.encodeToString(
                    ShellResult.serializer(),
                    ShellResult(
                        stdout = "",
                        stderr = "Process timed out after ${effectiveTimeout}s",
                        exitCode = -1
                    )
                )
            }

            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
                .take(MAX_OUTPUT_BYTES)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
                .take(MAX_OUTPUT_BYTES)
            val exitCode = process.exitValue()

            json.encodeToString(
                ShellResult.serializer(),
                ShellResult(stdout = stdout, stderr = stderr, exitCode = exitCode)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Shell execution failed", e)
            json.encodeToString(
                ShellResult.serializer(),
                ShellResult(
                    stdout = "",
                    stderr = "Execution failed: ${e.message}",
                    exitCode = -1
                )
            )
        }
    }

    @Serializable
    private data class ShellArgs(
        val command: String,
        val timeout: Int = DEFAULT_TIMEOUT_SECONDS
    )

    @Serializable
    private data class ShellResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )

    companion object {
        private const val TAG = "AdbModule"
        private const val DEFAULT_TIMEOUT_SECONDS = 30
        private const val MAX_TIMEOUT_SECONDS = 120
        private const val MAX_OUTPUT_BYTES = 50 * 1024 // 50KB
    }
}

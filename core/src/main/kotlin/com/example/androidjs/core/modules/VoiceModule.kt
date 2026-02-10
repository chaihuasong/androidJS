package com.example.androidjs.core.modules

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Generic voice recognition module.
 * Locale is passed from JS via startListening({locale}).
 */
class VoiceModule(private val context: Context) : NativeModule {

    override val name: String = "voice"

    private val json = Json { ignoreUnknownKeys = true }
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "isAvailable" -> {
                val available = SpeechRecognizer.isRecognitionAvailable(context)
                json.encodeToString(VoiceAvailableResult.serializer(), VoiceAvailableResult(available))
            }
            "stop" -> {
                stopListening()
                null
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    override fun invokeAsync(method: String, argsJson: String, callback: (String) -> Unit) {
        when (method) {
            "startListening" -> {
                val args = try {
                    json.decodeFromString<ListenArgs>(argsJson)
                } catch (e: Exception) {
                    ListenArgs()
                }
                startListening(args.locale, callback)
            }
            else -> {
                callback(json.encodeToString(
                    VoiceResult.serializer(),
                    VoiceResult(success = false, error = "Unknown method: $method")
                ))
            }
        }
    }

    private fun startListening(locale: String, callback: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback(json.encodeToString(
                VoiceResult.serializer(),
                VoiceResult(success = false, error = "Speech recognition not available")
            ))
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                            else -> "Unknown error: $error"
                        }
                        callback(json.encodeToString(
                            VoiceResult.serializer(),
                            VoiceResult(success = false, error = errorMsg)
                        ))
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        callback(json.encodeToString(
                            VoiceResult.serializer(),
                            VoiceResult(success = true, text = text)
                        ))
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            callback(json.encodeToString(
                VoiceResult.serializer(),
                VoiceResult(success = false, error = e.message ?: "Unknown error")
            ))
        }
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    override fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }

    @Serializable
    data class ListenArgs(val locale: String = "en-US")

    @Serializable
    data class VoiceResult(
        val success: Boolean,
        val text: String? = null,
        val error: String? = null
    )

    @Serializable
    data class VoiceAvailableResult(val available: Boolean)

    companion object {
        private const val TAG = "VoiceModule"
    }
}

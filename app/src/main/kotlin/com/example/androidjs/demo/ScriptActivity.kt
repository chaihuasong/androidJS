package com.example.androidjs.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.androidjs.R
import com.example.androidjs.core.AndroidJSEngine
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Generic script-driven Activity.
 * JS controls UI via UIModule; this Activity only provides the shell.
 */
class ScriptActivity : AppCompatActivity() {

    private lateinit var engine: AndroidJSEngine
    private lateinit var uiModule: UIModule
    private lateinit var editInput: EditText
    private lateinit var btnVoice: ImageButton
    private lateinit var btnSubmit: MaterialButton

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script)

        initViews()
        initEngine()
    }

    private fun initViews() {
        editInput = findViewById(R.id.edit_input)
        btnVoice = findViewById(R.id.btn_voice)
        btnSubmit = findViewById(R.id.btn_submit)

        val recyclerList = findViewById<RecyclerView>(R.id.recycler_list)
        val adapter = ScriptListAdapter()
        recyclerList.layoutManager = LinearLayoutManager(this)
        recyclerList.adapter = adapter

        uiModule = UIModule(this)
        uiModule.setListAdapter(adapter)

        btnSubmit.setOnClickListener { processInput() }
        btnVoice.setOnClickListener { startVoiceInput() }

        editInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                processInput()
                true
            } else false
        }
    }

    private fun initEngine() {
        lifecycleScope.launch {
            try {
                engine = AndroidJSEngine.Builder(applicationContext)
                    .addModule(uiModule)
                    .setExecutionTimeout(10_000)
                    .build()
                engine.initialize()
                Log.d(TAG, "Engine initialized")

                // Load JS script — try cached file first, fall back to asset
                val scriptPath = intent.getStringExtra("script_path")
                val assetPath = intent.getStringExtra("asset_path")
                var loaded = false

                if (scriptPath != null) {
                    try {
                        engine.executeFileScript(scriptPath)
                        loaded = true
                        Log.d(TAG, "Loaded script from file: $scriptPath")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load script from file: $scriptPath, trying asset fallback", e)
                    }
                }

                if (!loaded && assetPath != null) {
                    engine.executeAssetScript(assetPath)
                    Log.d(TAG, "Loaded script from asset: $assetPath")
                }

                // Trigger JS lifecycle callback (wrapped in try-catch to surface JS errors)
                val result = engine.executeScript(
                    "if(typeof onActivityCreated==='function'){" +
                    "try{onActivityCreated();'ok'}catch(e){console.error('onActivityCreated error: '+e.message);'error:'+e.message}" +
                    "}else{'no_func'}"
                )
                Log.d(TAG, "onActivityCreated result: $result")
            } catch (e: Exception) {
                Log.e(TAG, "Engine init failed", e)
                Toast.makeText(this@ScriptActivity, "引擎初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processInput() {
        val input = editInput.text.toString().trim()
        if (input.isEmpty()) return

        lifecycleScope.launch {
            try {
                val escaped = input.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n")
                engine.executeScript("if(typeof onInput==='function')onInput('$escaped')")
            } catch (e: Exception) {
                Log.e(TAG, "processInput failed", e)
                Toast.makeText(this@ScriptActivity, "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startVoiceInput() {
        if (!uiModule.showVoice) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }

        Toast.makeText(this, "正在听...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val localeJson = json.encodeToString(VoiceLocaleArgs.serializer(), VoiceLocaleArgs(uiModule.voiceLocale))
                val dispatcher = engine.getBridgeDispatcher()
                dispatcher.dispatchAsync("voice", "startListening", localeJson) { result ->
                    runOnUiThread {
                        try {
                            val response = json.decodeFromString<BridgeResponseData>(result)
                            val voiceResult = json.decodeFromString<VoiceResultData>(response.data ?: "{}")
                            if (voiceResult.success && voiceResult.text != null) {
                                editInput.setText(voiceResult.text)
                                processInput()
                            } else {
                                Toast.makeText(
                                    this@ScriptActivity,
                                    "语音识别失败: ${voiceResult.error ?: "未知错误"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Voice result processing failed", e)
                            Toast.makeText(this@ScriptActivity, "语音处理失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Voice input failed", e)
                Toast.makeText(this@ScriptActivity, "语音功能不可用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceInput()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::engine.isInitialized) {
            engine.destroy()
        }
    }

    @Serializable
    private data class VoiceLocaleArgs(val locale: String)

    @Serializable
    private data class VoiceResultData(
        val success: Boolean,
        val text: String? = null,
        val error: String? = null
    )

    @Serializable
    private data class BridgeResponseData(
        val success: Boolean,
        val data: String? = null,
        val error: String? = null
    )

    companion object {
        private const val TAG = "ScriptActivity"
        private const val REQUEST_RECORD_AUDIO = 1001
    }
}

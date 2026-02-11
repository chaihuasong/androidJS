package com.example.androidjs.demo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.androidjs.R
import com.example.androidjs.accounting.AccountingPlugin
import com.example.androidjs.accounting.ui.AccountingActivity
import com.example.androidjs.chat.ChatActivity
import com.example.androidjs.core.AndroidJSEngine
import com.example.androidjs.widget.WidgetPlugin
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity() {

    private lateinit var engine: AndroidJSEngine
    private lateinit var textEngineStatus: TextView
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textEngineStatus = findViewById(R.id.text_engine_status)

        setupWidgetDemo()
        setupAccountingDemo()
        initEngine()

        // FAB to open chat
        val fabChat = findViewById<FloatingActionButton>(R.id.fab_chat)
        fabChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    private fun initEngine() {
        textEngineStatus.text = "引擎初始化中..."

        lifecycleScope.launch {
            try {
                val widgetPlugin = WidgetPlugin(applicationContext)
                val accountingPlugin = AccountingPlugin(applicationContext)

                engine = AndroidJSEngine.Builder(applicationContext)
                    .addPlugin(widgetPlugin)
                    .addPlugin(accountingPlugin)
                    .setMemoryLimit(16 * 1024 * 1024)
                    .setExecutionTimeout(5000)
                    .build()

                engine.initialize()
                textEngineStatus.text = "Clawdbot Engine Ready"
            } catch (e: Exception) {
                textEngineStatus.text = "引擎初始化失败: ${e.message}"
            }
        }
    }

    private fun setupWidgetDemo() {
        val btnTest = findViewById<MaterialButton>(R.id.btn_test_widget)
        val cardResult = findViewById<MaterialCardView>(R.id.card_widget_result)
        val textResult = findViewById<TextView>(R.id.text_widget_result)

        btnTest.setOnClickListener {
            lifecycleScope.launch {
                try {
                    if (!::engine.isInitialized || !engine.isReady()) {
                        Toast.makeText(this@MainActivity, "引擎尚未就绪", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val result = engine.executeAssetScript("js/quran_widget.js")
                    if (result != null) {
                        val cleanResult = result.trim().let {
                            if (it.startsWith("\"") && it.endsWith("\"")) {
                                it.substring(1, it.length - 1)
                                    .replace("\\\"", "\"")
                                    .replace("\\\\", "\\")
                            } else it
                        }

                        val verse = json.decodeFromString<QuranVerse>(cleanResult)
                        textResult.text = buildString {
                            appendLine("${verse.arabic}")
                            appendLine()
                            appendLine("${verse.translation}")
                            appendLine()
                            append("${verse.reference} | ${verse.date}")
                        }
                        cardResult.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    textResult.text = "执行失败: ${e.message}"
                    cardResult.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupAccountingDemo() {
        val btnOpen = findViewById<MaterialButton>(R.id.btn_open_accounting)
        btnOpen.setOnClickListener {
            val intent = Intent(this, AccountingActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::engine.isInitialized) {
            engine.destroy()
        }
    }

    @Serializable
    private data class QuranVerse(
        val arabic: String = "",
        val translation: String = "",
        val surah: String = "",
        val ayah: String = "",
        val reference: String = "",
        val date: String = ""
    )
}

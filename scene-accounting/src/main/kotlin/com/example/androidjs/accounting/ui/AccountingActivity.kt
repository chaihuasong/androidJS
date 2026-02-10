package com.example.androidjs.accounting.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.androidjs.accounting.AccountingPlugin
import com.example.androidjs.accounting.R
import com.example.androidjs.accounting.data.AccountingDatabase
import com.example.androidjs.accounting.data.TransactionEntity
import com.example.androidjs.core.AndroidJSEngine
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AccountingActivity : AppCompatActivity() {

    private lateinit var editInput: EditText
    private lateinit var btnVoice: ImageButton
    private lateinit var btnSubmit: MaterialButton
    private lateinit var cardResult: MaterialCardView
    private lateinit var textResult: TextView
    private lateinit var recyclerTransactions: RecyclerView
    private lateinit var textEmpty: TextView
    private lateinit var textTotalExpense: TextView
    private lateinit var textTotalIncome: TextView

    private lateinit var adapter: TransactionAdapter
    private lateinit var engine: AndroidJSEngine
    private val json = Json { ignoreUnknownKeys = true }
    private val dao by lazy { AccountingDatabase.getInstance(this).transactionDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounting)

        initViews()
        setupRecyclerView()
        initEngine()
        loadTransactions()
    }

    private fun initViews() {
        editInput = findViewById(R.id.edit_input)
        btnVoice = findViewById(R.id.btn_voice)
        btnSubmit = findViewById(R.id.btn_submit)
        cardResult = findViewById(R.id.card_result)
        textResult = findViewById(R.id.text_result)
        recyclerTransactions = findViewById(R.id.recycler_transactions)
        textEmpty = findViewById(R.id.text_empty)
        textTotalExpense = findViewById(R.id.text_total_expense)
        textTotalIncome = findViewById(R.id.text_total_income)

        btnSubmit.setOnClickListener { processInput() }

        btnVoice.setOnClickListener { startVoiceInput() }

        editInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                processInput()
                true
            } else false
        }
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter()
        recyclerTransactions.layoutManager = LinearLayoutManager(this)
        recyclerTransactions.adapter = adapter
    }

    private fun initEngine() {
        lifecycleScope.launch {
            try {
                val plugin = AccountingPlugin(applicationContext)
                engine = AndroidJSEngine.Builder(applicationContext)
                    .addPlugin(plugin)
                    .setExecutionTimeout(10_000)
                    .build()
                engine.initialize()

                // Load the accounting JS module — prefer cached script if available
                val scriptPath = intent.getStringExtra("script_path")
                if (scriptPath != null) {
                    engine.executeFileScript(scriptPath)
                } else {
                    engine.executeAssetScript("js/accounting.js")
                }
            } catch (e: Exception) {
                Toast.makeText(this@AccountingActivity, "引擎初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processInput() {
        val input = editInput.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入记账内容", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Call JS to parse the input
                val escapedInput = input.replace("'", "\\'").replace("\"", "\\\"")
                val result = engine.executeScript("parseTransaction('$escapedInput')")

                if (result != null) {
                    val cleanResult = result.trim().let {
                        if (it.startsWith("\"") && it.endsWith("\"")) {
                            it.substring(1, it.length - 1)
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                        } else it
                    }

                    val parsed = json.decodeFromString<ParseResult>(cleanResult)

                    if (parsed.success) {
                        // Show parse result
                        val typeText = if (parsed.type == "expense") "支出" else "收入"
                        textResult.text = "✓ $typeText ¥${String.format("%.2f", parsed.amount)} | 分类: ${parsed.category} | ${parsed.description}"
                        cardResult.visibility = View.VISIBLE

                        // Save to database
                        val entity = TransactionEntity(
                            amount = parsed.amount,
                            category = parsed.category,
                            description = parsed.description,
                            type = parsed.type,
                            rawInput = parsed.rawInput
                        )
                        dao.insert(entity)

                        // Clear input and refresh
                        editInput.text?.clear()
                        loadTransactions()
                    } else {
                        textResult.text = "✗ 解析失败: ${parsed.error}"
                        cardResult.visibility = View.VISIBLE
                        (cardResult as? MaterialCardView)?.setCardBackgroundColor(
                            ContextCompat.getColor(this@AccountingActivity, android.R.color.holo_red_light)
                        )
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@AccountingActivity, "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTransactions() {
        lifecycleScope.launch {
            try {
                val transactions = dao.getAll()
                adapter.submitList(transactions)

                textEmpty.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
                recyclerTransactions.visibility = if (transactions.isEmpty()) View.GONE else View.VISIBLE

                // Update totals
                val totalExpense = dao.getTotalByType("expense") ?: 0.0
                val totalIncome = dao.getTotalByType("income") ?: 0.0
                textTotalExpense.text = "¥${String.format("%.2f", totalExpense)}"
                textTotalIncome.text = "¥${String.format("%.2f", totalIncome)}"
            } catch (e: Exception) {
                Toast.makeText(this@AccountingActivity, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }

        Toast.makeText(this, "正在听...", Toast.LENGTH_SHORT).show()

        // Use the voice module through the bridge
        lifecycleScope.launch {
            try {
                val dispatcher = engine.getBridgeDispatcher()
                dispatcher.dispatchAsync("voice", "startListening", "{}") { result ->
                    runOnUiThread {
                        try {
                            val voiceResult = json.decodeFromString<VoiceResult>(
                                result.let {
                                    // Extract data from bridge response
                                    val response = json.decodeFromString<BridgeResponseData>(it)
                                    response.data ?: "{}"
                                }
                            )
                            if (voiceResult.success && voiceResult.text != null) {
                                editInput.setText(voiceResult.text)
                                processInput()
                            } else {
                                Toast.makeText(
                                    this@AccountingActivity,
                                    "语音识别失败: ${voiceResult.error ?: "未知错误"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@AccountingActivity, "语音处理失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@AccountingActivity, "语音功能不可用", Toast.LENGTH_SHORT).show()
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
    private data class ParseResult(
        val success: Boolean,
        val amount: Double = 0.0,
        val category: String = "",
        val type: String = "expense",
        val description: String = "",
        val rawInput: String = "",
        val error: String? = null
    )

    @Serializable
    private data class VoiceResult(
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
        private const val REQUEST_RECORD_AUDIO = 1001
    }
}

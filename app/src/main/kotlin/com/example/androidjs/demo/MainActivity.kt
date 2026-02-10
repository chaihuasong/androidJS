package com.example.androidjs.demo

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.androidjs.R
import com.example.androidjs.accounting.AccountingPlugin
import com.example.androidjs.accounting.ui.AccountingActivity
import com.example.androidjs.core.AndroidJSEngine
import com.example.androidjs.core.script.ScriptInfo
import com.example.androidjs.core.script.ScriptManager
import com.example.androidjs.widget.WidgetPlugin
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity() {

    private lateinit var engine: AndroidJSEngine
    private lateinit var scriptManager: ScriptManager
    private lateinit var textEngineStatus: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerScripts: RecyclerView
    private lateinit var adapter: ScriptCardAdapter

    private val json = Json { ignoreUnknownKeys = true }
    private var scriptItems = mutableListOf<ScriptItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textEngineStatus = findViewById(R.id.text_engine_status)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        recyclerScripts = findViewById(R.id.recycler_scripts)

        scriptManager = ScriptManager(applicationContext)

        setupRecyclerView()
        setupSwipeRefresh()
        initEngine()
    }

    private fun setupRecyclerView() {
        adapter = ScriptCardAdapter { item -> onActionClick(item) }
        recyclerScripts.layoutManager = LinearLayoutManager(this)
        recyclerScripts.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            loadManifest()
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
                textEngineStatus.text = "AndroidJS Engine 已就绪 ✓"

                loadManifest()
            } catch (e: Exception) {
                textEngineStatus.text = "引擎初始化失败: ${e.message}"
            }
        }
    }

    private fun loadManifest() {
        lifecycleScope.launch {
            try {
                val manifest = try {
                    scriptManager.fetchManifest("mock://manifest")
                } catch (e: Exception) {
                    // Offline fallback: read from assets directly
                    val fallback = assets.open("mock/manifest.json").bufferedReader().use { it.readText() }
                    json.decodeFromString(fallback)
                }

                scriptItems = manifest.scripts.map { info ->
                    val cached = scriptManager.getCachedScript(info.id)
                    ScriptItem(info = info, cached = cached)
                }.toMutableList()

                adapter.submitList(scriptItems.toList())
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "加载脚本清单失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun onActionClick(item: ScriptItem) {
        if (!::engine.isInitialized || !engine.isReady()) {
            Toast.makeText(this, "引擎尚未就绪", Toast.LENGTH_SHORT).show()
            return
        }

        if (!item.isCached || item.hasUpdate) {
            downloadScript(item)
        } else {
            runScript(item.info, item.cached!!.localPath)
        }
    }

    private fun downloadScript(item: ScriptItem) {
        // Show loading state
        updateItemLoading(item.info.id, true)

        lifecycleScope.launch {
            try {
                scriptManager.downloadScript(item.info)

                // Refresh item with new cache state
                val cached = scriptManager.getCachedScript(item.info.id)
                updateItem(item.info.id) { it.copy(cached = cached, isLoading = false) }

                Toast.makeText(this@MainActivity, "${item.info.name} 下载完成", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                updateItemLoading(item.info.id, false)
                Toast.makeText(this@MainActivity, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runScript(info: ScriptInfo, path: String) {
        lifecycleScope.launch {
            try {
                when (info.id) {
                    "quran_widget" -> {
                        val result = engine.executeFileScript(path)
                        if (result != null) {
                            showQuranResult(result)
                        }
                    }
                    "accounting" -> {
                        val intent = Intent(this@MainActivity, AccountingActivity::class.java)
                        intent.putExtra("script_path", path)
                        startActivity(intent)
                    }
                    else -> {
                        val result = engine.executeFileScript(path)
                        Toast.makeText(this@MainActivity, "结果: $result", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "执行失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showQuranResult(result: String) {
        val cleanResult = result.trim().let {
            if (it.startsWith("\"") && it.endsWith("\"")) {
                it.substring(1, it.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            } else it
        }

        try {
            val verse = json.decodeFromString<QuranVerse>(cleanResult)
            val message = buildString {
                appendLine("${verse.arabic}")
                appendLine()
                appendLine("${verse.translation}")
                appendLine()
                append("${verse.reference} | ${verse.date}")
            }

            AlertDialog.Builder(this)
                .setTitle("今日经文")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "解析结果失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateItemLoading(scriptId: String, loading: Boolean) {
        updateItem(scriptId) { it.copy(isLoading = loading) }
    }

    private fun updateItem(scriptId: String, transform: (ScriptItem) -> ScriptItem) {
        val index = scriptItems.indexOfFirst { it.info.id == scriptId }
        if (index >= 0) {
            scriptItems[index] = transform(scriptItems[index])
            adapter.submitList(scriptItems.toList())
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

package com.example.androidjs.chat

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.androidjs.R
import com.example.androidjs.ai.tools.NativeModuleToolAdapter
import com.example.androidjs.ai.tools.ToolSchemas
import com.example.androidjs.core.modules.*
import com.google.android.material.appbar.MaterialToolbar
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatMessageAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var btnSend: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val markwon = Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .build()

        adapter = ChatMessageAdapter(markwon)
        recyclerView = findViewById(R.id.recycler_messages)
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.adapter = adapter

        editMessage = findViewById(R.id.edit_message)
        btnSend = findViewById(R.id.btn_send)

        btnSend.setOnClickListener { sendMessage() }

        // Register native module tools
        registerTools()

        // Observe chat items
        lifecycleScope.launch {
            viewModel.chatItems.collectLatest { items ->
                adapter.submitList(items.toList()) {
                    if (items.isNotEmpty()) {
                        recyclerView.scrollToPosition(items.size - 1)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { loading ->
                btnSend.isEnabled = !loading
                editMessage.isEnabled = !loading
            }
        }

        // Check API key on start
        lifecycleScope.launch {
            viewModel.needsApiKey.collectLatest { needsKey ->
                if (needsKey) {
                    ApiKeySetupDialog.show(this@ChatActivity) { key ->
                        viewModel.setApiKey(key)
                    }
                }
            }
        }
    }

    private fun registerTools() {
        // Register all available native module tools
        val modules = listOf(
            StorageModule(applicationContext),
            NetworkModule(),
            DatabaseModule(applicationContext),
            ClipboardModule(applicationContext),
            DeviceInfoModule(applicationContext),
            NotificationModule(applicationContext),
            FileModule(applicationContext),
            AlarmModule(applicationContext)
        )

        for (module in modules) {
            val definitions = ToolSchemas.getToolDefinitions(module)
            for (def in definitions) {
                val method = def.name.substringAfter("_")
                val toolAdapter = NativeModuleToolAdapter(module, method, def)
                viewModel.toolRegistry.register(toolAdapter)
            }
        }
    }

    private fun sendMessage() {
        val text = editMessage.text.toString().trim()
        if (text.isEmpty()) return
        editMessage.text.clear()
        viewModel.sendMessage(text)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_CLEAR, 0, "New Chat")
        menu.add(0, MENU_API_KEY, 1, "API Key")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_CLEAR -> {
                viewModel.clearConversation()
                true
            }
            MENU_API_KEY -> {
                ApiKeySetupDialog.show(this) { key ->
                    viewModel.setApiKey(key)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val MENU_CLEAR = 1
        private const val MENU_API_KEY = 2
    }
}

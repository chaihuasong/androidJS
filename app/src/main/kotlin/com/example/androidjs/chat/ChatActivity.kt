package com.example.androidjs.chat

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.androidjs.R
import com.example.androidjs.ai.tools.NativeModuleToolAdapter
import com.example.androidjs.ai.tools.ToolSchemas
import com.example.androidjs.core.modules.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
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
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var conversationAdapter: ConversationListAdapter
    private lateinit var drawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Setup drawer toggle (hamburger icon)
        drawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.drawer_open, R.string.drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        drawerToggle.drawerArrowDrawable.color = 0xFFFFFFFF.toInt()

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

        // Setup conversation list in drawer
        setupDrawer()

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

        // Observe conversations list
        lifecycleScope.launch {
            viewModel.conversations.collectLatest { conversations ->
                conversationAdapter.submitList(conversations)
            }
        }

        // Observe current conversation id for selection highlight
        lifecycleScope.launch {
            viewModel.currentConversationId.collectLatest { id ->
                conversationAdapter.setSelectedId(id)
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

    private fun setupDrawer() {
        conversationAdapter = ConversationListAdapter(
            onItemClick = { id ->
                viewModel.loadConversation(id)
                drawerLayout.closeDrawers()
            },
            onItemLongClick = { id ->
                AlertDialog.Builder(this)
                    .setTitle("Delete conversation?")
                    .setMessage("This action cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteConversation(id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        val recyclerConversations = findViewById<RecyclerView>(R.id.recycler_conversations)
        recyclerConversations.layoutManager = LinearLayoutManager(this)
        recyclerConversations.adapter = conversationAdapter

        findViewById<MaterialButton>(R.id.btn_new_chat).setOnClickListener {
            viewModel.newConversation()
            drawerLayout.closeDrawers()
        }
    }

    private fun registerTools() {
        val modules = listOf(
            StorageModule(applicationContext),
            NetworkModule(),
            DatabaseModule(applicationContext),
            ClipboardModule(applicationContext),
            DeviceInfoModule(applicationContext),
            NotificationModule(applicationContext),
            FileModule(applicationContext),
            AlarmModule(applicationContext),
            AdbModule()
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
        menu.add(0, MENU_NEW_CHAT, 0, "New Chat")
        menu.add(0, MENU_API_KEY, 1, "API Key")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) return true
        return when (item.itemId) {
            MENU_NEW_CHAT -> {
                viewModel.newConversation()
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
        private const val MENU_NEW_CHAT = 1
        private const val MENU_API_KEY = 2
    }
}

package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.data.api.AiService
import com.example.data.db.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.ConversationEntity
import com.example.data.model.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ChatRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val aiService = AiService()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("roxy_ai_prefs", Context.MODE_PRIVATE)

    private val _settingsState = MutableStateFlow(loadSettings())
    val settingsState = _settingsState.asStateFlow()

    val conversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    suspend fun getRecentMessages(conversationId: String): List<MessageEntity> {
        return withContext(Dispatchers.IO) {
            messageDao.getRecentMessages(conversationId).reversed()
        }
    }

    suspend fun createNewConversation(initialTitle: String = "New Chat"): ConversationEntity {
        return withContext(Dispatchers.IO) {
            val id = "chat_${System.currentTimeMillis()}"
            val conversation = ConversationEntity(
                id = id,
                title = initialTitle,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            conversationDao.insertConversation(conversation)
            conversation
        }
    }

    suspend fun renameConversation(conversationId: String, newTitle: String) {
        withContext(Dispatchers.IO) {
            conversationDao.updateTitle(conversationId, newTitle.trim())
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        withContext(Dispatchers.IO) {
            conversationDao.deleteConversationById(conversationId)
        }
    }

    suspend fun insertMessage(conversationId: String, role: String, content: String): MessageEntity {
        return withContext(Dispatchers.IO) {
            val message = MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            val id = messageDao.insertMessage(message)
            conversationDao.updateTimestamp(conversationId)
            message.copy(id = id)
        }
    }

    suspend fun updateConversationTitleIfFirstMessage(conversationId: String, userText: String) {
        withContext(Dispatchers.IO) {
            val conv = conversationDao.getConversationById(conversationId)
            if (conv != null && (conv.title == "New Chat" || conv.title.isBlank())) {
                val newTitle = if (userText.length > 35) userText.take(35) + "..." else userText
                conversationDao.updateTitle(conversationId, newTitle)
            }
        }
    }

    suspend fun deleteLastAssistantMessage(conversationId: String) {
        withContext(Dispatchers.IO) {
            messageDao.deleteLastAssistantMessage(conversationId)
        }
    }

    suspend fun getLastMessage(conversationId: String): MessageEntity? {
        return withContext(Dispatchers.IO) {
            messageDao.getLastMessage(conversationId)
        }
    }

    suspend fun requestAiResponse(
        conversationId: String,
        messages: List<Pair<String, String>>
    ): String {
        val currentSettings = _settingsState.value
        return if (currentSettings.provider.equals("openai", ignoreCase = true)) {
            val key = currentSettings.openaiKey.trim()
            if (key.isBlank()) {
                throw IllegalStateException("Please enter your OpenAI API key in Settings.")
            }
            aiService.generateWithOpenAi(key, currentSettings.openaiModel, messages)
        } else {
            // Gemini
            val customKey = currentSettings.geminiKey.trim()
            val effectiveKey = when {
                customKey.isNotBlank() -> customKey
                BuildConfig.GEMINI_API_KEY.isNotBlank() &&
                        BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
                else -> ""
            }
            if (effectiveKey.isBlank()) {
                throw IllegalStateException("Please enter your Google Gemini API key in Settings or AI Studio Secrets panel.")
            }
            val model = if (currentSettings.geminiModel.isNotBlank()) currentSettings.geminiModel else "gemini-3.5-flash"
            aiService.generateWithGemini(effectiveKey, model, messages)
        }
    }

    suspend fun testProviderConnection(provider: String, key: String, model: String): Result<String> {
        return aiService.testConnection(provider, key, model)
    }

    private fun loadSettings(): AppSettings {
        val provider = prefs.getString("provider", "gemini") ?: "gemini"
        val geminiKey = prefs.getString("geminiKey", "") ?: ""
        val geminiModel = prefs.getString("geminiModel", "gemini-3.5-flash") ?: "gemini-3.5-flash"
        val openaiKey = prefs.getString("openaiKey", "") ?: ""
        val openaiModel = prefs.getString("openaiModel", "gpt-4o-mini") ?: "gpt-4o-mini"
        val isDarkTheme = prefs.getBoolean("isDarkTheme", true)

        return AppSettings(
            provider = provider,
            geminiKey = geminiKey,
            geminiModel = geminiModel,
            openaiKey = openaiKey,
            openaiModel = openaiModel,
            isDarkTheme = isDarkTheme
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString("provider", settings.provider)
            .putString("geminiKey", settings.geminiKey)
            .putString("geminiModel", settings.geminiModel)
            .putString("openaiKey", settings.openaiKey)
            .putString("openaiModel", settings.openaiModel)
            .putBoolean("isDarkTheme", settings.isDarkTheme)
            .apply()
        _settingsState.value = settings
    }
}

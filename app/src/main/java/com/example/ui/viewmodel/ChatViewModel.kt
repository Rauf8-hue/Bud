package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppSettings
import com.example.data.model.ConversationEntity
import com.example.data.model.MessageEntity
import com.example.data.repository.ChatRepository
import com.example.ui.components.OrbState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val currentConversationId: String? = null,
    val messages: List<MessageEntity> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingContent: String? = null,
    val isListening: Boolean = false,
    val orbState: OrbState = OrbState.IDLE,
    val statusText: String = "Ready to chat",
    val speakingMessageContent: String? = null,
    val errorMessage: String? = null,
    val showSettingsDialog: Boolean = false,
    val appSettings: AppSettings = AppSettings()
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null
    private var messagesJob: Job? = null

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        initTts(application)

        // Observe settings
        viewModelScope.launch {
            repository.settingsState.collectLatest { settings ->
                _uiState.update { it.copy(appSettings = settings) }
            }
        }

        // Observe conversations
        viewModelScope.launch {
            repository.conversations.collectLatest { convs ->
                _uiState.update { state ->
                    val activeId = if (state.currentConversationId != null &&
                        convs.any { it.id == state.currentConversationId }
                    ) {
                        state.currentConversationId
                    } else if (convs.isNotEmpty()) {
                        convs.first().id
                    } else {
                        null
                    }
                    state.copy(conversations = convs, currentConversationId = activeId)
                }

                val currentId = _uiState.value.currentConversationId
                if (currentId != null) {
                    observeMessagesForConversation(currentId)
                } else if (convs.isEmpty()) {
                    // Automatically create first chat
                    createNewChat()
                }
            }
        }
    }

    private fun initTts(context: Context) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        _uiState.update {
                            it.copy(
                                speakingMessageContent = null,
                                statusText = "Ready to chat"
                            )
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        _uiState.update {
                            it.copy(
                                speakingMessageContent = null,
                                statusText = "Ready to chat"
                            )
                        }
                    }
                })
                isTtsInitialized = true
            }
        }
    }

    fun selectConversation(conversationId: String) {
        stopSpeaking()
        _uiState.update {
            it.copy(
                currentConversationId = conversationId,
                errorMessage = null,
                statusText = "Ready to chat"
            )
        }
        observeMessagesForConversation(conversationId)
    }

    private fun observeMessagesForConversation(conversationId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessages(conversationId).collectLatest { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun createNewChat() {
        stopSpeaking()
        stopGeneration()
        viewModelScope.launch {
            val newConv = repository.createNewConversation("New Chat")
            _uiState.update {
                it.copy(
                    currentConversationId = newConv.id,
                    inputText = "",
                    statusText = "Ready to chat",
                    errorMessage = null
                )
            }
            observeMessagesForConversation(newConv.id)
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(id, newTitle)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }

    fun setInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onVoiceResult(spokenText: String) {
        if (spokenText.isBlank()) return
        _uiState.update { current ->
            val newText = if (current.inputText.isBlank()) {
                spokenText
            } else {
                "${current.inputText} $spokenText"
            }
            current.copy(
                inputText = newText,
                isListening = false,
                orbState = OrbState.IDLE,
                statusText = "Ready to chat"
            )
        }
    }

    fun setListeningState(listening: Boolean) {
        _uiState.update {
            it.copy(
                isListening = listening,
                orbState = if (listening) OrbState.LISTENING else if (it.isGenerating) OrbState.GENERATING else OrbState.IDLE,
                statusText = if (listening) "Listening..." else if (it.isGenerating) "Generating..." else "Ready to chat"
            )
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isGenerating) return

        var activeConvId = _uiState.value.currentConversationId
        _uiState.update {
            it.copy(
                inputText = "",
                isGenerating = true,
                streamingContent = null,
                orbState = OrbState.GENERATING,
                statusText = "Generating...",
                errorMessage = null
            )
        }

        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            try {
                if (activeConvId == null) {
                    val newConv = repository.createNewConversation("New Chat")
                    activeConvId = newConv.id
                    _uiState.update { it.copy(currentConversationId = newConv.id) }
                    observeMessagesForConversation(newConv.id)
                }

                // Insert user message
                repository.insertMessage(activeConvId!!, "user", text)
                repository.updateConversationTitleIfFirstMessage(activeConvId!!, text)

                // Build context history
                val history = repository.getRecentMessages(activeConvId!!)
                    .map { it.role to it.content }

                // Call AI Service with streaming callback
                val response = repository.requestAiResponse(activeConvId!!, history) { chunk ->
                    _uiState.update { current ->
                        val updated = (current.streamingContent ?: "") + chunk
                        current.copy(streamingContent = updated)
                    }
                }

                // Insert assistant response
                repository.insertMessage(activeConvId!!, "assistant", response)

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingContent = null,
                        orbState = OrbState.IDLE,
                        statusText = "Ready to chat"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingContent = null,
                        orbState = OrbState.IDLE,
                        statusText = "Error",
                        errorMessage = e.message ?: "An unexpected error occurred."
                    )
                }
            }
        }
    }

    fun regenerateLastResponse() {
        val activeConvId = _uiState.value.currentConversationId ?: return
        if (_uiState.value.isGenerating) return

        _uiState.update {
            it.copy(
                isGenerating = true,
                streamingContent = null,
                orbState = OrbState.GENERATING,
                statusText = "Regenerating...",
                errorMessage = null
            )
        }

        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            try {
                // Delete last assistant message
                repository.deleteLastAssistantMessage(activeConvId)

                // Retrieve updated message history
                val history = repository.getRecentMessages(activeConvId)
                    .map { it.role to it.content }

                if (history.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            streamingContent = null,
                            orbState = OrbState.IDLE,
                            statusText = "Ready to chat"
                        )
                    }
                    return@launch
                }

                val response = repository.requestAiResponse(activeConvId, history) { chunk ->
                    _uiState.update { current ->
                        val updated = (current.streamingContent ?: "") + chunk
                        current.copy(streamingContent = updated)
                    }
                }
                repository.insertMessage(activeConvId, "assistant", response)

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingContent = null,
                        orbState = OrbState.IDLE,
                        statusText = "Ready to chat"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingContent = null,
                        orbState = OrbState.IDLE,
                        statusText = "Error",
                        errorMessage = e.message ?: "Failed to regenerate response."
                    )
                }
            }
        }
    }

    fun stopGeneration() {
        currentJob?.cancel()
        currentJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                streamingContent = null,
                orbState = OrbState.IDLE,
                statusText = "Ready to chat"
            )
        }
    }

    fun speak(text: String) {
        if (!isTtsInitialized || text.isBlank()) return
        stopSpeaking()
        _uiState.update {
            it.copy(
                speakingMessageContent = text,
                statusText = "Speaking..."
            )
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "roxy_utterance")
    }

    fun stopSpeaking() {
        if (isTtsInitialized) {
            tts?.stop()
        }
        _uiState.update {
            it.copy(
                speakingMessageContent = null,
                statusText = if (it.statusText == "Speaking...") "Ready to chat" else it.statusText
            )
        }
    }

    fun toggleTheme() {
        val currentSettings = _uiState.value.appSettings
        val updated = currentSettings.copy(isDarkTheme = !currentSettings.isDarkTheme)
        repository.saveSettings(updated)
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun dismissSettings() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun saveSettings(newSettings: AppSettings) {
        repository.saveSettings(newSettings)
    }

    suspend fun testConnection(provider: String, key: String, model: String): Result<String> {
        return repository.testProviderConnection(provider, key, model)
    }

    override fun onCleared() {
        super.onCleared()
        stopSpeaking()
        tts?.shutdown()
        currentJob?.cancel()
        messagesJob?.cancel()
    }
}

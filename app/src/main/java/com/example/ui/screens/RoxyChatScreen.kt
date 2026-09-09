package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MessageEntity
import com.example.ui.components.ChatDrawerContent
import com.example.ui.components.GlowingOrb
import com.example.ui.components.MessageBubble
import com.example.ui.components.OrbState
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TypingIndicator
import com.example.ui.theme.RoxyGenerating
import com.example.ui.theme.RoxyListening
import com.example.ui.theme.RoxyReady
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoxyChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Voice recognition launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.onVoiceResult(spoken)
            }
        }
        viewModel.setListeningState(false)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setListeningState(true)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Roxy...")
            }
            try {
                speechRecognizerLauncher.launch(intent)
            } catch (e: Exception) {
                viewModel.setListeningState(false)
                Toast.makeText(context, "Speech recognition is not available on this device.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission is required for voice input.", Toast.LENGTH_SHORT).show()
        }
    }

    // Scroll to bottom when new messages arrive or while typing/streaming
    LaunchedEffect(uiState.messages.size, uiState.isGenerating, uiState.streamingContent) {
        val count = uiState.messages.size + (if (uiState.isGenerating) 1 else 0)
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawerContent(
                conversations = uiState.conversations,
                activeConversationId = uiState.currentConversationId,
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.createNewChat()
                    scope.launch { drawerState.close() }
                },
                onRenameConversation = { id, title ->
                    viewModel.renameConversation(id, title)
                },
                onDeleteConversation = { id ->
                    viewModel.deleteConversation(id)
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlowingOrb(
                                state = uiState.orbState,
                                sizeDp = 34.dp
                            )
                            Column {
                                Text(
                                    text = "Roxy AI",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    val statusDotColor = when (uiState.orbState) {
                                        OrbState.LISTENING -> RoxyListening
                                        OrbState.GENERATING -> RoxyGenerating
                                        OrbState.IDLE -> if (uiState.statusText == "Error") RoxyListening else RoxyReady
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(statusDotColor)
                                    )
                                    Text(
                                        text = uiState.statusText,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open conversations"
                            )
                        }
                    },
                    actions = {
                        // Quick New Chat
                        IconButton(
                            onClick = { viewModel.createNewChat() },
                            modifier = Modifier.testTag("top_new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Chat"
                            )
                        }

                        // Theme Toggle
                        IconButton(
                            onClick = { viewModel.toggleTheme() },
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.appSettings.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle theme"
                            )
                        }

                        // Settings
                        IconButton(
                            onClick = { viewModel.openSettings() },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                // Bottom input bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Voice button
                            IconButton(
                                onClick = {
                                    if (uiState.isListening) {
                                        viewModel.setListeningState(false)
                                    } else {
                                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.isListening) RoxyListening.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .testTag("voice_input_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = if (uiState.isListening) "Stop voice" else "Voice input",
                                    tint = if (uiState.isListening) RoxyListening else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Text input field
                            OutlinedTextField(
                                value = uiState.inputText,
                                onValueChange = { viewModel.setInputText(it) },
                                placeholder = {
                                    Text(
                                        text = "Message Roxy...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 15.sp
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("message_input_field"),
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = { viewModel.sendMessage() }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                )
                            )

                            // Send or Stop button
                            if (uiState.isGenerating) {
                                IconButton(
                                    onClick = { viewModel.stopGeneration() },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                        .testTag("stop_generation_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop generating",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                val canSend = uiState.inputText.isNotBlank()
                                IconButton(
                                    onClick = { viewModel.sendMessage() },
                                    enabled = canSend,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (canSend) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                        .testTag("send_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send message",
                                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (uiState.messages.isEmpty()) {
                    // Empty state hero welcome
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        GlowingOrb(
                            state = uiState.orbState,
                            sizeDp = 84.dp
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Hey there! I'm Roxy",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your witty, clever companion. Ask me anything, have me write code, brainstorm ideas, or just chat!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )

                        Spacer(modifier = Modifier.height(26.dp))

                        // Suggestion prompt chips
                        val suggestions = listOf(
                            "Explain quantum computing simply",
                            "Write a Kotlin Compose animation example",
                            "Tell me a fun, witty joke",
                            "Give me 3 creative business ideas"
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (suggestion in suggestions) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            viewModel.setInputText(suggestion)
                                            viewModel.sendMessage()
                                        }
                                ) {
                                    Text(
                                        text = suggestion,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Messages list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(
                            uiState.messages,
                            key = { _, msg -> msg.id }
                        ) { index, msg ->
                            val isLast = index == uiState.messages.size - 1
                            MessageBubble(
                                message = msg,
                                isLastMessage = isLast,
                                isGenerating = uiState.isGenerating,
                                isSpeakingThis = uiState.speakingMessageContent == msg.content,
                                isDarkTheme = uiState.appSettings.isDarkTheme,
                                onSpeak = { viewModel.speak(it) },
                                onStopSpeaking = { viewModel.stopSpeaking() },
                                onRegenerate = { viewModel.regenerateLastResponse() }
                            )
                        }

                        // Typing indicator or real-time streaming bubble
                        if (uiState.isGenerating) {
                            val streaming = uiState.streamingContent
                            if (!streaming.isNullOrBlank()) {
                                item(key = "streaming_bubble") {
                                    MessageBubble(
                                        message = MessageEntity(
                                            id = -999L,
                                            conversationId = uiState.currentConversationId ?: "",
                                            role = "assistant",
                                            content = streaming,
                                            timestamp = System.currentTimeMillis()
                                        ),
                                        isLastMessage = true,
                                        isGenerating = true,
                                        isSpeakingThis = false,
                                        isDarkTheme = uiState.appSettings.isDarkTheme,
                                        onSpeak = {},
                                        onStopSpeaking = {},
                                        onRegenerate = {}
                                    )
                                }
                            } else {
                                item(key = "typing_indicator") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        TypingIndicator()
                                    }
                                }
                            }
                        }
                    }
                }

                // Error Banner at top if any
                AnimatedVisibility(
                    visible = uiState.errorMessage != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                ) {
                    uiState.errorMessage?.let { errorMsg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            tonalElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openSettings() }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = errorMsg,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Settings",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Settings Modal Sheet
    if (uiState.showSettingsDialog) {
        SettingsDialog(
            currentSettings = uiState.appSettings,
            onSaveSettings = { viewModel.saveSettings(it) },
            onTestConnection = { provider, key, model ->
                viewModel.testConnection(provider, key, model)
            },
            onDismiss = { viewModel.dismissSettings() }
        )
    }
}

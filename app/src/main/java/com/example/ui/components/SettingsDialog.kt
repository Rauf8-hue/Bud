package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentSettings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit,
    onTestConnection: suspend (provider: String, key: String, model: String) -> Result<String>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var selectedProvider by remember { mutableStateOf(currentSettings.provider) }
    var geminiKey by remember { mutableStateOf(currentSettings.geminiKey) }
    var geminiModel by remember { mutableStateOf(currentSettings.geminiModel) }
    var openaiKey by remember { mutableStateOf(currentSettings.openaiKey) }
    var openaiModel by remember { mutableStateOf(currentSettings.openaiModel) }

    var isGeminiKeyVisible by remember { mutableStateOf(false) }
    var isOpenaiKeyVisible by remember { mutableStateOf(false) }

    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("settings_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Provider Switcher Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isGemini = selectedProvider.equals("gemini", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isGemini) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .clickable {
                            selectedProvider = "gemini"
                            testStatusMessage = null
                            isTestSuccess = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Google Gemini",
                        fontWeight = if (isGemini) FontWeight.Bold else FontWeight.Normal,
                        color = if (isGemini) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }

                val isOpenAi = selectedProvider.equals("openai", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isOpenAi) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .clickable {
                            selectedProvider = "openai"
                            testStatusMessage = null
                            isTestSuccess = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OpenAI",
                        fontWeight = if (isOpenAi) FontWeight.Bold else FontWeight.Normal,
                        color = if (isOpenAi) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tab Content
            if (selectedProvider.equals("gemini", ignoreCase = true)) {
                // Google Gemini Settings
                Text(
                    text = "API Key",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                val hasBuildConfigKey = BuildConfig.GEMINI_API_KEY.isNotBlank() &&
                        BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"

                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    placeholder = {
                        Text(
                            if (hasBuildConfigKey) "Preconfigured via Secrets panel" else "AIzaSy..."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_api_key_input"),
                    visualTransformation = if (isGeminiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isGeminiKeyVisible = !isGeminiKeyVisible }) {
                            Icon(
                                imageVector = if (isGeminiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle key visibility"
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Model",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = geminiModel,
                    onValueChange = { geminiModel = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_model_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Quick model chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val models = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview")
                    for (m in models) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (geminiModel == m) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { geminiModel = m }
                        ) {
                            Text(
                                text = m,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = if (geminiModel == m) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Test / Clear Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testStatusMessage = "Testing connection..."
                                isTestSuccess = null
                                val effectiveKey = when {
                                    geminiKey.isNotBlank() -> geminiKey.trim()
                                    hasBuildConfigKey -> BuildConfig.GEMINI_API_KEY
                                    else -> ""
                                }
                                val result = onTestConnection("gemini", effectiveKey, geminiModel)
                                isTesting = false
                                result.onSuccess {
                                    testStatusMessage = "Connection successful!"
                                    isTestSuccess = true
                                }.onFailure { error ->
                                    testStatusMessage = error.message ?: "Connection failed"
                                    isTestSuccess = false
                                }
                            }
                        },
                        enabled = !isTesting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Test")
                    }

                    OutlinedButton(
                        onClick = {
                            geminiKey = ""
                            testStatusMessage = "Key cleared"
                            isTestSuccess = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear Key")
                    }
                }
            } else {
                // OpenAI Settings
                Text(
                    text = "API Key",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = openaiKey,
                    onValueChange = { openaiKey = it },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("openai_api_key_input"),
                    visualTransformation = if (isOpenaiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isOpenaiKeyVisible = !isOpenaiKeyVisible }) {
                            Icon(
                                imageVector = if (isOpenaiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle key visibility"
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Model",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = openaiModel,
                    onValueChange = { openaiModel = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("openai_model_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Test / Clear Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testStatusMessage = "Testing connection..."
                                isTestSuccess = null
                                val result = onTestConnection("openai", openaiKey.trim(), openaiModel)
                                isTesting = false
                                result.onSuccess {
                                    testStatusMessage = "Connection successful!"
                                    isTestSuccess = true
                                }.onFailure { error ->
                                    testStatusMessage = error.message ?: "Connection failed"
                                    isTestSuccess = false
                                }
                            }
                        },
                        enabled = !isTesting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Test")
                    }

                    OutlinedButton(
                        onClick = {
                            openaiKey = ""
                            testStatusMessage = "Key cleared"
                            isTestSuccess = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear Key")
                    }
                }
            }

            // Status message feedback
            AnimatedVisibility(visible = testStatusMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when (isTestSuccess) {
                        true -> Color(0x225EFF9A)
                        false -> Color(0x22FF5E7A)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (isTestSuccess) {
                            true -> Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF5EFF9A),
                                modifier = Modifier.size(18.dp)
                            )
                            false -> Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFFF5E7A),
                                modifier = Modifier.size(18.dp)
                            )
                            else -> {}
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = testStatusMessage ?: "",
                            fontSize = 13.sp,
                            color = when (isTestSuccess) {
                                true -> Color(0xFF5EFF9A)
                                false -> Color(0xFFFF5E7A)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Settings Button
            Button(
                onClick = {
                    val newSettings = currentSettings.copy(
                        provider = selectedProvider,
                        geminiKey = geminiKey.trim(),
                        geminiModel = geminiModel.trim(),
                        openaiKey = openaiKey.trim(),
                        openaiModel = openaiModel.trim()
                    )
                    onSaveSettings(newSettings)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_settings_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Save Settings",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Browser/Client-side API keys are stored locally on device. In production, connect through a secure backend proxy.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MessageEntity
import com.example.ui.theme.DarkAiBubble
import com.example.ui.theme.DarkUserBubble
import com.example.ui.theme.LightAiBubble
import com.example.ui.theme.LightUserBubble
import com.example.ui.theme.RoxyCodeBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class FormattedBlock {
    data class TextBlock(val text: String) : FormattedBlock()
    data class CodeBlock(val language: String, val code: String) : FormattedBlock()
}

fun parseMessageContent(raw: String): List<FormattedBlock> {
    val blocks = mutableListOf<FormattedBlock>()
    val codeFenceRegex = Regex("```(\\w*)\\n?([\\s\\S]*?)```")
    var lastIndex = 0

    codeFenceRegex.findAll(raw).forEach { matchResult ->
        val range = matchResult.range
        if (range.first > lastIndex) {
            val textBefore = raw.substring(lastIndex, range.first)
            if (textBefore.isNotBlank()) {
                blocks.add(FormattedBlock.TextBlock(textBefore.trim()))
            }
        }
        val lang = matchResult.groupValues[1]
        val code = matchResult.groupValues[2].trimEnd()
        blocks.add(FormattedBlock.CodeBlock(lang, code))
        lastIndex = range.last + 1
    }

    if (lastIndex < raw.length) {
        val remaining = raw.substring(lastIndex).trim()
        if (remaining.isNotBlank()) {
            blocks.add(FormattedBlock.TextBlock(remaining))
        }
    }

    if (blocks.isEmpty()) {
        blocks.add(FormattedBlock.TextBlock(raw))
    }
    return blocks
}

@Composable
fun buildFormattedText(text: String, isDark: Boolean): AnnotatedString {
    val inlineCodeColor = if (isDark) Color(0xFF2E2A4D) else Color(0xFFE4DFFA)
    val inlineTextColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)

    return buildAnnotatedString {
        var cursor = 0
        // Parse bold **text**, italics *text*, and inline `code`
        val tokenRegex = Regex("(`[^`]+`)|(\\*\\*[^*]+\\*\\*)|(\\*[^*]+\\*)")
        tokenRegex.findAll(text).forEach { match ->
            val range = match.range
            if (range.first > cursor) {
                append(text.substring(cursor, range.first))
            }
            val matchValue = match.value
            when {
                matchValue.startsWith("`") && matchValue.endsWith("`") -> {
                    val code = matchValue.removeSurrounding("`")
                    val start = length
                    append(" $code ")
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeColor,
                            color = inlineTextColor,
                            fontSize = 13.5.sp
                        ),
                        start,
                        length
                    )
                }
                matchValue.startsWith("**") && matchValue.endsWith("**") -> {
                    val boldText = matchValue.removeSurrounding("**")
                    val start = length
                    append(boldText)
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold),
                        start,
                        length
                    )
                }
                matchValue.startsWith("*") && matchValue.endsWith("*") -> {
                    val italicText = matchValue.removeSurrounding("*")
                    val start = length
                    append(italicText)
                    addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic),
                        start,
                        length
                    )
                }
            }
            cursor = range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isLastMessage: Boolean,
    isGenerating: Boolean,
    isSpeakingThis: Boolean,
    isDarkTheme: Boolean,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bubbleShape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    }

    val bubbleColor = if (isUser) {
        if (isDarkTheme) DarkUserBubble else LightUserBubble
    } else {
        if (isDarkTheme) DarkAiBubble else LightAiBubble
    }

    val textColor = if (isUser) {
        if (isDarkTheme) Color.White else Color(0xFF1A1A2E)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val blocks = remember(message.content) { parseMessageContent(message.content) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .then(
                    if (!isUser) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = bubbleShape
                        )
                    } else Modifier
                )
                .padding(14.dp)
                .testTag(if (isUser) "user_message_bubble" else "ai_message_bubble")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (block in blocks) {
                    when (block) {
                        is FormattedBlock.TextBlock -> {
                            val annotated = buildFormattedText(block.text, isDarkTheme)
                            Text(
                                text = annotated,
                                color = textColor,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }
                        is FormattedBlock.CodeBlock -> {
                            CodeBlockView(
                                language = block.language,
                                code = block.code,
                                isDarkTheme = isDarkTheme
                            )
                        }
                    }
                }
            }
        }

        // Action buttons under AI response
        if (!isUser) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            ) {
                // Copy Action
                var copyClicked by remember { mutableStateOf(false) }
                AssistChip(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.content))
                        copyClicked = true
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(1500)
                            copyClicked = false
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (copyClicked) "Copied!" else "Copy",
                            fontSize = 11.sp
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null,
                    shape = RoundedCornerShape(12.dp)
                )

                // Speak Action
                AssistChip(
                    onClick = {
                        if (isSpeakingThis) {
                            onStopSpeaking()
                        } else {
                            onSpeak(message.content)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isSpeakingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isSpeakingThis) "Stop reading" else "Speak response",
                            tint = if (isSpeakingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (isSpeakingThis) "Stop" else "Speak",
                            fontSize = 11.sp,
                            color = if (isSpeakingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null,
                    shape = RoundedCornerShape(12.dp)
                )

                // Regenerate Action (only available for the latest message if not generating)
                if (isLastMessage && !isGenerating) {
                    AssistChip(
                        onClick = onRegenerate,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate answer",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = { Text("Regenerate", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CodeBlockView(
    language: String,
    code: String,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = RoxyCodeBackground,
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Color(0x22FFFFFF),
                RoundedCornerShape(10.dp)
            )
            .testTag("code_block")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFA0A0B8)
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        isCopied = true
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(1500)
                            isCopied = false
                        }
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isCopied) Color(0xFF5EFF9A) else Color(0xFFA0A0B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Color(0xFFECEFF4),
                lineHeight = 18.sp
            )
        }
    }
}

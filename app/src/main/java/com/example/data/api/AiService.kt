package com.example.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

const val DEFAULT_SYSTEM_PROMPT =
    "You are Roxy, a witty, warm, and intelligent AI assistant with a playful personality. " +
    "You're like a supportive and clever girlfriend who always has your back. " +
    "You can answer questions on any topic, explain complex ideas simply, write and debug code, " +
    "help with writing and creative projects, and have natural conversations with humor and empathy. " +
    "Be concise when needed and thorough when it matters. Never pretend a simulated answer is real. " +
    "If you don't know something, admit it honestly. You're friendly, a bit flirty in a tasteful way, " +
    "and always respectful. Use occasional emojis, but don't overdo them."

class AiService {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateWithGemini(
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>> // role ("user" or "assistant"), content
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Please provide a valid Gemini API key in Settings.")
        }
        val effectiveModel = if (model.isBlank()) "gemini-3.5-flash" else model.trim()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$apiKey"

        val contentsArray = JSONArray()
        // Send recent conversation turns
        val recentMessages = messages.takeLast(20)
        for ((role, content) in recentMessages) {
            val geminiRole = if (role == "assistant") "model" else "user"
            val partsArray = JSONArray().apply {
                put(JSONObject().apply { put("text", content) })
            }
            contentsArray.put(JSONObject().apply {
                put("role", geminiRole)
                put("parts", partsArray)
            })
        }

        val requestBodyJson = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", DEFAULT_SYSTEM_PROMPT) })
                })
            })
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.8)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errorObj = JSONObject(responseBody).optJSONObject("error")
                    errorObj?.optString("message") ?: "HTTP error ${response.code}"
                } catch (e: Exception) {
                    "HTTP error ${response.code}: ${response.message}"
                }
                throw IOException("Gemini error: $errorMsg")
            }

            try {
                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val stringBuilder = StringBuilder()
                        for (i in 0 until parts.length()) {
                            stringBuilder.append(parts.getJSONObject(i).optString("text", ""))
                        }
                        return@withContext stringBuilder.toString().ifBlank { "No response received." }
                    }
                }
                "No content returned from Gemini."
            } catch (e: Exception) {
                throw IOException("Failed to parse Gemini response: ${e.message}")
            }
        }
    }

    suspend fun generateWithOpenAi(
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Please provide a valid OpenAI API key in Settings.")
        }
        val effectiveModel = if (model.isBlank()) "gpt-4o-mini" else model.trim()
        val url = "https://api.openai.com/v1/chat/completions"

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", DEFAULT_SYSTEM_PROMPT)
            })
        }

        val recentMessages = messages.takeLast(20)
        for ((role, content) in recentMessages) {
            messagesArray.put(JSONObject().apply {
                put("role", if (role == "assistant") "assistant" else "user")
                put("content", content)
            })
        }

        val requestBodyJson = JSONObject().apply {
            put("model", effectiveModel)
            put("messages", messagesArray)
            put("temperature", 0.8)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errorObj = JSONObject(responseBody).optJSONObject("error")
                    errorObj?.optString("message") ?: "HTTP error ${response.code}"
                } catch (e: Exception) {
                    "HTTP error ${response.code}: ${response.message}"
                }
                throw IOException("OpenAI error: $errorMsg")
            }

            try {
                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val message = firstChoice.optJSONObject("message")
                    return@withContext message?.optString("content", "") ?: "No response received."
                }
                "No choices returned from OpenAI."
            } catch (e: Exception) {
                throw IOException("Failed to parse OpenAI response: ${e.message}")
            }
        }
    }

    suspend fun generateWithLongCat(
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>,
        onChunk: ((String) -> Unit)? = null,
        stream: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Please provide a valid LongCat API key in Settings.")
        }
        val effectiveModel = if (model.isBlank()) "LongCat-2.0" else model.trim()
        val url = "https://api.longcat.chat/openai/v1/chat/completions"

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", DEFAULT_SYSTEM_PROMPT)
            })
        }

        val recentMessages = messages.takeLast(20)
        for ((role, content) in recentMessages) {
            messagesArray.put(JSONObject().apply {
                put("role", if (role == "assistant") "assistant" else "user")
                put("content", content)
            })
        }

        val useStreaming = stream && onChunk != null

        val requestBodyJson = JSONObject().apply {
            put("model", effectiveModel)
            put("messages", messagesArray)
            put("temperature", 0.8)
            put("stream", useStreaming)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val serverMsg = try {
                    val errorObj = JSONObject(responseBody).optJSONObject("error")
                    errorObj?.optString("message") ?: ""
                } catch (e: Exception) {
                    ""
                }

                val errorMsg = when {
                    serverMsg.isNotBlank() -> serverMsg
                    response.code == 401 -> "Invalid LongCat API key. Please check your key in Settings."
                    response.code == 402 -> "LongCat account has insufficient token quota."
                    response.code == 429 -> "LongCat rate limit exceeded. Please wait a moment and try again."
                    response.code in 500..599 -> "LongCat server is temporarily unavailable (${response.code})."
                    else -> "HTTP error ${response.code}: ${response.message}"
                }
                throw IOException("LongCat error: $errorMsg")
            }

            if (useStreaming) {
                val fullResponse = StringBuilder()
                val source = response.body?.source() ?: throw IOException("LongCat streaming response body was empty.")
                val reader = source.inputStream().bufferedReader(Charsets.UTF_8)

                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                        if (trimmed == "data: [DONE]") break

                        if (trimmed.startsWith("data: ")) {
                            val jsonStr = trimmed.substring(6).trim()
                            if (jsonStr == "[DONE]") break
                            try {
                                val chunkJson = JSONObject(jsonStr)
                                val choices = chunkJson.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val delta = choice.optJSONObject("delta")
                                    val contentChunk = delta?.optString("content", "") ?: ""
                                    if (contentChunk.isNotEmpty()) {
                                        fullResponse.append(contentChunk)
                                        withContext(Dispatchers.Main) {
                                            onChunk?.invoke(contentChunk)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Skip unparseable chunk
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (fullResponse.isEmpty()) {
                        throw IOException("Failed while streaming from LongCat: ${e.message}")
                    }
                }

                return@withContext fullResponse.toString().ifBlank { "No content returned from LongCat." }
            } else {
                val responseBody = response.body?.string() ?: ""
                try {
                    val json = JSONObject(responseBody)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val firstChoice = choices.getJSONObject(0)
                        val message = firstChoice.optJSONObject("message")
                        return@withContext message?.optString("content", "") ?: "No response received."
                    }
                    "No choices returned from LongCat."
                } catch (e: Exception) {
                    throw IOException("Failed to parse LongCat response: ${e.message}")
                }
            }
        }
    }

    suspend fun testConnection(
        provider: String,
        apiKey: String,
        model: String
    ): Result<String> = runCatching {
        when (provider.lowercase()) {
            "openai" -> generateWithOpenAi(apiKey, model, listOf("user" to "Say hello!"))
            "longcat" -> generateWithLongCat(apiKey, model, listOf("user" to "Say hello!"), stream = false)
            else -> generateWithGemini(apiKey, model, listOf("user" to "Say hello!"))
        }
    }
}

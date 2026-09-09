package com.example.data.model

data class AppSettings(
    val provider: String = "gemini", // "gemini" or "openai"
    val geminiKey: String = "",
    val geminiModel: String = "gemini-3.5-flash",
    val openaiKey: String = "",
    val openaiModel: String = "gpt-4o-mini",
    val isDarkTheme: Boolean = true
)

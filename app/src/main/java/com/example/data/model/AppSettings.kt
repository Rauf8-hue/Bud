package com.example.data.model

data class AppSettings(
    val provider: String = "gemini", // "gemini", "openai", or "longcat"
    val geminiKey: String = "",
    val geminiModel: String = "gemini-3.5-flash",
    val openaiKey: String = "",
    val openaiModel: String = "gpt-4o-mini",
    val longcatKey: String = "",
    val longcatModel: String = "LongCat-2.0",
    val isDarkTheme: Boolean = true
)

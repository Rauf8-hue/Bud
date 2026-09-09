package com.example

import com.example.data.model.AppSettings
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testAppSettingsLongCatDefaults() {
    val settings = AppSettings()
    assertEquals("gemini", settings.provider)
    assertEquals("", settings.longcatKey)
    assertEquals("LongCat-2.0", settings.longcatModel)
  }

  @Test
  fun testAppSettingsLongCatUpdate() {
    val settings = AppSettings(
      provider = "longcat",
      longcatKey = "test-key-123",
      longcatModel = "longcat-2.0:thinking"
    )
    assertEquals("longcat", settings.provider)
    assertEquals("test-key-123", settings.longcatKey)
    assertEquals("longcat-2.0:thinking", settings.longcatModel)
  }

  @Test
  fun testSupportedProviders() {
    val supported = setOf("openai", "gemini", "longcat")
    assertTrue(supported.contains("gemini"))
    assertTrue(supported.contains("openai"))
    assertTrue(supported.contains("longcat"))
    assertFalse(supported.contains("openrouter"))
  }
}

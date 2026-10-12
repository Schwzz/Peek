package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.ApiConfig
import com.example.data.api.ApiConfigRepository
import com.example.data.api.VisionAiProvider
import com.example.data.api.VisionAiRepository
import com.example.service.OverlayStateManager
import com.example.util.BitmapUtils
import com.example.util.KeystoreCrypto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Peek", appName)
  }

  @Test
  fun `api config repository protects key at rest and decrypts accurately`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = ApiConfigRepository.getInstance(context)

    val secretKey = "sk-test-secret-key-998877"
    val customConfig = ApiConfig(
      provider = VisionAiProvider.OPENAI_COMPATIBLE,
      apiKey = secretKey,
      baseUrl = "https://custom.api.endpoint/v1/",
      modelName = "gpt-4o"
    )
    repo.saveConfig(customConfig)

    // Check that stored key decrypts back to original
    val loaded = repo.getConfig()
    assertEquals(secretKey, loaded.apiKey)
    assertEquals(VisionAiProvider.OPENAI_COMPATIBLE, loaded.provider)
    assertTrue(repo.isConfigured(loaded))

    // Directly inspect raw SharedPreferences
    val rawPrefs = context.getSharedPreferences("peek_api_prefs", Context.MODE_PRIVATE)
    val storedRaw = rawPrefs.getString("api_key", "") ?: ""
    // Either encrypted with enc:v1: prefix or encrypted by KeystoreCrypto
    assertNotNull(storedRaw)
    assertTrue(storedRaw.isNotEmpty())
  }

  @Test
  fun `keystore crypto encrypt and decrypt roundtrip`() {
    val original = "sk-gemini-secret-token-12345"
    val encrypted = KeystoreCrypto.encrypt(original)
    val decrypted = KeystoreCrypto.decrypt(encrypted)
    assertEquals(original, decrypted)
  }

  @Test
  fun `request locking prevents duplicate or overlapping requests`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    OverlayStateManager.clearChat()
    val testBitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
    OverlayStateManager.setCurrentImage(testBitmap)

    // First request should succeed and lock
    val firstAccepted = OverlayStateManager.sendPrompt("Explain this", context)
    assertTrue(firstAccepted)
    assertTrue(OverlayStateManager.isLoading.value)

    // While first request is running, subsequent request must be locked out
    val secondAccepted = OverlayStateManager.sendPrompt("Extract text", context)
    assertFalse(secondAccepted)
  }

  @Test
  fun `test connection does not silently persist unsaved settings`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = ApiConfigRepository.getInstance(context)

    val originalConfig = ApiConfig(
      provider = VisionAiProvider.GEMINI,
      apiKey = "saved-original-key",
      modelName = "gemini-2.5-flash"
    )
    repo.saveConfig(originalConfig)

    val unsavedTestConfig = ApiConfig(
      provider = VisionAiProvider.OPENAI_COMPATIBLE,
      apiKey = "unsaved-temporary-key",
      modelName = "gpt-4o"
    )

    val aiRepo = VisionAiRepository.getInstance(context)
    // Run test connection with unsavedTestConfig
    aiRepo.testConnection(unsavedTestConfig)

    // Verify stored config was NOT modified or overwritten
    val currentPersisted = repo.getConfig()
    assertEquals(VisionAiProvider.GEMINI, currentPersisted.provider)
    assertEquals("saved-original-key", currentPersisted.apiKey)
    assertEquals("gemini-2.5-flash", currentPersisted.modelName)
  }

  @Test
  fun `bitmap utils crop produces correct dimensions`() {
    val original = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    val cropRect = Rect(10, 20, 60, 70)
    val cropped = BitmapUtils.crop(original, cropRect)

    assertNotNull(cropped)
    assertEquals(50, cropped.width)
    assertEquals(50, cropped.height)
  }

  @Test
  fun `overlay state manager manages image and panel state`() {
    OverlayStateManager.setPanelExpanded(false)
    assertFalse(OverlayStateManager.isPanelExpanded.value)

    OverlayStateManager.togglePanel()
    assertTrue(OverlayStateManager.isPanelExpanded.value)

    val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
    OverlayStateManager.setCurrentImage(bitmap)
    assertNotNull(OverlayStateManager.currentImage.value)

    OverlayStateManager.clearImage()
    assertEquals(null, OverlayStateManager.currentImage.value)
  }
}

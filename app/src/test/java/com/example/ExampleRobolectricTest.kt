package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.ApiConfig
import com.example.data.api.ApiConfigRepository
import com.example.data.api.VisionAiProvider
import com.example.service.OverlayStateManager
import com.example.util.BitmapUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun `api config repository saves and loads config`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = ApiConfigRepository.getInstance(context)

    val customConfig = ApiConfig(
      provider = VisionAiProvider.OPENAI_COMPATIBLE,
      apiKey = "sk-test-key-12345",
      baseUrl = "https://custom.api.endpoint/v1/",
      modelName = "gpt-4o"
    )
    repo.saveConfig(customConfig)

    val loaded = repo.getConfig()
    assertEquals(VisionAiProvider.OPENAI_COMPATIBLE, loaded.provider)
    assertEquals("sk-test-key-12345", loaded.apiKey)
    assertEquals("https://custom.api.endpoint/v1/", loaded.baseUrl)
    assertEquals("gpt-4o", loaded.modelName)
    assertTrue(repo.isConfigured(loaded))
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

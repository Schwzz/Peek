package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.service.OverlayStateManager
import com.example.ui.components.MarkdownText
import com.example.ui.components.OverlayPanelView
import com.example.ui.components.ScreenSelectorView
import com.example.ui.screens.MainScreen
import com.example.ui.theme.PeekTheme
import java.io.File
import org.junit.*
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiReworkTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Before fun reset() {
        OverlayStateManager.clearChat()
        OverlayStateManager.clearImage()
        OverlayStateManager.setServiceRunning(false)
    }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        val file = File("build/reports/ui/$name.png").apply { parentFile.mkdirs() }
        // Draw on the UI thread: Robolectric has no live compositor for PixelCopy.
        ui.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.width > 0 }
                ?: ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun homeAndChatPreserveUnsentDraft() {
        ui.setContent { PeekTheme { MainScreen(onOpenSettingsRequested = {}) } }
        ui.onNodeWithTag("open_chat_button").assertIsDisplayed()
        screenshot("home")
        ui.onNodeWithTag("open_chat_button").performClick()
        ui.onNodeWithTag("overlay_prompt_input").performTextInput("Keep this draft")
        screenshot("chat")
        ui.onNodeWithContentDescription("Back to home").performClick()
        ui.onNodeWithTag("open_chat_button").performClick()
        ui.onNodeWithTag("overlay_prompt_input").assertTextContains("Keep this draft")
    }

    @Test fun modelSettingsKeepLocalAndCloudDraftsSeparate() {
        val repo = com.example.data.api.ApiConfigRepository.getInstance(ui.activity)
        repo.saveConfig(com.example.data.api.ApiConfig(modelName = "cloud-test"))
        ui.setContent { PeekTheme { com.example.ui.screens.SettingsDialog(onDismiss = {}) } }
        screenshot("model-settings")
        ui.onNodeWithTag("provider_picker").performClick()
        ui.onNodeWithTag("provider_ollama_tab").performScrollTo().performClick()
        ui.onNodeWithTag("api_key_input").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        ui.onNodeWithTag("base_url_input").performTextReplacement("http://192.168.1.8:11434/")
        ui.onNodeWithTag("model_name_input").performScrollTo().performTextReplacement("local-vision")
        screenshot("local-model-settings")
        ui.onNodeWithTag("save_settings_button").performClick()
        ui.runOnIdle {
            Assert.assertEquals(com.example.data.api.VisionAiProvider.OLLAMA, repo.getConfig().provider)
            Assert.assertEquals("local-vision", repo.getConfig().modelName)
            Assert.assertEquals("cloud-test", repo.getConfig(com.example.data.api.VisionAiProvider.GEMINI).modelName)
        }
    }

    @Test fun darkThemeKeepsTheToolkitReadable() {
        ui.setContent { PeekTheme(darkTheme = true) { OverlayPanelView(
            modifier = Modifier.fillMaxSize(), onCaptureScreenRequested = {}, onPickImageRequested = {},
            onMinimizeRequested = {}, onCloseRequested = {}, onOpenSettingsRequested = {}
        ) } }
        ui.onNodeWithTag("overlay_model_picker").assertIsDisplayed()
        ui.onNodeWithTag("overlay_prompt_input").assertIsDisplayed()
        screenshot("dark-toolkit")
    }

    @Test fun markdownRendersHeadingsAndCodeAsReadableText() {
        ui.setContent { PeekTheme { Surface(Modifier.fillMaxSize()) { androidx.compose.foundation.layout.Box(Modifier.padding(20.dp)) {
            MarkdownText("# A clear answer\n\nThis is **important**.\n\n```python\nprint(42)\n```")
        } } } }
        ui.onNodeWithText("A clear answer").assertIsDisplayed()
        ui.onNodeWithText("This is important.").assertIsDisplayed()
        ui.onNodeWithText("print(42)").assertIsDisplayed()
        screenshot("markdown")
    }

    @Test fun fullImageSelectionUsesOriginalAspectRatio() {
        val image = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888)
        var result: android.graphics.Rect? = null
        ui.setContent { PeekTheme { ScreenSelectorView(image, { result = it }, {}) } }
        ui.onNodeWithTag("select_fullscreen_button").performClick()
        screenshot("crop")
        ui.onNodeWithTag("confirm_crop_button").performClick()
        Assert.assertEquals(android.graphics.Rect(0, 0, 1000, 500), result)
    }

    @Test fun overlayHandlesDeliverMovementAndResizeGestures() {
        var movement = 0f
        var resized = 0f
        ui.setContent { PeekTheme { OverlayPanelView(
            modifier = Modifier.fillMaxSize(), onCaptureScreenRequested = {}, onPickImageRequested = {},
            onMinimizeRequested = {}, onCloseRequested = {}, onOpenSettingsRequested = {},
            onMove = { x, _ -> movement += x }, onResize = { x, _ -> resized += x }
        ) } }
        ui.onNodeWithTag("overlay_drag_handle").performTouchInput { swipeRight() }
        ui.onNodeWithTag("overlay_resize_handle").performTouchInput { swipeLeft() }
        ui.runOnIdle { Assert.assertTrue(movement > 0f); Assert.assertTrue(resized < 0f) }
        screenshot("overlay")
    }

    @Test fun smallOverlayKeepsTheComposerVisible() {
        ui.setContent { PeekTheme { androidx.compose.foundation.layout.Box(Modifier.height(200.dp)) {
            OverlayPanelView(
                modifier = Modifier.fillMaxSize(), onCaptureScreenRequested = {}, onPickImageRequested = {},
                onMinimizeRequested = {}, onCloseRequested = {}, onOpenSettingsRequested = {}
            )
        } } }
        ui.onNodeWithTag("overlay_prompt_input").assertIsDisplayed()
        ui.onNodeWithTag("overlay_send_button").assertIsDisplayed()
        screenshot("compact-overlay")
    }
}

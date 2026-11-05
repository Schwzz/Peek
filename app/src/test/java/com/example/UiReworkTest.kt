package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
        OverlayStateManager.finishCapture(showPanel = false)
        OverlayStateManager.setToolboxExpanded(false)
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

    @Test fun toolboxActionsStayAlignedAndOnlyRunWhenChosen() {
        var selected = 0; var images = 0; var chats = 0; var stops = 0
        ui.setContent { PeekTheme { androidx.compose.foundation.layout.Box(Modifier.width(88.dp)) {
            com.example.ui.components.OverlayToolboxView({ selected++ }, { images++ }, { chats++ }, { stops++ })
        } } }
        val select = ui.onNodeWithTag("toolbox_select").fetchSemanticsNode().boundsInRoot
        val image = ui.onNodeWithTag("toolbox_image").fetchSemanticsNode().boundsInRoot
        val chat = ui.onNodeWithTag("toolbox_chat").fetchSemanticsNode().boundsInRoot
        Assert.assertEquals(select.left, image.left, 0.1f)
        Assert.assertTrue(image.top >= select.bottom)
        Assert.assertEquals(select.height, chat.height, 0.1f)
        Assert.assertEquals(select.width, image.width, 1f)
        ui.onNodeWithText("Peek").assertDoesNotExist()
        ui.onNodeWithContentDescription("Toolbox settings").assertDoesNotExist()
        Assert.assertEquals(0, selected)
        ui.onNodeWithTag("toolbox_chat").performClick()
        ui.runOnIdle { Assert.assertEquals(1, chats); Assert.assertEquals(0, selected); Assert.assertEquals(0, images) }
        ui.onNodeWithTag("toolbox_image").performClick()
        ui.onNodeWithTag("toolbox_select").performClick()
        ui.runOnIdle { Assert.assertEquals(1, images); Assert.assertEquals(1, selected) }
        ui.onNodeWithTag("toolbox_stop").performClick()
        ui.runOnIdle { Assert.assertEquals(1, stops) }
        screenshot("toolbox")
    }

    @Test fun multipleAttachmentsCanBePreviewedCroppedAndRemovedIndividually() {
        val first = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val second = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        OverlayStateManager.appendImage(first); OverlayStateManager.appendImage(second)
        var crops = 0
        ui.setContent { PeekTheme { androidx.compose.foundation.layout.Box(Modifier.width(320.dp).height(440.dp)) {
            OverlayPanelView(modifier = Modifier.fillMaxSize(), onCaptureScreenRequested = {}, onPickImageRequested = {},
                onMinimizeRequested = {}, onCloseRequested = {}, onOpenSettingsRequested = {}, onCropImageRequested = { crops++ })
        } } }
        ui.onNodeWithTag("attachment_0").assertIsDisplayed()
        ui.onNodeWithTag("attachment_1").assertIsDisplayed()
        ui.onNodeWithText("2/6").assertIsDisplayed()
        ui.onNodeWithText("Resize").assertIsDisplayed()
        screenshot("multiple-attachments")
        ui.onNodeWithTag("attachment_0").performClick()
        ui.runOnIdle { Assert.assertSame(first, OverlayStateManager.currentImage.value) }
        ui.onNodeWithTag("image_preview_back").performClick()
        ui.onNodeWithTag("overlay_recrop_button").performClick()
        ui.runOnIdle { Assert.assertEquals(1, crops) }
        ui.onNodeWithTag("remove_attachment_0").performClick()
        ui.onNodeWithText("1/6").assertIsDisplayed()
        ui.runOnIdle { Assert.assertEquals(listOf(second), OverlayStateManager.images.value) }
    }

    @Test fun sendButtonStaysCenteredWithSingleAndMultilineInput() {
        ui.setContent { PeekTheme { androidx.compose.foundation.layout.Box(Modifier.width(320.dp).height(440.dp)) {
            OverlayPanelView(modifier = Modifier.fillMaxSize(), onCaptureScreenRequested = {}, onPickImageRequested = {},
                onMinimizeRequested = {}, onCloseRequested = {}, onOpenSettingsRequested = {})
        } } }
        fun assertCenters() {
            val input = ui.onNodeWithTag("overlay_prompt_input").fetchSemanticsNode().boundsInRoot
            val send = ui.onNodeWithTag("overlay_send_button").fetchSemanticsNode().boundsInRoot
            Assert.assertEquals(input.center.y, send.center.y, 1f)
        }
        assertCenters()
        ui.onNodeWithTag("overlay_prompt_input").performTextInput("First line\nSecond line")
        assertCenters()
        screenshot("aligned-composer")
    }

    @Test fun opacitySliderAppliesImmediately() {
        val preferences = com.example.service.OverlayPreferences.getInstance(ui.activity)
        preferences.setButtonOpacity(1f)
        ui.setContent { PeekTheme { com.example.ui.screens.SettingsDialog(onDismiss = {}) } }
        ui.onNodeWithTag("button_opacity_slider").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.4f) }
        ui.onNodeWithTag("button_opacity_value").assertTextEquals("40%")
        ui.runOnIdle { Assert.assertEquals(0.4f, preferences.buttonOpacity.value, 0.001f) }
        screenshot("button-opacity-settings")
        preferences.setButtonOpacity(1f)
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

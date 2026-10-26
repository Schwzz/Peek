package com.example

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.service.MediaProjectionService
import com.example.service.OverlayService
import com.example.service.OverlayStateManager
import com.example.service.OverlayPreferences
import com.example.data.api.ApiConfig
import com.example.data.api.ApiConfigRepository
import com.example.data.api.VisionAiProvider
import com.example.model.MessageSender
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real Android consent, background projection, overlay windows and repeated captures. */
@RunWith(AndroidJUnit4::class)
class CaptureFlowInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    private fun awaitState(message: String, predicate: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 15000
        while (!predicate() && android.os.SystemClock.elapsedRealtime() < end) android.os.SystemClock.sleep(50)
        assertTrue(message + "; error=" + OverlayStateManager.errorMessage.value, predicate())
    }

    private fun click(selector: BySelector) {
        val end = android.os.SystemClock.elapsedRealtime() + 15000
        while (android.os.SystemClock.elapsedRealtime() < end) {
            try {
                val node = device.wait(Until.findObject(selector), 1000) ?: continue
                node.click()
                return
            } catch (_: StaleObjectException) {
                // Activity transitions can invalidate an accessibility node. Query it again.
            }
        }
        fail("Control did not become available: $selector")
    }

    /** Wait for the real IME and moving overlay to settle before tapping Send.
     * A stale accessibility coordinate during resize can hit Gboard's microphone. */
    private fun enterPromptAndSend(prompt: String) {
        click(By.clazz("android.widget.EditText"))
        var stableSince = 0L
        var previous: android.graphics.Rect? = null
        awaitState("Composer did not settle above the keyboard") {
            try {
            val keyboard = instrumentation.uiAutomation.windows.firstOrNull {
                it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }
            val input = device.findObject(By.clazz("android.widget.EditText"))
            val send = device.findObject(By.desc("Send question"))
            val keyboardBounds = android.graphics.Rect().also { keyboard?.getBoundsInScreen(it) }
            val bounds = send?.visibleBounds
            val inputBounds = input?.visibleBounds
            val ready = keyboard != null && inputBounds != null && !inputBounds.isEmpty && bounds != null && !bounds.isEmpty &&
                !keyboardBounds.isEmpty && inputBounds.bottom <= keyboardBounds.top && bounds.bottom <= keyboardBounds.top
            val now = android.os.SystemClock.elapsedRealtime()
            if (!ready || bounds != previous) { stableSince = now; previous = bounds?.let { android.graphics.Rect(it) } }
            ready && now - stableSince >= 500
            } catch (_: StaleObjectException) { stableSince = 0L; previous = null; false }
        }
        checkNotNull(device.findObject(By.clazz("android.widget.EditText"))).text = prompt
        awaitState("Typed prompt did not reach the composer") { OverlayStateManager.draftPrompt.value == prompt }
        click(By.desc("Send question"))
        awaitState("Send did not start the request") { OverlayStateManager.chatMessages.value.any { it.sender == MessageSender.USER && it.text == prompt } }
    }

    private fun evidence(name: String) {
        val folder = File(context.filesDir, "capture-evidence").apply { mkdirs() }
        device.dumpWindowHierarchy(File(folder, "$name.xml"))
        instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
            File(folder, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        }
    }

    private fun assertOtherAppRemainsResumed() {
        val activities = device.executeShellCommand("dumpsys activity activities")
        assertTrue("Capture switched to a Peek activity: $activities", activities.lineSequence().any {
            (it.contains("mResumedActivity") || it.contains("topResumedActivity")) && it.contains("CaptureTargetActivity")
        })
    }

    private fun openToolbox() {
        val version = OverlayStateManager.captureVersion
        click(By.desc("Open Peek toolbox"))
        awaitState("Toolbox did not open") { OverlayStateManager.isToolboxExpanded.value }
        checkNotNull(device.wait(Until.findObject(By.desc("Select screen")), 5000))
        assertFalse("Opening the toolbox started selection", OverlayStateManager.isCapturing.value)
        assertNull(OverlayStateManager.capturedScreenToCrop.value)
        assertEquals("Opening the toolbox requested a screenshot", version, OverlayStateManager.captureVersion)
        val bubble = checkNotNull(device.findObject(By.desc("Open Peek toolbox"))).visibleBounds
        val select = checkNotNull(device.findObject(By.desc("Select screen"))).visibleBounds
        assertTrue("Toolbox was not beside the button: $select vs $bubble", select.left >= bubble.right || select.right <= bubble.left)
        assertFalse(device.hasObject(By.desc("Toolbox settings")))
        assertOtherAppRemainsResumed()
    }

    @Test fun switchingAppsAndRepeatedRegionCapturesKeepTheSameSessionAlive() {
        instrumentation.uiAutomation.serviceInfo = checkNotNull(instrumentation.uiAutomation.serviceInfo).apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        var scenario: ActivityScenario<MainActivity>? = null
        val configRepo = ApiConfigRepository.getInstance(context)
        val originalConfig = configRepo.getConfig()
        val preferences = OverlayPreferences.getInstance(context)
        val originalOpacity = preferences.buttonOpacity.value
        val server = MockWebServer().apply { start() }
        try {
            val icon = context.packageManager.getApplicationIcon(context.packageName)
            val iconBitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, 192, 192); icon.draw(android.graphics.Canvas(iconBitmap))
            val folder = File(context.filesDir, "capture-evidence").apply { mkdirs() }
            File(folder, "app-icon.png").outputStream().use { iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            iconBitmap.recycle()
            configRepo.saveConfig(ApiConfig(VisionAiProvider.OLLAMA, baseUrl = server.url("/").toString(), modelName = "test-vision", supportsImages = true))
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
            }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            click(By.desc("Settings"))
            checkNotNull(device.wait(Until.findObject(By.text("Models & appearance")), 10000))
            evidence("model-settings")
            click(By.desc("Close settings"))
            click(By.text("Start Peek"))
            val approve = device.wait(Until.findObject(By.res("android:id/button1")), 10000)
                ?: device.wait(Until.findObject(By.textContains("Start now")), 3000)
            checkNotNull(approve) { "Android screen consent did not appear" }.click()
            awaitState("Projection never became ready") { OverlayStateManager.isScreenSharing.value }
            // Destroy the main Activity, then work in another app.
            scenario.close(); scenario = null
            device.executeShellCommand("am start -W -n ${context.packageName}.test/com.example.CaptureTargetActivity")
            device.waitForIdle()
            assertOtherAppRemainsResumed()
            assertTrue(OverlayStateManager.isScreenSharing.value)

            openToolbox()
            evidence("toolbox")
            click(By.desc("Open Peek toolbox"))
            awaitState("Tapping the button again did not close the toolbox") { !OverlayStateManager.isToolboxExpanded.value }
            openToolbox()
            device.pressBack()
            awaitState("Back did not dismiss the toolbox") { !OverlayStateManager.isToolboxExpanded.value }
            assertOtherAppRemainsResumed()
            openToolbox()
            click(By.desc("Open chat"))
            awaitState("Chat action did not open chat") { OverlayStateManager.isPanelExpanded.value }
            assertFalse(OverlayStateManager.isToolboxExpanded.value)
            assertFalse(OverlayStateManager.isCapturing.value)
            click(By.desc("Minimize to bubble"))

            repeat(2) {
                evidence("before-capture-$it")
                openToolbox()
                click(By.desc("Select screen"))
                awaitState("No captured screen reached the selector") { OverlayStateManager.capturedScreenToCrop.value != null }
                assertOtherAppRemainsResumed()
                checkNotNull(device.wait(Until.findObject(By.text("Use selection")), 15000)) { "Region selector controls were not visible" }
                evidence("selector-$it")
                val frame = OverlayStateManager.capturedScreenToCrop.value!!
                val colors = mutableSetOf<Int>()
                for (x in 1..20) for (y in 1..20) colors += frame.getPixel((frame.width - 1) * x / 21, (frame.height - 1) * y / 21)
                assertTrue("Projection produced a blank frame", colors.size > 2)
                if (it == 0) {
                    click(By.text("Use selection"))
                    awaitState("Selection did not return an image to the overlay") {
                        !OverlayStateManager.isCapturing.value && OverlayStateManager.currentImage.value != null
                    }
                    assertOtherAppRemainsResumed()
                    assertTrue("Finishing a screenshot stopped projection", OverlayStateManager.isScreenSharing.value)
                    // Exercise Android cleartext policy, the optional-key local connection,
                    // actual cropped JPEG payload, and the answer in a service-owned window.
                    server.enqueue(MockResponse().setBody("""{"message":{"content":"Local image response received"}}"""))
                    enterPromptAndSend("Describe this selection")
                    awaitState("The local model answer never reached chat") {
                        OverlayStateManager.chatMessages.value.any { it.sender == MessageSender.AI && it.text == "Local image response received" }
                    }
                    val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                    assertEquals("/api/chat", request.path)
                    assertNull(request.getHeader("Authorization"))
                    val payload = JSONObject(request.body.readUtf8())
                    assertFalse(payload.getBoolean("stream"))
                    assertEquals("test-vision", payload.getString("model"))
                    val encoded = payload.getJSONArray("messages").getJSONObject(0).getJSONArray("images").getString(0)
                    val jpeg = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                    val decoded = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    assertNotNull(decoded)
                    decoded?.recycle()
                    assertOtherAppRemainsResumed()
                    evidence("answer-panel")
                    click(By.desc("Minimize to bubble"))
                } else {
                    // Back must dismiss selection without navigating the other app or ending sharing.
                    device.pressBack()
                    awaitState("Back did not cancel region selection") { !OverlayStateManager.isCapturing.value }
                    assertOtherAppRemainsResumed()
                    assertTrue("Cancelling a screenshot stopped projection", OverlayStateManager.isScreenSharing.value)
                }
            }
            evidence("capture-over-other-app")
            context.stopService(Intent(context, MediaProjectionService::class.java))
            awaitState("Explicit stop did not release projection") { !OverlayStateManager.isScreenSharing.value }
            openToolbox()
            assertFalse("Opening tools requested screen permission", OverlayStateManager.isStartingScreenSharing.value)
            click(By.desc("Add images"))
            awaitState("System image picker did not open") {
                device.executeShellCommand("dumpsys activity activities").lineSequence().any {
                    (it.contains("mResumedActivity") || it.contains("topResumedActivity")) &&
                        (it.contains("photopicker", ignoreCase = true) || it.contains("documentsui", ignoreCase = true))
                }
            }
            evidence("image-picker")
            device.pressBack()
            awaitState("Image picker did not return to the toolbox") {
                OverlayStateManager.isToolboxExpanded.value && device.hasObject(By.desc("Stop overlay"))
            }
            assertOtherAppRemainsResumed()
            click(By.desc("Open Peek toolbox"))
            // Exercise the same import callback used by the multi-select picker with
            // two real files, then verify both JPEGs reach the local model.
            val files = listOf(android.graphics.Color.BLUE, android.graphics.Color.RED).mapIndexed { index, color ->
                File(context.cacheDir, "attachment-$index.png").also { file ->
                    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
            }
            instrumentation.runOnMainSync { com.example.util.PickedImageImporter.load(context, files.map { android.net.Uri.fromFile(it) }) }
            awaitState("Multiple images did not reach chat") { OverlayStateManager.images.value.size == 2 && !OverlayStateManager.isLoadingImages.value }
            checkNotNull(device.wait(Until.findObject(By.text("2/6")), 5000))
            evidence("multiple-attachments")
            server.enqueue(MockResponse().setBody("""{"message":{"content":"Both images received"}}"""))
            enterPromptAndSend("Compare both images")
            awaitState("Multi-image answer did not reach chat") { OverlayStateManager.chatMessages.value.any { it.text == "Both images received" } }
            val multiple = JSONObject(checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)).body.readUtf8()).getJSONArray("messages")
            val sent = multiple.getJSONObject(multiple.length() - 1).getJSONArray("images")
            assertEquals(2, sent.length())
            assertNotEquals(sent.getString(0), sent.getString(1))
            assertTrue(OverlayStateManager.images.value.isEmpty())
            files.forEach { it.delete() }
            assertOtherAppRemainsResumed()
            evidence("multiple-image-answer")
            click(By.desc("Minimize to bubble"))
            preferences.setButtonOpacity(0.4f)
            device.waitForIdle()
            evidence("faded-floating-button")
            openToolbox()
            click(By.desc("Stop overlay"))
            awaitState("Stop in the toolbox did not stop the overlay") { !OverlayStateManager.isServiceRunning.value }
            assertFalse(OverlayStateManager.isScreenSharing.value)
            assertOtherAppRemainsResumed()
        } finally {
            evidence("capture-final")
            scenario?.close()
            context.stopService(Intent(context, MediaProjectionService::class.java))
            context.stopService(Intent(context, OverlayService::class.java))
            configRepo.saveConfig(originalConfig)
            preferences.setButtonOpacity(originalOpacity)
            server.shutdown()
        }
    }
}

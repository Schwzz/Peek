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

    @Test fun switchingAppsAndRepeatedRegionCapturesKeepTheSameSessionAlive() {
        device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
            }
            scenario = ActivityScenario.launch(MainActivity::class.java)
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

            repeat(2) {
                evidence("before-capture-$it")
                click(By.descContains("Peek: tap to select screen"))
                awaitState("No captured screen reached the selector") { OverlayStateManager.capturedScreenToCrop.value != null }
                assertOtherAppRemainsResumed()
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
        } finally {
            evidence("capture-final")
            scenario?.close()
            context.stopService(Intent(context, MediaProjectionService::class.java))
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}

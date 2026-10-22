package com.example

import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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

    private fun assertSettingsRemainsResumed() {
        val activities = device.executeShellCommand("dumpsys activity activities")
        assertTrue("Capture switched to a Peek activity: $activities", activities.lineSequence().any {
            (it.contains("mResumedActivity") || it.contains("topResumedActivity")) && it.contains("com.android.settings")
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
            checkNotNull(device.wait(Until.findObject(By.text("Start Peek")), 10000)) { "Start Peek was not visible" }.click()
            val approve = device.wait(Until.findObject(By.res("android:id/button1")), 10000)
                ?: device.wait(Until.findObject(By.textContains("Start now")), 3000)
            checkNotNull(approve) { "Android screen consent did not appear" }.click()
            awaitState("Projection never became ready") { OverlayStateManager.isScreenSharing.value }
            // Destroy the main Activity, then work in another app.
            scenario.close(); scenario = null
            device.executeShellCommand("am start -a android.settings.SETTINGS")
            device.waitForIdle()
            assertSettingsRemainsResumed()
            assertTrue(OverlayStateManager.isScreenSharing.value)

            repeat(2) {
                val bubble = device.wait(Until.findObject(By.descContains("Peek: tap to select screen")), 10000)
                checkNotNull(bubble) { "Floating bubble was not available over Settings" }.click()
                awaitState("No captured screen reached the selector") { OverlayStateManager.capturedScreenToCrop.value != null }
                assertSettingsRemainsResumed()
                val frame = OverlayStateManager.capturedScreenToCrop.value!!
                val colors = mutableSetOf<Int>()
                for (x in 1..20) for (y in 1..20) colors += frame.getPixel((frame.width - 1) * x / 21, (frame.height - 1) * y / 21)
                assertTrue("Projection produced a blank frame", colors.size > 2)
                if (it == 0) {
                    checkNotNull(device.wait(Until.findObject(By.text("Use selection")), 10000)).click()
                    awaitState("Selection did not return an image to the overlay") {
                        !OverlayStateManager.isCapturing.value && OverlayStateManager.currentImage.value != null
                    }
                    assertSettingsRemainsResumed()
                    assertTrue("Finishing a screenshot stopped projection", OverlayStateManager.isScreenSharing.value)
                    checkNotNull(device.wait(Until.findObject(By.desc("Minimize to bubble")), 5000)).click()
                } else {
                    // Back must dismiss the overlay selector without navigating Settings or ending sharing.
                    device.pressBack()
                    awaitState("Back did not cancel region selection") { !OverlayStateManager.isCapturing.value }
                    assertSettingsRemainsResumed()
                    assertTrue("Cancelling a screenshot stopped projection", OverlayStateManager.isScreenSharing.value)
                }
            }
            val folder = checkNotNull(context.getExternalFilesDir(null))
            device.takeScreenshot(File(folder, "capture-over-other-app.png"))
            context.stopService(Intent(context, MediaProjectionService::class.java))
            awaitState("Explicit stop did not release projection") { !OverlayStateManager.isScreenSharing.value }
        } finally {
            val folder = context.getExternalFilesDir(null)
            if (folder != null) {
                device.dumpWindowHierarchy(File(folder, "capture-window-hierarchy.xml"))
                device.takeScreenshot(File(folder, "capture-final.png"))
            }
            scenario?.close()
            context.stopService(Intent(context, MediaProjectionService::class.java))
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}

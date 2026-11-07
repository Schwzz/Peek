package com.example

import android.view.WindowManager
import com.example.service.MediaProjectionService
import com.example.service.OverlayWindowMetrics
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Evidence for the split-screen outcome: a Service is not a visual context, so
 * getCurrentWindowMetrics() reports the whole display, not an app's split-screen cell.
 * This is Robolectric evidence; a physical-device run is still required to confirm the
 * same on each OEM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayWindowMetricsTest {
    @Test fun serviceWindowMetricsMatchTheWholeDisplayOnSdk34() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        try {
            val display = service.resources.displayMetrics
            val windowBounds = service.getSystemService(WindowManager::class.java)
                .currentWindowMetrics.bounds
            val used = OverlayWindowMetrics.bounds(service)

            assertEquals(display.widthPixels, windowBounds.width())
            assertEquals(display.heightPixels, windowBounds.height())
            assertEquals(windowBounds, used)
        } finally {
            controller.destroy()
        }
    }
}

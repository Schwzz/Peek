package com.example

import com.example.service.MediaProjectionService
import com.example.service.OverlayStateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaProjectionServiceTest {
    @Test
    fun projectionStopStopsTheServiceAndClearsSharingState() {
        OverlayStateManager.setScreenSharing(true)
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()

        service.handleProjectionStopped()

        assertTrue(shadowOf(service).isStoppedBySelf)
        assertTrue(OverlayStateManager.isScreenSharing.value)

        controller.destroy()

        assertFalse(OverlayStateManager.isScreenSharing.value)
    }
}

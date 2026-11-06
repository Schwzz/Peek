package com.example

import com.example.service.OverlayService
import com.example.service.OverlayStateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayServicePermissionTest {
    @Test
    fun deniedOverlayPermissionStopsTheService() {
        ShadowSettings.setCanDrawOverlays(false)
        OverlayStateManager.setServiceRunning(false)
        val controller = Robolectric.buildService(OverlayService::class.java).create()
        val service = controller.get()

        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(OverlayStateManager.isServiceRunning.value)

        controller.destroy()
    }
}

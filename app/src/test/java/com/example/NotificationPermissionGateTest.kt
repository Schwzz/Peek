package com.example

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.service.OverlayService
import com.example.service.OverlayStartGate
import com.example.service.OverlayStateManager
import com.example.ui.screens.MainScreen
import com.example.ui.theme.PeekTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class NotificationPermissionGateTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        ShadowSettings.setCanDrawOverlays(true)
        OverlayStateManager.setServiceRunning(false)
        OverlayStateManager.setErrorMessage(null)
        OverlayStateManager.finishCapture(showPanel = false)
        OverlayStateManager.setToolboxExpanded(false)
        OverlayStateManager.setPanelExpanded(false)
        shadowOf(ui.activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun grantedNotificationPermissionStartsTheOverlayService() {
        ui.setContent { PeekTheme { MainScreen(onOpenSettingsRequested = {}) } }

        ui.onNodeWithTag("toggle_overlay_service_button").performClick()
        ui.waitForIdle()

        val started = shadowOf(ui.activity.application).nextStartedService
        assertEquals(OverlayService::class.java.name, started?.component?.className)
    }

    @Test
    fun deniedNotificationPermissionRefusesToStartAndExplainsInline() {
        shadowOf(ui.activity.application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ui.setContent { PeekTheme { MainScreen(onOpenSettingsRequested = {}) } }

        ui.onNodeWithTag("toggle_overlay_service_button").performClick()
        ui.waitForIdle()

        assertNull(shadowOf(ui.activity.application).nextStartedService)
        assertEquals(
            OverlayStartGate.NOTIFICATION_REQUIRED_MESSAGE,
            OverlayStateManager.errorMessage.value
        )
        ui.onNodeWithText(OverlayStartGate.NOTIFICATION_REQUIRED_MESSAGE)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun notificationsRowExplainsTheRequirementBeforeStart() {
        shadowOf(ui.activity.application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ui.setContent { PeekTheme { MainScreen(onOpenSettingsRequested = {}) } }

        ui.onNodeWithText("Required for the overlay's persistent notification")
            .performScrollTo()
            .assertIsDisplayed()
    }
}

package com.example

import android.speech.SpeechRecognizer
import android.view.WindowManager
import com.example.service.OverlayService
import com.example.service.OverlayStateManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayServiceCaptureTest {
    private lateinit var controller: ServiceController<OverlayService>

    @Before
    fun setUp() {
        ShadowSettings.setCanDrawOverlays(true)
        OverlayStateManager.setServiceRunning(false)
        OverlayStateManager.finishCapture(showPanel = false)
        OverlayStateManager.setToolboxExpanded(false)
        OverlayStateManager.setPanelExpanded(false)
        OverlayStateManager.setErrorMessage(null)
        controller = Robolectric.buildService(OverlayService::class.java).create()
        ShadowLooper.idleMainLooper()
        OverlayStateManager.setPanelExpanded(true)
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() {
        controller.destroy()
        ShadowLooper.idleMainLooper()
        ShadowSettings.setCanDrawOverlays(false)
        OverlayStateManager.setServiceRunning(false)
        OverlayStateManager.setPanelExpanded(false)
    }

    @Test
    fun captureKeepsTheExactPanelParamsInstanceAndFlagWord() {
        val before = panelParams()
        val flagsBefore = before.flags

        assertTrue(OverlayStateManager.beginCapture())
        ShadowLooper.idleMainLooper()
        OverlayStateManager.finishCapture()
        ShadowLooper.idleMainLooper()

        val after = panelParams()
        assertSame(before, after)
        assertEquals(flagsBefore, after.flags)
    }

    @Test
    fun panelFocusToggleChangesFlagsWhileVisibleAndIdle() {
        val params = panelParams()
        assertNotEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        setPanelFocus(true)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        setPanelFocus(false)
        assertNotEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    @Test
    fun panelFocusNoOpsWhileCaptureIsInFlight() {
        val params = panelParams()
        val flagsBefore = params.flags

        assertTrue(OverlayStateManager.beginCapture())
        ShadowLooper.idleMainLooper()

        setPanelFocus(true)
        setPanelFocus(false)

        assertEquals(flagsBefore, params.flags)
        OverlayStateManager.finishCapture()
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun panelFocusNoOpsWhilePanelIsHidden() {
        OverlayStateManager.setPanelExpanded(false)
        ShadowLooper.idleMainLooper()
        val params = panelParams()
        val flagsBefore = params.flags

        setPanelFocus(true)

        assertEquals(flagsBefore, params.flags)
    }

    @Test
    fun stoppingVoiceInputDestroysTheRecognizer() {
        val recognizer = installSpeechRecognizer()

        invokeNoArg("stopVoiceInput")
        ShadowLooper.idleMainLooper()

        assertTrue(shadowOf(recognizer).isDestroyed)
    }

    @Test
    fun hidingThePanelDestroysTheVoiceRecognizer() {
        val recognizer = installSpeechRecognizer()

        OverlayStateManager.setPanelExpanded(false)
        ShadowLooper.idleMainLooper()

        assertTrue(shadowOf(recognizer).isDestroyed)
    }

    private fun installSpeechRecognizer(): SpeechRecognizer {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(controller.get())
        val field = OverlayService::class.java.getDeclaredField("speechRecognizer")
        field.isAccessible = true
        field.set(controller.get(), recognizer)
        return recognizer
    }

    private fun invokeNoArg(name: String) {
        val method = OverlayService::class.java.getDeclaredMethod(name)
        method.isAccessible = true
        method.invoke(controller.get())
    }

    private fun panelParams(): WindowManager.LayoutParams {
        val field = OverlayService::class.java.getDeclaredField("panelParams")
        field.isAccessible = true
        return field.get(controller.get()) as WindowManager.LayoutParams
    }

    private fun setPanelFocus(focused: Boolean) {
        val method = OverlayService::class.java.getDeclaredMethod(
            "setPanelFocus",
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(controller.get(), focused)
        ShadowLooper.idleMainLooper()
    }
}

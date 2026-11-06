package com.example

import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import com.example.service.OverlayWindowParams
import com.example.util.PanelBounds
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayWindowParamsTest {
    private val overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private val format = PixelFormat.TRANSLUCENT
    private val gravity = Gravity.TOP or Gravity.START

    @Test
    fun bubbleIsTheStandardNonFocusableChathead() {
        val params = OverlayWindowParams.bubble(12, 34)
        assertEquals(overlayType, params.type)
        assertEquals(format, params.format)
        assertEquals(gravity, params.gravity)
        assertEquals(12, params.x)
        assertEquals(34, params.y)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.width)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            params.flags and (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        )
        assertEquals(
            0,
            params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
    }

    @Test
    fun panelIsNonFocusableSoAddingItOpensNoKeyboardAndStealsNoFocus() {
        val params = OverlayWindowParams.panel(PanelBounds(10, 20, 300, 400))
        assertEquals(overlayType, params.type)
        assertEquals(format, params.format)
        assertEquals(gravity, params.gravity)
        assertEquals(10, params.x)
        assertEquals(20, params.y)
        assertEquals(300, params.width)
        assertEquals(400, params.height)
        assertNotEquals(
            0,
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        assertNotEquals(
            0,
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        assertNotEquals(
            0,
            params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN,
            params.softInputMode
        )
    }

    @Test
    fun focusedPanelTakesInputAndAsksForTheKeyboard() {
        val params = OverlayWindowParams.focusedPanel(PanelBounds(10, 20, 300, 400))
        assertEquals(overlayType, params.type)
        assertEquals(format, params.format)
        assertEquals(gravity, params.gravity)
        assertEquals(10, params.x)
        assertEquals(20, params.y)
        assertEquals(300, params.width)
        assertEquals(400, params.height)
        assertEquals(
            0,
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        assertNotEquals(
            0,
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        assertNotEquals(
            0,
            params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
            params.softInputMode
        )
    }

    @Test
    fun toolboxWatchesOutsideTouchesAndNeverRequestsTheKeyboard() {
        val params = OverlayWindowParams.toolbox(PanelBounds(0, 0, 100, 200))
        assertEquals(overlayType, params.type)
        assertEquals(100, params.width)
        assertEquals(200, params.height)
        assertEquals(
            0,
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        assertNotEquals(
            0,
            params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        assertNotEquals(
            0,
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN,
            params.softInputMode
        )
    }

    @Test
    fun selectorFillsTheScreenWithoutLimits() {
        val params = OverlayWindowParams.selector()
        assertEquals(overlayType, params.type)
        assertEquals(format, params.format)
        assertEquals(gravity, params.gravity)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.height)
        assertNotEquals(0, params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        assertNotEquals(0, params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        // R+ only: keep the frozen screen aligned with system bars and cutouts.
        assertEquals(
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
            params.layoutInDisplayCutoutMode
        )
    }
}

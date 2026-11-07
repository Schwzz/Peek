package com.example.service

import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import com.example.util.PanelBounds

/**
 * Single source of truth for every overlay window's [WindowManager.LayoutParams].
 * Keep the four presets behaviorally identical to the literals they replace.
 */
object OverlayWindowParams {

    val overlayType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /**
     * Floating launcher bubble. Like every Android chathead it never needs text input,
     * so it stays non-focusable: taps only report position, it never steals the input
     * method, and the app underneath keeps the keyboard.
     */
    fun bubble(x: Int, y: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

    /**
     * Interactive chat panel before its text field is tapped. It must be added
     * non-focusable: a focusable overlay steals input focus from the app underneath
     * and asks the system for a keyboard the moment it appears. Touch still reaches
     * the panel, and ACTION_OUTSIDE still dismisses it.
     */
    fun panel(bounds: PanelBounds): WindowManager.LayoutParams =
        panel(bounds, focusable = false)

    /**
     * Same chat panel once the user taps its text field. Only then may it take input
     * focus and ask for the keyboard.
     */
    fun focusedPanel(bounds: PanelBounds): WindowManager.LayoutParams =
        panel(bounds, focusable = true)

    private fun panel(bounds: PanelBounds, focusable: Boolean): WindowManager.LayoutParams {
        val focusFlags = if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val inputState = if (focusable) {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }
        return WindowManager.LayoutParams(
            bounds.width,
            bounds.height,
            overlayType,
            focusFlags or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.x
            y = bounds.y
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or inputState
        }
    }

    /** Small quadrants menu anchored next to the bubble; keyboard never requested. */
    fun toolbox(bounds: PanelBounds): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            bounds.width,
            bounds.height,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }

    /**
     * Full-screen host for the frozen-screen region selection. Limits-free on purpose:
     * projection includes system bars, so use identical display bounds and
     * cutout-always so the frozen screen and its selection do not shrink or shift.
     */
    fun selector(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
}

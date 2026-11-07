package com.example.service

import android.hardware.display.DisplayManager
import com.example.util.OverlayGeometry
import com.example.util.PanelBounds

/**
 * Tracks the display that currently hosts the overlay panel.
 *
 * Android can remove a display (external screen unplugged, cast ended) while the panel is
 * attached to it. The panel must then move back to the default display and be re-clamped,
 * otherwise it is positioned against bounds that no longer exist.
 */
class OverlayDisplayListener(
    private val panelDisplayId: () -> Int,
    private val onPanelDisplayRemoved: () -> Unit
) : DisplayManager.DisplayListener {

    override fun onDisplayAdded(displayId: Int) = Unit

    override fun onDisplayChanged(displayId: Int) = Unit

    override fun onDisplayRemoved(displayId: Int) {
        if (displayId == panelDisplayId()) onPanelDisplayRemoved()
    }
}

/** Re-placement math for a panel whose host display disappeared. */
object DisplayRelocation {
    fun rebase(bounds: PanelBounds, screenWidth: Int, screenHeight: Int, density: Float): PanelBounds =
        OverlayGeometry.clampPanel(bounds, screenWidth, screenHeight, density)
}

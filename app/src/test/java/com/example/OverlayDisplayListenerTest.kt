package com.example

import com.example.service.DisplayRelocation
import com.example.service.OverlayDisplayListener
import com.example.util.PanelBounds
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayDisplayListenerTest {
    @Test fun removalOfThePanelDisplayAsksForRelocation() {
        var relocations = 0
        val listener = OverlayDisplayListener(
            panelDisplayId = { 3 },
            onPanelDisplayRemoved = { relocations++ }
        )

        listener.onDisplayRemoved(3)

        assertEquals(1, relocations)
    }

    @Test fun removalOfAnotherDisplayIsIgnored() {
        var relocations = 0
        val listener = OverlayDisplayListener(
            panelDisplayId = { 3 },
            onPanelDisplayRemoved = { relocations++ }
        )

        listener.onDisplayRemoved(7)

        assertEquals(0, relocations)
    }

    @Test fun relocationReclampsThePanelAgainstTheDefaultDisplay() {
        val moved = DisplayRelocation.rebase(PanelBounds(1900, 900, 320, 440), 800, 1000, 1f)

        assertEquals(480, moved.x)
        assertEquals(560, moved.y)
        assertEquals(320, moved.width)
        assertEquals(440, moved.height)
    }
}

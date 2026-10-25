package com.example

import com.example.util.*
import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    @Test fun toolboxFitsAtEveryScreenEdgeAndAvoidsTheButtonWhenSpaceAllows() {
        for ((width, height) in listOf(400 to 800, 800 to 400)) {
            for ((x, y) in listOf(0 to 0, width - 60 to 0, 0 to height - 60, width - 60 to height - 60)) {
                val menu = OverlayGeometry.toolboxBounds(x, y, width, height, 1f)
                assertTrue(menu.x >= 0 && menu.x + menu.width <= width)
                assertTrue(menu.y >= 0 && menu.y + menu.height <= height)
                assertTrue(menu.y >= y + 60 || menu.y + menu.height <= y)
            }
        }
    }

    @Test fun panelsAndBubblesStayOnScreenEvenWhenKeyboardLeavesLittleSpace() {
        val bounds = OverlayGeometry.clampPanel(PanelBounds(-500, 900, 1200, 1600), 800, 250, 2f)
        assertEquals(0, bounds.x)
        assertEquals(0, bounds.y)
        assertEquals(800, bounds.width)
        assertEquals(250, bounds.height)
        assertEquals(0 to 190, OverlayGeometry.clampBubble(-20, 900, 60, 800, 250))
    }

    @Test fun imageFitPreservesAspectRatioAndMapsSelectionsAfterZoom() {
        val fit = CropGeometry.fit(1000, 500, 400f, 800f)
        assertEquals(400f, fit.width, 0.01f)
        assertEquals(200f, fit.height, 0.01f)
        assertEquals(300f, fit.top, 0.01f)
        assertEquals(ImageCrop(0, 0, 1000, 500), CropGeometry.map(0f, 300f, 400f, 500f, fit, 1000, 500))
        val zoom = CropGeometry.fit(1000, 500, 400f, 800f, 2f)
        assertEquals(ImageCrop(250, 125, 750, 375), CropGeometry.map(0f, 300f, 400f, 500f, zoom, 1000, 500))
    }

    @Test fun reversedAndOutOfBoundsCropsRemainValid() {
        val fit = CropGeometry.fit(100, 50, 100f, 50f)
        assertEquals(ImageCrop(0, 0, 100, 50), CropGeometry.map(150f, 90f, -20f, -20f, fit, 100, 50))
        val tiny = CropGeometry.map(1000f, 1000f, 1000f, 1000f, fit, 100, 50)
        assertEquals(ImageCrop(99, 49, 100, 50), tiny)
    }
}

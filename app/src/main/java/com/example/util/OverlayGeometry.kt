package com.example.util

data class PanelBounds(val x: Int, val y: Int, val width: Int, val height: Int)

object OverlayGeometry {
    fun toolboxBounds(bubbleX: Int, bubbleY: Int, screenWidth: Int, screenHeight: Int, density: Float): PanelBounds {
        val width = minOf((88 * density).toInt(), screenWidth)
        val height = minOf((240 * density).toInt(), screenHeight)
        val bubbleSize = (60 * density).toInt()
        val gap = (8 * density).toInt()
        val right = bubbleX + bubbleSize + gap
        val left = bubbleX - width - gap
        val x = if (right + width <= screenWidth) right else if (left >= 0) left
            else if (bubbleX < screenWidth / 2) right else left
        val y = bubbleY + bubbleSize / 2 - height / 2
        return PanelBounds(x.coerceIn(0, screenWidth - width), y.coerceIn(0, screenHeight - height), width, height)
    }

    fun clampPanel(bounds: PanelBounds, screenWidth: Int, screenHeight: Int, density: Float): PanelBounds {
        val width = bounds.width.coerceIn(minOf((280 * density).toInt(), screenWidth), screenWidth)
        val height = bounds.height.coerceIn(minOf((260 * density).toInt(), screenHeight), screenHeight)
        return PanelBounds(bounds.x.coerceIn(0, screenWidth - width), bounds.y.coerceIn(0, screenHeight - height), width, height)
    }

    fun clampBubble(x: Int, y: Int, size: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> =
        x.coerceIn(0, (screenWidth - size).coerceAtLeast(0)) to y.coerceIn(0, (screenHeight - size).coerceAtLeast(0))
}

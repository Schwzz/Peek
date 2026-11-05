package com.example.util

import kotlin.math.min

data class ImageTransform(val left: Float, val top: Float, val width: Float, val height: Float)
data class ImageCrop(val left: Int, val top: Int, val right: Int, val bottom: Int)

object CropGeometry {
    fun fit(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float, zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f): ImageTransform {
        val scale = min(viewWidth / imageWidth, viewHeight / imageHeight) * zoom
        val width = imageWidth * scale
        val height = imageHeight * scale
        return ImageTransform((viewWidth - width) / 2 + panX, (viewHeight - height) / 2 + panY, width, height)
    }

    fun map(left: Float, top: Float, right: Float, bottom: Float, transform: ImageTransform, imageWidth: Int, imageHeight: Int): ImageCrop {
        val l = (((minOf(left, right) - transform.left) / transform.width) * imageWidth).toInt().coerceIn(0, imageWidth - 1)
        val t = (((minOf(top, bottom) - transform.top) / transform.height) * imageHeight).toInt().coerceIn(0, imageHeight - 1)
        val r = (((maxOf(left, right) - transform.left) / transform.width) * imageWidth).toInt().coerceIn(l + 1, imageWidth)
        val b = (((maxOf(top, bottom) - transform.top) / transform.height) * imageHeight).toInt().coerceIn(t + 1, imageHeight)
        return ImageCrop(l, t, r, b)
    }
}

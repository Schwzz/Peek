package com.example.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

object BitmapUtils {

    fun toBase64Jpeg(source: Bitmap, maxDimension: Int = 1600, quality: Int = 85): String {
        val scaled = scaleDownIfNeeded(source, maxDimension)
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        if (scaled !== source) scaled.recycle()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun scaleDownIfNeeded(source: Bitmap, maxDimension: Int = 1600): Bitmap {
        val width = source.width
        val height = source.height
        val maxSide = max(width, height)
        if (maxSide <= maxDimension) {
            return source
        }
        val scale = maxDimension.toFloat() / maxSide
        val matrix = Matrix().apply { postScale(scale, scale) }
        return Bitmap.createBitmap(source, 0, 0, width, height, matrix, true)
    }

    fun crop(source: Bitmap, cropRect: Rect): Bitmap {
        val left = cropRect.left.coerceIn(0, source.width - 1)
        val top = cropRect.top.coerceIn(0, source.height - 1)
        val width = cropRect.width().coerceIn(1, source.width - left)
        val height = cropRect.height().coerceIn(1, source.height - top)
        return Bitmap.createBitmap(source, left, top, width, height)
    }
}

package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ScreenCaptureHelper {

    suspend fun captureVirtualDisplay(
        context: Context,
        mediaProjection: MediaProjection
    ): Bitmap? = suspendCancellableCoroutine { continuation ->
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null

        val handler = Handler(Looper.getMainLooper())
        var isCompleted = false

        val projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                if (!isCompleted) {
                    isCompleted = true
                    try {
                        virtualDisplay?.release()
                        imageReader.close()
                    } catch (_: Exception) {}
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }

        fun cleanup() {
            try {
                virtualDisplay?.release()
                virtualDisplay = null
                imageReader.close()
                mediaProjection.unregisterCallback(projectionCallback)
                mediaProjection.stop()
            } catch (_: Exception) {}
        }

        runCatching {
            mediaProjection.registerCallback(projectionCallback, handler)
        }

        imageReader.setOnImageAvailableListener({ reader ->
            if (isCompleted) return@setOnImageAvailableListener
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    if (bitmap != cleanBitmap) {
                        bitmap.recycle()
                    }

                    isCompleted = true
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(cleanBitmap)
                    }
                }
            } catch (e: Exception) {
                if (!isCompleted) {
                    isCompleted = true
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }, handler)

        try {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "PeekCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                handler
            )
        } catch (e: Exception) {
            isCompleted = true
            cleanup()
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }

        // Timeout safety after 2.5 seconds
        handler.postDelayed({
            if (!isCompleted) {
                isCompleted = true
                cleanup()
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }, 2500)

        continuation.invokeOnCancellation {
            isCompleted = true
            cleanup()
        }
    }
}

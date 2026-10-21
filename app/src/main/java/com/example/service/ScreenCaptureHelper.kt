package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ScreenCaptureHelper {
    suspend fun captureVirtualDisplay(context: Context, mediaProjection: MediaProjection): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val metrics = context.resources.displayMetrics
            val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds else null
            var width = bounds?.width() ?: metrics.widthPixels
            var height = bounds?.height() ?: metrics.heightPixels
            val density = context.resources.configuration.densityDpi
            var reader: ImageReader? = null
            var display: VirtualDisplay? = null
            var completed = false
            var callback: MediaProjection.Callback? = null
            var timeout: Runnable? = null

            fun finish(bitmap: Bitmap?, stopProjection: Boolean = true) {
                if (completed) { bitmap?.recycle(); return }
                completed = true
                timeout?.let(handler::removeCallbacks)
                runCatching { display?.release() }
                display = null
                runCatching { reader?.close() }
                reader = null
                callback?.let { runCatching { mediaProjection.unregisterCallback(it) } }
                if (stopProjection) runCatching { mediaProjection.stop() }
                if (continuation.isActive) continuation.resume(bitmap) else bitmap?.recycle()
            }

            fun createReader(w: Int, h: Int): ImageReader {
                return ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).apply {
                    setOnImageAvailableListener({ source ->
                        if (completed || source !== reader) return@setOnImageAvailableListener
                        try {
                            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                            val result = image.use {
                                val plane = it.planes[0]
                                val paddedWidth = it.width + (plane.rowStride - plane.pixelStride * it.width) / plane.pixelStride
                                val padded = Bitmap.createBitmap(paddedWidth, it.height, Bitmap.Config.ARGB_8888)
                                try {
                                    padded.copyPixelsFromBuffer(plane.buffer)
                                    Bitmap.createBitmap(padded, 0, 0, it.width, it.height).also { cropped ->
                                        if (cropped !== padded) padded.recycle()
                                    }
                                } catch (e: Exception) {
                                    padded.recycle()
                                    throw e
                                }
                            }
                            finish(result)
                        } catch (_: Exception) {
                            finish(null)
                        }
                    }, handler)
                }
            }

            callback = object : MediaProjection.Callback() {
                override fun onStop() { finish(null, stopProjection = false) }

                override fun onCapturedContentResize(newWidth: Int, newHeight: Int) {
                    if (completed || newWidth <= 0 || newHeight <= 0 || (newWidth == width && newHeight == height)) return
                    try {
                        val replacement = createReader(newWidth, newHeight)
                        val previous = reader
                        reader = replacement
                        width = newWidth
                        height = newHeight
                        display?.resize(width, height, density)
                        display?.surface = replacement.surface
                        previous?.close()
                    } catch (_: Exception) {
                        finish(null)
                    }
                }
            }
            try {
                mediaProjection.registerCallback(callback!!, handler)
                reader = createReader(width, height)
                display = mediaProjection.createVirtualDisplay(
                    "PeekCapture", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler
                )
                timeout = Runnable { finish(null) }
                handler.postDelayed(timeout!!, 5000)
            } catch (_: Exception) {
                finish(null)
            }
            continuation.invokeOnCancellation { handler.post { finish(null) } }
        }
}

package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** One display per consent session. Frames stay on device until a capture is requested. */
class ScreenCaptureHelper(
    context: Context,
    private val projection: MediaProjection,
    private val onStopped: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.configuration.densityDpi
    private var width: Int
    private var height: Int
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var latestImage: Image? = null
    private var latestFrameAt = 0L
    private var closed = false
    private val frames = MutableStateFlow(0L)

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (closed) return
            close(stopProjection = false)
            onStopped()
        }

        override fun onCapturedContentResize(newWidth: Int, newHeight: Int) {
            if (closed || newWidth <= 0 || newHeight <= 0 || (newWidth == width && newHeight == height)) return
            try {
                latestImage?.close(); latestImage = null
                val oldReader = reader
                width = newWidth; height = newHeight
                reader = createReader()
                display?.resize(width, height, density)
                display?.surface = reader!!.surface
                oldReader?.close()
            } catch (_: Exception) {
                close()
                onStopped()
            }
        }
    }

    init {
        val metrics = context.resources.displayMetrics
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds else null
        width = bounds?.width() ?: metrics.widthPixels
        height = bounds?.height() ?: metrics.heightPixels
        try {
            projection.registerCallback(callback, handler)
            reader = createReader()
            display = projection.createVirtualDisplay(
                "PeekScreenSession", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler
            )
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private fun createReader(): ImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).apply {
        setOnImageAvailableListener({ source ->
            if (closed || source !== reader) return@setOnImageAvailableListener
            // Retain just the newest buffer, without encoding every video frame.
            latestImage?.close(); latestImage = null
            try {
                latestImage = source.acquireLatestImage()
                if (latestImage != null) {
                    latestFrameAt = System.nanoTime() / 1_000_000
                    frames.value++
                }
            } catch (_: IllegalStateException) { /* A resize/stop invalidated this reader. */ }
        }, handler)
    }

    suspend fun capture(notBefore: Long): Bitmap {
        check(!closed) { "Screen sharing has ended. Choose Select in the toolbox to reconnect." }
        // Window removal must reach the compositor before selecting its newest frame.
        delay(200)
        withTimeout(5000) { frames.first { closed || (latestImage != null && latestFrameAt >= notBefore) } }
        check(!closed) { "Screen sharing has ended. Choose Select in the toolbox to reconnect." }
        val image = checkNotNull(latestImage) { "No screen frame is available. Please try again." }
        latestImage = null
        // All image ownership and teardown run on Main to avoid closing a buffer during a copy.
        return image.use {
            val plane = it.planes[0]
            val paddedWidth = it.width + (plane.rowStride - plane.pixelStride * it.width) / plane.pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, it.height, Bitmap.Config.ARGB_8888)
            try {
                padded.copyPixelsFromBuffer(plane.buffer)
                Bitmap.createBitmap(padded, 0, 0, it.width, it.height).also { result -> if (result !== padded) padded.recycle() }
            } catch (e: Exception) {
                padded.recycle()
                throw e
            }
        }
    }

    fun close(stopProjection: Boolean = true) {
        if (closed) return
        closed = true
        latestImage?.close(); latestImage = null
        runCatching { display?.release() }; display = null
        runCatching { reader?.close() }; reader = null
        runCatching { projection.unregisterCallback(callback) }
        if (stopProjection) runCatching { projection.stop() }
        frames.value++
    }
}

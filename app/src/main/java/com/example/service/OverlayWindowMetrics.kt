package com.example.service

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager

/**
 * Window bounds used to clamp overlay geometry.
 *
 * On Android 30+ this asks WindowManager for the current window metrics instead of reading
 * displayMetrics directly. A Service is not a visual context, so the platform reports the
 * whole display here (verified in [com.example.OverlayWindowMetricsTest]); that is a real
 * limitation for split screen, not a silent fix. If a future platform associates the service
 * with a split-screen cell, this call is where the narrower bounds would appear.
 */
object OverlayWindowMetrics {
    fun bounds(context: Context): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val current = runCatching {
                context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            }.getOrNull()
            if (current != null && !current.isEmpty) return Rect(current)
        }
        val metrics = context.resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }
}

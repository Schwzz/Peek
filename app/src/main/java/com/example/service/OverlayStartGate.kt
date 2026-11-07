package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Start-time requirements for the overlay foreground service.
 *
 * The overlay is a foreground service, so Android shows a persistent notification while it
 * runs. On Android 13+ that notification is invisible unless POST_NOTIFICATIONS is granted,
 * which leaves a degraded service that some OEMs kill. Peek refuses to start instead of
 * pretending the overlay is healthy.
 */
object OverlayStartGate {
    const val NOTIFICATION_REQUIRED_MESSAGE =
        "Peek's overlay runs as a foreground service, and Android hides its persistent " +
            "notification without permission. Allow notifications to start the overlay."

    /** Returns the user-facing reason start must be refused, or null when start is allowed. */
    fun notificationBlocker(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) null else NOTIFICATION_REQUIRED_MESSAGE
    }

    fun canStart(context: Context): Boolean = notificationBlocker(context) == null
}

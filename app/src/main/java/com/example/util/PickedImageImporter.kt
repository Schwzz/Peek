package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.service.OverlayStateManager

/** Hold only newly acquired read grants until asynchronous decoding completes. */
object PickedImageImporter {
    fun load(context: Context, uris: List<Uri>) {
        val resolver = context.applicationContext.contentResolver
        val acquired = uris.distinct().filter { uri ->
            val held = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
            !held && runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
        }
        OverlayStateManager.loadImages(context, uris.distinct()) {
            acquired.forEach { uri -> runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        }
    }
}

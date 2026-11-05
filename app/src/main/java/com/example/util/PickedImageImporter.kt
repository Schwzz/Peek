package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.service.OverlayStateManager

/** Hold read grants until decoding finishes, including overlapping imports. */
object PickedImageImporter {
    private data class Lease(var users: Int, val releaseWhenDone: Boolean)
    private val leases = mutableMapOf<Uri, Lease>()

    fun load(context: Context, uris: List<Uri>) {
        val resolver = context.applicationContext.contentResolver
        val unique = uris.distinct()
        synchronized(leases) {
            unique.forEach { uri ->
                val existing = leases[uri]
                if (existing != null) existing.users++
                else {
                    val held = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                    val acquired = !held && runCatching {
                        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }.isSuccess
                    leases[uri] = Lease(1, acquired)
                }
            }
        }
        OverlayStateManager.loadImages(context, unique) {
            synchronized(leases) {
                unique.forEach { uri ->
                    val lease = leases[uri] ?: return@forEach
                    if (--lease.users == 0) {
                        leases.remove(uri)
                        if (lease.releaseWhenDone) runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    }
                }
            }
        }
    }
}

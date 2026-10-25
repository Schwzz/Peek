package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.service.OverlayStateManager

/** Launch the system photo picker without bringing Peek's main task to the foreground. */
class ImagePickerActivity : ComponentActivity() {
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val resolver = applicationContext.contentResolver
            val alreadyHeld = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
            val acquired = !alreadyHeld && runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            // Keep access through decoding after this transient task finishes, then
            // release only the grant acquired here. Some pickers grant access without it.
            OverlayStateManager.loadImage(applicationContext, uri) {
                if (acquired) runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
        }
        else if (OverlayStateManager.isServiceRunning.value) OverlayStateManager.setToolboxExpanded(true)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, ImagePickerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
        }
    }
}

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
    private val picker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(OverlayStateManager.MAX_IMAGES)) { uris ->
        if (uris.isNotEmpty()) com.example.util.PickedImageImporter.load(applicationContext, uris)
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

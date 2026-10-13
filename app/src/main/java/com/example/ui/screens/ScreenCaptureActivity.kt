package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.service.MediaProjectionService
import com.example.service.OverlayStateManager

class ScreenCaptureActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionService.startCapture(
                this@ScreenCaptureActivity,
                result.resultCode,
                result.data!!
            )
        } else {
            OverlayStateManager.setStatusMessage("Screen capture request was cancelled.")
            OverlayStateManager.setPanelExpanded(true)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        captureLauncher.launch(captureIntent)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

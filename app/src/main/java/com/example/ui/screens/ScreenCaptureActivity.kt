package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.service.MediaProjectionService
import com.example.service.OverlayStateManager
import com.example.service.ScreenCaptureHelper
import kotlinx.coroutines.launch

class ScreenCaptureActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intentData = result.data!!
            lifecycleScope.launch {
                try {
                    // Start FGS of type mediaProjection now that user consented
                    MediaProjectionService.start(this@ScreenCaptureActivity)

                    val mediaProjection = mediaProjectionManager.getMediaProjection(
                        result.resultCode,
                        intentData
                    )
                    if (mediaProjection != null) {
                        val bitmap = ScreenCaptureHelper.captureVirtualDisplay(this@ScreenCaptureActivity, mediaProjection)
                        if (bitmap != null) {
                            OverlayStateManager.setCapturedScreenToCrop(bitmap)
                            val selectorIntent = Intent(this@ScreenCaptureActivity, ScreenSelectorActivity::class.java)
                            startActivity(selectorIntent)
                        } else {
                            OverlayStateManager.setErrorMessage("Failed to capture screen buffer. Please try again.")
                            OverlayStateManager.setPanelExpanded(true)
                        }
                    } else {
                        OverlayStateManager.setErrorMessage("MediaProjection could not be initialized.")
                        OverlayStateManager.setPanelExpanded(true)
                    }
                } catch (e: Exception) {
                    OverlayStateManager.setErrorMessage("Capture error: ${e.message}")
                    OverlayStateManager.setPanelExpanded(true)
                } finally {
                    MediaProjectionService.stop(this@ScreenCaptureActivity)
                    finish()
                }
            }
        } else {
            OverlayStateManager.setStatusMessage("Screen capture request was cancelled.")
            OverlayStateManager.setPanelExpanded(true)
            finish()
        }
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

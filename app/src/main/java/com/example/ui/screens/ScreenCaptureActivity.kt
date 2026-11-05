package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.service.MediaProjectionService
import com.example.service.OverlayService
import com.example.service.OverlayStateManager

/** Transient system-consent host, isolated from Peek's main task. Never owns the capture session. */
class ScreenCaptureActivity : ComponentActivity() {
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            runCatching {
                MediaProjectionService.startSession(this, result.resultCode, result.data!!, intent.getBooleanExtra(CAPTURE_WHEN_READY, true))
            }.onFailure {
                OverlayStateManager.setStartingScreenSharing(false)
                OverlayStateManager.setErrorMessage("Could not start screen access: ${it.localizedMessage}")
            }
        } else {
            OverlayStateManager.setStartingScreenSharing(false)
            OverlayStateManager.setStatusMessage("Screen access was cancelled. Choose Select in the toolbox to try again.")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OverlayStateManager.isScreenSharing.value) {
            OverlayStateManager.setStartingScreenSharing(false)
            if (intent.getBooleanExtra(CAPTURE_WHEN_READY, true)) OverlayService.captureScreen(this, delayMs = 350)
            finish()
            return
        }
        OverlayStateManager.setStartingScreenSharing(true)
        if (savedInstanceState == null) {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            // Peek works across apps. Sharing only Peek's own window cannot provide that workflow.
            val consent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            else manager.createScreenCaptureIntent()
            captureLauncher.launch(consent)
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val CAPTURE_WHEN_READY = "capture_when_ready"
        fun launch(context: Context, captureWhenReady: Boolean = true) {
            if (OverlayStateManager.isStartingScreenSharing.value) return
            OverlayStateManager.setStartingScreenSharing(true)
            runCatching {
                context.startActivity(Intent(context, ScreenCaptureActivity::class.java)
                    .putExtra(CAPTURE_WHEN_READY, captureWhenReady)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
            }.onFailure {
                OverlayStateManager.setStartingScreenSharing(false)
                OverlayStateManager.setErrorMessage("Could not request screen access: ${it.localizedMessage}")
            }
        }
    }
}

package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.example.service.MediaProjectionService
import com.example.service.OverlayStateManager

class ScreenCaptureActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var handedToSelector = false

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            runCatching {
                MediaProjectionService.startCapture(this, result.resultCode, result.data!!)
            }.onFailure {
                OverlayStateManager.setErrorMessage("Could not start capture: ${it.localizedMessage}")
                OverlayStateManager.finishCapture()
                finish()
            }
        } else {
            OverlayStateManager.setStatusMessage("Screen capture request was cancelled.")
            OverlayStateManager.finishCapture()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !OverlayStateManager.beginCapture()) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                OverlayStateManager.finishCapture()
                stopService(android.content.Intent(this@ScreenCaptureActivity, MediaProjectionService::class.java))
                finish()
            }
        })
        lifecycleScope.launch {
            combine(OverlayStateManager.capturedScreenToCrop, OverlayStateManager.isCapturing) { bitmap, capturing ->
                bitmap to capturing
            }.collect { (bitmap, capturing) ->
                if (bitmap != null && capturing && !handedToSelector) {
                    handedToSelector = true
                    startActivity(android.content.Intent(this@ScreenCaptureActivity, ScreenSelectorActivity::class.java))
                    finish()
                } else if (!capturing) {
                    finish()
                }
            }
        }
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (savedInstanceState == null) {
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            captureLauncher.launch(captureIntent)
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

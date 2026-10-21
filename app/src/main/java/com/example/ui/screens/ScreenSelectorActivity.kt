package com.example.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.service.OverlayStateManager
import com.example.ui.components.ScreenSelectorView
import com.example.ui.theme.PeekTheme
import com.example.util.BitmapUtils

class ScreenSelectorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PeekTheme {
                BackHandler {
                    OverlayStateManager.finishCapture()
                    finish()
                }
                val capturedBitmap by OverlayStateManager.capturedScreenToCrop.collectAsState()

                if (capturedBitmap != null) {
                    ScreenSelectorView(
                        capturedBitmap = capturedBitmap!!,
                        onSelectionConfirmed = { cropRect ->
                            val cropped = BitmapUtils.crop(capturedBitmap!!, cropRect)
                            OverlayStateManager.setCurrentImage(cropped)
                            OverlayStateManager.finishCapture()
                            finish()
                        },
                        onCancel = {
                            OverlayStateManager.finishCapture()
                            finish()
                        }
                    )
                } else {
                    finish()
                }
            }
        }
    }

    override fun finish() {
        OverlayStateManager.finishCapture()
        super.finish()
        overridePendingTransition(0, 0)
    }
}

package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.example.service.OverlayStateManager
import com.example.ui.screens.MainScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.PeekTheme

class MainActivity : ComponentActivity() {

    private val openSettingsTrigger = mutableStateOf(false)
    private val pickImageTrigger = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            PeekTheme {
                val showSettingsDialog by openSettingsTrigger

                // Image picker launcher for intent-triggered pick
                val photoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia(OverlayStateManager.MAX_IMAGES)
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        com.example.util.PickedImageImporter.load(applicationContext, uris)
                    } else if (OverlayStateManager.isServiceRunning.value) {
                        OverlayStateManager.setPanelExpanded(true)
                    }
                }

                // Request POST_NOTIFICATIONS on Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* no-op */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                LaunchedEffect(pickImageTrigger.value) {
                    if (pickImageTrigger.value) {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                        pickImageTrigger.value = false
                    }
                }

                MainScreen(
                    onOpenSettingsRequested = { openSettingsTrigger.value = true }
                )

                if (showSettingsDialog) {
                    SettingsDialog(
                        onDismiss = { openSettingsTrigger.value = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == ACTION_OPEN_SETTINGS) {
            openSettingsTrigger.value = true
        } else if (intent.action == ACTION_PICK_IMAGE) {
            pickImageTrigger.value = true
        }
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "com.example.action.OPEN_SETTINGS"
        const val ACTION_PICK_IMAGE = "com.example.action.PICK_IMAGE"
    }
}

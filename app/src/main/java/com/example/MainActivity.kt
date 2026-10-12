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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.service.OverlayStateManager
import com.example.ui.screens.MainScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.PeekTheme

class MainActivity : ComponentActivity() {

    private var initialOpenSettings = false
    private var initialPickImage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            PeekTheme {
                var showSettingsDialog by remember { mutableStateOf(initialOpenSettings) }

                // Image picker launcher for intent-triggered pick
                val photoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri: Uri? ->
                    if (uri != null) {
                        runCatching {
                            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val source = ImageDecoder.createSource(contentResolver, uri)
                                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                    decoder.isMutableRequired = true
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            }
                            OverlayStateManager.setCurrentImage(bitmap)
                            OverlayStateManager.setPanelExpanded(true)
                            Toast.makeText(this, "Image loaded into Peek", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            OverlayStateManager.setErrorMessage("Failed to load image: ${it.message}")
                        }
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

                    if (initialPickImage) {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                        initialPickImage = false
                    }
                }

                MainScreen(
                    onOpenSettingsRequested = { showSettingsDialog = true }
                )

                if (showSettingsDialog) {
                    SettingsDialog(
                        onDismiss = { showSettingsDialog = false }
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
            initialOpenSettings = true
        } else if (intent.action == ACTION_PICK_IMAGE) {
            initialPickImage = true
        }
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "com.example.action.OPEN_SETTINGS"
        const val ACTION_PICK_IMAGE = "com.example.action.PICK_IMAGE"
    }
}

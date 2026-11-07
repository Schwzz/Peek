package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.service.OverlayService
import com.example.service.OverlayStateManager

/** Transient host for the RECORD_AUDIO runtime prompt; it never owns recognition. */
class VoicePermissionActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            OverlayService.requestVoiceInput(this)
        } else {
            OverlayStateManager.setErrorMessage(
                "Microphone permission denied. Use the follow-up suggestions instead."
            )
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            OverlayService.requestVoiceInput(this)
            finish()
            return
        }
        if (savedInstanceState == null) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        fun launch(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(context, VoicePermissionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                )
            }.onFailure {
                OverlayStateManager.setErrorMessage(
                    "Could not request microphone permission: ${it.localizedMessage}"
                )
            }
        }
    }
}

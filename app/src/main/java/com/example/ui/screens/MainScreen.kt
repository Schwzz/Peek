package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.api.ApiConfigRepository
import com.example.service.OverlayService
import com.example.service.OverlayStateManager
import com.example.ui.components.OverlayPanelView
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavySurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onOpenSettingsRequested: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ApiConfigRepository.getInstance(context) }
    val config by repo.configFlow.collectAsState()
    val running by OverlayStateManager.isServiceRunning.collectAsState()
    val sharing by OverlayStateManager.isScreenSharing.collectAsState()
    val startingSharing by OverlayStateManager.isStartingScreenSharing.collectAsState()
    val image by OverlayStateManager.currentImage.collectAsState()
    val messages by OverlayStateManager.chatMessages.collectAsState()
    val configured = repo.isConfigured(config)
    var hasPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var showChat by rememberSaveable { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = Settings.canDrawOverlays(context)
                if (!hasPermission && OverlayStateManager.isServiceRunning.value) OverlayService.stop(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(image, running) { if (image != null && !running) showChat = true }
    BackHandler(enabled = showChat) { showChat = false }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            OverlayStateManager.loadImage(context, uri)
            showChat = true
        }
    }
    fun permission() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
    }
    fun startOverApps(captureWhenReady: Boolean) {
        if (!Settings.canDrawOverlays(context)) { permission(); return }
        runCatching {
            OverlayService.start(context)
            // Put Peek's main task away before opening the isolated consent task or capturing.
            (context as? Activity)?.moveTaskToBack(true)
            if (OverlayStateManager.isScreenSharing.value) {
                if (captureWhenReady) OverlayService.captureScreen(context, delayMs = 400)
            } else ScreenCaptureActivity.launch(context, captureWhenReady)
        }.onFailure { OverlayStateManager.setErrorMessage("Could not start Peek: ${it.localizedMessage}") }
    }
    fun capture() { startOverApps(captureWhenReady = true) }
    fun pickImage() { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Scaffold(
        containerColor = DarkNavyBackground,
        topBar = {
            TopAppBar(
                title = { Text(if (showChat) "Conversation" else "Peek", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (showChat) IconButton(onClick = { showChat = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to home")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettingsRequested, modifier = Modifier.testTag("main_settings_button")) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavyBackground)
            )
        }
    ) { padding ->
        if (showChat) {
            OverlayPanelView(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
                isFloating = false,
                onCaptureScreenRequested = ::capture,
                onPickImageRequested = ::pickImage,
                onMinimizeRequested = { showChat = false },
                onCloseRequested = { showChat = false },
                onOpenSettingsRequested = onOpenSettingsRequested,
                onCropImageRequested = { ScreenSelectorActivity.cropCurrentImage(context) }
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).background(CyanPrimary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Visibility, null, tint = CyanPrimary, modifier = Modifier.size(30.dp))
                    }
                    Column {
                        Text("A little clarity, anywhere.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Capture a detail. Ask a question.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = DarkNavySurface), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Floating assistant", style = MaterialTheme.typography.titleMedium)
                                Text(if (sharing) "Screen access ready across apps" else if (running) "Tap the bubble to enable screen access" else "Select a region over any app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (running) "ON" else "OFF", color = if (running) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                when {
                                    running -> OverlayService.stop(context)
                                    !Settings.canDrawOverlays(context) -> permission()
                                    else -> startOverApps(captureWhenReady = false)
                                }
                            },
                            enabled = !startingSharing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("toggle_overlay_service_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (running) "Stop overlay" else "Start Peek")
                        }
                    }
                }
                Text("START WITH", style = MaterialTheme.typography.labelMedium, color = CyanPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = ::capture, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("quick_capture_button"), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.CropFree, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Capture")
                    }
                    OutlinedButton(onClick = ::pickImage, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("quick_pick_image_button"), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Add image")
                    }
                }
                FilledTonalButton(
                    onClick = { showChat = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open_chat_button"), shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, null); Spacer(Modifier.width(10.dp))
                    Text(if (messages.isEmpty()) "Open chat" else "Continue conversation · ${messages.size} messages")
                }
                Text("SETUP", style = MaterialTheme.typography.labelMedium, color = CyanPrimary)
                SetupRow("AI provider", if (configured) "${config.provider.displayName} · ${config.modelName}" else "Add your API key to ask questions", configured, if (configured) "Edit" else "Configure", onOpenSettingsRequested)
                SetupRow("Display over apps", if (hasPermission) "Permission granted" else "Needed for the floating bubble", hasPermission, if (hasPermission) "Manage" else "Grant", ::permission)
                SetupRow("Screen access", if (sharing) "Active while you switch apps" else "Approve once for this session", sharing, if (sharing) "Stop" else "Enable", {
                    if (sharing) context.stopService(Intent(context, com.example.service.MediaProjectionService::class.java))
                    else startOverApps(captureWhenReady = false)
                })
                Text("Tap the bubble to select a screen region. Hold it to open chat. Images are sent to your selected AI provider only when you send a question.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val error by OverlayStateManager.errorMessage.collectAsState()
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, detail: String, ready: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Info, null, tint = if (ready) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

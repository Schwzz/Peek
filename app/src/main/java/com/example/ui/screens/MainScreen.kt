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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { Text(if (showChat) "Conversation" else "Peek", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { if (showChat) IconButton(onClick = { showChat = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to home") } },
                actions = { IconButton(onClick = onOpenSettingsRequested, modifier = Modifier.testTag("main_settings_button")) { Icon(Icons.Default.Tune, "Settings") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        }
    ) { padding ->
        if (showChat) {
            OverlayPanelView(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp), isFloating = false,
                onCaptureScreenRequested = ::capture, onPickImageRequested = ::pickImage,
                onMinimizeRequested = { showChat = false }, onCloseRequested = { showChat = false },
                onOpenSettingsRequested = onOpenSettingsRequested,
                onCropImageRequested = { ScreenSelectorActivity.cropCurrentImage(context) })
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(if (sharing) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(if (sharing) "Screen access active" else if (running) "Overlay ready" else "Screen assistant", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Select something on screen.\nAsk about it without switching apps.", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = {
                        when {
                            running -> OverlayService.stop(context)
                            !Settings.canDrawOverlays(context) -> permission()
                            else -> startOverApps(captureWhenReady = false)
                        }
                    }, enabled = !startingSharing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("toggle_overlay_service_button"), shape = RoundedCornerShape(8.dp)) {
                        Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text(if (running) "Stop overlay" else "Start Peek")
                    }
                    Text("Tap the bubble to select. Hold it for chat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text("TOOLS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    ToolRow("Select screen", "Draw a region over another app", Icons.Default.CropFree, "quick_capture_button", ::capture)
                    ToolRow("Open image", "Choose from your photos", Icons.Default.AddPhotoAlternate, "quick_pick_image_button", ::pickImage)
                    ToolRow(if (messages.isEmpty()) "Conversation" else "Continue conversation", if (messages.isEmpty()) "Ask a question or follow up on an image" else "${messages.size} messages", Icons.Default.ChatBubbleOutline, "open_chat_button") { showChat = true }
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("CONNECTIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SetupRow("Model", if (configured) "${config.provider.displayName} · ${config.modelName}" else "Choose a local or cloud model", configured, "Change", onOpenSettingsRequested)
                    SetupRow("Display over apps", if (hasPermission) "Allowed" else "Permission needed", hasPermission, if (hasPermission) "Manage" else "Allow", ::permission)
                    SetupRow("Screen access", if (sharing) "Active across apps" else "Approve once per session", sharing, if (sharing) "Stop" else "Enable", {
                        if (sharing) context.stopService(Intent(context, com.example.service.MediaProjectionService::class.java)) else startOverApps(false)
                    })
                }
                Text("Screenshots stay on this device until you send a question to your selected model.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val error by OverlayStateManager.errorMessage.collectAsState()
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ToolRow(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SetupRow(title: String, detail: String, ready: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.api.ApiConfigRepository
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.service.OverlayStateManager
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavySurface

@Composable
fun OverlayPanelView(
    modifier: Modifier = Modifier,
    onCaptureScreenRequested: () -> Unit,
    onPickImageRequested: () -> Unit,
    onMinimizeRequested: () -> Unit,
    onCloseRequested: () -> Unit,
    onOpenSettingsRequested: () -> Unit,
    isFloating: Boolean = true,
    onCropImageRequested: () -> Unit = {},
    onMove: (Float, Float) -> Unit = { _, _ -> },
    onResize: (Float, Float) -> Unit = { _, _ -> },
    onGeometryChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentImage by OverlayStateManager.currentImage.collectAsState()
    val messages by OverlayStateManager.chatMessages.collectAsState()
    val loading by OverlayStateManager.isLoading.collectAsState()
    val error by OverlayStateManager.errorMessage.collectAsState()
    val status by OverlayStateManager.statusMessage.collectAsState()
    val prompt by OverlayStateManager.draftPrompt.collectAsState()
    val config by remember { ApiConfigRepository.getInstance(context) }.configFlow.collectAsState()
    val list = rememberLazyListState()
    var menu by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    LaunchedEffect(currentImage) { if (currentImage == null) preview = false }
    LaunchedEffect(messages.size, loading) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex + if (loading) 1 else 0)
    }

    Surface(modifier.fillMaxSize().testTag("overlay_panel_surface"), shape = RoundedCornerShape(22.dp), color = DarkNavySurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 420.dp
        val tiny = maxHeight < 230.dp
        Column(Modifier.fillMaxSize().then(if (isFloating) Modifier else Modifier.imePadding()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isFloating && !tiny) {
                Box(
                    Modifier.fillMaxWidth().height(28.dp).testTag("overlay_drag_handle").pointerInput(onMove) {
                        detectDragGestures(onDragEnd = onGeometryChanged, onDragCancel = onGeometryChanged) { change, amount ->
                            change.consume(); onMove(amount.x, amount.y)
                        }
                    }, contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.width(40.dp).height(4.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)))
                }
            }
            if (!tiny) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Visibility, null, tint = CyanPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (isFloating) "Peek" else config.provider.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(config.modelName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (compact && currentImage != null) IconButton(onClick = { preview = true }) { Icon(Icons.Default.Image, "Preview attached image") }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("overlay_menu_button")) { Icon(Icons.Default.MoreVert, "Conversation actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onOpenSettingsRequested() }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                        DropdownMenuItem(text = { Text("Clear conversation") }, modifier = Modifier.testTag("overlay_clear_button"), onClick = {
                            menu = false; OverlayStateManager.clearChat(); OverlayStateManager.clearImage()
                        }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) })
                        if (isFloating) DropdownMenuItem(text = { Text("Stop overlay") }, onClick = { menu = false; onCloseRequested() }, leadingIcon = { Icon(Icons.Default.Close, null) })
                    }
                }
                if (isFloating) IconButton(onClick = onMinimizeRequested, modifier = Modifier.testTag("overlay_minimize_button")) { Icon(Icons.Default.Remove, "Minimize to bubble") }
            }
            if (preview && currentImage != null) {
                ImagePreviewView(currentImage!!, Modifier.weight(1f), onBack = { preview = false }, onCrop = onCropImageRequested)
            } else {
                if (error != null) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3)
                        IconButton(onClick = { OverlayStateManager.setErrorMessage(null) }) { Icon(Icons.Default.Close, "Dismiss error") }
                    }
                }
                if (!compact) currentImage?.let { bitmap ->
                    Row(Modifier.fillMaxWidth().background(Color(0xFF1E293B), RoundedCornerShape(12.dp)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(bitmap.asImageBitmap(), "Preview attached image", contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).clickable { preview = true }.testTag("image_preview_button"))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Attached image", style = MaterialTheme.typography.labelLarge)
                            Text("${bitmap.width} × ${bitmap.height} · Tap to preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onCropImageRequested, modifier = Modifier.testTag("overlay_recrop_button")) { Icon(Icons.Default.Crop, "Crop attached image") }
                        IconButton(onClick = { OverlayStateManager.clearImage() }, modifier = Modifier.testTag("overlay_remove_image_button")) { Icon(Icons.Default.Close, "Remove image") }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF090D16))) {
                    if (messages.isEmpty() && !loading) {
                        Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ChatBubbleOutline, null, tint = CyanPrimary, modifier = Modifier.size(30.dp))
                            Text("What caught your eye?", style = MaterialTheme.typography.titleMedium)
                            Text("Capture a region, add an image, or type a question.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("chat_messages"), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(messages, key = { it.id }) { message -> ChatMessageBubble(message) { copyText(context, it) } }
                            if (loading) item {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp)); Text("Thinking…", style = MaterialTheme.typography.bodySmall, color = CyanPrimary)
                                }
                            }
                        }
                    }
                }
                if (!compact && (messages.isEmpty() || currentImage != null)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (suggestion in listOf("Explain this", "Extract text", "Summarize", "Translate", "What is this error?", "Solve step-by-step")) {
                            SuggestionChip(onClick = {
                                if (OverlayStateManager.sendPrompt(suggestion, context)) OverlayStateManager.setDraftPrompt("")
                            }, enabled = !loading, label = { Text(suggestion) })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Box {
                        var attachmentMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { attachmentMenu = true }, modifier = Modifier.testTag("overlay_add_image_button")) { Icon(Icons.Default.Add, "Add attachment") }
                        DropdownMenu(expanded = attachmentMenu, onDismissRequest = { attachmentMenu = false }) {
                            DropdownMenuItem(text = { Text("Capture screen region") }, onClick = { attachmentMenu = false; onCaptureScreenRequested() }, modifier = Modifier.testTag("overlay_capture_screen_button"))
                            DropdownMenuItem(text = { Text("Choose image") }, onClick = { attachmentMenu = false; onPickImageRequested() }, modifier = Modifier.testTag("overlay_upload_image_button"))
                        }
                    }
                    OutlinedTextField(
                        value = prompt, onValueChange = OverlayStateManager::setDraftPrompt,
                        placeholder = { Text(if (currentImage == null) "Ask anything…" else "Ask about this image…") },
                        modifier = Modifier.weight(1f).heightIn(max = 120.dp).testTag("overlay_prompt_input"), maxLines = 4,
                        shape = RoundedCornerShape(16.dp), textStyle = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = {
                        if (loading) OverlayStateManager.cancelRequest()
                        else if (OverlayStateManager.sendPrompt(prompt, context)) OverlayStateManager.setDraftPrompt("")
                    }, enabled = loading || prompt.isNotBlank() || currentImage != null, modifier = Modifier.testTag("overlay_send_button")) {
                        Icon(if (loading) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.Send, if (loading) "Stop generating" else "Send question", tint = CyanPrimary)
                    }
                }
                if (!compact && status != null && !loading && error == null) Text(status.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (isFloating && !tiny) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(Modifier.size(36.dp).testTag("overlay_resize_handle").pointerInput(onResize) {
                        detectDragGestures(onDragEnd = onGeometryChanged, onDragCancel = onGeometryChanged) { change, amount -> change.consume(); onResize(amount.x, amount.y) }
                    }, contentAlignment = Alignment.Center) { Icon(Icons.Default.OpenInFull, "Drag to resize", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        }
    }
}

internal fun copyText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Peek response", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

@Composable
fun ChatMessageBubble(msg: ChatMessage, onCopy: (String) -> Unit) {
    val user = msg.sender == MessageSender.USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Text(if (user) "You" else "Peek", style = MaterialTheme.typography.labelSmall, color = if (user) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = if (user) Color(0xFF1E293B) else Color(0xFF111B2D)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (msg.hasImageAttachment) Text("Image attached", style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
                SelectionContainer {
                    if (user || msg.isError) Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    else MarkdownText(msg.text)
                }
                if (msg.isCancelled) Text("Stopped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!user && !msg.isError) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { onCopy(msg.text) }) { Icon(Icons.Default.ContentCopy, "Copy answer", modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

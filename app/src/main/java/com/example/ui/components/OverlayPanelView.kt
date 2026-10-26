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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.api.ApiConfigRepository
import com.example.data.api.VisionAiProvider
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.service.OverlayStateManager

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
    val repo = remember { ApiConfigRepository.getInstance(context) }
    val config by repo.configFlow.collectAsState()
    val images by OverlayStateManager.images.collectAsState()
    val loadingImages by OverlayStateManager.isLoadingImages.collectAsState()
    val currentImage by OverlayStateManager.currentImage.collectAsState()
    val messages by OverlayStateManager.chatMessages.collectAsState()
    val loading by OverlayStateManager.isLoading.collectAsState()
    val error by OverlayStateManager.errorMessage.collectAsState()
    val prompt by OverlayStateManager.draftPrompt.collectAsState()
    val list = rememberLazyListState()
    var menu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    LaunchedEffect(currentImage) { if (currentImage == null) preview = false }
    LaunchedEffect(messages.size, loading) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex + if (loading) 1 else 0)
    }
    fun send() {
        if (loading) OverlayStateManager.cancelRequest()
        else if (OverlayStateManager.sendPrompt(prompt, context)) OverlayStateManager.setDraftPrompt("")
    }

    Surface(modifier.fillMaxSize().testTag("overlay_panel_surface"), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tiny = maxHeight < 240.dp
            val compact = maxHeight < 350.dp
            Column(Modifier.fillMaxSize().then(if (isFloating) Modifier else Modifier.imePadding()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!tiny) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).height(40.dp).testTag("overlay_drag_handle").then(if (!isFloating) Modifier else Modifier.pointerInput(onMove) {
                            detectDragGestures(onDragEnd = onGeometryChanged, onDragCancel = onGeometryChanged) { change, amount -> change.consume(); onMove(amount.x, amount.y) }
                        }), verticalAlignment = Alignment.CenterVertically) {
                            if (isFloating) { Icon(Icons.Default.DragIndicator, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(4.dp)) }
                            Text("Peek", style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = onCaptureScreenRequested, modifier = Modifier.size(40.dp).testTag("overlay_quick_capture_button")) { Icon(Icons.Default.CropFree, "Select screen region", Modifier.size(20.dp)) }
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp).testTag("overlay_menu_button")) { Icon(Icons.Default.MoreHoriz, "Conversation actions", Modifier.size(20.dp)) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Models & appearance") }, onClick = { menu = false; onOpenSettingsRequested() }, leadingIcon = { Icon(Icons.Default.Tune, null) })
                                DropdownMenuItem(text = { Text("New conversation") }, modifier = Modifier.testTag("overlay_clear_button"), onClick = {
                                    menu = false; OverlayStateManager.clearChat(); OverlayStateManager.clearImage()
                                }, leadingIcon = { Icon(Icons.Default.Add, null) })
                                if (isFloating) DropdownMenuItem(text = { Text("Stop overlay") }, onClick = { menu = false; onCloseRequested() }, leadingIcon = { Icon(Icons.Default.Close, null) })
                            }
                        }
                        if (isFloating) IconButton(onClick = onMinimizeRequested, modifier = Modifier.size(40.dp).testTag("overlay_minimize_button")) { Icon(Icons.Default.Remove, "Minimize to bubble", Modifier.size(20.dp)) }
                    }
                    Box {
                        Row(Modifier.fillMaxWidth().clickable(enabled = !loading) { modelMenu = true }.padding(bottom = 4.dp).testTag("overlay_model_picker"), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (config.provider.isLocal) Icons.Default.Computer else Icons.Default.CloudQueue, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(config.modelName.ifBlank { "Choose model" }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(modelMenu, { modelMenu = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                            VisionAiProvider.entries.map { repo.getConfig(it) }.filter { repo.isConfigured(it) }.forEach { saved ->
                                DropdownMenuItem(text = { Column { Text(saved.modelName, maxLines = 1); Text(saved.provider.displayName, style = MaterialTheme.typography.labelSmall) } }, onClick = {
                                    modelMenu = false
                                    runCatching { repo.saveConfig(saved) }.onFailure { OverlayStateManager.setErrorMessage(it.localizedMessage) }
                                })
                            }
                            DropdownMenuItem(text = { Text("Configure models…") }, onClick = { modelMenu = false; onOpenSettingsRequested() })
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (preview && currentImage != null) {
                    ImagePreviewView(currentImage!!, Modifier.weight(1f), onBack = { preview = false }, onCrop = onCropImageRequested)
                } else {
                    error?.let {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = if (tiny) 1 else 3, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { OverlayStateManager.setErrorMessage(null) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Dismiss error", Modifier.size(16.dp)) }
                        }
                    }
                    if (loadingImages) Text("Loading images…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (images.isNotEmpty() && !tiny) {
                        Row(Modifier.fillMaxWidth().testTag("attachment_strip"), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                images.forEachIndexed { index, bitmap ->
                                    Box(Modifier.size(56.dp).border(1.dp, if (bitmap === currentImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))) {
                                        Image(bitmap.asImageBitmap(), "Preview image ${index + 1}", contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).clickable {
                                                OverlayStateManager.selectImage(bitmap); preview = true
                                            }.testTag("attachment_$index"))
                                        Surface(Modifier.align(Alignment.TopEnd), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = RoundedCornerShape(bottomStart = 6.dp)) {
                                            IconButton(onClick = { OverlayStateManager.removeImage(bitmap) }, modifier = Modifier.size(32.dp).testTag("remove_attachment_$index")) {
                                                Icon(Icons.Default.Close, "Remove image ${index + 1}", Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${images.size}/${OverlayStateManager.MAX_IMAGES}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = onCropImageRequested, modifier = Modifier.size(36.dp).testTag("overlay_recrop_button")) {
                                    Icon(Icons.Default.Crop, "Crop selected image", Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (messages.isEmpty() && !loading) {
                            Column(Modifier.align(Alignment.TopStart).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (currentImage == null) "No selection yet" else "${images.size} image(s) ready", style = MaterialTheme.typography.titleSmall)
                                Text(if (currentImage == null) "Select a screen region, open an image, or type a question." else "Ask a question or compare your attached images.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("chat_messages"), contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(messages, key = { it.id }) { message -> ChatMessageBubble(message) { copyText(context, it) } }
                                if (loading) item {
                                    Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp); Spacer(Modifier.width(8.dp)); Text("Working…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                    if (!compact && messages.isEmpty() && currentImage != null) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Explain", "Read text", "Translate").forEach { tool ->
                                TextButton(onClick = { if (OverlayStateManager.sendPrompt(if (tool == "Read text") "Extract the text from this image." else "$tool this image.", context)) OverlayStateManager.setDraftPrompt("") }, enabled = !loading, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(tool, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = prompt, onValueChange = OverlayStateManager::setDraftPrompt,
                            placeholder = { Text(if (currentImage == null) "Ask a question" else "Ask about these images", style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.weight(1f).heightIn(max = 100.dp).testTag("overlay_prompt_input"), maxLines = 3,
                            shape = RoundedCornerShape(8.dp), textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }))
                        IconButton(onClick = ::send, enabled = loading || (!loadingImages && (prompt.isNotBlank() || images.isNotEmpty())), modifier = Modifier.testTag("overlay_send_button")) {
                            Icon(if (loading) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send, if (loading) "Stop generating" else "Send question", Modifier.size(20.dp))
                        }
                    }
                }
                if (!tiny) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        var attachments by remember { mutableStateOf(false) }
                        TextButton(onClick = { attachments = true }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.testTag("overlay_add_image_button")) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Attach", style = MaterialTheme.typography.labelSmall) }
                        DropdownMenu(attachments, { attachments = false }) {
                            DropdownMenuItem(text = { Text("Select screen region") }, onClick = { attachments = false; onCaptureScreenRequested() }, modifier = Modifier.testTag("overlay_capture_screen_button"))
                            DropdownMenuItem(text = { Text("Add images") }, onClick = { attachments = false; onPickImageRequested() }, modifier = Modifier.testTag("overlay_upload_image_button"))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (isFloating) Row(Modifier.width(76.dp).height(32.dp).testTag("overlay_resize_handle").pointerInput(onResize) {
                        detectDragGestures(onDragEnd = onGeometryChanged, onDragCancel = onGeometryChanged) { change, amount -> change.consume(); onResize(amount.x, amount.y) }
                    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        Text("Resize", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.SouthEast, "Drag to resize chat", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(if (user) "You" else "Peek", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = if (user) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent) {
            Column(Modifier.padding(if (user) 10.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (msg.hasImageAttachment) Text(if (msg.imageCount == 1) "1 image attached" else "${msg.imageCount} images attached", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    if (user || msg.isError) Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    else MarkdownText(msg.text)
                }
                if (msg.isCancelled) Text("Stopped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!user && !msg.isError) TextButton(onClick = { onCopy(msg.text) }, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Copy answer", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.ApiConfigRepository
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.service.OverlayStateManager
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DarkNavySurfaceElevated
import com.example.ui.theme.DarkNavySurfaceVariant
import com.example.ui.theme.PurpleAccent

@Composable
fun OverlayPanelView(
    modifier: Modifier = Modifier,
    onCaptureScreenRequested: () -> Unit,
    onPickImageRequested: () -> Unit,
    onMinimizeRequested: () -> Unit,
    onCloseRequested: () -> Unit,
    onOpenSettingsRequested: () -> Unit
) {
    val context = LocalContext.current
    val currentImage by OverlayStateManager.currentImage.collectAsState()
    val chatMessages by OverlayStateManager.chatMessages.collectAsState()
    val isLoading by OverlayStateManager.isLoading.collectAsState()
    val errorMessage by OverlayStateManager.errorMessage.collectAsState()
    val statusMessage by OverlayStateManager.statusMessage.collectAsState()

    val configRepo = remember { ApiConfigRepository.getInstance(context) }
    val apiConfig by configRepo.configFlow.collectAsState()
    val isConfigured = remember(apiConfig) { configRepo.isConfigured(apiConfig) }

    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size, isLoading) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("overlay_panel_surface"),
        shape = RoundedCornerShape(24.dp),
        color = DarkNavySurface,
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00363F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Peek Eye",
                            tint = CyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Peek",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isConfigured) Color(0xFF064E3B) else Color(0xFF7F1D1D)
                            ) {
                                Text(
                                    text = apiConfig.modelName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isConfigured) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenSettingsRequested,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("overlay_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (chatMessages.isNotEmpty() || currentImage != null) {
                        IconButton(
                            onClick = {
                                OverlayStateManager.clearChat()
                                OverlayStateManager.clearImage()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("overlay_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onMinimizeRequested,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("overlay_minimize_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Minimize",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onCloseRequested,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("overlay_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Error or Status message banner if any
            AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x33EF4444),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66EF4444)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { OverlayStateManager.setErrorMessage(null) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Image Source Section
            if (currentImage != null) {
                // Attached Image Thumbnail Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkNavySurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bitmap = currentImage!!
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Attached image thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, CyanPrimary, RoundedCornerShape(8.dp))
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Image",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${bitmap.width} × ${bitmap.height} px",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        // Re-crop button
                        IconButton(
                            onClick = onCaptureScreenRequested,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("overlay_recrop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Crop,
                                contentDescription = "Re-capture screen region",
                                tint = CyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Replace with image picker
                        IconButton(
                            onClick = onPickImageRequested,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("overlay_replace_image_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Replace Image",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Remove
                        IconButton(
                            onClick = { OverlayStateManager.clearImage() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("overlay_remove_image_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Image",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                // Dual Capture / Add Image buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onCaptureScreenRequested() }
                            .testTag("overlay_capture_screen_button"),
                        color = Color(0xFF00363F),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropFree,
                                contentDescription = "Capture Screen",
                                tint = CyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Select Region",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPickImageRequested() }
                            .testTag("overlay_upload_image_button"),
                        color = DarkNavySurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Upload Image",
                                tint = PurpleAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+ Add Image",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chat Messages Stream
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 120.dp, max = 280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF090D16))
                    .padding(8.dp)
            ) {
                if (chatMessages.isEmpty() && !isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Peek AI Ready",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Peek is listening",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Select any part of your screen or add an image, then ask a question.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp),
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chatMessages, key = { it.id }) { msg ->
                            ChatMessageBubble(msg = msg, onCopy = { text ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Peek AI Response", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                            })
                        }

                        if (isLoading) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = CyanPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Peek is analyzing...",
                                        fontSize = 12.sp,
                                        color = CyanPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val promptSuggestions = listOf(
                    "Explain this",
                    "Extract text",
                    "Summarize",
                    "Translate",
                    "What is this error?",
                    "Solve step-by-step"
                )
                for (suggestion in promptSuggestions) {
                    FilterChip(
                        selected = false,
                        enabled = !isLoading,
                        onClick = {
                            if (!isLoading) {
                                inputPrompt = suggestion
                                OverlayStateManager.sendPrompt(suggestion, context)
                                inputPrompt = ""
                            }
                        },
                        label = { Text(suggestion, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DarkNavySurfaceElevated,
                            labelColor = Color(0xFFCBD5E1),
                            disabledContainerColor = DarkNavySurfaceElevated.copy(alpha = 0.5f),
                            disabledLabelColor = Color(0xFF64748B)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = !isLoading,
                            selected = false,
                            borderColor = Color(0xFF334155),
                            disabledBorderColor = Color(0xFF1E293B)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Prompt Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick + Gallery button
                IconButton(
                    onClick = onPickImageRequested,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("overlay_add_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add image",
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = {
                        Text(
                            text = if (currentImage != null) "Ask about this image..." else "Ask a question...",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("overlay_prompt_input"),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = DarkNavySurfaceElevated,
                        unfocusedContainerColor = DarkNavySurfaceElevated
                    ),
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        val promptToSend = inputPrompt
                        inputPrompt = ""
                        OverlayStateManager.sendPrompt(promptToSend, context)
                    },
                    enabled = !isLoading && (inputPrompt.isNotBlank() || currentImage != null),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (!isLoading && (inputPrompt.isNotBlank() || currentImage != null)) CyanPrimary else Color(0xFF334155)
                        )
                        .testTag("overlay_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (!isLoading && (inputPrompt.isNotBlank() || currentImage != null)) Color(0xFF00363F) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    msg: ChatMessage,
    onCopy: (String) -> Unit
) {
    val isUser = msg.sender == MessageSender.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isUser) "You" else "Peek AI",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUser) CyanPrimary else PurpleAccent
            )
            if (msg.hasImageAttachment) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "• [Image]",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 12.dp
            ),
            color = when {
                msg.isError -> Color(0x33EF4444)
                isUser -> Color(0xFF1E293B)
                else -> DarkNavySurfaceVariant
            },
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (msg.isError) Color(0x66EF4444) else Color(0xFF334155)
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                SelectionContainer {
                    Text(
                        text = msg.text,
                        fontSize = 12.5.sp,
                        color = if (msg.isError) Color(0xFFFCA5A5) else Color(0xFFF1F5F9),
                        lineHeight = 17.sp
                    )
                }

                if (!isUser && !msg.isError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { onCopy(msg.text) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy message",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.service.OverlayStateManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingBubbleView(modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    onDrag: (Float, Float) -> Unit = { _, _ -> }, onDragEnd: () -> Unit = {}) {
    val loading by OverlayStateManager.isLoading.collectAsState()
    val image by OverlayStateManager.currentImage.collectAsState()
    Box(modifier.size(60.dp).pointerInput(onDrag) {
        detectDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, amount -> change.consume(); onDrag(amount.x, amount.y) }
    }.combinedClickable(onClick = onClick, onLongClick = onLongClick).testTag("floating_bubble_button"), contentAlignment = Alignment.Center) {
        Surface(Modifier.size(48.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 3.dp) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CropFree, "Peek: tap to select screen, hold for chat", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                if (loading) CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 1.5.dp)
                else if (image != null) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
    }
}

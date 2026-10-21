package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun ImagePreviewView(bitmap: Bitmap, modifier: Modifier = Modifier, onBack: () -> Unit, onCrop: () -> Unit) {
    var zoom by remember(bitmap) { mutableStateOf(1f) }
    var pan by remember(bitmap) { mutableStateOf(Offset.Zero) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pinch to zoom · Drag to inspect", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black).clipToBounds().pointerInput(bitmap) {
            detectTransformGestures { _, delta, scale, _ ->
                zoom = (zoom * scale).coerceIn(1f, 6f)
                pan = if (zoom == 1f) Offset.Zero else Offset(
                    (pan.x + delta.x).coerceIn(-size.width * (zoom - 1f) / 2, size.width * (zoom - 1f) / 2),
                    (pan.y + delta.y).coerceIn(-size.height * (zoom - 1f) / 2, size.height * (zoom - 1f) / 2)
                )
            }
        }, contentAlignment = Alignment.Center) {
            Image(bitmap.asImageBitmap(), "Attached image preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("image_preview_back")) { Text("Back") }
            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Reset zoom") }
            Button(onClick = onCrop, modifier = Modifier.testTag("crop_preview_button")) { Text("Crop") }
        }
    }
}

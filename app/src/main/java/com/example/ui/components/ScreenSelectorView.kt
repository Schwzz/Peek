package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanPrimary
import kotlin.math.max
import kotlin.math.min

@Composable
fun ScreenSelectorView(
    capturedBitmap: Bitmap,
    onSelectionConfirmed: (Rect) -> Unit,
    onCancel: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // Initial selection centered in middle third of screen
        var rectLeft by remember(screenWidthPx, screenHeightPx) { mutableStateOf(screenWidthPx * 0.15f) }
        var rectTop by remember(screenWidthPx, screenHeightPx) { mutableStateOf(screenHeightPx * 0.30f) }
        var rectRight by remember(screenWidthPx, screenHeightPx) { mutableStateOf(screenWidthPx * 0.85f) }
        var rectBottom by remember(screenWidthPx, screenHeightPx) { mutableStateOf(screenHeightPx * 0.65f) }

        var dragMode by remember { mutableStateOf<DragMode?>(null) }
        val handleRadius = 36f

        val imageBitmap = remember(capturedBitmap) { capturedBitmap.asImageBitmap() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(screenWidthPx, screenHeightPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val l = min(rectLeft, rectRight)
                            val r = max(rectLeft, rectRight)
                            val t = min(rectTop, rectBottom)
                            val b = max(rectTop, rectBottom)

                            dragMode = when {
                                (offset - Offset(l, t)).getDistance() <= handleRadius * 1.5f -> DragMode.TOP_LEFT
                                (offset - Offset(r, t)).getDistance() <= handleRadius * 1.5f -> DragMode.TOP_RIGHT
                                (offset - Offset(l, b)).getDistance() <= handleRadius * 1.5f -> DragMode.BOTTOM_LEFT
                                (offset - Offset(r, b)).getDistance() <= handleRadius * 1.5f -> DragMode.BOTTOM_RIGHT
                                offset.x in l..r && offset.y in t..b -> DragMode.MOVE
                                else -> {
                                    // Start drawing a new rectangle
                                    rectLeft = offset.x
                                    rectTop = offset.y
                                    rectRight = offset.x
                                    rectBottom = offset.y
                                    DragMode.NEW
                                }
                            }
                        },
                        onDragEnd = {
                            // Normalize coordinates
                            val l = min(rectLeft, rectRight)
                            val r = max(rectLeft, rectRight)
                            val t = min(rectTop, rectBottom)
                            val b = max(rectTop, rectBottom)
                            rectLeft = l
                            rectRight = r
                            rectTop = t
                            rectBottom = b
                            dragMode = null
                        },
                        onDragCancel = { dragMode = null },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            when (dragMode) {
                                DragMode.MOVE -> {
                                    val dx = dragAmount.x
                                    val dy = dragAmount.y
                                    val width = rectRight - rectLeft
                                    val height = rectBottom - rectTop
                                    val newLeft = (rectLeft + dx).coerceIn(0f, screenWidthPx - width)
                                    val newTop = (rectTop + dy).coerceIn(0f, screenHeightPx - height)
                                    rectLeft = newLeft
                                    rectRight = newLeft + width
                                    rectTop = newTop
                                    rectBottom = newTop + height
                                }
                                DragMode.TOP_LEFT -> {
                                    rectLeft = (rectLeft + dragAmount.x).coerceIn(0f, (rectRight - 40f).coerceAtLeast(0f))
                                    rectTop = (rectTop + dragAmount.y).coerceIn(0f, (rectBottom - 40f).coerceAtLeast(0f))
                                }
                                DragMode.TOP_RIGHT -> {
                                    rectRight = (rectRight + dragAmount.x).coerceIn((rectLeft + 40f).coerceAtMost(screenWidthPx), screenWidthPx)
                                    rectTop = (rectTop + dragAmount.y).coerceIn(0f, (rectBottom - 40f).coerceAtLeast(0f))
                                }
                                DragMode.BOTTOM_LEFT -> {
                                    rectLeft = (rectLeft + dragAmount.x).coerceIn(0f, (rectRight - 40f).coerceAtLeast(0f))
                                    rectBottom = (rectBottom + dragAmount.y).coerceIn((rectTop + 40f).coerceAtMost(screenHeightPx), screenHeightPx)
                                }
                                DragMode.BOTTOM_RIGHT -> {
                                    rectRight = (rectRight + dragAmount.x).coerceIn((rectLeft + 40f).coerceAtMost(screenWidthPx), screenWidthPx)
                                    rectBottom = (rectBottom + dragAmount.y).coerceIn((rectTop + 40f).coerceAtMost(screenHeightPx), screenHeightPx)
                                }
                                DragMode.NEW -> {
                                    rectRight = change.position.x.coerceIn(0f, screenWidthPx)
                                    rectBottom = change.position.y.coerceIn(0f, screenHeightPx)
                                }
                                null -> {}
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw background screenshot
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )

                val l = min(rectLeft, rectRight)
                val r = max(rectLeft, rectRight)
                val t = min(rectTop, rectBottom)
                val b = max(rectTop, rectBottom)
                val rectW = max(1f, r - l)
                val rectH = max(1f, b - t)

                // Darken surrounding area outside the selection
                // Top
                drawRect(color = Color(0x99000000), topLeft = Offset.Zero, size = Size(size.width, t))
                // Bottom
                drawRect(color = Color(0x99000000), topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
                // Left
                drawRect(color = Color(0x99000000), topLeft = Offset(0f, t), size = Size(l, rectH))
                // Right
                drawRect(color = Color(0x99000000), topLeft = Offset(r, t), size = Size(size.width - r, rectH))

                // Selection border
                drawRect(
                    color = CyanPrimary,
                    topLeft = Offset(l, t),
                    size = Size(rectW, rectH),
                    style = Stroke(width = 4f)
                )

                // 3x3 Grid guides inside selection box
                val oneThirdW = rectW / 3f
                val oneThirdH = rectH / 3f
                drawLine(
                    color = Color(0x6600E5FF),
                    start = Offset(l + oneThirdW, t),
                    end = Offset(l + oneThirdW, b),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color(0x6600E5FF),
                    start = Offset(l + oneThirdW * 2, t),
                    end = Offset(l + oneThirdW * 2, b),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color(0x6600E5FF),
                    start = Offset(l, t + oneThirdH),
                    end = Offset(r, t + oneThirdH),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color(0x6600E5FF),
                    start = Offset(l, t + oneThirdH * 2),
                    end = Offset(r, t + oneThirdH * 2),
                    strokeWidth = 1.5f
                )

                // Corner handles
                val corners = listOf(
                    Offset(l, t),
                    Offset(r, t),
                    Offset(l, b),
                    Offset(r, b)
                )
                for (corner in corners) {
                    drawCircle(color = CyanPrimary, radius = 14f, center = corner)
                    drawCircle(color = Color.White, radius = 7f, center = corner)
                }
            }

            // Top Control Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xDD0F172A),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = "Crop mode",
                            tint = CyanPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Select Region to Analyze",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            val selW = (max(rectLeft, rectRight) - min(rectLeft, rectRight)).toInt()
                            val selH = (max(rectBottom, rectTop) - min(rectTop, rectBottom)).toInt()
                            Text(
                                text = "Drag handles or anywhere on screen ($selW × $selH px)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            rectLeft = 0f
                            rectTop = 0f
                            rectRight = screenWidthPx
                            rectBottom = screenHeightPx
                        },
                        modifier = Modifier.testTag("select_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full Screen",
                            tint = CyanPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Full", color = Color.White)
                    }
                }
            }

            // Bottom Actions Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xDD0F172A),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_crop_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val l = min(rectLeft, rectRight)
                            val r = max(rectLeft, rectRight)
                            val t = min(rectTop, rectBottom)
                            val b = max(rectTop, rectBottom)

                            // Scale to actual bitmap dimensions
                            val scaleX = capturedBitmap.width.toFloat() / screenWidthPx
                            val scaleY = capturedBitmap.height.toFloat() / screenHeightPx

                            val cropRect = Rect(
                                (l * scaleX).toInt().coerceIn(0, capturedBitmap.width - 1),
                                (t * scaleY).toInt().coerceIn(0, capturedBitmap.height - 1),
                                (r * scaleX).toInt().coerceIn(1, capturedBitmap.width),
                                (b * scaleY).toInt().coerceIn(1, capturedBitmap.height)
                            )
                            onSelectionConfirmed(cropRect)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        enabled = kotlin.math.abs(rectRight - rectLeft) >= 40f && kotlin.math.abs(rectBottom - rectTop) >= 40f,
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("confirm_crop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm",
                            tint = Color(0xFF00363F)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Crop & Ask Peek", color = Color(0xFF00363F))
                    }
                }
            }
        }
    }
}

private enum class DragMode {
    MOVE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    NEW
}

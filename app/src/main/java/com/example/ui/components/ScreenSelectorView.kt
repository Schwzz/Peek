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
import androidx.compose.material3.TextButton
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
import com.example.util.CropGeometry
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

        val fit = CropGeometry.fit(capturedBitmap.width, capturedBitmap.height, screenWidthPx, screenHeightPx)
        var zoom by remember(screenWidthPx, screenHeightPx) { mutableStateOf(1f) }
        var pan by remember(screenWidthPx, screenHeightPx) { mutableStateOf(Offset.Zero) }
        var panMode by remember { mutableStateOf(false) }
        val transform = CropGeometry.fit(capturedBitmap.width, capturedBitmap.height, screenWidthPx, screenHeightPx, zoom, pan.x, pan.y)
        val latestTransform by androidx.compose.runtime.rememberUpdatedState(transform)
        // Selection is mapped through the fitted image instead of stretching the screenshot.
        var rectLeft by remember(screenWidthPx, screenHeightPx) { mutableStateOf(fit.left + fit.width * 0.15f) }
        var rectTop by remember(screenWidthPx, screenHeightPx) { mutableStateOf(fit.top + fit.height * 0.30f) }
        var rectRight by remember(screenWidthPx, screenHeightPx) { mutableStateOf(fit.left + fit.width * 0.85f) }
        var rectBottom by remember(screenWidthPx, screenHeightPx) { mutableStateOf(fit.top + fit.height * 0.65f) }

        var dragMode by remember { mutableStateOf<DragMode?>(null) }
        val handleRadius = 36f

        val imageBitmap = remember(capturedBitmap) { capturedBitmap.asImageBitmap() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(screenWidthPx, screenHeightPx, panMode, zoom) {
                    detectDragGestures(
                        onDragStart = dragStart@ { offset ->
                            if (panMode) return@dragStart
                            val l = min(rectLeft, rectRight)
                            val r = max(rectLeft, rectRight)
                            val t = min(rectTop, rectBottom)
                            val b = max(rectTop, rectBottom)
                            rectLeft = l.coerceIn(0f, screenWidthPx)
                            rectRight = r.coerceIn(rectLeft, screenWidthPx)
                            rectTop = t.coerceIn(0f, screenHeightPx)
                            rectBottom = b.coerceIn(rectTop, screenHeightPx)

                            dragMode = when {
                                (offset - Offset(l, t)).getDistance() <= handleRadius * 1.5f -> DragMode.TOP_LEFT
                                (offset - Offset(r, t)).getDistance() <= handleRadius * 1.5f -> DragMode.TOP_RIGHT
                                (offset - Offset(l, b)).getDistance() <= handleRadius * 1.5f -> DragMode.BOTTOM_LEFT
                                (offset - Offset(r, b)).getDistance() <= handleRadius * 1.5f -> DragMode.BOTTOM_RIGHT
                                offset.x in l..r && offset.y in t..b -> DragMode.MOVE
                                else -> {
                                    // Start drawing a new rectangle
                                    rectLeft = offset.x.coerceIn(0f, screenWidthPx)
                                    rectTop = offset.y.coerceIn(0f, screenHeightPx)
                                    rectRight = rectLeft
                                    rectBottom = rectTop
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
                        onDrag = drag@ { change, dragAmount ->
                            change.consume()
                            if (panMode) {
                                pan = Offset((pan.x + dragAmount.x).coerceIn(-latestTransform.width / 2, latestTransform.width / 2),
                                    (pan.y + dragAmount.y).coerceIn(-latestTransform.height / 2, latestTransform.height / 2))
                                return@drag
                            }
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
                    dstOffset = IntOffset(transform.left.toInt(), transform.top.toInt()),
                    dstSize = IntSize(transform.width.toInt().coerceAtLeast(1), transform.height.toInt().coerceAtLeast(1))
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
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("Select a region", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(modifier = Modifier.width(44.dp), onClick = { zoom = (zoom / 1.5f).coerceAtLeast(1f); if (zoom == 1f) pan = Offset.Zero }) { Text("−", color = CyanPrimary) }
                        Text("${(zoom * 100).toInt()}%", color = Color.White)
                        TextButton(modifier = Modifier.width(44.dp), onClick = { zoom = (zoom * 1.5f).coerceAtMost(6f) }) { Text("+", color = CyanPrimary) }
                        TextButton(onClick = { panMode = !panMode }) { Text(if (panMode) "Select" else "Move", color = CyanPrimary) }
                        TextButton(onClick = {
                            zoom = 1f; pan = Offset.Zero
                            rectLeft = fit.left; rectTop = fit.top
                            rectRight = fit.left + fit.width; rectBottom = fit.top + fit.height
                        }, modifier = Modifier.testTag("select_fullscreen_button")) { Text("Full", color = CyanPrimary) }
                    }
                    Text(if (panMode) "Drag the image. Tap Select to adjust the region." else "Drag a region or adjust its corners. Use + for detail.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
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

                            val crop = CropGeometry.map(l, t, r, b, transform, capturedBitmap.width, capturedBitmap.height)
                            val cropRect = Rect(crop.left, crop.top, crop.right, crop.bottom)
                            onSelectionConfirmed(cropRect)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        enabled = kotlin.math.abs(rectRight - rectLeft) >= 2f && kotlin.math.abs(rectBottom - rectTop) >= 2f &&
                            max(rectLeft, rectRight) > transform.left && min(rectLeft, rectRight) < transform.left + transform.width &&
                            max(rectTop, rectBottom) > transform.top && min(rectTop, rectBottom) < transform.top + transform.height,
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
                        Text("Use selection", color = Color(0xFF00363F))
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

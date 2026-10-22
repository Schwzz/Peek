package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.service.OverlayStateManager
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavySurface

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingBubbleView(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    val isLoading by OverlayStateManager.isLoading.collectAsState()
    val hasImage = OverlayStateManager.currentImage.collectAsState().value != null

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isLoading) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(60.dp)
            .pointerInput(onDrag) {
                detectDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, amount ->
                    change.consume(); onDrag(amount.x, amount.y)
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("floating_bubble_button"),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow when loading
        if (isLoading) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(CyanPrimary.copy(alpha = 0.25f))
            )
        }

        Surface(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(2.dp, CyanPrimary, CircleShape),
            color = DarkNavySurface,
            shadowElevation = 12.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Peek: tap to select screen, hold for chat",
                    tint = CyanPrimary,
                    modifier = Modifier.size(28.dp)
                )

                // Has image attached indicator
                if (hasImage) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF70E000))
                            .border(1.5.dp, DarkNavySurface, CircleShape)
                    )
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A narrow action rail beside the floating button. Tap the button or outside to close. */
@Composable
fun OverlayToolboxView(
    onSelect: () -> Unit,
    onAddImage: () -> Unit,
    onChat: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.testTag("overlay_toolbox"), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(8.dp)) {
            ToolboxAction("Select", "Select screen", Icons.Default.CropFree, onSelect, "toolbox_select")
            ToolboxAction("Images", "Add images", Icons.Default.AddPhotoAlternate, onAddImage, "toolbox_image")
            ToolboxAction("Chat", "Open chat", Icons.Default.ChatBubbleOutline, onChat, "toolbox_chat")
            ToolboxAction("Stop", "Stop overlay", Icons.Default.PowerSettingsNew, onStop, "toolbox_stop", MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ToolboxAction(label: String, description: String, icon: ImageVector, onClick: () -> Unit, tag: String,
                          tint: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp).testTag(tag).semantics { contentDescription = description },
        shape = RoundedCornerShape(8.dp), color = Color.Transparent) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = tint)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = tint)
        }
    }
}

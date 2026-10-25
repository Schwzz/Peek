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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun OverlayToolboxView(
    onSelect: () -> Unit,
    onAddImage: () -> Unit,
    onChat: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.testTag("overlay_toolbox"), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_peek_mark), null, Modifier.padding(start = 8.dp).size(18.dp))
                Text("Peek", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Tune, "Toolbox settings", Modifier.size(18.dp)) }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, "Close toolbox", Modifier.size(18.dp)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolboxAction("Select", "Select screen", Icons.Default.CropFree, onSelect, Modifier.weight(1f).testTag("toolbox_select"))
                ToolboxAction("Image", "Add image", Icons.Default.AddPhotoAlternate, onAddImage, Modifier.weight(1f).testTag("toolbox_image"))
                ToolboxAction("Chat", "Open chat", Icons.Default.ChatBubbleOutline, onChat, Modifier.weight(1f).testTag("toolbox_chat"))
            }
        }
    }
}

@Composable
private fun ToolboxAction(label: String, description: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier.height(64.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(8.dp), color = Color.Transparent) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

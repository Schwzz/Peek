package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.R
import com.example.service.OverlayPreferences
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.api.*
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ApiConfigRepository.getInstance(context) }
    val catalog = remember { ModelCatalog() }
    var draft by remember { mutableStateOf(repo.getConfig()) }
    val drafts = remember { mutableMapOf<VisionAiProvider, ApiConfig>() }
    var providerMenu by remember { mutableStateOf(false) }
    var revealKey by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(emptyList<RemoteModel>()) }
    var showModels by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val appearance = remember { ThemePreference.get(context) }
    val theme by appearance.mode.collectAsState()
    val overlayPreferences = remember { OverlayPreferences.getInstance(context) }
    val buttonOpacity by overlayPreferences.buttonOpacity.collectAsState()

    fun change(next: ApiConfig) {
        job?.cancel(); busy = false; status = null
        if (next.provider != draft.provider || next.baseUrl != draft.baseUrl || next.apiKey != draft.apiKey) {
            models = emptyList(); showModels = false; search = ""
        }
        draft = next
    }
    fun test(image: Boolean) {
        val snapshot = draft
        busy = true; status = null
        job = scope.launch {
            val result = VisionAiRepository.getInstance(context).testConnection(snapshot, testImage = image)
            if (draft != snapshot) return@launch
            busy = false; failed = result.isFailure
            result.onSuccess {
                val recognized = Regex("\\bblue\\b", RegexOption.IGNORE_CASE).containsMatchIn(it)
                if (image && recognized) draft = draft.copy(supportsImages = true)
                failed = image && !recognized
                status = if (!image) "Text connection works. Model response: $it"
                    else if (recognized) "Image test passed: the blue swatch was recognized."
                    else "Image accepted, but the swatch was not recognized. Model response: $it"
            }.onFailure { status = it.localizedMessage ?: "Could not connect." }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding().widthIn(max = 560.dp).testTag("settings_dialog_surface"), color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Models & appearance", style = MaterialTheme.typography.titleLarge)
                        Text("Choose where your questions go", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("settings_close_button")) { Icon(Icons.Default.Close, "Close settings") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Connection", style = MaterialTheme.typography.titleSmall)
                    Box {
                        OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("provider_picker"), shape = RoundedCornerShape(8.dp)) {
                            Icon(if (draft.provider.isLocal) Icons.Default.Computer else Icons.Default.CloudQueue, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(draft.provider.displayName, Modifier.weight(1f))
                            Icon(Icons.Default.ExpandMore, null)
                        }
                        DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }, modifier = Modifier.heightIn(max = 400.dp)) {
                            for ((group, providers) in listOf(
                                "Cloud" to VisionAiProvider.entries.filter { !it.isLocal && it != VisionAiProvider.OPENAI_COMPATIBLE },
                                "Local servers" to VisionAiProvider.entries.filter { it.isLocal },
                                "Other" to listOf(VisionAiProvider.OPENAI_COMPATIBLE)
                            )) {
                                Text(group, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                providers.forEach { provider ->
                                    DropdownMenuItem(text = { Text(provider.displayName) }, modifier = Modifier.testTag("provider_${provider.name.lowercase()}_tab"),
                                        trailingIcon = { if (draft.provider == provider) Icon(Icons.Default.Check, null) }, onClick = {
                                            drafts[draft.provider] = draft
                                            change(drafts[provider] ?: repo.getConfig(provider))
                                            revealKey = false; providerMenu = false
                                        })
                                }
                            }
                        }
                    }
                    if (draft.provider.isLocal) {
                        Text("Run ${draft.provider.displayName} on your computer and enable LAN access. Connect the phone to the same Wi-Fi, then enter the computer's IP address below. Localhost refers to this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (draft.provider == VisionAiProvider.OPENAI_COMPATIBLE) {
                        Text("For a compatible server such as vLLM, llama.cpp, or another hosted API. Use its API base URL.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (draft.provider.supportsCustomUrl) OutlinedTextField(
                        value = draft.baseUrl, onValueChange = { change(draft.copy(baseUrl = it, supportsImages = null)) },
                        label = { Text("Server URL") }, placeholder = { Text(if (draft.provider == VisionAiProvider.OLLAMA) "http://192.168.1.10:11434/" else "http://192.168.1.10:1234/v1/") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("base_url_input"), shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(value = draft.apiKey, onValueChange = { change(draft.copy(apiKey = it)) },
                        label = { Text(if (draft.provider.requiresApiKey) "API key" else "API key · optional") },
                        visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { revealKey = !revealKey }) { Icon(if (revealKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (revealKey) "Hide key" else "Show key") } },
                        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("api_key_input"), shape = RoundedCornerShape(8.dp)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Model", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(enabled = !busy, modifier = Modifier.testTag("load_models_button"), onClick = {
                            val snapshot = draft
                            busy = true; status = null
                            job = scope.launch {
                                val result = catalog.list(snapshot, repo.getEffectiveApiKey(snapshot))
                                if (draft != snapshot) return@launch
                                busy = false; failed = result.isFailure
                                result.onSuccess {
                                    models = it; showModels = true
                                    status = if (it.isEmpty()) "No models found. Enter a model ID manually, or load a model on the server." else "${it.size} models available. Choose one below."
                                }.onFailure { status = it.localizedMessage ?: "Could not load models. You can enter a model ID manually." }
                            }
                        }) { Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Load models") }
                    }
                    OutlinedTextField(value = draft.modelName, onValueChange = { change(draft.copy(modelName = it, supportsImages = null)) },
                        label = { Text("Model ID") }, supportingText = { Text("Choose from the server, or enter an exact model ID.") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("model_name_input"), shape = RoundedCornerShape(8.dp))
                    if (models.isNotEmpty()) {
                        TextButton(onClick = { showModels = !showModels }, modifier = Modifier.testTag("toggle_model_list")) { Text(if (showModels) "Hide available models" else "Browse ${models.size} models") }
                        if (showModels) {
                            OutlinedTextField(search, { search = it }, label = { Text("Filter models") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("model_search_input"))
                            val filtered = models.filter { it.id.contains(search, true) || it.name.contains(search, true) }
                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).testTag("available_models")) {
                                items(filtered, key = { it.id }) { model ->
                                    ListItem(headlineContent = { Text(model.id, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = { Text(when (model.supportsImages) { true -> "Images + text"; false -> "Text only"; null -> "Image support unverified" }, style = MaterialTheme.typography.bodySmall) },
                                        trailingContent = { if (model.id == draft.modelName) Icon(Icons.Default.Check, null) },
                                        modifier = Modifier.clickable {
                                            change(draft.copy(modelName = model.id, supportsImages = model.supportsImages)); showModels = false
                                            if (draft.provider.protocol == ApiProtocol.OLLAMA) {
                                                val snapshot = draft
                                                busy = true
                                                job = scope.launch {
                                                    val capability = catalog.inspect(snapshot, repo.getEffectiveApiKey(snapshot)).getOrNull()
                                                    if (draft == snapshot) { draft = snapshot.copy(supportsImages = capability); busy = false }
                                                }
                                            }
                                        })
                                }
                            }
                        }
                    }
                    Text(when (draft.supportsImages) { true -> "Images + text"; false -> "Text-only model. Use chat without an image."; null -> "Image support depends on the model. Test an image before using screen capture." },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { test(false) }, enabled = !busy && draft.modelName.isNotBlank(), modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag("test_connection_button"), shape = RoundedCornerShape(8.dp)) { Text("Test text") }
                        OutlinedButton(onClick = { test(true) }, enabled = !busy && draft.modelName.isNotBlank(), modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag("test_vision_button"), shape = RoundedCornerShape(8.dp)) { Text("Test image") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Appearance", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(selected = theme == mode, onClick = { appearance.select(mode) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }, modifier = Modifier.testTag("theme_${mode.name.lowercase()}"))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(40.dp).graphicsLayer { alpha = buttonOpacity }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_peek_mark), null, Modifier.size(24.dp)) }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Floating button", style = MaterialTheme.typography.titleSmall)
                            Text("Opacity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${(buttonOpacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("button_opacity_value"))
                    }
                    Slider(value = buttonOpacity, onValueChange = overlayPreferences::setButtonOpacity, valueRange = 0.2f..1f, steps = 15,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Floating button opacity" }.testTag("button_opacity_slider"))
                    Text("Lower opacity makes the floating button more transparent. Toolbox and chat remain fully visible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Keys are stored encrypted on this device. Each provider keeps its own saved settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).testTag("connection_status")) }
                Button(enabled = !busy && draft.modelName.isNotBlank(), onClick = {
                    runCatching { repo.saveConfig(draft) }.onSuccess { onDismiss() }.onFailure { failed = true; status = it.localizedMessage }
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).heightIn(min = 48.dp).testTag("save_settings_button"), shape = RoundedCornerShape(8.dp)) { Text("Use this model") }
            }
        }
    }
}

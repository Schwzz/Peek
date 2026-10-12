package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.api.ApiConfig
import com.example.data.api.ApiConfigRepository
import com.example.data.api.VisionAiProvider
import com.example.data.api.VisionAiRepository
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DarkNavySurfaceElevated
import com.example.ui.theme.DarkNavySurfaceVariant
import com.example.ui.theme.PurpleAccent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configRepo = remember { ApiConfigRepository.getInstance(context) }
    val initialConfig = remember { configRepo.getConfig() }

    var selectedProvider by remember { mutableStateOf(initialConfig.provider) }
    var apiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(initialConfig.baseUrl) }
    var modelName by remember { mutableStateOf(initialConfig.modelName) }
    var isKeyVisible by remember { mutableStateOf(false) }

    var isModelDropdownExpanded by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkNavySurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("settings_dialog_surface")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Vision AI Settings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Configure API provider and vision model",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("settings_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close settings",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Provider Selector Tabs
                Text(
                    text = "AI PROVIDER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (provider in VisionAiProvider.values()) {
                        val isSelected = selectedProvider == provider
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF00363F) else DarkNavySurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) CyanPrimary else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedProvider = provider
                                    modelName = provider.defaultModel
                                    if (provider == VisionAiProvider.OPENAI_COMPATIBLE && baseUrl.isBlank()) {
                                        baseUrl = provider.defaultBaseUrl
                                    }
                                }
                                .testTag("provider_${provider.name.lowercase()}_tab")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = provider.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Model Selection
                Text(
                    text = "VISION MODEL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = isModelDropdownExpanded,
                    onExpandedChange = { isModelDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("model_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModelDropdownExpanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = DarkNavySurfaceElevated,
                            unfocusedContainerColor = DarkNavySurfaceElevated
                        ),
                        singleLine = true
                    )

                    ExposedDropdownMenu(
                        expanded = isModelDropdownExpanded,
                        onDismissRequest = { isModelDropdownExpanded = false },
                        modifier = Modifier.background(DarkNavySurfaceVariant)
                    ) {
                        for (model in selectedProvider.supportedModels) {
                            DropdownMenuItem(
                                text = { Text(model, color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    modelName = model
                                    isModelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Base URL if OpenAI-Compatible
                if (selectedProvider.supportsCustomUrl) {
                    Text(
                        text = "API ENDPOINT (BASE URL)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        placeholder = { Text("https://api.openai.com/v1/", color = Color(0xFF64748B)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("base_url_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = DarkNavySurfaceElevated,
                            unfocusedContainerColor = DarkNavySurfaceElevated
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // API Key Field
                Text(
                    text = "API KEY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = {
                        Text(
                            text = if (selectedProvider == VisionAiProvider.GEMINI) {
                                "Paste Gemini API key or use Secrets"
                            } else {
                                "Paste API key (sk-...)"
                            },
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input"),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Key",
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "Hide key" else "Show key",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = DarkNavySurfaceElevated,
                        unfocusedContainerColor = DarkNavySurfaceElevated
                    ),
                    singleLine = true
                )

                if (selectedProvider == VisionAiProvider.GEMINI && apiKey.isBlank()) {
                    Text(
                        text = "Tip: When blank, Peek automatically checks BuildConfig / Secrets.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Test Connection Feedback
                if (testResult != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isTestSuccess) Color(0x3310B981) else Color(0x33EF4444),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isTestSuccess) Color(0x6610B981) else Color(0x66EF4444)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isTestSuccess) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isTestSuccess) Color(0xFF34D399) else Color(0xFFF87171),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = testResult!!,
                                fontSize = 12.sp,
                                color = if (isTestSuccess) Color(0xFFA7F3D0) else Color(0xFFFCA5A5)
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isTestingConnection = true
                                testResult = null

                                // Temporarily save to test with current values
                                val testConfig = ApiConfig(
                                    provider = selectedProvider,
                                    apiKey = apiKey,
                                    baseUrl = baseUrl,
                                    modelName = modelName
                                )
                                configRepo.saveConfig(testConfig)

                                val repo = VisionAiRepository.getInstance(context)
                                val result = repo.testConnection()
                                isTestingConnection = false
                                result.onSuccess { msg ->
                                    isTestSuccess = true
                                    testResult = "Connection OK! Response: $msg"
                                }.onFailure { err ->
                                    isTestSuccess = false
                                    testResult = "Failed: ${err.message}"
                                }
                            }
                        },
                        enabled = !isTestingConnection,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_connection_button")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = "Test connection",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = {
                            val newConfig = ApiConfig(
                                provider = selectedProvider,
                                apiKey = apiKey,
                                baseUrl = baseUrl,
                                modelName = modelName
                            )
                            configRepo.saveConfig(newConfig)
                            Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("save_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save",
                            tint = Color(0xFF00363F),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Settings", color = Color(0xFF00363F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

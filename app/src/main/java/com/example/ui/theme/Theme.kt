package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class ThemePreference private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("peek_appearance", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM))
    val mode = state.asStateFlow()
    fun select(mode: ThemeMode) { prefs.edit().putString("theme", mode.name).apply(); state.value = mode }
    companion object {
        @Volatile private var instance: ThemePreference? = null
        fun get(context: Context): ThemePreference = instance ?: synchronized(this) { instance ?: ThemePreference(context).also { instance = it } }
    }
}

private val dark = darkColorScheme(
    primary = CyanPrimary, onPrimary = Color(0xFF30291D), primaryContainer = Color(0xFF3A342A), onPrimaryContainer = CyanPrimary,
    secondary = PurpleAccent, onSecondary = DarkNavyBackground, secondaryContainer = DarkNavySurfaceVariant,
    onSecondaryContainer = TextPrimaryDark, tertiary = CyanSecondary,
    background = DarkNavyBackground, onBackground = TextPrimaryDark,
    surface = DarkNavySurface, onSurface = TextPrimaryDark,
    surfaceVariant = DarkNavySurfaceVariant, onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = DarkNavySurfaceVariant, surfaceContainerHigh = DarkNavySurfaceElevated,
    outline = Color(0xFF54544D), outlineVariant = Color(0xFF383834), error = Color(0xFFF2AAA2)
)
private val light = lightColorScheme(
    primary = LightCyanPrimary, onPrimary = Color.White, primaryContainer = Color(0xFFECE0CA), onPrimaryContainer = Color(0xFF46361C),
    secondary = TextSecondaryLight, onSecondary = Color.White, secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = TextPrimaryLight, tertiary = Color(0xFF53674E),
    background = LightBackground, onBackground = TextPrimaryLight,
    surface = LightSurface, onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = LightSurfaceVariant, surfaceContainerHigh = Color(0xFFE4E1D9),
    outline = Color(0xFF97968C), outlineVariant = Color(0xFFD8D5CB), error = Color(0xFFA3382F)
)

@Composable
fun PeekTheme(darkTheme: Boolean? = null, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode by remember(context) { ThemePreference.get(context) }.mode.collectAsState()
    val useDark = darkTheme ?: when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(colorScheme = if (useDark) dark else light, typography = Typography, content = content)
}

@Composable
fun MyApplicationTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) =
    PeekTheme(darkTheme, dynamicColor, content)

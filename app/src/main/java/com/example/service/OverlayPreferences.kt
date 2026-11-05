package com.example.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverlayPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("peek_overlay_preferences", Context.MODE_PRIVATE)
    private val opacity = MutableStateFlow(clamp(prefs.getFloat("button_opacity", 1f)))
    val buttonOpacity = opacity.asStateFlow()

    fun setButtonOpacity(value: Float) {
        val clamped = clamp(value)
        prefs.edit().putFloat("button_opacity", clamped).apply()
        opacity.value = clamped
    }

    companion object {
        private fun clamp(value: Float) = if (value.isFinite()) value.coerceIn(0.2f, 1f) else 1f
        @Volatile private var instance: OverlayPreferences? = null
        fun getInstance(context: Context): OverlayPreferences = instance ?: synchronized(this) {
            instance ?: OverlayPreferences(context).also { instance = it }
        }
    }
}

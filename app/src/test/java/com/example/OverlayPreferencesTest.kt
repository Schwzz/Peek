package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.OverlayPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayPreferencesTest {
    @Test fun opacitySurvivesReloadAndCannotMakeTheButtonInvisible() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("peek_overlay_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        val preferences = OverlayPreferences(context)
        assertEquals(1f, preferences.buttonOpacity.value, 0.001f)
        preferences.setButtonOpacity(0.4f)
        assertEquals(0.4f, OverlayPreferences(context).buttonOpacity.value, 0.001f)
        preferences.setButtonOpacity(0f)
        assertEquals(0.2f, preferences.buttonOpacity.value, 0.001f)
        preferences.setButtonOpacity(Float.NaN)
        assertEquals(1f, OverlayPreferences(context).buttonOpacity.value, 0.001f)
    }
}

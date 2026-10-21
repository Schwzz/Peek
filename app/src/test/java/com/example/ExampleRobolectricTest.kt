package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.ApiConfig
import com.example.data.api.ApiConfigRepository
import com.example.data.api.VisionAiProvider
import com.example.util.BitmapUtils
import com.example.util.ImageLoader
import com.example.util.KeyCipher
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("peek_api_prefs", Context.MODE_PRIVATE)
    private val cipher = object : KeyCipher {
        private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun encrypt(plainText: String): String {
            if (plainText.isEmpty()) return ""
            val engine = Cipher.getInstance("AES/GCM/NoPadding")
            engine.init(Cipher.ENCRYPT_MODE, key)
            return "test:" + Base64.getEncoder().encodeToString(engine.iv + engine.doFinal(plainText.toByteArray()))
        }
        override fun decrypt(encryptedText: String): String {
            if (!encryptedText.startsWith("test:")) return encryptedText
            val bytes = Base64.getDecoder().decode(encryptedText.removePrefix("test:"))
            val engine = Cipher.getInstance("AES/GCM/NoPadding")
            engine.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            return String(engine.doFinal(bytes.copyOfRange(12, bytes.size)))
        }
    }

    @Before fun resetPreferences() { prefs.edit().clear().commit() }

    @Test fun providerProfilesKeepSeparateKeysAndSurviveReload() {
        val repo = ApiConfigRepository(context, cipher)
        repo.saveConfig(ApiConfig(apiKey = "gemini-secret"))
        repo.saveConfig(ApiConfig(provider = VisionAiProvider.OPENAI_COMPATIBLE, apiKey = "openai-secret", modelName = "gpt-4o-mini"))
        val reloaded = ApiConfigRepository(context, cipher)
        assertEquals("gemini-secret", reloaded.getConfig(VisionAiProvider.GEMINI).apiKey)
        assertEquals("openai-secret", reloaded.getConfig().apiKey)
        assertNotEquals("gemini-secret", prefs.getString("gemini_api_key", ""))
        assertNotEquals("openai-secret", prefs.getString("openai_compatible_api_key", ""))
        assertFalse(prefs.contains("api_key"))
    }

    @Test fun legacyKeyIsNotExposedToOtherProviderAndIsPreservedOnSave() {
        prefs.edit().putString("provider", "GEMINI").putString("api_key", "legacy-key")
            .putString("model_name", "gemini-2.5-flash").commit()
        val repo = ApiConfigRepository(context, cipher)
        assertEquals("", repo.getConfig(VisionAiProvider.OPENAI_COMPATIBLE).apiKey)
        repo.saveConfig(ApiConfig(provider = VisionAiProvider.OPENAI_COMPATIBLE, apiKey = "new-key", modelName = "gpt-4o-mini"))
        assertEquals("legacy-key", ApiConfigRepository(context, cipher).getConfig(VisionAiProvider.GEMINI).apiKey)
        assertFalse(prefs.contains("api_key"))
    }

    @Test fun encryptionFailureDoesNotWriteOrChangeSelectedProfile() {
        val broken = object : KeyCipher {
            override fun encrypt(plainText: String): String = error("Keystore unavailable")
            override fun decrypt(encryptedText: String): String = encryptedText
        }
        val repo = ApiConfigRepository(context, broken)
        assertThrows(IllegalStateException::class.java) { repo.saveConfig(ApiConfig(apiKey = "do-not-save")) }
        assertTrue(prefs.all.isEmpty())
        assertEquals("", repo.getConfig().apiKey)
    }

    @Test fun invalidEndpointIsRejectedBeforeSavingKey() {
        val repo = ApiConfigRepository(context, cipher)
        assertThrows(IllegalArgumentException::class.java) {
            repo.saveConfig(ApiConfig(provider = VisionAiProvider.OPENAI_COMPATIBLE, apiKey = "secret", baseUrl = "http://example.com", modelName = "vision"))
        }
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun cropClampsOutOfBoundsAndEmptySelections() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        assertEquals(50, BitmapUtils.crop(bitmap, Rect(10, 20, 60, 70)).width)
        val edge = BitmapUtils.crop(bitmap, Rect(200, 200, 200, 200))
        assertEquals(1, edge.width)
        assertEquals(1, edge.height)
    }

    @Test fun largeImageDecodeIsBounded() = runBlocking {
        val file = java.io.File(context.cacheDir, "large-test.png")
        val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val result = ImageLoader.decode(context, android.net.Uri.fromFile(file), 800)
        assertEquals(800, result.width)
        assertEquals(400, result.height)
        result.recycle()
        file.delete()
        Unit
    }
}

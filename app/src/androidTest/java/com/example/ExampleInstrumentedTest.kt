package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @Test
  fun apiKeyEncryptionNeverStoresPlaintext() {
    val secret = "device-test-key"
    val encrypted = com.example.util.KeystoreCrypto.encrypt(secret)
    assertTrue(encrypted.startsWith("enc:v1:"))
    assertNotEquals(secret, encrypted)
    assertEquals(secret, com.example.util.KeystoreCrypto.decrypt(encrypted))
  }

  @Test
  fun useAppContext() {
    // Context of the app under test.
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
  }
}

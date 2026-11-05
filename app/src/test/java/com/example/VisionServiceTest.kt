package com.example

import com.example.data.api.ApiConfig
import com.example.data.api.OpenAiVisionService
import com.example.data.api.VisionAiProvider
import com.example.model.ChatMessage
import com.example.model.MessageSender
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VisionServiceTest {
    @Test fun historicalImagesAreSentAndCancelledMessagesAreExcluded() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"answer"}}]}"""))
            val config = ApiConfig(provider = VisionAiProvider.OPENAI_COMPATIBLE, baseUrl = server.url("/v1/").toString(), modelName = "test-model")
            val history = listOf(
                ChatMessage(sender = MessageSender.USER, text = "old picture", imagesBase64 = listOf("encoded-original"), hasImageAttachment = true),
                ChatMessage(sender = MessageSender.AI, text = "old answer"),
                ChatMessage(sender = MessageSender.USER, text = "cancelled", isCancelled = true)
            )
            val result = OpenAiVisionService().analyze(emptyList(), "followup", history, config, "fake-key")
            assertEquals("answer", result.getOrThrow())
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/v1/chat/completions", request.path)
            val messages = JSONObject(request.body.readUtf8()).getJSONArray("messages")
            assertEquals(3, messages.length())
            assertTrue(messages.getJSONObject(0).toString().contains("encoded-original"))
            assertFalse(messages.toString().contains("cancelled"))
        }
    }

    @Test fun cancellationStopsAnUnresponsiveNetworkCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val config = ApiConfig(provider = VisionAiProvider.OPENAI_COMPATIBLE, baseUrl = server.url("/").toString())
            val job = launch(Dispatchers.Default) {
                OpenAiVisionService().analyze(emptyList(), "test", emptyList(), config, "fake-key")
                fail("Cancelled request returned a result")
            }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
            withTimeout(3000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }
}

package com.example

import com.example.data.api.*
import com.example.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiRequestTest {
    private fun client(server: MockWebServer) = OkHttpClient.Builder().addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
    }.build()

    @Test fun geminiTextOnlyMessagesAlwaysContainCurrentPartsForEveryModel() = runBlocking {
        MockWebServer().use { server ->
            for (model in listOf("gemini-3.5-flash-lite", "gemini-3-flash")) {
                server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}"""))
                assertEquals("OK", GeminiVisionService(client(server)).analyze(emptyList(), "hello", emptyList(), ApiConfig(modelName = model), "fake-key").getOrThrow())
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("/v1beta/models/$model:generateContent", request.path)
                assertEquals("fake-key", request.getHeader("x-goog-api-key"))
                val turn = JSONObject(request.body.readUtf8()).getJSONArray("contents").getJSONObject(0)
                assertEquals("user", turn.getString("role"))
                assertEquals("hello", turn.getJSONArray("parts").getJSONObject(0).getString("text"))
            }
        }
    }

    @Test fun geminiIncludesEveryCurrentAndHistoricalImageAndSkipsEmptyHistory() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"thought":true,"text":"private"},{"text":"answer"}]}}]}"""))
            val history = listOf(ChatMessage(sender = MessageSender.AI, text = "orphaned"),
                ChatMessage(sender = MessageSender.USER, text = "previous", imagesBase64 = listOf("old-1", "old-2")),
                ChatMessage(sender = MessageSender.AI, text = "earlier answer"),
                ChatMessage(sender = MessageSender.USER, text = "stopped", isCancelled = true),
                ChatMessage(sender = MessageSender.AI, text = "error", isError = true))
            assertEquals("answer", GeminiVisionService(client(server)).analyze(listOf("new-1", "new-2"), "compare", history, ApiConfig(), "key").getOrThrow())
            val turns = JSONObject(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()).getJSONArray("contents")
            assertEquals(3, turns.length())
            assertEquals("model", turns.getJSONObject(1).getString("role"))
            for ((index, prefix) in listOf(0 to "old", 2 to "new")) {
                val parts = turns.getJSONObject(index).getJSONArray("parts")
                assertEquals(3, parts.length())
                for (image in 1..2) {
                    val data = parts.getJSONObject(image).getJSONObject("inlineData")
                    assertEquals("image/jpeg", data.getString("mimeType"))
                    assertEquals("$prefix-$image", data.getString("data"))
                }
            }
            assertEquals("compare", turns.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text"))
        }
    }

    @Test fun multipleImagesAreIncludedInEveryOtherProtocolAndItsHistory() = runBlocking {
        for (provider in listOf(VisionAiProvider.OPENAI, VisionAiProvider.ANTHROPIC, VisionAiProvider.OLLAMA)) {
            MockWebServer().use { server ->
                val response = when (provider) {
                    VisionAiProvider.OPENAI -> """{"choices":[{"message":{"content":"OK"}}]}"""
                    VisionAiProvider.ANTHROPIC -> """{"content":[{"type":"text","text":"OK"}]}"""
                    else -> """{"message":{"content":"OK"}}"""
                }
                server.enqueue(MockResponse().setBody(response))
                val service: VisionAiService = if (provider == VisionAiProvider.OPENAI) OpenAiVisionService(client(server)) else NativeVisionService(client(server))
                val history = listOf(ChatMessage(sender = MessageSender.USER, text = "old", imagesBase64 = listOf("old-1", "old-2")))
                assertEquals("OK", service.analyze(listOf("new-1", "new-2"), "compare", history, ApiConfig(provider, modelName = "test"), "key").getOrThrow())
                val messages = JSONObject(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()).getJSONArray("messages")
                assertEquals(2, messages.length())
                for ((index, prefix) in listOf(0 to "old", 1 to "new")) {
                    val message = messages.getJSONObject(index)
                    when (provider) {
                        VisionAiProvider.OLLAMA -> assertEquals(listOf("$prefix-1", "$prefix-2"), (0..1).map { message.getJSONArray("images").getString(it) })
                        VisionAiProvider.ANTHROPIC -> {
                            val blocks = message.getJSONArray("content")
                            assertEquals(3, blocks.length())
                            assertEquals("$prefix-1", blocks.getJSONObject(0).getJSONObject("source").getString("data"))
                            assertEquals("$prefix-2", blocks.getJSONObject(1).getJSONObject("source").getString("data"))
                        }
                        else -> {
                            val blocks = message.getJSONArray("content")
                            assertEquals(3, blocks.length())
                            assertEquals("data:image/jpeg;base64,$prefix-2", blocks.getJSONObject(2).getJSONObject("image_url").getString("url"))
                        }
                    }
                }
            }
        }
    }
}

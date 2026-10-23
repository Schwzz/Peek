package com.example

import com.example.data.api.*
import com.example.model.ChatMessage
import com.example.model.MessageSender
import kotlinx.coroutines.*
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
class ProviderSupportTest {
    private fun redirectedClient(server: MockWebServer) = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
        val url = chain.request().url
        chain.proceed(chain.request().newBuilder().url(server.url(url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: ""))).build())
    }.build()

    @Test fun ollamaSendsRawImagesAndHistoryWithoutRequiringAKey() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"message":{"content":"local answer"}}"""))
            val config = ApiConfig(VisionAiProvider.OLLAMA, baseUrl = server.url("/api/").toString(), modelName = "vision-local")
            val history = listOf(ChatMessage(sender = MessageSender.USER, text = "previous", imageBase64 = "old-image"),
                ChatMessage(sender = MessageSender.AI, text = "ignored", isCancelled = true))
            assertEquals("local answer", NativeVisionService().analyze("new-image", "describe", history, config, "").getOrThrow())
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/api/chat", request.path)
            assertNull(request.getHeader("Authorization"))
            val body = JSONObject(request.body.readUtf8())
            assertFalse(body.getBoolean("stream"))
            assertEquals("vision-local", body.getString("model"))
            val messages = body.getJSONArray("messages")
            assertEquals(2, messages.length())
            assertEquals("old-image", messages.getJSONObject(0).getJSONArray("images").getString(0))
            assertEquals("new-image", messages.getJSONObject(1).getJSONArray("images").getString(0))
        }
    }

    @Test fun anthropicUsesItsNativeHeadersImageBlocksAndTextResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"content":[{"type":"thinking","thinking":"private"},{"type":"text","text":"visible answer"}]}"""))
            val config = ApiConfig(VisionAiProvider.ANTHROPIC, modelName = "vision-cloud")
            assertEquals("visible answer", NativeVisionService(redirectedClient(server)).analyze("jpeg", "describe", emptyList(), config, "test-secret").getOrThrow())
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/v1/messages", request.path)
            assertEquals("test-secret", request.getHeader("x-api-key"))
            assertEquals("2023-06-01", request.getHeader("anthropic-version"))
            assertNull(request.getHeader("Authorization"))
            val body = JSONObject(request.body.readUtf8())
            assertTrue(body.getInt("max_tokens") > 0)
            val image = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0).getJSONObject("source")
            assertEquals("image/jpeg", image.getString("media_type"))
            assertEquals("jpeg", image.getString("data"))
        }
    }

    @Test fun cloudPresetsUseCompatiblePathsAndBearerAuthentication() = runBlocking {
        for (provider in listOf(VisionAiProvider.OPENAI, VisionAiProvider.OPENROUTER, VisionAiProvider.GROQ, VisionAiProvider.XAI, VisionAiProvider.DEEPSEEK)) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"answer"}}]}"""))
                val config = ApiConfig(provider, modelName = "account-model")
                assertTrue(OpenAiVisionService(redirectedClient(server)).analyze("jpeg", "question", emptyList(), config, "key-for-${provider.name}").isSuccess)
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals(ProviderEndpoints.url(config, "chat/completions").encodedPath, request.path)
                assertEquals("Bearer key-for-${provider.name}", request.getHeader("Authorization"))
                assertTrue(request.body.readUtf8().contains("data:image/jpeg;base64,jpeg"))
            }
        }
    }

    @Test fun lmStudioAcceptsAnOptionalTokenAndACompletionUrl() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"local"}}]}"""))
            val config = ApiConfig(VisionAiProvider.LM_STUDIO, baseUrl = server.url("/v1/chat/completions").toString(), modelName = "loaded-model")
            assertTrue(OpenAiVisionService().analyze(null, "hello", emptyList(), config, "").isSuccess)
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/v1/chat/completions", request.path)
            assertNull(request.getHeader("Authorization"))
        }
    }

    @Test fun catalogReadsImageCapabilitiesAndOllamaModelDetails() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"vision","architecture":{"input_modalities":["text","image"]}},{"id":"text","architecture":{"input_modalities":["text"]}}]}"""))
            val models = ModelCatalog(redirectedClient(server)).list(ApiConfig(VisionAiProvider.OPENROUTER), "key").getOrThrow()
            assertEquals(false, models.first { it.id == "text" }.supportsImages)
            assertEquals(true, models.first { it.id == "vision" }.supportsImages)
            assertEquals("/api/v1/models", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        }
        MockWebServer().use { server ->
            val config = ApiConfig(VisionAiProvider.OLLAMA, baseUrl = server.url("/").toString(), modelName = "local")
            server.enqueue(MockResponse().setBody("""{"models":[{"name":"local"}]}"""))
            server.enqueue(MockResponse().setBody("""{"capabilities":["completion","vision"]}"""))
            val catalog = ModelCatalog()
            assertEquals("local", catalog.list(config, "").getOrThrow().single().id)
            assertEquals(true, catalog.inspect(config, "").getOrThrow())
            assertEquals("/api/tags", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
            val show = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/api/show", show.path)
            assertEquals("local", JSONObject(show.body.readUtf8()).getString("model"))
        }
    }

    @Test fun geminiCatalogFollowsPagesAndExcludesEmbeddingModels() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"models":[{"name":"models/embedding","supportedGenerationMethods":["embedContent"]}],"nextPageToken":"page2"}"""))
            server.enqueue(MockResponse().setBody("""{"models":[{"name":"models/vision","supportedGenerationMethods":["generateContent"]}]}"""))
            val models = ModelCatalog(redirectedClient(server)).list(ApiConfig(), "key").getOrThrow()
            assertEquals("vision", models.single().id)
            assertEquals("key", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("x-goog-api-key"))
            assertTrue(server.takeRequest(5, TimeUnit.SECONDS)!!.path!!.contains("pageToken=page2"))
        }
    }

    @Test fun endpointPolicyAllowsLanButRejectsPublicHttpAndUrlCredentials() {
        for (host in listOf("192.168.1.5", "10.0.2.2", "172.16.0.8", "localhost", "[::1]")) {
            assertEquals("http", ProviderEndpoints.base(ApiConfig(VisionAiProvider.OLLAMA, baseUrl = "http://$host:11434")).scheme)
        }
        for (url in listOf("http://example.com", "http://172.32.0.1", "https://user:pass@example.com", "https://example.com/?key=secret")) {
            assertThrows(IllegalArgumentException::class.java) { ProviderEndpoints.base(ApiConfig(VisionAiProvider.OPENAI_COMPATIBLE, baseUrl = url)) }
        }
    }

    @Test fun credentialsDoNotFollowServerRedirects() = runBlocking {
        MockWebServer().use { target -> MockWebServer().use { source ->
            target.start()
            source.enqueue(MockResponse().setResponseCode(302).addHeader("Location", target.url("/stolen")))
            val config = ApiConfig(VisionAiProvider.LM_STUDIO, baseUrl = source.url("/v1/").toString(), modelName = "local")
            assertTrue(OpenAiVisionService().analyze(null, "hello", emptyList(), config, "private-token").isFailure)
            assertEquals(0, target.requestCount)
        } }
    }
}

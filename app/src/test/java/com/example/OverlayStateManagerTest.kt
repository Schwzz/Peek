package com.example

import com.example.service.OverlayStateManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayStateManagerTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        OverlayStateManager.clearChat()
        OverlayStateManager.clearImage()
    }
    @After fun cleanup() {
        OverlayStateManager.clearChat()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun secondRequestIsRejectedAndStopCancelsWork() = runTest(dispatcher) {
        var cancelled = false
        assertTrue(OverlayStateManager.sendPrompt("first") { _, _, _ ->
            try { awaitCancellation() } finally { cancelled = true }
        })
        runCurrent()
        assertFalse(OverlayStateManager.sendPrompt("second") { _, _, _ -> Result.success("unexpected") })
        OverlayStateManager.cancelRequest()
        runCurrent()
        assertTrue(cancelled)
        assertFalse(OverlayStateManager.isLoading.value)
        assertTrue(OverlayStateManager.chatMessages.value.single().isCancelled)
    }

    @Test fun clearedSessionRejectsLateResponseAndDoesNotUnlockNewRequest() = runTest(dispatcher) {
        val oldResponse = CompletableDeferred<Unit>()
        OverlayStateManager.sendPrompt("old") { _, _, _ ->
            withContext(NonCancellable) { oldResponse.await() }
            Result.success("stale answer")
        }
        runCurrent()
        OverlayStateManager.clearChat()
        OverlayStateManager.sendPrompt("new") { _, _, _ -> awaitCancellation() }
        runCurrent()
        oldResponse.complete(Unit)
        runCurrent()
        assertTrue(OverlayStateManager.isLoading.value)
        assertEquals(listOf("new"), OverlayStateManager.chatMessages.value.map { it.text })
    }

    @Test fun followupReceivesHistorySnapshot() = runTest(dispatcher) {
        OverlayStateManager.sendPrompt("hello") { _, _, history ->
            assertTrue(history.isEmpty())
            Result.success("hi")
        }
        runCurrent()
        OverlayStateManager.sendPrompt("followup") { _, _, history ->
            assertEquals(listOf("hello", "hi"), history.map { it.text })
            Result.success("done")
        }
        runCurrent()
        assertEquals("done", OverlayStateManager.chatMessages.value.last().text)
        assertFalse(OverlayStateManager.isLoading.value)
    }

    @Test fun captureIsExclusiveAndFinishRestoresPanel() {
        OverlayStateManager.finishCapture()
        assertTrue(OverlayStateManager.beginCapture())
        assertFalse(OverlayStateManager.beginCapture())
        OverlayStateManager.finishCapture()
        assertFalse(OverlayStateManager.isCapturing.value)
        assertTrue(OverlayStateManager.isPanelExpanded.value)
        assertNull(OverlayStateManager.capturedScreenToCrop.value)
    }
}

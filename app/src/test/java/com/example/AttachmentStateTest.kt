package com.example

import android.graphics.Bitmap
import com.example.service.OverlayStateManager as State
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttachmentStateTest {
    private val dispatcher = StandardTestDispatcher()
    private fun image() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    @Before fun setup() { Dispatchers.setMain(dispatcher); State.clearChat(); State.clearImage(); State.finishCapture(false) }
    @After fun cleanup() { State.clearChat(); State.clearImage(); dispatcher.scheduler.runCurrent(); Dispatchers.resetMain() }

    @Test fun importsAppendAndCroppingReplacesOnlyTheSelectedAttachment() {
        val first = image(); val second = image(); val cropped = image()
        assertTrue(State.appendImage(first)); assertTrue(State.appendImage(second))
        State.selectImage(first)
        assertTrue(State.beginCapture(replaceCurrentImage = true))
        State.completeImageSelection(cropped); State.finishCapture(false)
        assertEquals(listOf(cropped, second), State.images.value)
        assertSame(cropped, State.currentImage.value)
        State.removeImage(cropped)
        assertEquals(listOf(second), State.images.value)
        assertSame(second, State.currentImage.value)
        State.removeImage(second)
        assertNull(State.currentImage.value)
    }

    @Test fun imageLimitRejectsExtraImagesWithoutReplacingExistingOnes() {
        repeat(State.MAX_IMAGES) { assertTrue(State.appendImage(image())) }
        val originals = State.images.value.toList()
        assertFalse(State.appendImage(image()))
        assertEquals(originals, State.images.value)
        assertTrue(State.errorMessage.value!!.contains("6"))
    }

    @Test fun sendingUsesAnAttachmentSnapshotAndKeepsImagesAddedDuringTheRequest() = runTest(dispatcher) {
        val first = image(); val second = image(); val later = image()
        State.appendImage(first); State.appendImage(second)
        val received = CompletableDeferred<List<String>>()
        val finish = CompletableDeferred<Unit>()
        assertTrue(State.sendPrompt("compare") { images, _, _ -> received.complete(images); finish.await(); Result.success("answer") })
        assertEquals(2, received.await().size)
        State.appendImage(later)
        finish.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(later), State.images.value)
        assertEquals(2, State.chatMessages.value.first().imageCount)
        assertEquals(2, State.chatMessages.value.first().imagesBase64.size)
        val followup = CompletableDeferred<Unit>()
        State.clearImage()
        assertTrue(State.sendPrompt("follow up") { images, _, history ->
            assertTrue(images.isEmpty()); assertEquals(2, history.first().imagesBase64.size)
            followup.complete(Unit); Result.success("done")
        })
        followup.await(); advanceUntilIdle()
    }

    @Test fun failedRequestsKeepAttachmentsForRetryAndAreExcludedFromFutureHistory() = runTest(dispatcher) {
        val bitmap = image(); State.appendImage(bitmap)
        val failed = CompletableDeferred<Unit>()
        State.sendPrompt("describe") { _, _, _ -> failed.complete(Unit); Result.failure(IllegalStateException("offline")) }
        failed.await(); advanceUntilIdle()
        assertEquals(listOf(bitmap), State.images.value)
        assertTrue(State.chatMessages.value.first().isError)
    }
}

package com.myothuonion.languagetalk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myothuonion.languagetalk.network.AudioPayload
import com.myothuonion.languagetalk.util.GeminiAudioPlayer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AudioPlaybackTest {
    private fun checkPlayback(bytes: ByteArray, expectSuccess: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val player = GeminiAudioPlayer(instrumentation.targetContext)
        val finished = CountDownLatch(1)
        val completions = AtomicInteger()
        val error = AtomicReference<Throwable?>(null)
        try {
            instrumentation.runOnMainSync {
                player.play(AudioPayload(bytes, "audio/wav"), onError = { error.set(it); finished.countDown() }) {
                    completions.incrementAndGet(); finished.countDown()
                }
            }
            assertTrue("Audio player did not finish", finished.await(15, TimeUnit.SECONDS))
            if (expectSuccess) { assertNull(error.get()); assertEquals(1, completions.get()) }
            else { assertNotNull(error.get()); assertEquals(0, completions.get()) }
        } finally { instrumentation.runOnMainSync { player.stop() } }
    }

    @Test fun realGeminiWavPlaysToCompletionOnAndroid() {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("gemini-greeting.wav").use { it.readBytes() }
        assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
        checkPlayback(bytes, expectSuccess = true)
    }

    @Test fun invalidAudioReportsAnErrorInsteadOfPretendingPlaybackCompleted() {
        checkPlayback("invalid audio fixture".toByteArray(), expectSuccess = false)
    }
}

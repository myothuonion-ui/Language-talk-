package com.myothuonion.languagetalk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myothuonion.languagetalk.data.*
import com.myothuonion.languagetalk.network.AudioPayload
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class ReplayAndTrackTest {
    @Test fun actualEncodedAudioIsDurablePairedExportableAndRemovedPerSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = AppDatabase.open(context, "replay-test-${UUID.randomUUID()}.db")
        val settings = SettingsStore(context)
        val previous = settings.settings.first()
        try {
            settings.save(previous.copy(recordPractice = true))
            val archive = ReplayRepository(context, db.dao(), settings)
            val session = archive.begin("TEST", UUID.randomUUID().toString(), "Paired audio")
            val bytes = instrumentation.context.assets.open("gemini-greeting.wav").use { it.readBytes() }
            val audio = AudioPayload(bytes, "audio/wav")
            archive.save(session, "USER", "안녕하세요.", audio)
            archive.save(session, "AI", "안녕하세요.")
            archive.attachAI(session, "안녕하세요.", audio)
            val clips = archive.contents(session)
            assertEquals(listOf("USER", "AI"), clips.map { it.speaker })
            assertTrue(clips.all { it.durationMs > 100 && archive.file(it)?.isFile == true })
            assertArrayEquals(bytes, archive.file(clips[0])!!.readBytes())
            val output = ByteArrayOutputStream(); archive.export(session, output)
            val entries = mutableListOf<String>()
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; entries += entry.name; zip.closeEntry() }
            }
            assertEquals(2, entries.count { it.startsWith("audio/") })
            assertTrue("transcript.json" in entries)
            val files = clips.map { archive.file(it)!! }; archive.delete(session)
            assertTrue(archive.contents(session).isEmpty())
            assertTrue(files.none { it.exists() })
        } finally { settings.save(previous); db.close() }
    }
    @Test fun trackPageLinksPersistWithRoomAndPortableMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.open(context, "track-test-${UUID.randomUUID()}.db")
        try {
            val id = "a".repeat(64)
            val asset = BookAudioAsset(id, "ttmik-beginner", 2, "$id.audio", "audio/mpeg", "Track 02", pagesJson = "[13,14]")
            db.dao().saveBookAudio(asset)
            assertEquals(listOf(13, 14), db.dao().allBookAudio().single().pages())
            assertEquals(2, db.dao().allBookAudio().single().track)
        } finally { db.close() }
    }
}

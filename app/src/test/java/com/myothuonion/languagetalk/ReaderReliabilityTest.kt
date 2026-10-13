package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.data.*
import com.myothuonion.languagetalk.util.PdfTextLayer
import com.myothuonion.languagetalk.util.PdfWord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ReaderReliabilityTest {
    private val blocks = listOf(SourceBlock("line-0", "안녕하세요."), SourceBlock("line-1", "저는 학생이에요."))
    @Test fun printedTrackNumbersHaveTapRegionsAndAmbiguousNamesAreNotGuessed() {
        val layer = PdfTextLayer("Track 02", listOf(PdfWord("Track", .1f, .2f, .2f, .3f, "Track 02", 0),
            PdfWord("02", .21f, .2f, .25f, .3f, "Track 02", 0)))
        val marker = ReaderTracks.detect(layer).single()
        assertEquals(2, marker.number)
        assertTrue(marker.region!!.valid())
        assertEquals(2, ReaderTracks.numberFromName("TTMIK Track 02.mp3"))
        assertEquals(3, ReaderTracks.numberFromName("003.mp3"))
        assertNull(ReaderTracks.numberFromName("lesson-2-page-13.mp3"))
        assertTrue(ReaderTracks.detect(PdfTextLayer("Track 99999")).isEmpty())
    }
    @Test fun malformedPageReplyIsReportedWithoutDumpingTheProviderText() {
        val error = assertThrows(IllegalArgumentException::class.java) { PageTeachingCodec.decodePartial("ဒီစာမျက်နှာကို ရှင်းပြပါမယ်", blocks) }
        assertFalse(error.message.orEmpty().contains("JSON input"))
        assertFalse(error.message.orEmpty().contains("offset"))
        assertEquals("object", PageTeachingCodec.schema(blocks)["type"]!!.jsonPrimitive.content)
        assertFalse(PageTeachingCodec.schema(blocks)["properties"]!!.jsonObject.containsKey("reply"))
    }
    @Test fun validSectionsSurviveAnInventedQuoteAndCanResumeTheMissingBlock() {
        val mixed = PageTeaching("နှုတ်ဆက်ခြင်း", sentences = listOf(TeachingSentence("line-0", "안녕하세요.", "မင်္ဂလာပါ။"),
            TeachingSentence("line-1", "저는 의사예요.", "I am a doctor")))
        val first = PageTeachingCodec.decodePartial(PageTeachingCodec.json.encodeToString(mixed), blocks)
        assertEquals(listOf("line-0"), first.sentences.map { it.sourceId })
        assertEquals(listOf("line-1"), PageTeachingCodec.missing(first, blocks).map { it.id })
        val next = PageTeaching("မိတ်ဆက်ခြင်း", sentences = listOf(TeachingSentence("line-1", blocks[1].text, "ကျောင်းသားပါ။")))
        assertTrue(PageTeachingCodec.missing(PageTeachingCodec.merge(first, next), blocks).isEmpty())
        val cached = PageExplanation("cache", "pdf-test", "sha", 13, "hash", PageTeachingCodec.json.encodeToString(first), PageTeachingCodec.json.encodeToString(blocks))
        assertEquals(first, BackupCodec.decode(BackupCodec.json.encodeToString(LearningBackup(pageExplanations = listOf(cached))).encodeToByteArray()).pageExplanations.single().value())
    }
    @Test fun aSingleWordDoesNotClaimToExplainItsEntireParagraph() {
        val value = PageTeaching("အဓိပ္ပာယ်", vocabulary = listOf(TeachingWord("line-1", "학생", "학생", "ကျောင်းသား")))
        assertEquals(2, PageTeachingCodec.missing(value, blocks).size)
    }
    @Test fun pairedReplayBackupKeepsSpeakerAndRejectsDanglingAudio() {
        val session = ReplaySession("session", "Conversation", "CONVERSATION", "chat-1")
        val clips = listOf(ReplayClip(1, "session", "USER", "안녕하세요."), ReplayClip(2, "session", "AI", "반가워요."))
        val backup = LearningBackup(replaySessions = listOf(session), replayClips = clips)
        assertEquals(clips, BackupCodec.decode(BackupCodec.json.encodeToString(backup).encodeToByteArray()).replayClips)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.json.encodeToString(
            backup.copy(replayAudio = listOf(BackupReplayAudio(99, "AA==")))).encodeToByteArray()) }
    }
}

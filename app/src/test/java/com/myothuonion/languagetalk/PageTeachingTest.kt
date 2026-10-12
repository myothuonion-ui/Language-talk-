package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.data.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class PageTeachingTest {
    private val blocks = listOf(SourceBlock("line-0", "밥을 먹고 왔어요.", listOf(PageRect(.1f, .2f, .6f, .25f))))
    private fun value() = PageTeaching("တစ်ခုခုလုပ်ပြီးလာတဲ့ပုံစံ", sentences = listOf(TeachingSentence("line-0", "밥을 먹고 왔어요.", "ထမင်းစားပြီး လာခဲ့တယ်။")))
    @Test fun aStructuredExplanationPreservesSourceAndCanRoundTripOffline() {
        val raw = PageTeachingCodec.json.encodeToString(value())
        assertEquals(value(), PageTeachingCodec.decode(raw, blocks))
        val cached = PageExplanation("key", "book", "fingerprint", 64, "sha", raw, PageTeachingCodec.json.encodeToString(blocks))
        assertEquals(value(), cached.value())
        assertEquals(blocks, cached.blocks())
    }
    @Test fun hallucinatedSourceAndMissingCoverageAreRejectedBeforeCaching() {
        val invented = value().copy(sentences = listOf(TeachingSentence("line-0", "안녕하세요.", "hello")))
        assertThrows(IllegalArgumentException::class.java) { PageTeachingCodec.decode(PageTeachingCodec.json.encodeToString(invented), blocks) }
        assertThrows(IllegalArgumentException::class.java) { PageTeachingCodec.decode(PageTeachingCodec.json.encodeToString(value()), blocks + SourceBlock("line-1", "먹다")) }
    }
    @Test fun reflowedTextAnchorsKeepWhitespaceAndRepeatedOccurrencesSeparate() {
        val text = "안녕하세요.\n밥을 먹고\n왔어요.\n밥을 먹고 왔어요."
        val first = ReaderAnchors.range(text, "밥을 먹고 왔어요.")!!
        val second = ReaderAnchors.range(text, "밥을 먹고 왔어요.", 23)!!
        assertTrue(first.first < second.first)
        assertEquals("밥을 먹고\n왔어요.", text.substring(first.first, first.last + 1))
        assertNull(ReaderAnchors.range(text, "absent"))
    }
    @Test fun oldBackupDefaultsAndNewAnnotationsAreCompatibleWithoutApiKeys() {
        val annotation = ReaderAnnotation("id", "pdf-test", "fingerprint", "Reader", 64, "밥을 먹고 왔어요.",
            kind = "BOLD", rectangles = PageTeachingCodec.json.encodeToString(blocks[0].rectangles), startOffset = 0, endOffset = 11)
        val bytes = BackupCodec.json.encodeToString(LearningBackup(annotations = listOf(annotation))).encodeToByteArray()
        assertEquals(annotation, BackupCodec.decode(bytes).annotations.single())
        assertEquals("SYSTEM", BackupCodec.decode("""{"format":"language-talk-backup","version":1}""".encodeToByteArray()).preferences.appearance)
        assertFalse(bytes.decodeToString().contains("apiKey"))
        assertTrue(annotation.regions().single().valid())
        assertFalse(PageRect(.8f, .2f, .1f, .3f).valid())
    }
}

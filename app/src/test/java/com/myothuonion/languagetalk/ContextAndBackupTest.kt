package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.data.*
import com.myothuonion.languagetalk.model.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ContextAndBackupTest {
    @Test fun otherChatAndDisabledMemoriesNeverLeak() {
        val items = listOf(MemoryEntity(title = "A", content = "a", scopeChatId = 1),
            MemoryEntity(title = "B", content = "b", scopeChatId = 2), MemoryEntity(title = "C", content = "c", enabled = false))
        assertEquals(listOf("A"), ContextSelector.memories(items, 1, "work").map { it.title })
    }
    @Test fun chatMemorySurvivesManyGlobalMemories() {
        val global = (1..30).map { MemoryEntity(title = "global $it", content = "general") }
        val scoped = MemoryEntity(title = "current lesson", content = "Korean", scopeChatId = 7)
        assertTrue(ContextSelector.memories(global + scoped, 7, "Korean").contains(scoped))
    }
    @Test fun roleplayFactsCannotBecomePersonalMemory() {
        assertNull(ContextSelector.explicitFact("remember I am the manager", "I am the manager", "I am the manager", PracticeMode.ROLEPLAY))
    }
    @Test fun personalMemoryRequiresBothExplicitRequestAndMatchingEvidence() {
        assertNull(ContextSelector.explicitFact("I work mornings", "I work mornings", "I work mornings", PracticeMode.FREE_TALK))
        assertNull(ContextSelector.explicitFact("remember I work mornings", "I work evenings", "I work evenings", PracticeMode.FREE_TALK))
        assertEquals("I work mornings", ContextSelector.explicitFact("remember I work mornings", "I work mornings", "I work mornings", PracticeMode.FREE_TALK))
    }
    @Test fun irrelevantDocumentsAreNotAddedToEveryRequest() {
        val sources = listOf(KnowledgeSourceEntity(name = "contract", mimeType = "text/plain", summary = "factory salary"))
        assertTrue(ContextSelector.sources(sources, "food ordering").isEmpty())
        assertEquals(1, ContextSelector.sources(sources, "factory work").size)
    }
    private fun chat(id: Long = 1) = ChatEntity(id, "Work", "KOREAN", "Work", "Beginner", "TEACHER", "AFTER_REPLY", "", "Kore", "Calm", "GEMINI_ONLY")
    @Test fun backupRoundTripPreservesScopeAndProgressAndHasNoCredentialFields() {
        val backup = LearningBackup(chats = listOf(chat()), memories = listOf(MemoryEntity(title = "lesson", content = "greeting", scopeChatId = 1)),
            progress = listOf(LearningProgressEntity(1, "Work", stage = "APPLY", targetSentence = "안녕하세요.")))
        val encoded = BackupCodec.json.encodeToString(backup)
        val decoded = BackupCodec.decode(encoded.encodeToByteArray())
        assertEquals(1L, decoded.memories.single().scopeChatId); assertEquals("APPLY", decoded.progress.single().stage)
        assertFalse(encoded.contains("apiKey")); assertFalse(encoded.contains("nvidiaApiKey"))
    }
    @Test(expected = IllegalArgumentException::class) fun missingChatReferencesAreRejectedBeforeWrites() {
        val backup = LearningBackup(messages = listOf(MessageEntity(1, 99, "USER", "hello")))
        BackupCodec.decode(BackupCodec.json.encodeToString(backup).encodeToByteArray())
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateChatIdsAreRejected() {
        BackupCodec.decode(BackupCodec.json.encodeToString(LearningBackup(chats = listOf(chat(), chat()))).encodeToByteArray())
    }
    @Test(expected = IllegalArgumentException::class) fun unrelatedJsonIsNotAValidBackup() {
        BackupCodec.decode("{\"format\":\"another-app\"}".encodeToByteArray())
    }
}

package com.myothuonion.languagetalk

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myothuonion.languagetalk.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpgradeAndBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun legacyHelperJsonRestoresThePreviousDatabaseFields() = runBlocking<Unit> {
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val backup = testContext.assets.open("legacy-backup.json").use { it.readBytes() }
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val recordings = File(context.cacheDir, "legacy-json-test")
        try {
            assertEquals(1, BackupStore(db, SettingsStore(context), recordings).restore(backup))
            val chat = db.dao().allChats().single()
            assertEquals("အရင်သင်ခန်းစာ", chat.title)
            assertEquals("GUIDED", chat.practiceMode)
            assertTrue(chat.pinned)
            assertEquals("안녕하세요.", db.dao().allMessages().single().content)
            assertEquals(chat.id, db.dao().allMemories().single().scopeChatId)
            assertEquals("Saved summary", db.dao().allSources().single().summary)
            assertEquals("", db.dao().allSources().single().uri)
        } finally { db.close(); recordings.deleteRecursively() }
    }

    @Test fun openingAnExistingV2DatabasePreservesChatsAndMemories() = runBlocking<Unit> {
        context.deleteDatabase("upgrade-test.db")
        val path = context.getDatabasePath("upgrade-test.db")
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE chats (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, language TEXT NOT NULL, topic TEXT NOT NULL, level TEXT NOT NULL, tutorRole TEXT NOT NULL, correctionMode TEXT NOT NULL, customPrompt TEXT NOT NULL, voiceName TEXT NOT NULL, voiceStyle TEXT NOT NULL, brainMode TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, summary TEXT NOT NULL, archived INTEGER NOT NULL, pinned INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, chatId INTEGER NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, translation TEXT NOT NULL, correction TEXT NOT NULL, explanation TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(chatId) REFERENCES chats(id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_messages_chatId ON messages(chatId)")
            db.execSQL("CREATE TABLE memories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, category TEXT NOT NULL, scopeChatId INTEGER, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX index_memories_scopeChatId ON memories(scopeChatId)")
            db.execSQL("CREATE TABLE knowledge_sources (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, mimeType TEXT NOT NULL, uri TEXT NOT NULL, summary TEXT NOT NULL, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("INSERT INTO chats VALUES(1, 'Previous lesson', 'KOREAN', 'Factory', 'Beginner', 'TEACHER', 'AFTER_REPLY', '', 'Charon', 'Calm', 'GEMINI_ONLY', 1, 1, 'Old notes', 0, 0)")
            db.execSQL("INSERT INTO memories VALUES(1, 'My old memory', 'Day shift', 'Personal', NULL, 1, 1, 1)")
            db.execSQL("INSERT INTO messages VALUES(1, 1, 'USER', '안녕하세요.', '', '', '', 1)")
            db.version = 2
        }
        val upgraded = AppDatabase.open(context, "upgrade-test.db")
        assertEquals("Previous lesson", upgraded.dao().allChats().single().title)
        assertEquals("GUIDED", upgraded.dao().allChats().single().practiceMode)
        assertEquals(2000, upgraded.dao().allChats().single().silenceMs)
        assertEquals("Day shift", upgraded.dao().allMemories().single().content)
        assertEquals("안녕하세요.", upgraded.dao().allMessages().single().content)
        assertTrue(upgraded.dao().allProgress().isEmpty())
        upgraded.close()
        context.deleteDatabase("upgrade-test.db")
    }

    @Test fun portableBackupRestoresRecordingsAndRemapsChatScopes() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val directory = File(context.cacheDir, "backup-recordings-test").apply { mkdirs() }
        try {
            val dao = db.dao()
            val id = dao.insertChat(ChatEntity(title = "Practice", language = "KOREAN", topic = "Work", level = "Beginner",
                tutorRole = "TEACHER", correctionMode = "AFTER_REPLY", customPrompt = "", voiceName = "Kore", voiceStyle = "Calm", brainMode = "GEMINI_ONLY"))
            File(directory, "sample.wav").writeBytes(com.myothuonion.languagetalk.util.pcm16Wave(ByteArray(3200)))
            dao.insertMessage(MessageEntity(chatId = id, role = "USER", content = "안녕하세요.", audioPath = "sample.wav", audioMimeType = "audio/wav"))
            dao.insertMemory(MemoryEntity(title = "Scope", content = "Only this chat", scopeChatId = id))
            dao.saveProgress(LearningProgressEntity(id, "Work", stage = "APPLY", targetSentence = "안녕하세요."))
            val store = BackupStore(db, SettingsStore(context), directory)
            val backup = store.export()
            assertEquals(1, store.restore(backup))
            val restored = dao.allChats().first { it.id != id }
            assertEquals(restored.id, dao.allMemories().first { it.scopeChatId != id }.scopeChatId)
            assertEquals("APPLY", dao.getProgress(restored.id)?.stage)
            val recording = dao.allMessages().first { it.chatId == restored.id }
            assertTrue(File(directory, recording.audioPath).isFile)
            assertFalse(recording.audioPath == "sample.wav")
        } finally { db.close(); directory.deleteRecursively() }
    }
}

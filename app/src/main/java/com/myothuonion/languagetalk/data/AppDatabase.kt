package com.myothuonion.languagetalk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatEntity::class, MessageEntity::class, MemoryEntity::class, KnowledgeSourceEntity::class, LearningProgressEntity::class, ReviewItemEntity::class, ReaderAnnotation::class, PageExplanation::class, BookAudioAsset::class, ReplaySession::class, ReplayClip::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LanguageTalkDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: open(context, "language-talk.db").also { instance = it }
        }

        fun open(context: Context, name: String): AppDatabase = Room.databaseBuilder(
            context.applicationContext, AppDatabase::class.java, name
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE book_audio_assets ADD COLUMN pagesJson TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("CREATE TABLE IF NOT EXISTS replay_sessions (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, kind TEXT NOT NULL, sourceId TEXT NOT NULL, bookId TEXT NOT NULL, page INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS replay_clips (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL, speaker TEXT NOT NULL, text TEXT NOT NULL, audioName TEXT NOT NULL, mimeType TEXT NOT NULL, durationMs INTEGER NOT NULL, origin TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(sessionId) REFERENCES replay_sessions(id) ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_replay_clips_sessionId ON replay_clips(sessionId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE learning_progress ADD COLUMN readyNext INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE learning_progress ADD COLUMN pendingStage TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE learning_progress ADD COLUMN pendingTarget TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE learning_progress ADD COLUMN pendingCompleted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE TABLE IF NOT EXISTS reader_annotations (id TEXT NOT NULL PRIMARY KEY, bookId TEXT NOT NULL, fingerprint TEXT NOT NULL, bookTitle TEXT NOT NULL, page INTEGER NOT NULL, quote TEXT NOT NULL, context TEXT NOT NULL, kind TEXT NOT NULL, savedType TEXT NOT NULL, color INTEGER NOT NULL, rectangles TEXT NOT NULL, startOffset INTEGER NOT NULL, endOffset INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reader_annotations_bookId_page ON reader_annotations(bookId, page)")
                database.execSQL("CREATE TABLE IF NOT EXISTS page_explanations (`key` TEXT NOT NULL PRIMARY KEY, bookId TEXT NOT NULL, fingerprint TEXT NOT NULL, page INTEGER NOT NULL, sourceHash TEXT NOT NULL, teaching TEXT NOT NULL, sourceBlocks TEXT NOT NULL, review TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_page_explanations_bookId_page ON page_explanations(bookId, page)")
                database.execSQL("CREATE TABLE IF NOT EXISTS book_audio_assets (id TEXT NOT NULL PRIMARY KEY, bookId TEXT NOT NULL, track INTEGER NOT NULL, fileName TEXT NOT NULL, mimeType TEXT NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_book_audio_assets_bookId_track ON book_audio_assets(bookId, track)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                listOf("answerPattern", "answerExample", "answerHint").forEach {
                    database.execSQL("ALTER TABLE learning_progress ADD COLUMN $it TEXT NOT NULL DEFAULT ''")
                }
                database.execSQL("ALTER TABLE learning_progress ADD COLUMN assistedCurrent INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chats ADD COLUMN practiceMode TEXT NOT NULL DEFAULT 'GUIDED'")
                database.execSQL("ALTER TABLE chats ADD COLUMN speakingPace TEXT NOT NULL DEFAULT 'SLOW'")
                database.execSQL("ALTER TABLE chats ADD COLUMN silenceMs INTEGER NOT NULL DEFAULT 2000")
                database.execSQL("ALTER TABLE chats ADD COLUMN speakCorrections INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE messages ADD COLUMN audioPath TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE messages ADD COLUMN audioMimeType TEXT NOT NULL DEFAULT ''")
                database.execSQL("CREATE TABLE IF NOT EXISTS learning_progress (chatId INTEGER NOT NULL PRIMARY KEY, goal TEXT NOT NULL, stage TEXT NOT NULL, targetSentence TEXT NOT NULL, completed INTEGER NOT NULL, summary TEXT NOT NULL, lastCorrection TEXT NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(chatId) REFERENCES chats(id) ON DELETE CASCADE)")
                database.execSQL("CREATE TABLE IF NOT EXISTS review_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, chatId INTEGER NOT NULL, sentence TEXT NOT NULL, correction TEXT NOT NULL, dueAt INTEGER NOT NULL, successes INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(chatId) REFERENCES chats(id) ON DELETE CASCADE)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_review_items_chatId_sentence ON review_items(chatId, sentence)")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE memories ADD COLUMN scopeChatId INTEGER")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_memories_scopeChatId ON memories(scopeChatId)")
            }
        }
    }
}

package com.myothuonion.languagetalk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatEntity::class, MessageEntity::class, MemoryEntity::class, KnowledgeSourceEntity::class, LearningProgressEntity::class, ReviewItemEntity::class],
    version = 4,
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

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

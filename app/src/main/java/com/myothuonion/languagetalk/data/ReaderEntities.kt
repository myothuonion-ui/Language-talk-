package com.myothuonion.languagetalk.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myothuonion.languagetalk.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
@Entity(tableName = "reader_annotations", indices = [Index(value = ["bookId", "page"])])
data class ReaderAnnotation(
    @PrimaryKey val id: String,
    val bookId: String, val fingerprint: String, val bookTitle: String, val page: Int,
    val quote: String, val context: String = "", val kind: String = "HIGHLIGHT", val savedType: String = "PASSAGE",
    val color: Long = 0xFFFFD666, val rectangles: String = "[]", val startOffset: Int = -1, val endOffset: Int = -1,
    val note: String = "", val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis()
) {
    fun regions(): List<PageRect> = runCatching { PageTeachingCodec.json.decodeFromString<List<PageRect>>(rectangles) }.getOrDefault(emptyList())
}

@Serializable
@Entity(tableName = "page_explanations", indices = [Index(value = ["bookId", "page"])])
data class PageExplanation(
    @PrimaryKey val key: String, val bookId: String, val fingerprint: String, val page: Int,
    val sourceHash: String, val teaching: String, val sourceBlocks: String, val review: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun value() = PageTeachingCodec.json.decodeFromString<PageTeaching>(teaching)
    fun blocks() = PageTeachingCodec.json.decodeFromString<List<SourceBlock>>(sourceBlocks)
}

@Serializable
@Entity(tableName = "book_audio_assets", indices = [Index(value = ["bookId", "track"], unique = true)])
data class BookAudioAsset(
    @PrimaryKey val id: String, val bookId: String, val track: Int, val fileName: String,
    val mimeType: String, val title: String, val createdAt: Long = System.currentTimeMillis()
)

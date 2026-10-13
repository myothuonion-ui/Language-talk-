package com.myothuonion.languagetalk.data

import androidx.room.*
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "replay_sessions")
data class ReplaySession(@PrimaryKey val id: String, val title: String, val kind: String, val sourceId: String,
    val bookId: String = "", val page: Int = -1, val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis())

@Serializable
@Entity(tableName = "replay_clips", foreignKeys = [ForeignKey(entity = ReplaySession::class,
    parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")])
data class ReplayClip(@PrimaryKey(autoGenerate = true) val id: Long = 0, val sessionId: String,
    val speaker: String, val text: String, val audioName: String = "", val mimeType: String = "",
    val durationMs: Long = 0, val origin: String = "TEXT_ONLY", val createdAt: Long = System.currentTimeMillis())

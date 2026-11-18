package com.jstensix.cardspending.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "unparsed", indices = [Index(value = ["contentHash"], unique = true)])
data class UnparsedNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentHash: String,
    val packageName: String,
    val title: String?,
    val body: String,
    @Serializable(with = LocalDateTimeSerializer::class) val postedAt: LocalDateTime,
    val parserVersion: Int,
    /** Hidden from the list but kept, so the same alert re-posted later is not shown again. */
    val dismissed: Boolean = false,
)

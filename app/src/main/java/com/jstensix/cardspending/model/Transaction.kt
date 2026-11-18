package com.jstensix.cardspending.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

// New fields must have defaults: Drive backups written by older versions are decoded into this class.
@Serializable
@Entity(tableName = "transactions", indices = [Index("issuer", "mask", "approvedAt")])
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issuer: Issuer,
    val kind: TxKind,
    val amount: Long,
    val merchant: String,
    val mask: String,
    val installment: Boolean,
    @Serializable(with = LocalDateTimeSerializer::class) val approvedAt: LocalDateTime,
    val cumulative: Long? = null,
    val source: TxSource,
    val sourcePackage: String? = null,
    val rawText: String? = null,
    /** null = follow the card's exclusion rules. */
    val manualRecognition: Boolean? = null,
    /** For a cancel: the approval it cancels. */
    val originalId: Long? = null,
    /** Notification transactions are soft-deleted so a re-posted KakaoTalk alert is still seen as a duplicate. */
    val deleted: Boolean = false,
    @Serializable(with = LocalDateTimeSerializer::class) val createdAt: LocalDateTime,
)

package com.jstensix.cardspending.backup

import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.UnparsedNotification
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val cards: List<Card>,
    val transactions: List<Transaction>,
    val unparsed: List<UnparsedNotification>,
)

object BackupCodec {
    // Unknown keys are ignored and defaults filled in, so backups survive schema changes in both directions.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(data: BackupData): String = json.encodeToString(BackupData.serializer(), data)
    fun decode(text: String): BackupData = json.decodeFromString(BackupData.serializer(), text)
}

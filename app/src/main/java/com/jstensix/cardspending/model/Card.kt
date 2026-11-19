package com.jstensix.cardspending.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issuer: Issuer,
    val last4: String,
    val nickname: String,
    val exclusions: Set<ExclusionItem> = emptySet(),
    /** Ascending by amount, at most 5. The default matches the column default added by MIGRATION_1_2. */
    @ColumnInfo(defaultValue = "[]") val tiers: List<Tier> = emptyList(),
)

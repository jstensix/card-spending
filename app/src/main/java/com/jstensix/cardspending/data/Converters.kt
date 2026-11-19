package com.jstensix.cardspending.data

import androidx.room.TypeConverter
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.MINUTE_FORMAT
import com.jstensix.cardspending.model.Tier
import java.time.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// Enums use Room's built-in name conversion, so queries can compare against 'APPROVAL' etc.
class Converters {
    /** Fixed-width minute format: string comparison in SQL equals time comparison. */
    @TypeConverter fun fromDateTime(value: LocalDateTime?): String? = value?.format(MINUTE_FORMAT)
    @TypeConverter fun toDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it, MINUTE_FORMAT) }

    @TypeConverter fun fromExclusions(value: Set<ExclusionItem>): String = value.joinToString(",") { it.name }
    @TypeConverter fun toExclusions(value: String): Set<ExclusionItem> =
        value.split(",").mapNotNull { name -> ExclusionItem.entries.firstOrNull { it.name == name } }.toSet()

    @TypeConverter fun fromTiers(value: List<Tier>): String = tierJson.encodeToString(tierList, value)
    @TypeConverter fun toTiers(value: String): List<Tier> = tierJson.decodeFromString(tierList, value)
}

// Outside the class: Room's processor reads every member of a converter class and cannot resolve these types.
private val tierJson: Json = Json { ignoreUnknownKeys = true }
private val tierList: KSerializer<List<Tier>> = ListSerializer(Tier.serializer())

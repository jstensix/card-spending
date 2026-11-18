package com.jstensix.cardspending.data

import androidx.room.TypeConverter
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.MINUTE_FORMAT
import java.time.LocalDateTime

// Enums use Room's built-in name conversion, so queries can compare against 'APPROVAL' etc.
class Converters {
    /** Fixed-width minute format: string comparison in SQL equals time comparison. */
    @TypeConverter fun fromDateTime(value: LocalDateTime?): String? = value?.format(MINUTE_FORMAT)
    @TypeConverter fun toDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it, MINUTE_FORMAT) }

    @TypeConverter fun fromExclusions(value: Set<ExclusionItem>): String = value.joinToString(",") { it.name }
    @TypeConverter fun toExclusions(value: String): Set<ExclusionItem> =
        value.split(",").mapNotNull { name -> ExclusionItem.entries.firstOrNull { it.name == name } }.toSet()
}

package com.jstensix.cardspending.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jstensix.cardspending.model.UnparsedNotification
import kotlinx.coroutines.flow.Flow

@Dao
interface UnparsedDao {
    /** Returns -1 when the same content hash already exists (including dismissed rows). */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(item: UnparsedNotification): Long
    @Query("SELECT * FROM unparsed WHERE dismissed = 0 ORDER BY postedAt DESC") fun observeVisible(): Flow<List<UnparsedNotification>>
    @Query("SELECT * FROM unparsed WHERE id = :id") suspend fun get(id: Long): UnparsedNotification?
    @Query("SELECT * FROM unparsed WHERE dismissed = 0 AND parserVersion < :version") suspend fun pending(version: Int): List<UnparsedNotification>
    @Query("UPDATE unparsed SET parserVersion = :version WHERE id = :id") suspend fun updateVersion(id: Long, version: Int)
    @Query("UPDATE unparsed SET dismissed = 1 WHERE id = :id") suspend fun dismiss(id: Long)
    @Query("DELETE FROM unparsed WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM unparsed") suspend fun all(): List<UnparsedNotification>
    @Query("DELETE FROM unparsed") suspend fun deleteAll()
    @Insert suspend fun insertAll(items: List<UnparsedNotification>)
}

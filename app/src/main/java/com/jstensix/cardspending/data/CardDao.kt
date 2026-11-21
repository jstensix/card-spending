package com.jstensix.cardspending.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jstensix.cardspending.model.Card
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY sortOrder, id") fun observeAll(): Flow<List<Card>>
    @Query("SELECT * FROM cards ORDER BY sortOrder, id") suspend fun all(): List<Card>
    @Query("SELECT MAX(sortOrder) FROM cards") suspend fun maxSortOrder(): Int?
    @Query("UPDATE cards SET sortOrder = :order WHERE id = :id") suspend fun setSortOrder(id: Long, order: Int)
    @Query("SELECT * FROM cards WHERE id = :id") suspend fun get(id: Long): Card?
    @Insert suspend fun insert(card: Card): Long
    @Update suspend fun update(card: Card)
    @Query("DELETE FROM cards WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM cards") suspend fun count(): Int
    @Query("DELETE FROM cards") suspend fun deleteAll()
    @Insert suspend fun insertAll(cards: List<Card>)
}

package com.jstensix.cardspending.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deleted = 0 AND approvedAt >= :start AND approvedAt < :end ORDER BY approvedAt DESC")
    fun observeRange(start: LocalDateTime, end: LocalDateTime): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id IN (:ids)") suspend fun byIds(ids: List<Long>): List<Transaction>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun get(id: Long): Transaction?
    @Insert suspend fun insert(tx: Transaction): Long
    @Query("UPDATE transactions SET manualRecognition = :value WHERE id = :id") suspend fun setManual(id: Long, value: Boolean?)
    @Query("UPDATE transactions SET deleted = 1 WHERE id = :id") suspend fun softDelete(id: Long)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun hardDelete(id: Long)

    @Query(
        "SELECT * FROM transactions WHERE issuer = :issuer AND mask = :mask AND kind = 'APPROVAL' AND deleted = 0 " +
            "AND approvedAt <= :before AND amount >= :minAmount ORDER BY approvedAt DESC",
    )
    suspend fun approvalsForCancel(issuer: Issuer, mask: String, before: LocalDateTime, minAmount: Long): List<Transaction>

    @Query(
        "SELECT * FROM transactions WHERE source = 'NOTIFICATION' AND issuer = :issuer AND mask = :mask " +
            "AND amount = :amount AND kind = :kind AND approvedAt = :approvedAt",
    )
    suspend fun findSameKey(issuer: Issuer, mask: String, amount: Long, kind: TxKind, approvedAt: LocalDateTime): List<Transaction>

    @Query(
        "SELECT * FROM transactions WHERE deleted = 0 AND source = :source AND issuer = :issuer AND mask = :mask " +
            "AND amount = :amount AND kind = :kind AND approvedAt >= :start AND approvedAt < :end",
    )
    suspend fun findInRange(
        source: TxSource,
        issuer: Issuer,
        mask: String,
        amount: Long,
        kind: TxKind,
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<Transaction>

    /** Notification rows of one day, soft-deleted ones included, like [findSameKey]. */
    @Query(
        "SELECT * FROM transactions WHERE source = 'NOTIFICATION' AND issuer = :issuer AND mask = :mask " +
            "AND amount = :amount AND kind = :kind AND approvedAt >= :start AND approvedAt < :end",
    )
    suspend fun notificationsInRange(
        issuer: Issuer,
        mask: String,
        amount: Long,
        kind: TxKind,
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<Transaction>

    @Update suspend fun update(tx: Transaction)

    @Query("SELECT COUNT(*) FROM transactions") suspend fun count(): Int
    @Query("SELECT * FROM transactions") suspend fun all(): List<Transaction>
    @Query("DELETE FROM transactions") suspend fun deleteAll()
    @Insert suspend fun insertAll(transactions: List<Transaction>)
}

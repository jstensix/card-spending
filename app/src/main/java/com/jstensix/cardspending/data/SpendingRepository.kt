package com.jstensix.cardspending.data

import androidx.room.withTransaction
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import com.jstensix.cardspending.model.UnparsedNotification
import com.jstensix.cardspending.rules.monthBounds
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MonthData(val transactions: List<Transaction>, val originalsById: Map<Long, Transaction>)

/** Every database read and write goes through here; each write reports [onChanged] (drives the Drive backup). */
class SpendingRepository(private val db: AppDatabase, private val onChanged: () -> Unit) {
    private val cardDao = db.cardDao()
    private val txDao = db.transactionDao()
    private val unparsedDao = db.unparsedDao()

    fun observeCards(): Flow<List<Card>> = cardDao.observeAll()
    suspend fun getCard(id: Long): Card? = cardDao.get(id)

    suspend fun saveCard(card: Card): Long {
        val id = if (card.id == 0L) cardDao.insert(card) else card.id.also { cardDao.update(card) }
        onChanged()
        return id
    }

    /** Transactions stay: registering a matching card later picks them up again. */
    suspend fun deleteCard(id: Long) {
        cardDao.delete(id)
        onChanged()
    }

    /** Cancels may point at an approval in an earlier month, so their originals are loaded too. */
    fun observeMonth(month: YearMonth): Flow<MonthData> {
        val (start, end) = monthBounds(month)
        return txDao.observeRange(start, end).map { list ->
            val inRange = list.associateBy { it.id }
            val missing = list.mapNotNull { it.originalId }.filter { it !in inRange }.distinct()
            val fetched = if (missing.isEmpty()) emptyMap() else txDao.byIds(missing).associateBy { it.id }
            MonthData(list, inRange + fetched)
        }
    }

    suspend fun getTransaction(id: Long): Transaction? = txDao.get(id)

    suspend fun addManual(tx: Transaction): Long = txDao.insert(tx).also { onChanged() }

    suspend fun setManualRecognition(txId: Long, value: Boolean?) {
        txDao.setManual(txId, value)
        onChanged()
    }

    /** Notification rows are only flagged, so the same alert re-posted later is still recognized as a duplicate. */
    suspend fun deleteTransaction(txId: Long) {
        val tx = txDao.get(txId) ?: return
        if (tx.source == TxSource.NOTIFICATION) txDao.softDelete(txId) else txDao.hardDelete(txId)
        onChanged()
    }

    suspend fun approvalsForCancel(issuer: Issuer, mask: String, before: LocalDateTime, minAmount: Long): List<Transaction> =
        txDao.approvalsForCancel(issuer, mask, before, minAmount)

    suspend fun findSameKey(issuer: Issuer, mask: String, amount: Long, kind: TxKind, approvedAt: LocalDateTime): List<Transaction> =
        txDao.findSameKey(issuer, mask, amount, kind, approvedAt)

    suspend fun insertNotificationTx(tx: Transaction): Long = txDao.insert(tx).also { onChanged() }

    /** Live rows of [source] on the same card, amount and kind during [day]. */
    suspend fun findSameDay(source: TxSource, issuer: Issuer, mask: String, amount: Long, kind: TxKind, day: LocalDate): List<Transaction> =
        txDao.findInRange(source, issuer, mask, amount, kind, day.atStartOfDay(), day.plusDays(1).atStartOfDay())

    /** Replaces a hand-entered row with the real notification data, keeping its id so cancels linked to it stay linked. */
    suspend fun upgradeManual(tx: Transaction) {
        txDao.update(tx)
        onChanged()
    }

    suspend fun knownSourcePackages(): Set<String> = txDao.sourcePackages().toSet()

    fun observeUnparsed(): Flow<List<UnparsedNotification>> = unparsedDao.observeVisible()
    suspend fun getUnparsed(id: Long): UnparsedNotification? = unparsedDao.get(id)

    suspend fun insertUnparsedIfNew(item: UnparsedNotification) {
        if (unparsedDao.insertIgnore(item) != -1L) onChanged()
    }

    suspend fun pendingUnparsed(currentVersion: Int): List<UnparsedNotification> = unparsedDao.pending(currentVersion)

    suspend fun updateUnparsedVersion(id: Long, version: Int) {
        unparsedDao.updateVersion(id, version)
        onChanged()
    }

    suspend fun dismissUnparsed(id: Long) {
        unparsedDao.dismiss(id)
        onChanged()
    }

    /** Removes the text entirely (also from the next backup), e.g. something that should never have been captured. */
    suspend fun deleteUnparsed(id: Long) {
        unparsedDao.delete(id)
        onChanged()
    }

    suspend fun isEmpty(): Boolean = cardDao.count() == 0 && txDao.count() == 0

    /** Everything, including soft-deleted and dismissed rows: they keep duplicate detection working after a restore. */
    suspend fun snapshot(): Triple<List<Card>, List<Transaction>, List<UnparsedNotification>> =
        Triple(cardDao.all(), txDao.all(), unparsedDao.all())

    suspend fun replaceAll(cards: List<Card>, transactions: List<Transaction>, unparsed: List<UnparsedNotification>) {
        db.withTransaction {
            cardDao.deleteAll()
            txDao.deleteAll()
            unparsedDao.deleteAll()
            cardDao.insertAll(cards)
            txDao.insertAll(transactions)
            unparsedDao.insertAll(unparsed)
        }
    }
}

package com.jstensix.cardspending.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()

/** ViewModel state is fed by Room flows on real background threads, so wait in real time. */
internal fun <T> Flow<T>.await(predicate: (T) -> Boolean): T = runBlocking { withTimeout(5_000) { first(predicate) } }

internal fun notificationTx(
    amount: Long,
    merchant: String,
    approvedAt: LocalDateTime,
    mask: String = "7*1*",
    issuer: Issuer = Issuer.LOTTE,
    kind: TxKind = TxKind.APPROVAL,
    originalId: Long? = null,
) = Transaction(
    issuer = issuer, kind = kind, amount = amount, merchant = merchant, mask = mask, installment = false,
    approvedAt = approvedAt, source = TxSource.NOTIFICATION, originalId = originalId, createdAt = approvedAt,
)

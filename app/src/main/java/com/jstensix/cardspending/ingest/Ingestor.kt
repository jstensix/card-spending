package com.jstensix.cardspending.ingest

import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import com.jstensix.cardspending.model.UnparsedNotification
import com.jstensix.cardspending.parsing.CardNotificationParser
import com.jstensix.cardspending.parsing.NotificationMessage
import com.jstensix.cardspending.parsing.PARSER_VERSION
import com.jstensix.cardspending.parsing.ParseOutcome
import com.jstensix.cardspending.parsing.ParsedTransaction
import com.jstensix.cardspending.rules.findOriginal
import com.jstensix.cardspending.rules.isSameTransaction
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class IngestResult { INSERTED, DUPLICATE, MERGED, UNPARSED, IGNORED }

/** Identity of an unrecognized alert. Arrival time is left out so a re-posted alert maps to the same row. */
fun NotificationMessage.contentHash(): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest("$packageName\u0000${title.orEmpty()}\u0000$body".toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

class Ingestor(
    private val repo: SpendingRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    /**
     * The Hana app push and the KakaoTalk alert for one payment arrive within milliseconds on separate
     * coroutines; without serializing check-then-insert both would pass the duplicate check.
     */
    private val mutex = Mutex()

    suspend fun ingestAll(messages: List<NotificationMessage>): List<IngestResult> = messages.map { ingest(it) }

    suspend fun ingest(message: NotificationMessage): IngestResult = mutex.withLock { ingestLocked(message) }

    private suspend fun ingestLocked(message: NotificationMessage): IngestResult =
        when (val outcome = CardNotificationParser.parse(message, repo.observeCards().first())) {
            is ParseOutcome.Parsed -> store(outcome.tx, message.packageName)
            ParseOutcome.Candidate -> {
                repo.insertUnparsedIfNew(
                    UnparsedNotification(
                        contentHash = message.contentHash(),
                        packageName = message.packageName,
                        title = message.title,
                        body = message.body,
                        postedAt = message.postedAt,
                        parserVersion = PARSER_VERSION,
                    ),
                )
                IngestResult.UNPARSED
            }
            ParseOutcome.Ignored -> IngestResult.IGNORED
        }

    /**
     * Re-runs the parsers over stored unrecognized alerts after an app update changed them: newly understood
     * alerts become payments, and ones the current rules would not keep (ads, other apps) are removed.
     */
    suspend fun reparsePending(): Int = mutex.withLock {
        var converted = 0
        val cards = repo.observeCards().first()
        for (item in repo.pendingUnparsed(PARSER_VERSION)) {
            val message = NotificationMessage(item.packageName, item.title, item.body, item.postedAt)
            when (val outcome = CardNotificationParser.parse(message, cards)) {
                is ParseOutcome.Parsed -> {
                    if (store(outcome.tx, item.packageName) == IngestResult.INSERTED) converted++
                    repo.dismissUnparsed(item.id)
                }
                ParseOutcome.Candidate -> repo.updateUnparsedVersion(item.id, PARSER_VERSION)
                ParseOutcome.Ignored -> repo.deleteUnparsed(item.id)
            }
        }
        converted
    }

    private suspend fun store(tx: ParsedTransaction, sourcePackage: String): IngestResult {
        val sameKey = repo.findSameKey(tx.issuer, tx.mask, tx.amount, tx.kind, tx.approvedAt)
        if (sameKey.any { isSameTransaction(it, tx) }) return IngestResult.DUPLICATE

        val originalId = if (tx.kind == TxKind.CANCEL) {
            findOriginal(tx, repo.approvalsForCancel(tx.issuer, tx.mask, tx.approvedAt, tx.amount))?.id
        } else {
            null
        }
        val row = Transaction(
            issuer = tx.issuer,
            kind = tx.kind,
            amount = tx.amount,
            merchant = tx.merchant,
            mask = tx.mask,
            installment = tx.installment,
            approvedAt = tx.approvedAt,
            cumulative = tx.cumulative,
            source = TxSource.NOTIFICATION,
            sourcePackage = sourcePackage,
            rawText = tx.rawText,
            originalId = originalId,
            createdAt = now(),
        )

        // A hand-entered row for the same payment (before install, or from an unrecognized alert) is
        // replaced by the real data instead of counting the payment twice.
        val manual = repo.findSameDay(TxSource.MANUAL, tx.issuer, tx.mask, tx.amount, tx.kind, tx.approvedAt.toLocalDate())
            .minByOrNull { Duration.between(it.approvedAt, tx.approvedAt).abs() }
        if (manual != null) {
            repo.upgradeManual(
                row.copy(id = manual.id, manualRecognition = manual.manualRecognition, originalId = originalId ?: manual.originalId),
            )
            return IngestResult.MERGED
        }

        repo.insertNotificationTx(row)
        return IngestResult.INSERTED
    }
}

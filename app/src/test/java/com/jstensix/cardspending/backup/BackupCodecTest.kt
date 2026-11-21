package com.jstensix.cardspending.backup

import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Tier
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import com.jstensix.cardspending.model.UnparsedNotification
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCodecTest {
    private val at = LocalDateTime.of(2026, 11, 18, 11, 43)
    private val approval = Transaction(
        id = 1, issuer = Issuer.LOTTE, kind = TxKind.APPROVAL, amount = 11000, merchant = "편의점", mask = "7*1*",
        installment = false, approvedAt = at, cumulative = 3220782, source = TxSource.NOTIFICATION,
        sourcePackage = "com.kakao.talk", rawText = "원문", createdAt = at,
    )
    private val data = BackupData(
        exportedAt = "2026-11-18T11:43",
        cards = listOf(Card(1, Issuer.LOTTE, "7512", "텔로", setOf(ExclusionItem.TAX))),
        transactions = listOf(
            approval,
            approval.copy(id = 2, kind = TxKind.CANCEL, amount = 3000, originalId = 1, manualRecognition = false, deleted = true),
        ),
        unparsed = listOf(
            UnparsedNotification(1, "hash", "com.kakao.talk", "하나카드", "본문", at, parserVersion = 1, dismissed = true),
        ),
    )

    @Test fun roundTrip_preservesEverything() {
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }

    @Test fun unknownField_ignored() {
        val json = BackupCodec.encode(data).replaceFirst("{", "{\"futureField\":1,")
        assertEquals(data, BackupCodec.decode(json))
    }

    @Test fun roundTrip_preservesTiers() {
        val withTiers = data.copy(cards = data.cards.map { it.copy(tiers = listOf(Tier(300000, "통신비 5천원"))) })
        assertEquals(withTiers, BackupCodec.decode(BackupCodec.encode(withTiers)))
    }

    @Test fun roundTrip_preservesSortOrder() {
        val ordered = data.copy(cards = data.cards.map { it.copy(sortOrder = 3) })
        assertEquals(ordered, BackupCodec.decode(BackupCodec.encode(ordered)))
    }

    // Backups written before card order existed have no "sortOrder" key: they restore in registration order.
    @Test fun oldBackupWithoutSortOrder_decodesZero() {
        val json = BackupCodec.encode(data).replace(",\"sortOrder\":0", "")
        assertFalse(json.contains("\"sortOrder\""))
        assertEquals(data, BackupCodec.decode(json))
    }

    // Backups written before tiers existed have no "tiers" key.
    @Test fun oldBackupWithoutTiers_decodesEmptyTiers() {
        val json = BackupCodec.encode(data).replace(",\"tiers\":[]", "")
        assertFalse(json.contains("\"tiers\""))
        assertEquals(data, BackupCodec.decode(json))
    }

    @Test fun missingOptionalField_usesDefault() {
        val json = BackupCodec.encode(data.copy(transactions = listOf(approval))).replace(",\"deleted\":false", "")
        assertFalse(json.contains("\"deleted\""))
        assertFalse(BackupCodec.decode(json).transactions.single().deleted)
    }
}

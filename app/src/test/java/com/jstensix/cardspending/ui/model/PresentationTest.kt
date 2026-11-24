package com.jstensix.cardspending.ui.model

import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Tier
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import com.jstensix.cardspending.rules.tierProgress
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresentationTest {
    @Test fun recognizedRatio_normal() {
        assertEquals(481200f / 523400f, recognizedRatio(523400, 481200)!!, 0.0001f)
    }

    @Test fun recognizedRatio_clamped() {
        assertEquals(1f, recognizedRatio(1000, 1500))
        assertEquals(0f, recognizedRatio(1000, -500))
    }

    @Test fun recognizedPercent_floors() {
        assertEquals(93, recognizedPercent(643400, 601200))
    }

    @Test fun recognizedPercent_nonPositivePayment_null() {
        assertNull(recognizedPercent(0, 0))
        assertNull(recognizedPercent(-20000, -20000))
        assertNull(recognizedRatio(-1, -1))
    }

    @Test fun dayLabel_koreanWeekday() {
        assertEquals("11월 18일 (수)", dayLabel(LocalDate.of(2026, 11, 18)))
        assertEquals("11월 17일 (화)", dayLabel(LocalDate.of(2026, 11, 17)))
    }

    @Test fun groupByDay_keepsOrder() {
        val d18 = LocalDate.of(2026, 11, 18)
        val d17 = LocalDate.of(2026, 11, 17)
        val groups = groupByDay(listOf("a" to d18, "b" to d18, "c" to d17)) { it.second }
        assertEquals(listOf(d18, d17), groups.map { it.date })
        assertEquals(listOf("a", "b"), groups[0].items.map { it.first })
        assertEquals("11월 17일 (화)", groups[1].label)
    }

    @Test fun compactManwon_formats() {
        assertEquals("30만", compactManwon(300_000))
        assertEquals("100만", compactManwon(1_000_000))
        assertEquals("12.5만", compactManwon(125_000))
        assertEquals("1,500만", compactManwon(15_000_000))
    }

    @Test fun tierStatus_texts() {
        val tiers = listOf(Tier(300_000), Tier(500_000), Tier(1_000_000))
        assertEquals("30만까지 180,000원 남음", tierStatus(tierProgress(120_000, tiers)!!))
        assertEquals("50만 구간 달성 · 100만까지 380,000원 남음", tierStatus(tierProgress(620_000, tiers)!!))
        assertEquals("최고 구간 달성", tierStatus(tierProgress(1_200_000, tiers)!!))
    }

    // Positions are in px: label centers under each tier mark, measured label widths, gauge width, minimum gap.
    @Test fun tierLabelPositions_spacedAllShown() {
        assertEquals(listOf(75, 135, 260), tierLabelPositions(listOf(90, 150, 300), listOf(30, 30, 40), 300, 4))
    }

    // 50만/100만/120만 on a phone: "100만 ✓" would overlap "120만 ✓", which is pushed in to end at the right edge.
    @Test fun tierLabelPositions_neighbourOfTopHidden() {
        assertEquals(listOf(102, null, 254), tierLabelPositions(listOf(125, 250, 300), listOf(46, 46, 46), 300, 4))
    }

    // 10만/50만 under a 300만 top: the first label is pushed in to start at 0 and crowds the second.
    @Test fun tierLabelPositions_leftClampedNeighbourHidden() {
        assertEquals(listOf(0, null, 260), tierLabelPositions(listOf(10, 50, 300), listOf(40, 40, 40), 300, 4))
    }

    @Test fun txMeta_notificationApproval() {
        val tx = Transaction(
            issuer = Issuer.LOTTE, kind = TxKind.APPROVAL, amount = 5600, merchant = "카페", mask = "7*1*",
            installment = false, approvedAt = LocalDateTime.of(2026, 11, 18, 14, 32), source = TxSource.NOTIFICATION,
            sourcePackage = "com.hanacard.app", createdAt = LocalDateTime.of(2026, 11, 18, 14, 32),
        )
        assertEquals("14:32 · 일시불 · 자동(PUSH)", txMeta(tx))
    }

    @Test fun txMeta_smsApproval() {
        val tx = Transaction(
            issuer = Issuer.HANA, kind = TxKind.APPROVAL, amount = 26950, merchant = "SK브로드밴드-자동납", mask = "0*8*",
            installment = false, approvedAt = LocalDateTime.of(2026, 11, 18, 14, 32), source = TxSource.NOTIFICATION,
            sourcePackage = "com.samsung.android.messaging", createdAt = LocalDateTime.of(2026, 11, 18, 14, 32),
        )
        assertEquals("14:32 · 일시불 · 자동(SMS)", txMeta(tx))
    }

    @Test fun txMeta_manualInstallmentCancel() {
        val tx = Transaction(
            issuer = Issuer.HANA, kind = TxKind.CANCEL, amount = 3000, merchant = "마트", mask = "0*8*",
            installment = true, approvedAt = LocalDateTime.of(2026, 11, 18, 9, 5), source = TxSource.MANUAL,
            createdAt = LocalDateTime.of(2026, 11, 18, 9, 5),
        )
        assertEquals("09:05 · 할부 · 취소 · 직접 입력", txMeta(tx))
    }
}

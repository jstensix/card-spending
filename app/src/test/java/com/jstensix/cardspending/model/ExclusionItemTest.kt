package com.jstensix.cardspending.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusionItemTest {
    @Test fun keywordItems_matchMerchant() {
        assertTrue(ExclusionItem.TAX.matches("국세(카드납부)", false))
        assertTrue(ExclusionItem.UTILITY.matches("한국전력공사", false))
        assertFalse(ExclusionItem.INSURANCE.matches("세븐일레븐", false))
    }

    @Test fun installmentItem_matchesOnlyInstallment() {
        assertTrue(ExclusionItem.INSTALLMENT.matches("아무가게", true))
        assertFalse(ExclusionItem.INSTALLMENT.matches("아무가게", false))
    }
}

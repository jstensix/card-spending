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

    @Test fun rentItem_matchesRentMerchants() {
        assertTrue(ExclusionItem.RENT.matches("월세 카드납부", false))
        assertTrue(ExclusionItem.RENT.matches("상가임대료", false))
        assertTrue(ExclusionItem.RENT.matches("행복부동산", false))
        assertFalse(ExclusionItem.RENT.matches("쏘카 렌트", false))
    }

    @Test fun installmentItem_matchesOnlyInstallment() {
        assertTrue(ExclusionItem.INSTALLMENT.matches("아무가게", true))
        assertFalse(ExclusionItem.INSTALLMENT.matches("아무가게", false))
    }
}

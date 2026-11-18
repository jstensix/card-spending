package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CancelLinkerTest {
    private fun cancel(amount: Long = 11000, merchant: String = "편의점", day: Int = 16) =
        parsed(amount = amount, merchant = merchant, kind = TxKind.CANCEL, approvedAt = at(day))

    @Test fun picksLatestEligibleApproval() {
        val approvals = listOf(tx(id = 1, approvedAt = at(10)), tx(id = 2, approvedAt = at(15)))
        assertEquals(2L, findOriginal(cancel(), approvals)?.id)
    }

    @Test fun prefersSameMerchant() {
        val approvals = listOf(tx(id = 1, merchant = "A", approvedAt = at(15)), tx(id = 2, merchant = "B", approvedAt = at(16, 11)))
        assertEquals(1L, findOriginal(cancel(merchant = "A"), approvals)?.id)
    }

    @Test fun ignoresLaterApprovals_returnsNullWhenNone() {
        assertNull(findOriginal(cancel(day = 16), listOf(tx(id = 1, approvedAt = at(17)))))
    }

    @Test fun partialCancel_links() {
        assertEquals(1L, findOriginal(cancel(amount = 5000), listOf(tx(id = 1, amount = 11000, approvedAt = at(15))))?.id)
    }
}

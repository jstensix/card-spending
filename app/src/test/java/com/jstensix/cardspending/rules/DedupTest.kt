package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.TxKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupTest {
    @Test fun identical_isSame() {
        assertTrue(isSameTransaction(tx(cumulative = 532231), parsed(cumulative = 532231)))
    }

    @Test fun differentCumulative_isDifferent() {
        assertFalse(isSameTransaction(tx(cumulative = 532231), parsed(cumulative = 542131)))
    }

    @Test fun nullCumulative_isSame() {
        assertTrue(isSameTransaction(tx(cumulative = null), parsed(cumulative = 532231)))
    }

    @Test fun differentMinuteOrKind_isDifferent() {
        assertFalse(isSameTransaction(tx(approvedAt = at(18, 12, 0)), parsed(approvedAt = at(18, 12, 1))))
        assertFalse(isSameTransaction(tx(kind = TxKind.APPROVAL), parsed(kind = TxKind.CANCEL)))
    }
}

package com.jstensix.cardspending.ui.main

import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Tier
import com.jstensix.cardspending.model.UnparsedNotification
import com.jstensix.cardspending.ui.await
import com.jstensix.cardspending.ui.inMemoryDb
import com.jstensix.cardspending.ui.notificationTx
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SpendingRepository
    private val november = YearMonth.of(2026, 11)

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = inMemoryDb()
        repo = SpendingRepository(db) {}
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(warning: String? = null) = MainViewModel(repo, { false }, flowOf(warning), november)

    private fun seedTaxCard(): Long = runBlocking {
        repo.saveCard(Card(issuer = Issuer.LOTTE, last4 = "7512", nickname = "텔로", exclusions = setOf(ExclusionItem.TAX)))
    }

    @Test fun row_carriesCardTiers() {
        val tiers = listOf(Tier(300000, "통신"), Tier(500000, "커피"))
        runBlocking { repo.saveCard(Card(issuer = Issuer.LOTTE, last4 = "7512", nickname = "텔로", tiers = tiers)) }
        assertEquals(tiers, vm().state.await { it.rows.isNotEmpty() }.rows.single().tiers)
    }

    @Test fun rows_showMonthTotals() {
        val cardId = seedTaxCard()
        runBlocking {
            repo.insertNotificationTx(notificationTx(10000, "편의점", LocalDateTime.of(2026, 11, 3, 12, 0)))
            repo.insertNotificationTx(notificationTx(50000, "국세", LocalDateTime.of(2026, 11, 4, 12, 0)))
        }
        val state = vm().state.await { it.rows.singleOrNull()?.payment == 60000L }
        assertEquals(CardRow(cardId, Issuer.LOTTE, "텔로", "7*1*", 60000, 10000, excludedCount = 1), state.rows.single())
    }

    @Test fun notLoadedUntilDataArrives_thenLoaded() {
        val viewModel = MainViewModel(repo, { false }, flowOf(null), november)
        val state = viewModel.state.await { it.loaded }
        assertEquals(emptyList<CardRow>(), state.rows)
        assertEquals(false, MainUiState(november, emptyList(), 0, 0, false, null).loaded)
    }

    @Test fun totalsAndExcludedCount() {
        seedTaxCard()
        runBlocking {
            repo.insertNotificationTx(notificationTx(10000, "편의점", LocalDateTime.of(2026, 11, 3, 12, 0)))
            repo.insertNotificationTx(notificationTx(50000, "국세", LocalDateTime.of(2026, 11, 4, 12, 0)))
        }
        val state = vm().state.await { it.totalPayment == 60000L }
        assertEquals(10000L, state.totalRecognized)
        assertEquals(1, state.rows.single().excludedCount)
    }

    @Test fun previousMonth_switchesData() {
        seedTaxCard()
        runBlocking { repo.insertNotificationTx(notificationTx(5000, "편의점", LocalDateTime.of(2026, 10, 20, 12, 0))) }
        val viewModel = vm()
        viewModel.state.await { it.rows.isNotEmpty() }
        viewModel.previousMonth()
        val state = viewModel.state.await { it.month == YearMonth.of(2026, 10) && it.rows.single().payment == 5000L }
        assertEquals(5000L, state.rows.single().recognized)
    }

    @Test fun counts_unparsedAndUnregistered() {
        seedTaxCard()
        runBlocking {
            repo.insertUnparsedIfNew(
                UnparsedNotification(
                    contentHash = "h", packageName = "com.kakao.talk", title = "하나카드", body = "?",
                    postedAt = LocalDateTime.of(2026, 11, 18, 12, 0), parserVersion = 1,
                ),
            )
            repo.insertNotificationTx(notificationTx(1000, "편의점", LocalDateTime.of(2026, 11, 5, 12, 0), mask = "9*9*"))
        }
        val state = vm().state.await { it.unparsedCount == 1 && it.unregisteredCount == 1 }
        assertEquals(1, state.unparsedCount)
    }

    @Test fun backupWarning_passedThrough() {
        val state = vm("백업 경고").state.await { it.backupWarning != null }
        assertEquals("백업 경고", state.backupWarning)
    }
}

package com.jstensix.cardspending.ui.cards

import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.ui.await
import com.jstensix.cardspending.ui.inMemoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CardEditViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SpendingRepository

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = inMemoryDb()
        repo = SpendingRepository(db) {}
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun invalidLast4_cannotSave() {
        val vm = CardEditViewModel(repo, null)
        vm.setLast4("12a4")
        assertFalse(vm.state.value.canSave)
        assertFalse(runBlocking { vm.save() })
    }

    @Test fun conflict_showsWarning_butSaves() {
        runBlocking { repo.saveCard(Card(issuer = Issuer.LOTTE, last4 = "7512", nickname = "텔로")) }
        val vm = CardEditViewModel(repo, null)
        vm.setIssuer(Issuer.LOTTE)
        vm.setLast4("7913")
        val state = vm.state.await { it.conflictWarning != null }
        assertTrue(state.conflictWarning!!.contains("텔로"))
        assertTrue(runBlocking { vm.save() })
        assertEquals(2, runBlocking { repo.observeCards().first() }.size)
    }

    @Test fun save_persistsExclusions() {
        val vm = CardEditViewModel(repo, null)
        vm.setLast4("7512")
        vm.toggleExclusion(ExclusionItem.TAX)
        vm.toggleExclusion(ExclusionItem.INSTALLMENT)
        assertTrue(runBlocking { vm.save() })
        val saved = runBlocking { repo.observeCards().first() }.single()
        assertEquals(setOf(ExclusionItem.TAX, ExclusionItem.INSTALLMENT), saved.exclusions)
    }

    @Test fun blankNickname_defaultsToIssuerName() {
        val vm = CardEditViewModel(repo, null)
        vm.setIssuer(Issuer.HANA)
        vm.setLast4("0381")
        assertTrue(runBlocking { vm.save() })
        assertEquals("하나카드", runBlocking { repo.observeCards().first() }.single().nickname)
    }

    @Test fun existingCard_loadsValues() {
        val id = runBlocking {
            repo.saveCard(Card(issuer = Issuer.HANA, last4 = "0381", nickname = "생활", exclusions = setOf(ExclusionItem.UTILITY)))
        }
        val state = CardEditViewModel(repo, id).state.await { it.last4 == "0381" }
        assertEquals(Issuer.HANA, state.issuer)
        assertEquals("생활", state.nickname)
        assertEquals(setOf(ExclusionItem.UTILITY), state.exclusions)
    }
}

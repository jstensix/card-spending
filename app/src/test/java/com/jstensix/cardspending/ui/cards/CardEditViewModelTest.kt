package com.jstensix.cardspending.ui.cards

import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Tier
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

    private fun newCardVm() = CardEditViewModel(repo, null).also { it.setLast4("7512") }

    @Test fun addTier_capsAtFive() {
        val vm = newCardVm()
        repeat(6) { vm.addTier() }
        assertEquals(MAX_TIERS, vm.state.await { it.tiers.size == MAX_TIERS }.tiers.size)
        assertEquals(5, MAX_TIERS)
    }

    @Test fun save_persistsTiersSortedAndTrimmed() {
        val vm = newCardVm()
        vm.addTier()
        vm.addTier()
        vm.setTierAmount(0, "500,000")
        vm.setTierBenefit(0, "커피 ")
        vm.setTierAmount(1, "300000")
        vm.setTierBenefit(1, "통신")
        vm.state.await { it.canSave && it.tiers.all { t -> t.amountText.isNotEmpty() } }
        assertTrue(runBlocking { vm.save() })
        val saved = runBlocking { repo.observeCards().first() }.single()
        assertEquals(listOf(Tier(300000, "통신"), Tier(500000, "커피")), saved.tiers)
    }

    @Test fun blankTierAmount_blocksSave() {
        val vm = newCardVm()
        vm.addTier()
        val blank = vm.state.await { it.tiers.size == 1 }
        assertFalse(blank.canSave)
        assertEquals("구간 금액을 입력해 주세요", blank.tierWarning)
        assertFalse(runBlocking { vm.save() })
        vm.setTierAmount(0, "0")
        assertEquals("구간 금액을 입력해 주세요", vm.state.await { it.tiers.single().amountText == "0" }.tierWarning)
    }

    @Test fun duplicateTierAmounts_blockSave() {
        val vm = newCardVm()
        vm.addTier()
        vm.addTier()
        vm.setTierAmount(0, "300000")
        vm.setTierAmount(1, "300000")
        val state = vm.state.await { it.tiers.size == 2 && it.tiers.all { t -> t.amountText == "300000" } }
        assertEquals("같은 금액의 구간이 있어요", state.tierWarning)
        assertFalse(state.canSave)
    }

    @Test fun removeTier_dropsRow() {
        val vm = newCardVm()
        vm.addTier()
        vm.addTier()
        vm.setTierBenefit(0, "첫째")
        vm.setTierBenefit(1, "둘째")
        vm.removeTier(0)
        assertEquals(listOf("둘째"), vm.state.await { it.tiers.size == 1 }.tiers.map { it.benefit })
    }

    @Test fun existingCard_loadsTiers() {
        val id = runBlocking {
            repo.saveCard(Card(issuer = Issuer.LOTTE, last4 = "7512", nickname = "텔로", tiers = listOf(Tier(300000, "통신"))))
        }
        val state = CardEditViewModel(repo, id).state.await { it.tiers.isNotEmpty() }
        assertEquals(listOf(TierInput("300000", "통신")), state.tiers)
    }
}

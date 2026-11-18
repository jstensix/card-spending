package com.jstensix.cardspending.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.toRoute
import com.jstensix.cardspending.ui.cards.CardEditScreen
import com.jstensix.cardspending.ui.cards.CardEditViewModel
import com.jstensix.cardspending.ui.cards.CardListScreen
import com.jstensix.cardspending.ui.detail.CardDetailScreen
import com.jstensix.cardspending.ui.detail.CardDetailViewModel
import com.jstensix.cardspending.ui.entry.ManualEntryScreen
import com.jstensix.cardspending.ui.entry.ManualEntryViewModel
import com.jstensix.cardspending.backup.BackupScheduler
import com.jstensix.cardspending.ui.settings.SettingsScreen
import com.jstensix.cardspending.ui.settings.SettingsViewModel
import com.jstensix.cardspending.ui.setup.SetupScreen
import com.jstensix.cardspending.ui.unparsed.UnparsedScreen
import java.time.YearMonth
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jstensix.cardspending.AppContainer
import com.jstensix.cardspending.notification.DeviceStatus
import com.jstensix.cardspending.ui.main.MainScreen
import com.jstensix.cardspending.ui.main.MainViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

// Optional ids use 0 for "none": Room ids start at 1.
@Serializable object MainRoute
@Serializable object CardsRoute
@Serializable data class CardEditRoute(val cardId: Long = 0)
@Serializable data class CardDetailRoute(val cardId: Long, val month: String)
@Serializable data class EntryRoute(val cardId: Long = 0, val unparsedId: Long = 0, val cancel: Boolean = false)
@Serializable object UnparsedRoute
@Serializable object SetupRoute
@Serializable object SettingsRoute

@Composable
fun AppNavHost(container: AppContainer, backupWarning: Flow<String?> = container.backupPrefs.observeWarning()) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = MainRoute) {
        composable<MainRoute> {
            val context = LocalContext.current
            val vm = viewModel {
                MainViewModel(container.repository, { DeviceStatus.isListenerEnabled(context) }, backupWarning)
            }
            val state by vm.state.collectAsStateWithLifecycle()
            LifecycleResumeEffect(Unit) {
                vm.refreshDeviceStatus()
                onPauseOrDispose { }
            }
            MainScreen(
                state = state,
                onPreviousMonth = vm::previousMonth,
                onNextMonth = vm::nextMonth,
                onOpenCard = { id -> nav.navigate(CardDetailRoute(id, state.month.toString())) },
                onOpenCards = { nav.navigate(CardsRoute) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
                onOpenSetup = { nav.navigate(SetupRoute) },
                onOpenUnparsed = { nav.navigate(UnparsedRoute) },
            )
        }
        composable<CardsRoute> {
            val cards by container.repository.observeCards().collectAsStateWithLifecycle(emptyList())
            CardListScreen(
                cards = cards,
                onBack = { nav.popBackStack() },
                onEdit = { id -> nav.navigate(CardEditRoute(id)) },
                onAdd = { nav.navigate(CardEditRoute()) },
            )
        }
        composable<CardEditRoute> { entry ->
            val cardId = entry.toRoute<CardEditRoute>().cardId.takeIf { it != 0L }
            val vm = viewModel { CardEditViewModel(container.repository, cardId) }
            val state by vm.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            CardEditScreen(
                state = state,
                isEditing = cardId != null,
                onIssuer = vm::setIssuer,
                onLast4 = vm::setLast4,
                onNickname = vm::setNickname,
                onToggle = vm::toggleExclusion,
                onSave = { scope.launch { if (vm.save()) nav.popBackStack() } },
                onDelete = { scope.launch { vm.delete(); nav.popBackStack() } },
                onBack = { nav.popBackStack() },
            )
        }
        composable<CardDetailRoute> { entry ->
            val route = entry.toRoute<CardDetailRoute>()
            val vm = viewModel { CardDetailViewModel(container.repository, route.cardId, YearMonth.parse(route.month)) }
            val state by vm.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            CardDetailScreen(
                state = state,
                onBack = { nav.popBackStack() },
                onSetRecognition = { id, value -> scope.launch { vm.setRecognition(id, value) } },
                onDelete = { id -> scope.launch { vm.delete(id) } },
                onAddPayment = { nav.navigate(EntryRoute(cardId = route.cardId)) },
                onAddCancel = { nav.navigate(EntryRoute(cardId = route.cardId, cancel = true)) },
            )
        }
        composable<EntryRoute> { entry ->
            val route = entry.toRoute<EntryRoute>()
            val vm = viewModel {
                ManualEntryViewModel(
                    container.repository,
                    route.cardId.takeIf { it != 0L },
                    route.unparsedId.takeIf { it != 0L },
                    route.cancel,
                )
            }
            val state by vm.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            ManualEntryScreen(
                state = state,
                vm = vm,
                onSave = { scope.launch { if (vm.save()) nav.popBackStack() } },
                onBack = { nav.popBackStack() },
            )
        }
        composable<UnparsedRoute> {
            val items by container.repository.observeUnparsed().collectAsStateWithLifecycle(emptyList())
            val scope = rememberCoroutineScope()
            UnparsedScreen(
                items = items,
                onBack = { nav.popBackStack() },
                onRegisterPayment = { id -> nav.navigate(EntryRoute(unparsedId = id)) },
                onRegisterCancel = { id -> nav.navigate(EntryRoute(unparsedId = id, cancel = true)) },
                onDismiss = { id -> scope.launch { container.repository.dismissUnparsed(id) } },
                onDelete = { id -> scope.launch { container.repository.deleteUnparsed(id) } },
            )
        }
        composable<SetupRoute> { SetupScreen(onBack = { nav.popBackStack() }) }
        composable<SettingsRoute> {
            val context = LocalContext.current.applicationContext
            val vm = viewModel {
                SettingsViewModel(
                    container.repository,
                    container.driveClient,
                    container.backupManager,
                    container.backupPrefs,
                    scheduleBackup = { BackupScheduler.request(context) },
                    cancelBackup = { BackupScheduler.cancel(context) },
                )
            }
            val state by vm.state.collectAsStateWithLifecycle()
            SettingsScreen(state = state, vm = vm, driveAuth = container.driveAuth, onBack = { nav.popBackStack() })
        }
    }
}

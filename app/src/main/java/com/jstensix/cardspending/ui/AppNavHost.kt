package com.jstensix.cardspending.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.flowOf
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
fun AppNavHost(container: AppContainer, backupWarning: Flow<String?> = flowOf(null)) {
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
        composable<CardsRoute> { Placeholder("카드 관리") }
        composable<CardEditRoute> { Placeholder("카드 편집") }
        composable<CardDetailRoute> { Placeholder("카드 결제 내역") }
        composable<EntryRoute> { Placeholder("결제 직접 추가") }
        composable<UnparsedRoute> { Placeholder("인식 실패 목록") }
        composable<SetupRoute> { Placeholder("권한 안내") }
        composable<SettingsRoute> { Placeholder("설정") }
    }
}

@Composable
private fun Placeholder(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title) }
}

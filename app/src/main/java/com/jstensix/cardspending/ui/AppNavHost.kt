package com.jstensix.cardspending.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.jstensix.cardspending.R
import com.jstensix.cardspending.ui.components.bounce
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText
import kotlinx.coroutines.flow.map
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
import androidx.lifecycle.createSavedStateHandle
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
/** [txId] != 0 opens a saved hand-entered payment for editing. */
@Serializable data class EntryRoute(val cardId: Long = 0, val unparsedId: Long = 0, val cancel: Boolean = false, val txId: Long = 0)
@Serializable object UnparsedRoute
@Serializable object SetupRoute
@Serializable object SettingsRoute

/** Tab icons come from the user's icon sheet: full gradient when selected, grey otherwise (drawn untinted). */
private data class Tab(val route: Any, val label: String, @DrawableRes val on: Int, @DrawableRes val off: Int)

private val TABS = listOf(
    Tab(MainRoute, "홈", R.drawable.ic_tab_home_on, R.drawable.ic_tab_home_off),
    Tab(CardsRoute, "카드", R.drawable.ic_tab_card_on, R.drawable.ic_tab_card_off),
    Tab(UnparsedRoute, "알림함", R.drawable.ic_tab_bell_on, R.drawable.ic_tab_bell_off),
    Tab(SettingsRoute, "설정", R.drawable.ic_tab_settings_on, R.drawable.ic_tab_settings_off),
)

@Composable
private fun TabIcon(tab: Tab, selected: Boolean, source: MutableInteractionSource) {
    Icon(
        painterResource(if (selected) tab.on else tab.off),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier.bounce(source, 0.85f).size(26.dp),
    )
}

private fun NavDestination?.isTab(tab: Tab): Boolean = this?.hasRoute(tab.route::class) == true

@Composable
fun AppNavHost(container: AppContainer, backupWarning: Flow<String?> = container.backupPrefs.observeWarning()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val unparsedCountFlow = remember { container.repository.observeUnparsed().map { it.size } }
    val unparsedCount by unparsedCountFlow.collectAsStateWithLifecycle(0)

    // Screens draw their own status-bar padding (BrandTopBar) and bottom bars; the navigation bar pads itself.
    Scaffold(
        containerColor = Brand.Canvas,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (TABS.any { destination.isTab(it) }) {
                Column {
                    HorizontalDivider(color = Brand.Hairline)
                    NavigationBar(containerColor = Brand.Canvas, tonalElevation = 0.dp) {
                        TABS.forEach { tab ->
                            val selected = destination.isTab(tab)
                            val source = remember(tab) { MutableInteractionSource() }
                            NavigationBarItem(
                                selected = selected,
                                interactionSource = source,
                                onClick = { nav.navigateToTab(tab.route) },
                                icon = {
                                    if (tab.route == UnparsedRoute && unparsedCount > 0) {
                                        BadgedBox(badge = { Badge(containerColor = Brand.Ruby) { Text("$unparsedCount") } }) { TabIcon(tab, selected, source) }
                                    } else {
                                        TabIcon(tab, selected, source)
                                    }
                                },
                                label = { Text(tab.label, style = BrandText.eyebrow) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Brand.PrimaryDeep,
                                    selectedTextColor = Brand.PrimaryDeep,
                                    indicatorColor = Brand.PrimaryTint,
                                    unselectedIconColor = Brand.InkMute,
                                    unselectedTextColor = Brand.InkMute,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        // Side insets (landscape 3-button navigation, display cutouts) for every screen; top/bottom are handled
        // by BrandTopBar and each screen's bottom bar.
        AppNavGraph(
            nav,
            container,
            backupWarning,
            Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        )
    }
}

@Composable
private fun AppNavGraph(nav: NavHostController, container: AppContainer, backupWarning: Flow<String?>, modifier: Modifier) {
    // No screen transition animations: screens switch instantly. Back has its own pair (shrink + fade by
    // default) that also has to be switched off.
    NavHost(
        navController = nav,
        startDestination = MainRoute,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
        predictivePopEnterTransition = { EnterTransition.None },
        predictivePopExitTransition = { ExitTransition.None },
    ) {
        composable<MainRoute> {
            val context = LocalContext.current
            val activity = LocalActivity.current
            val exitGate = remember { ExitOnSecondBack() }
            val warning = remember { arrayOfNulls<Toast>(1) }
            // Handling back here also keeps the system's back-to-home animation from playing.
            BackHandler {
                if (exitGate.onBack()) {
                    warning[0]?.cancel()
                    activity?.finish()
                } else {
                    warning[0] = Toast.makeText(context, "한 번 더 뒤로 가면 앱이 꺼져요", Toast.LENGTH_SHORT).also { it.show() }
                }
            }
            val vm = viewModel {
                MainViewModel(container.repository, { DeviceStatus.isListenerEnabled(context) }, backupWarning)
            }
            val state by vm.state.collectAsStateWithLifecycle()
            LifecycleResumeEffect(Unit) {
                vm.refreshDeviceStatus()
                onPauseOrDispose { }
            }
            val actions = remember(nav) { MainNavActions(nav) }
            MainScreen(
                state = state,
                onPreviousMonth = vm::previousMonth,
                onNextMonth = vm::nextMonth,
                onOpenCard = { id -> nav.navigate(CardDetailRoute(id, state.month.toString())) },
                onOpenCards = actions::openCards,
                onOpenSettings = actions::openSettings,
                onOpenSetup = { nav.navigate(SetupRoute) },
                onOpenUnparsed = actions::openUnparsed,
            )
        }
        composable<CardsRoute> {
            val cards by container.repository.observeCards().collectAsStateWithLifecycle(emptyList())
            CardListScreen(
                cards = cards,
                onEdit = { id -> nav.navigate(CardEditRoute(id)) },
                onAdd = { nav.navigate(CardEditRoute()) },
                // App scope: leaving the tab right after a drop must not cancel the save.
                onReorder = { ids -> container.appScope.launch { container.repository.reorderCards(ids) } },
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
                onAddTier = vm::addTier,
                onRemoveTier = vm::removeTier,
                onTierAmount = vm::setTierAmount,
                onTierBenefit = vm::setTierBenefit,
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
                onEdit = { id -> nav.navigate(EntryRoute(txId = id)) },
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
                    route.txId.takeIf { it != 0L },
                    createSavedStateHandle(),
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
            SettingsScreen(state = state, vm = vm, driveAuth = container.driveAuth, onOpenSetup = { nav.navigate(SetupRoute) })
        }
    }
}

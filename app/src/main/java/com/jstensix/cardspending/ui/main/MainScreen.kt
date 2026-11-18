package com.jstensix.cardspending.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.ui.IssuerLogo
import com.jstensix.cardspending.ui.components.BrandTopBar
import com.jstensix.cardspending.ui.components.CreamBanner
import com.jstensix.cardspending.ui.components.MonthSwitcher
import com.jstensix.cardspending.ui.components.NavySummaryCard
import com.jstensix.cardspending.ui.components.PillButton
import com.jstensix.cardspending.ui.components.RatioBar
import com.jstensix.cardspending.ui.components.StatusTag
import com.jstensix.cardspending.ui.components.TagKind
import com.jstensix.cardspending.ui.components.WhiteCard
import com.jstensix.cardspending.ui.formatMonth
import com.jstensix.cardspending.ui.formatWon
import com.jstensix.cardspending.ui.model.recognizedPercent
import com.jstensix.cardspending.ui.model.recognizedRatio
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText

@Composable
fun MainScreen(
    state: MainUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenCard: (Long) -> Unit,
    onOpenCards: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenUnparsed: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Brand.Canvas)) {
        BrandTopBar("카드 실적") {
            MonthSwitcher(formatMonth(state.month), onPreviousMonth, onNextMonth)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.loaded) return@LazyColumn
            item {
                val percent = recognizedPercent(state.totalPayment, state.totalRecognized)
                NavySummaryCard(
                    eyebrow = "${state.month.monthValue}월 실적인정 합계",
                    amount = state.totalRecognized,
                    caption = percent?.let { "결제 ${formatWon(state.totalPayment)} 중 $it%" } ?: "결제 ${formatWon(state.totalPayment)}",
                    ratio = recognizedRatio(state.totalPayment, state.totalRecognized),
                )
            }
            if (!state.listenerEnabled) {
                item { CreamBanner("알림 접근 권한이 꺼져 있어요. 결제 알림을 읽을 수 없어요.", onClick = onOpenSetup) }
            }
            if (state.unparsedCount > 0) {
                item { CreamBanner("읽지 못한 카드 알림 ${state.unparsedCount}건", onClick = onOpenUnparsed) }
            }
            if (state.unregisteredCount > 0) {
                item { CreamBanner("등록되지 않은 카드의 결제 ${state.unregisteredCount}건", onClick = onOpenCards) }
            }
            state.backupWarning?.let { warning -> item { CreamBanner(warning, onClick = onOpenSettings) } }
            if (state.rows.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("등록된 카드가 없어요", style = BrandText.body, color = Brand.InkMute)
                        Spacer(Modifier.height(16.dp))
                        PillButton("카드 등록", onClick = onOpenCards)
                    }
                }
            }
            items(state.rows, key = { it.cardId }) { row -> CardSummary(row) { onOpenCard(row.cardId) } }
        }
    }
}

@Composable
private fun CardSummary(row: CardRow, onClick: () -> Unit) {
    WhiteCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IssuerLogo(row.issuer, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.nickname, style = BrandText.body, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${row.issuer.displayName} · ${row.mask}", style = BrandText.caption, color = Brand.InkMute)
            }
            if (row.excludedCount > 0) {
                Spacer(Modifier.width(8.dp))
                StatusTag("미인정 ${row.excludedCount}건", TagKind.Excluded)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatWon(row.recognized), style = BrandText.amount, color = Brand.Ink, modifier = Modifier.weight(1f))
            Text("/ 결제 ${formatWon(row.payment)}", style = BrandText.caption, color = Brand.InkMute)
        }
        Spacer(Modifier.height(8.dp))
        RatioBar(recognizedRatio(row.payment, row.recognized), onDark = false)
    }
}

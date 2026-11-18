package com.jstensix.cardspending.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.ui.IssuerLogo
import com.jstensix.cardspending.ui.formatMonth
import com.jstensix.cardspending.ui.formatWon

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카드 실적") },
                actions = {
                    TextButton(onClick = onOpenCards) { Text("카드 관리") }
                    TextButton(onClick = onOpenSettings) { Text("설정") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatMonth(state.month), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onPreviousMonth) { Text("◀") }
                    TextButton(onClick = onNextMonth) { Text("▶") }
                }
            }
            if (!state.listenerEnabled) {
                item { WarningBanner("알림 접근 권한이 꺼져 있어요. 결제 알림을 읽을 수 없어요.", onOpenSetup) }
            }
            if (state.unparsedCount > 0) {
                item { WarningBanner("읽지 못한 카드 알림 ${state.unparsedCount}건", onOpenUnparsed) }
            }
            if (state.unregisteredCount > 0) {
                item { WarningBanner("등록되지 않은 카드의 결제 ${state.unregisteredCount}건", onOpenCards) }
            }
            state.backupWarning?.let { warning -> item { WarningBanner(warning, onOpenSettings) } }
            if (state.rows.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("등록된 카드가 없어요")
                        Spacer(Modifier.padding(8.dp))
                        Button(onClick = onOpenCards) { Text("카드 등록") }
                    }
                }
            }
            items(state.rows, key = { it.cardId }) { row -> CardRowItem(row) { onOpenCard(row.cardId) } }
        }
    }
}

@Composable
private fun CardRowItem(row: CardRow, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IssuerLogo(row.issuer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.nickname, style = MaterialTheme.typography.titleMedium)
                Text(row.mask, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                AmountLine("결제", row.payment)
                AmountLine("실적인정", row.recognized, bold = true)
            }
        }
    }
}

@Composable
private fun AmountLine(label: String, amount: Long, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Text(formatWon(amount), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun WarningBanner(text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

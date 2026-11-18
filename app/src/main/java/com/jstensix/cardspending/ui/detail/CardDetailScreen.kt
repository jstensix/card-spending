package com.jstensix.cardspending.ui.detail

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.ui.formatMonth
import com.jstensix.cardspending.ui.formatWon
import java.time.format.DateTimeFormatter

private val ITEM_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    state: CardDetailState,
    onBack: () -> Unit,
    onSetRecognition: (txId: Long, value: Boolean?) -> Unit,
    onDelete: (txId: Long) -> Unit,
    onAddPayment: () -> Unit,
    onAddCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf<TxItem?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${state.card?.nickname.orEmpty()} · ${formatMonth(state.month)}") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddPayment, modifier = Modifier.weight(1f)) { Text("결제 추가") }
                OutlinedButton(onClick = onAddCancel, modifier = Modifier.weight(1f)) { Text("취소 추가") }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("결제 ${formatWon(state.payment)}")
                    Text("실적인정 ${formatWon(state.recognized)}", style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
            }
            items(state.items, key = { it.tx.id }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { selected = item }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.tx.merchant, style = MaterialTheme.typography.bodyLarge)
                        Text(item.tx.approvedAt.format(ITEM_TIME), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatWon(item.signedAmount))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.recognized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(item.tx.merchant) },
            text = {
                Column {
                    listOf<Pair<String, Boolean?>>("규칙대로" to null, "인정" to true, "미인정" to false).forEach { (label, value) ->
                        TextButton(onClick = { onSetRecognition(item.tx.id, value); selected = null }) { Text(label) }
                    }
                    item.tx.rawText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { onDelete(item.tx.id); selected = null }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("닫기") } },
        )
    }
}

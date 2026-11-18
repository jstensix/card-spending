package com.jstensix.cardspending.ui.unparsed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.model.UnparsedNotification
import java.time.format.DateTimeFormatter

private val RECEIVED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnparsedScreen(
    items: List<UnparsedNotification>,
    onBack: () -> Unit,
    onRegisterPayment: (Long) -> Unit,
    onRegisterCancel: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("읽지 못한 카드 알림") }, navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (items.isEmpty()) {
                item { Text("읽지 못한 알림이 없어요", modifier = Modifier.padding(16.dp)) }
            }
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${item.postedAt.format(RECEIVED)} · ${item.packageName}", style = MaterialTheme.typography.bodySmall)
                        item.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                        Text(item.body, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onRegisterPayment(item.id) }) { Text("결제로 등록") }
                            TextButton(onClick = { onRegisterCancel(item.id) }) { Text("취소로 등록") }
                            TextButton(onClick = { onDismiss(item.id) }) { Text("무시") }
                        }
                    }
                }
            }
        }
    }
}

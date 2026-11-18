package com.jstensix.cardspending.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.rules.maskOf
import com.jstensix.cardspending.ui.IssuerLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(cards: List<Card>, onBack: () -> Unit, onEdit: (Long) -> Unit, onAdd: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("카드 관리") }, navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } })
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd) { Text("카드 추가") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(cards, key = { it.id }) { card ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onEdit(card.id) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IssuerLogo(card.issuer)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(card.nickname, style = MaterialTheme.typography.titleMedium)
                        Text("${card.issuer.displayName} · ${maskOf(card.last4)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

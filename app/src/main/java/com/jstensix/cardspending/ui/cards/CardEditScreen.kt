package com.jstensix.cardspending.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditScreen(
    state: CardEditState,
    isEditing: Boolean,
    onIssuer: (Issuer) -> Unit,
    onLast4: (String) -> Unit,
    onNickname: (String) -> Unit,
    onToggle: (ExclusionItem) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "카드 수정" else "카드 추가") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("카드사", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Issuer.entries.forEach { issuer ->
                    FilterChip(selected = state.issuer == issuer, onClick = { onIssuer(issuer) }, label = { Text(issuer.displayName) })
                }
            }
            OutlinedTextField(
                value = state.last4,
                onValueChange = { onLast4(it.filter(Char::isDigit)) },
                label = { Text("카드번호 끝 4자리") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.nickname,
                onValueChange = onNickname,
                label = { Text("별칭 (비우면 카드사 이름)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.conflictWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("실적 미인정 항목", style = MaterialTheme.typography.titleSmall)
            ExclusionItem.entries.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = item in state.exclusions,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(item) },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = item in state.exclusions, onCheckedChange = null, modifier = Modifier.padding(vertical = 8.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(item.label)
                }
            }
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) { Text("저장") }
            if (isEditing) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("카드 삭제") }
            }
        }
    }
}

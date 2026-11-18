package com.jstensix.cardspending.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.ui.formatWon
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val ORIGINAL_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    state: EntryState,
    vm: ManualEntryViewModel,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.cancel) "취소 직접 추가" else "결제 직접 추가") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.rawText?.let { raw ->
                Card(Modifier.fillMaxWidth()) {
                    Text(raw, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("카드", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.cards.forEach { card ->
                    FilterChip(selected = state.cardId == card.id, onClick = { vm.setCard(card.id) }, label = { Text(card.nickname) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.cancel, onClick = { vm.setCancel(false) }, label = { Text("승인") })
                FilterChip(selected = state.cancel, onClick = { vm.setCancel(true) }, label = { Text("취소") })
            }
            OutlinedTextField(
                value = state.amountText,
                onValueChange = vm::setAmount,
                label = { Text("금액(원)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.merchant,
                onValueChange = vm::setMerchant,
                label = { Text("가맹점") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParsedField("날짜 (2026-11-18)", state.date.format(DATE), Modifier.weight(1f)) {
                    LocalDate.parse(it, DATE).also(vm::setDate)
                }
                ParsedField("시각 (17:30)", state.time.format(TIME), Modifier.weight(1f)) {
                    LocalTime.parse(it, TIME).also(vm::setTime)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(state.installment, role = Role.Checkbox, onValueChange = vm::setInstallment),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.installment, onCheckedChange = null)
                Text("할부", modifier = Modifier.padding(start = 12.dp))
            }
            if (state.cancel) {
                Text("원래 결제", style = MaterialTheme.typography.titleSmall)
                OriginalOption("원래 결제 없음", state.originalId == null) { vm.setOriginal(null) }
                state.originalCandidates.forEach { tx ->
                    OriginalOption(
                        "${tx.approvedAt.format(ORIGINAL_TIME)} ${tx.merchant} ${formatWon(tx.amount)}",
                        state.originalId == tx.id,
                    ) { vm.setOriginal(tx.id) }
                }
            }
            state.duplicateWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) { Text("저장") }
        }
    }
}

/** Free-text field that only pushes a value to the ViewModel once it parses. */
@Composable
private fun ParsedField(label: String, initial: String, modifier: Modifier, parse: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(initial) { if (!error) text = initial }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            error = try {
                parse(it)
                false
            } catch (e: DateTimeParseException) {
                true
            }
        },
        label = { Text(label) },
        isError = error,
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun OriginalOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

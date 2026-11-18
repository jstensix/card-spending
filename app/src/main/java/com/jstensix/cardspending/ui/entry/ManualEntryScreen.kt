package com.jstensix.cardspending.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.R
import com.jstensix.cardspending.ui.components.BrandIcon
import com.jstensix.cardspending.ui.components.BrandTextField
import com.jstensix.cardspending.ui.components.BrandTopBar
import com.jstensix.cardspending.ui.components.CreamBanner
import com.jstensix.cardspending.ui.components.LogoChoiceCard
import com.jstensix.cardspending.ui.components.PillButton
import com.jstensix.cardspending.ui.components.PillChip
import com.jstensix.cardspending.ui.components.SectionLabel
import com.jstensix.cardspending.ui.components.SegmentedToggle
import com.jstensix.cardspending.ui.components.SoftBox
import com.jstensix.cardspending.ui.formatWon
import com.jstensix.cardspending.ui.model.dayLabel
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val ORIGINAL_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm")

/** Shows "12000" as "12,000" while the field keeps raw digits. */
private object ThousandsSeparator : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = StringBuilder()
        val toOut = IntArray(digits.length + 1)
        digits.forEachIndexed { index, c ->
            toOut[index] = out.length
            out.append(c)
            val remaining = digits.length - index - 1
            if (remaining > 0 && remaining % 3 == 0) out.append(',')
        }
        toOut[digits.length] = out.length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = toOut[offset.coerceIn(0, digits.length)]
            override fun transformedToOriginal(offset: Int): Int =
                (0..digits.length).lastOrNull { toOut[it] <= offset } ?: 0
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    state: EntryState,
    vm: ManualEntryViewModel,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    // Typed text lives here, not in vm.state: the ViewModel re-queries the DB on every change, so its state
    // arrives a beat late and would reset the cursor mid-typing (typing 15000 produced 50001).
    var amount by rememberSaveable { mutableStateOf(state.amountText) }
    var merchant by rememberSaveable { mutableStateOf(state.merchant) }

    Column(Modifier.fillMaxSize().background(Brand.Canvas).imePadding()) {
        BrandTopBar(if (state.cancel) "취소 직접 추가" else "결제 직접 추가", onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            state.rawText?.let {
                SoftBox(it)
                Spacer(Modifier.height(12.dp))
            }
            SegmentedToggle(listOf("승인", "취소"), if (state.cancel) 1 else 0, { vm.setCancel(it == 1) })

            SectionLabel("카드")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.cards.forEach { card ->
                    LogoChoiceCard(card.issuer, card.nickname, selected = state.cardId == card.id, onClick = { vm.setCard(card.id) })
                }
            }

            SectionLabel("금액")
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = amount,
                    onValueChange = {
                        amount = it.filter(Char::isDigit)
                        vm.setAmount(amount)
                    },
                    textStyle = BrandText.display.copy(color = Brand.Ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandsSeparator,
                    singleLine = true,
                    cursorBrush = SolidColor(Brand.Primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { field ->
                        Box {
                            if (amount.isEmpty()) Text("0", style = BrandText.display, color = Brand.Hairline)
                            field()
                        }
                    },
                )
                Text("원", style = BrandText.title, color = Brand.InkMute, modifier = Modifier.padding(bottom = 4.dp))
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Brand.Primary))

            SectionLabel("가맹점")
            BrandTextField(
                value = merchant,
                onValueChange = {
                    merchant = it
                    vm.setMerchant(it)
                },
                label = "가맹점 이름",
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("날짜와 시각")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip(
                    dayLabel(state.date),
                    selected = false,
                    onClick = { showDate = true },
                    leading = { BrandIcon(R.drawable.ic_calendar, 18.dp) },
                )
                PillChip(state.time.format(TIME), selected = false, onClick = { showTime = true })
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("할부", style = BrandText.body, color = Brand.Ink, modifier = Modifier.weight(1f))
                Switch(
                    checked = state.installment,
                    onCheckedChange = vm::setInstallment,
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand.Primary, uncheckedTrackColor = Brand.Hairline, uncheckedBorderColor = Brand.Hairline),
                )
            }

            if (state.cancel) {
                SectionLabel("원래 결제")
                OriginalChoice(selected = state.originalId == null, onClick = { vm.setOriginal(null) }) {
                    Text("원래 결제 없음", style = BrandText.label, color = Brand.Ink)
                }
                state.originalCandidates.forEach { tx ->
                    // Only the merchant may shrink: the amount is what tells candidates apart.
                    OriginalChoice(selected = state.originalId == tx.id, onClick = { vm.setOriginal(tx.id) }) {
                        Text(tx.approvedAt.format(ORIGINAL_TIME), style = BrandText.caption, color = Brand.InkMute)
                        Spacer(Modifier.width(8.dp))
                        Text(tx.merchant, style = BrandText.label, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(formatWon(tx.amount), style = BrandText.amountSmall, color = Brand.Ink)
                    }
                }
            }

            state.duplicateWarning?.let {
                Spacer(Modifier.height(12.dp))
                CreamBanner(it)
            }
            Spacer(Modifier.height(24.dp))
        }
        HorizontalDivider(color = Brand.Hairline)
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            PillButton("저장", onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth())
        }
    }

    if (showDate) {
        // DatePicker works in UTC midnight millis.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDate = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("취소") } },
        ) { DatePicker(state = pickerState) }
    }
    if (showTime) {
        val timeState = rememberTimePickerState(initialHour = state.time.hour, initialMinute = state.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = Brand.Canvas,
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setTime(LocalTime.of(timeState.hour, timeState.minute))
                    showTime = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun OriginalChoice(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Brand.Primary else Brand.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

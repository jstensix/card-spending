package com.jstensix.cardspending.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.model.TxSource
import com.jstensix.cardspending.ui.components.BounceTextButton
import com.jstensix.cardspending.ui.components.bounceClickable
import com.jstensix.cardspending.ui.components.BrandTopBar
import com.jstensix.cardspending.ui.components.MonthPill
import com.jstensix.cardspending.ui.components.NavySummaryCard
import com.jstensix.cardspending.ui.components.PillButton
import com.jstensix.cardspending.ui.components.PillStyle
import com.jstensix.cardspending.ui.components.SectionLabel
import com.jstensix.cardspending.ui.components.SegmentedToggle
import com.jstensix.cardspending.ui.components.SoftBox
import com.jstensix.cardspending.ui.components.StatusTag
import com.jstensix.cardspending.ui.components.TagKind
import com.jstensix.cardspending.ui.formatWon
import com.jstensix.cardspending.ui.model.dayLabel
import com.jstensix.cardspending.ui.model.recognizedRatio
import com.jstensix.cardspending.ui.model.txMeta
import com.jstensix.cardspending.ui.model.compactManwon
import com.jstensix.cardspending.rules.TierProgress
import com.jstensix.cardspending.rules.tierProgress
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText

private fun TxItem.tagKind(): TagKind = when {
    label.startsWith("직접 지정") -> TagKind.Manual
    !recognized -> TagKind.Excluded
    else -> TagKind.Neutral
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    state: CardDetailState,
    onBack: () -> Unit,
    onSetRecognition: (txId: Long, value: Boolean?) -> Unit,
    onDelete: (txId: Long) -> Unit,
    onEdit: (txId: Long) -> Unit,
    onAddPayment: () -> Unit,
    onAddCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf<TxItem?>(null) }

    Column(Modifier.fillMaxSize().background(Brand.Canvas)) {
        BrandTopBar(state.card?.nickname.orEmpty(), onBack = onBack) { MonthPill("${state.month.monthValue}월") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                NavySummaryCard(
                    eyebrow = "실적인정",
                    amount = state.recognized,
                    caption = "결제 ${formatWon(state.payment)} · 미인정 ${formatWon(state.payment - state.recognized)}",
                    ratio = recognizedRatio(state.payment, state.recognized),
                    amountStyle = BrandText.amountLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            state.card?.let { tierProgress(state.recognized, it.tiers) }?.let { progress ->
                item(key = "tiers") { TierList(progress) }
            }
            if (state.days.isEmpty()) {
                item {
                    Text(
                        "이 달에는 결제가 없어요",
                        style = BrandText.body,
                        color = Brand.InkMute,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 32.dp),
                    )
                }
            }
            state.days.forEach { day ->
                item(key = "day-${day.date}") {
                    Text(day.label, style = BrandText.label, color = Brand.InkMute, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
                }
                items(day.items, key = { it.tx.id }) { item -> TxRow(item) { selected = item } }
            }
        }
        HorizontalDivider(color = Brand.Hairline)
        Row(
            modifier = Modifier.fillMaxWidth().background(Brand.Canvas).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton("＋ 결제 추가", onClick = onAddPayment, modifier = Modifier.weight(1f))
            PillButton("취소 추가", onClick = onAddCancel, modifier = Modifier.weight(1f), style = PillStyle.Secondary)
        }
    }

    selected?.let { item ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = Brand.Canvas) {
            TxSheet(
                item = item,
                onSetRecognition = { value ->
                    onSetRecognition(item.tx.id, value)
                    selected = null
                },
                onEdit = if (item.tx.source == TxSource.MANUAL) {
                    {
                        selected = null
                        onEdit(item.tx.id)
                    }
                } else {
                    null
                },
                onDelete = {
                    onDelete(item.tx.id)
                    selected = null
                },
            )
        }
    }
}

/** The card's 실적 구간 with their benefits; the next tier to reach shows how much is left. */
@Composable
private fun TierList(progress: TierProgress) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionLabel("실적 구간")
        progress.tiers.forEachIndexed { index, tier ->
            val reached = index < progress.achieved
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(if (reached) "✓" else "○", style = BrandText.body, color = if (reached) Brand.Primary else Brand.InkMute)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${compactManwon(tier.amount)}원",
                    style = BrandText.body,
                    color = if (reached) Brand.Ink else Brand.InkMute,
                    modifier = Modifier.width(72.dp),
                )
                Column(Modifier.weight(1f)) {
                    if (tier.benefit.isNotBlank()) Text(tier.benefit, style = BrandText.body, color = Brand.Ink)
                    if (tier == progress.next) {
                        Text("${formatWon(progress.remaining ?: 0)} 남음", style = BrandText.caption, color = Brand.Primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TxRow(item: TxItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().bounceClickable(pressedScale = 0.98f, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.tx.merchant, style = BrandText.body, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(txMeta(item.tx), style = BrandText.caption, color = Brand.InkMute)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatWon(item.signedAmount), style = BrandText.amountSmall, color = Brand.Ink)
            Spacer(Modifier.height(4.dp))
            StatusTag(item.label, item.tagKind())
        }
    }
    HorizontalDivider(color = Brand.Hairline, modifier = Modifier.padding(start = 16.dp))
}

@Composable
/** [onEdit] is null for alert payments: only hand-entered ones can be edited. */
private fun TxSheet(item: TxItem, onSetRecognition: (Boolean?) -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
        Text(item.tx.merchant, style = BrandText.title, color = Brand.Ink)
        Text(
            "${dayLabel(item.tx.approvedAt.toLocalDate())} ${txMeta(item.tx)} · ${formatWon(item.signedAmount)}",
            style = BrandText.caption,
            color = Brand.InkMute,
        )
        Spacer(Modifier.height(8.dp))
        StatusTag(item.label, item.tagKind())
        SectionLabel("실적 인정")
        val choices = listOf(null, true, false)
        SegmentedToggle(
            options = listOf("규칙대로", "인정", "미인정"),
            selectedIndex = choices.indexOf(item.tx.manualRecognition),
            onSelect = { onSetRecognition(choices[it]) },
        )
        item.tx.rawText?.let {
            SectionLabel("알림 원문")
            SoftBox(it)
        }
        if (onEdit != null) {
            Spacer(Modifier.height(16.dp))
            PillButton("수정", onClick = onEdit, style = PillStyle.Secondary, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        BounceTextButton(onClick = onDelete) { Text("이 결제 삭제", style = BrandText.label, color = Brand.InkMute) }
    }
}

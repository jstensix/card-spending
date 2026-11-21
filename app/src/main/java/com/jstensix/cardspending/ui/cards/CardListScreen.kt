package com.jstensix.cardspending.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.rules.maskOf
import androidx.compose.foundation.layout.height
import com.jstensix.cardspending.R
import com.jstensix.cardspending.ui.IssuerLogo
import com.jstensix.cardspending.ui.components.IconTile
import com.jstensix.cardspending.ui.components.BrandTopBar
import com.jstensix.cardspending.ui.components.PillButton
import com.jstensix.cardspending.ui.components.WhiteCard
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Tab screen: no back button; the bottom navigation bar sits below. A long press lifts a card so it can be dragged
 * into a new place; the new order is handed to [onReorder] once, when the card is dropped.
 */
@Composable
fun CardListScreen(cards: List<Card>, onEdit: (Long) -> Unit, onAdd: () -> Unit, onReorder: (List<Long>) -> Unit) {
    val haptics = LocalHapticFeedback.current
    // What the screen shows while dragging; reset whenever the stored list changes.
    var shown by remember(cards) { mutableStateOf(cards) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = shown.indexOfFirst { it.id == from.key }
        val toIndex = shown.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            shown = shown.move(fromIndex, toIndex)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    Column(Modifier.fillMaxSize().background(Brand.Canvas)) {
        BrandTopBar("카드")
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (cards.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconTile(R.drawable.ic_card_add)
                        Spacer(Modifier.height(16.dp))
                        Text("아직 등록한 카드가 없어요", style = BrandText.body, color = Brand.InkMute)
                    }
                }
            }
            items(shown, key = { it.id }) { card ->
                ReorderableItem(reorderState, key = card.id) { isDragging ->
                    val lift by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "lift")
                    WhiteCard(
                        modifier = Modifier
                            .shadow(lift, LiftShape)
                            .longPressDraggableHandle(
                                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = {
                                    val ids = shown.map { it.id }
                                    if (ids != cards.map { it.id }) onReorder(ids)
                                },
                            ),
                        // Releasing a long press without moving must not also open the edit screen.
                        onClick = { if (!isDragging) onEdit(card.id) },
                    ) {
                        CardRowContent(card)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            PillButton("＋ 카드 추가", onClick = onAdd, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val LiftShape = RoundedCornerShape(12.dp)

@Composable
private fun CardRowContent(card: Card) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IssuerLogo(card.issuer, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(card.nickname, style = BrandText.body, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${card.issuer.displayName} · ${maskOf(card.last4)}", style = BrandText.caption, color = Brand.InkMute)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (card.exclusions.isEmpty()) "미인정 항목 없음" else "미인정 항목 ${card.exclusions.size}개",
            style = BrandText.caption,
            color = Brand.InkMute,
        )
    }
}

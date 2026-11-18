package com.jstensix.cardspending.ui.unparsed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import com.jstensix.cardspending.R
import com.jstensix.cardspending.model.UnparsedNotification
import com.jstensix.cardspending.ui.components.BounceTextButton
import com.jstensix.cardspending.ui.components.IconTile
import com.jstensix.cardspending.ui.components.BrandTopBar
import com.jstensix.cardspending.ui.components.PillButton
import com.jstensix.cardspending.ui.components.PillStyle
import com.jstensix.cardspending.ui.components.SoftBox
import com.jstensix.cardspending.ui.components.WhiteCard
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText
import java.time.format.DateTimeFormatter

private val RECEIVED = DateTimeFormatter.ofPattern("M월 d일 HH:mm")

/** Tab screen ("알림함"): alerts that looked like card payments but no parser understood. */
@Composable
fun UnparsedScreen(
    items: List<UnparsedNotification>,
    onRegisterPayment: (Long) -> Unit,
    onRegisterCancel: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Brand.Canvas)) {
        BrandTopBar("알림함")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("카드 결제처럼 보이지만 읽지 못한 알림이에요", style = BrandText.caption, color = Brand.InkMute)
            }
            if (items.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconTile(R.drawable.ic_check_circle)
                        Spacer(Modifier.height(16.dp))
                        Text("읽지 못한 알림이 없어요", style = BrandText.body, color = Brand.InkMute)
                    }
                }
            }
            items(items, key = { it.id }) { item ->
                WhiteCard {
                    Text("${item.postedAt.format(RECEIVED)} · ${item.packageName}", style = BrandText.caption, color = Brand.InkMute)
                    item.title?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = BrandText.body, color = Brand.Ink)
                    }
                    Spacer(Modifier.height(8.dp))
                    SoftBox(item.body)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("결제로 등록", onClick = { onRegisterPayment(item.id) }, modifier = Modifier.weight(1f))
                        PillButton("취소로 등록", onClick = { onRegisterCancel(item.id) }, style = PillStyle.Secondary, modifier = Modifier.weight(1f))
                    }
                    Row {
                        // 무시: hidden, and the same alert re-posted later stays hidden. 삭제: the text is gone for good.
                        BounceTextButton(onClick = { onDismiss(item.id) }) { Text("무시", style = BrandText.label, color = Brand.InkMute) }
                        BounceTextButton(onClick = { onDelete(item.id) }) { Text("삭제", style = BrandText.label, color = Brand.InkMute) }
                    }
                }
            }
        }
    }
}

package com.jstensix.cardspending.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.rules.isValidLast4
import com.jstensix.cardspending.rules.maskOf
import com.jstensix.cardspending.ui.components.BrandTextField
import com.jstensix.cardspending.ui.components.BrandTopBar
import com.jstensix.cardspending.ui.components.DigitBoxes
import com.jstensix.cardspending.ui.components.LogoChoiceCard
import com.jstensix.cardspending.ui.components.PillButton
import com.jstensix.cardspending.ui.components.PillChip
import com.jstensix.cardspending.ui.components.SectionLabel
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText

@OptIn(ExperimentalLayoutApi::class)
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
    Column(Modifier.fillMaxSize().background(Brand.Canvas).imePadding()) {
        BrandTopBar(if (isEditing) "카드 수정" else "카드 추가", onBack = onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel("카드사", Modifier.padding(top = 0.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Issuer.entries.forEach { issuer ->
                    LogoChoiceCard(
                        issuer = issuer,
                        label = issuer.displayName,
                        selected = state.issuer == issuer,
                        onClick = { onIssuer(issuer) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionLabel("카드번호 끝 4자리")
            DigitBoxes(state.last4, onLast4)
            Spacer(Modifier.height(8.dp))
            val mask = if (isValidLast4(state.last4)) maskOf(state.last4) else "□*□*"
            Text(
                buildAnnotatedString {
                    append("알림에는 ")
                    withStyle(SpanStyle(color = Brand.PrimaryDeep)) { append(mask) }
                    append("처럼 색칠한 자리만 보여요")
                },
                style = BrandText.caption,
                color = Brand.InkMute,
            )

            SectionLabel("별칭")
            BrandTextField(state.nickname, onNickname, label = "비우면 카드사 이름", modifier = Modifier.fillMaxWidth())
            state.conflictWarning?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = BrandText.caption, color = Brand.Ruby)
            }

            SectionLabel("실적 미인정 항목")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExclusionItem.entries.forEach { item ->
                    PillChip(item.label, selected = item in state.exclusions, onClick = { onToggle(item) })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        HorizontalDivider(color = Brand.Hairline)
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isEditing) {
                TextButton(onClick = onDelete) { Text("카드 삭제", style = BrandText.label, color = Brand.InkMute) }
            }
            PillButton("저장", onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth())
        }
    }
}

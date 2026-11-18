package com.jstensix.cardspending.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.ui.formatWon
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText

private val CardShape = RoundedCornerShape(12.dp)

/** DESIGN.md elevation level 1: rgba(0,55,112,0.08). */
private val ShadowBlue = Color(0x14003770)

/** card-pricing-featured: the inverted navy card that carries the month's headline number. */
@Composable
fun NavySummaryCard(
    eyebrow: String,
    amount: Long,
    caption: String?,
    ratio: Float?,
    modifier: Modifier = Modifier,
    amountStyle: TextStyle = BrandText.display,
) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(Brand.Navy).padding(18.dp)) {
        Text(eyebrow, style = BrandText.eyebrow, color = Brand.Subdued)
        Spacer(Modifier.height(6.dp))
        Text(formatWon(amount), style = amountStyle, color = Color.White, maxLines = 1)
        if (caption != null) {
            Spacer(Modifier.height(6.dp))
            Text(caption, style = BrandText.caption, color = Brand.Subdued)
        }
        Spacer(Modifier.height(14.dp))
        RatioBar(ratio, onDark = true)
    }
}

/** card-feature-light at app scale: white, 12dp, hairline, level-1 lift. */
@Composable
fun WhiteCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, CardShape, ambientColor = ShadowBlue, spotColor = ShadowBlue)
            .background(Brand.Canvas, CardShape)
            .border(1.dp, Brand.Hairline, CardShape)
            .clip(CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content,
    )
}

/** Plain top bar that also pads for the status bar (screens run edge-to-edge). */
@Composable
fun BrandTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로", tint = Brand.Ink)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = BrandText.title,
            color = Brand.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        actions()
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun MonthSwitcher(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.clip(PillShape).background(Brand.CanvasSoft), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "이전 달", tint = Brand.Ink)
        }
        Text(label, style = BrandText.label, color = Brand.Ink)
        IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "다음 달", tint = Brand.Ink)
        }
    }
}

/** Month shown without navigation (detail screen keeps its month fixed). */
@Composable
fun MonthPill(label: String) {
    Text(
        label,
        style = BrandText.label,
        color = Brand.Ink,
        modifier = Modifier.clip(PillShape).background(Brand.CanvasSoft).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = BrandText.label, color = Brand.InkSecondary, modifier = modifier.padding(top = 16.dp, bottom = 8.dp))
}

/** Soft box for raw notification text and helper copy. */
@Composable
fun SoftBox(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = BrandText.label,
        color = Brand.InkSecondary,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Brand.CanvasSoft).padding(12.dp),
    )
}

package com.jstensix.cardspending.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.ui.IssuerLogo
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText

val PillShape = RoundedCornerShape(50)

enum class PillStyle { Primary, Secondary }

/** DESIGN.md button-primary-pill / button-secondary. At least 44dp tall for touch. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val primary = style == PillStyle.Primary
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        color = when {
            !primary -> Brand.Canvas
            pressed -> Brand.PrimaryPress
            else -> Brand.Primary
        },
        contentColor = if (primary) Brand.Canvas else Brand.Primary,
        border = if (primary) null else BorderStroke(1.dp, Brand.Primary),
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 44.dp).alpha(if (enabled) 1f else 0.4f),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, style = BrandText.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Toggle chip: selected = soft indigo fill, unselected = white with hairline. */
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = if (selected) Brand.Subdued else Brand.Canvas,
        contentColor = if (selected) Brand.PrimaryDeep else Brand.Ink,
        border = if (selected) null else BorderStroke(1.dp, Brand.Hairline),
        modifier = modifier.heightIn(min = 36.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = BrandText.label)
        }
    }
}

@Composable
fun SegmentedToggle(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clip(PillShape).background(Brand.CanvasSoft).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val cell = Modifier.weight(1f).heightIn(min = 36.dp)
            Box(
                modifier = (if (selected) cell.shadow(1.dp, PillShape).background(Brand.Canvas, PillShape) else cell.clip(PillShape))
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = BrandText.label, color = if (selected) Brand.Ink else Brand.InkMute)
            }
        }
    }
}

/** Card-issuer / card picker tile with its logo; the selected one gets a 2dp indigo frame. */
@Composable
fun LogoChoiceCard(issuer: Issuer, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Brand.Canvas)
            .border(if (selected) 2.dp else 1.dp, if (selected) Brand.Primary else Brand.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(if (selected) 9.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IssuerLogo(issuer, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = BrandText.label, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

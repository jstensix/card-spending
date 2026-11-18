package com.jstensix.cardspending.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText

enum class TagKind { Neutral, Excluded, Manual, Accent }

/** pill-tag-soft family. Excluded uses the cream interlude so "미인정" reads as a warm note, not an error. */
@Composable
fun StatusTag(text: String, kind: TagKind, modifier: Modifier = Modifier) {
    val (background, foreground) = when (kind) {
        TagKind.Neutral -> Brand.CanvasSoft to Brand.InkMute
        TagKind.Excluded -> Brand.Cream to Brand.Lemon
        TagKind.Manual, TagKind.Accent -> Brand.Subdued to Brand.PrimaryDeep
    }
    Text(
        text,
        style = BrandText.eyebrow,
        color = foreground,
        maxLines = 1,
        modifier = modifier.clip(PillShape).background(background).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** card-cream-band used for notices. Tappable when [onClick] is given (shows a chevron). */
@Composable
fun CreamBanner(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bounce(source, 0.97f) else Modifier)
            .clip(shape)
            .background(Brand.Cream)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            BrandIcon(icon, 20.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = BrandText.label, color = Brand.Ink, modifier = Modifier.weight(1f))
        if (onClick != null) Text("›", style = BrandText.title, color = Brand.Lemon)
    }
}

package com.jstensix.cardspending.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.ui.theme.Brand

private val TileShadow = Color(0x1F3F4CFF)

/** Gradient icon drawn as-is (no tint), as in the icon sheet. */
@Composable
fun BrandIcon(@DrawableRes icon: Int, size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(icon), contentDescription = null, modifier = modifier.size(size))
}

/** The icon sheet's soft white tile with a faint indigo shadow; used for empty states and setting rows. */
@Composable
fun IconTile(@DrawableRes icon: Int, size: Dp = 64.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier = modifier
            .size(size)
            .shadow(6.dp, shape, ambientColor = TileShadow, spotColor = TileShadow)
            .background(Brand.Canvas, shape)
            .border(1.dp, Brand.Hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        BrandIcon(icon, size * 0.56f)
    }
}

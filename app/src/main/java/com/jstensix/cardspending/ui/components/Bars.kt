package com.jstensix.cardspending.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.ui.theme.Brand

/** Recognized share of the payment. A null ratio (no positive payment) leaves the track empty. */
@Composable
fun RatioBar(ratio: Float?, onDark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(PillShape)
            .background(if (onDark) Color.White.copy(alpha = 0.18f) else Brand.Hairline),
    ) {
        if (ratio != null && ratio > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(PillShape)
                    .background(if (onDark) Color.White else Brand.Primary),
            )
        }
    }
}

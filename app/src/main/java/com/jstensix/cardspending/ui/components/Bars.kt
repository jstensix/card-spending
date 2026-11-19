package com.jstensix.cardspending.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.rules.TierProgress
import com.jstensix.cardspending.ui.model.compactManwon
import com.jstensix.cardspending.ui.model.tierLabelPositions
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText

/**
 * Gauge split by a card's 실적 구간: full at the top tier, a divider at each lower tier, and the tier amounts
 * below (✓ once reached). Labels that would collide are dropped; the end labels never run off the edges.
 */
@Composable
fun TierBar(progress: TierProgress, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
            val width = maxWidth
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(PillShape)
                    .background(Brand.Hairline),
            ) {
                if (progress.fill > 0f) {
                    Box(Modifier.fillMaxWidth(progress.fill).fillMaxHeight().clip(PillShape).background(Brand.Primary))
                }
            }
            progress.marks.forEachIndexed { index, mark ->
                if (mark < 1f) {
                    // White over the filled part reads as a segment break; grey on the empty track.
                    val onFill = mark <= progress.fill
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = width * mark - 1.dp)
                            .width(2.dp)
                            .height(if (onFill) 6.dp else 12.dp)
                            .background(if (onFill) Color.White else Brand.InkMute.copy(alpha = 0.45f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // Every label is measured; tierLabelPositions decides from the real widths which ones fit.
        Layout(
            content = {
                progress.tiers.forEachIndexed { index, tier ->
                    val reached = index < progress.achieved
                    Text(
                        compactManwon(tier.amount) + if (reached) " ✓" else "",
                        style = BrandText.caption,
                        color = if (reached) Brand.PrimaryDeep else Brand.InkMute,
                        maxLines = 1,
                    )
                }
            },
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val width = constraints.maxWidth
            val positions = tierLabelPositions(
                centers = progress.marks.map { (it * width).toInt() },
                widths = placeables.map { it.width },
                totalWidth = width,
                gap = 4.dp.roundToPx(),
            )
            layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
                placeables.forEachIndexed { i, placeable ->
                    positions[i]?.let { x -> placeable.placeRelative(x, 0) }
                }
            }
        }
    }
}

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

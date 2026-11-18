package com.jstensix.cardspending.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * Press feedback for anything tappable: shrinks while held, then springs back with a small overshoot.
 * Put it before the background/border in a modifier chain so the whole element scales together.
 */
fun Modifier.bounce(interactionSource: InteractionSource, pressedScale: Float = 0.95f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bounce",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** clickable() plus [bounce] sharing one interaction source; keeps the theme's ripple. */
fun Modifier.bounceClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    bounce(source, pressedScale).clickable(
        interactionSource = source,
        indication = LocalIndication.current,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
}

@Composable
fun BounceTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    TextButton(onClick = onClick, interactionSource = source, modifier = modifier.bounce(source), content = content)
}

@Composable
fun BounceIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, interactionSource = source, modifier = modifier.bounce(source, 0.88f), content = content)
}

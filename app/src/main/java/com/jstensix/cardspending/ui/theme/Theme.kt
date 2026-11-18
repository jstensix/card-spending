package com.jstensix.cardspending.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

private val Colors = lightColorScheme(
    primary = Brand.Primary,
    onPrimary = Brand.Canvas,
    primaryContainer = Brand.Subdued,
    onPrimaryContainer = Brand.PrimaryDeep,
    secondary = Brand.Navy,
    onSecondary = Brand.Canvas,
    secondaryContainer = Brand.Subdued,
    onSecondaryContainer = Brand.PrimaryDeep,
    tertiary = Brand.Navy,
    background = Brand.CanvasSoft,
    onBackground = Brand.Ink,
    surface = Brand.Canvas,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.CanvasSoft,
    onSurfaceVariant = Brand.InkMute,
    surfaceContainerLowest = Brand.Canvas,
    surfaceContainerLow = Brand.Canvas,
    surfaceContainer = Brand.Canvas,
    surfaceContainerHigh = Brand.Canvas,
    surfaceContainerHighest = Brand.CanvasSoft,
    surfaceTint = Brand.Canvas,
    outline = Brand.HairlineInput,
    outlineVariant = Brand.Hairline,
    error = Brand.Ruby,
    onError = Brand.Canvas,
)

private fun TextStyle.pretendard() = copy(fontFamily = Pretendard)

private val Default = Typography()

private val Type = Typography(
    displayLarge = BrandText.display,
    displayMedium = BrandText.display,
    displaySmall = BrandText.amountLarge,
    headlineLarge = BrandText.headline,
    headlineMedium = BrandText.headline,
    headlineSmall = BrandText.headline,
    titleLarge = BrandText.title,
    titleMedium = BrandText.title.copy(fontSize = BrandText.body.fontSize),
    titleSmall = BrandText.label,
    bodyLarge = BrandText.body,
    bodyMedium = BrandText.body.copy(fontSize = BrandText.label.fontSize),
    bodySmall = BrandText.caption,
    labelLarge = BrandText.button.copy(fontSize = BrandText.label.fontSize),
    labelMedium = Default.labelMedium.pretendard(),
    labelSmall = Default.labelSmall.pretendard(),
)

private val Shape = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Light only: DESIGN.md defines no dark palette, and Material You colors would override the brand. */
@Composable
fun CardSpendingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = Shape, content = content)
}

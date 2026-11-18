package com.jstensix.cardspending.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jstensix.cardspending.R

/** Colors from DESIGN.md, verbatim. */
object Brand {
    val Primary = Color(0xFF533AFD)
    val PrimaryDeep = Color(0xFF4434D4)
    val PrimaryPress = Color(0xFF2E2B8C)
    val Subdued = Color(0xFFB9B9F9)
    val Navy = Color(0xFF1C1E54)
    val Ink = Color(0xFF0D253D)
    val InkSecondary = Color(0xFF273951)
    val InkMute = Color(0xFF64748D)
    val Canvas = Color(0xFFFFFFFF)
    val CanvasSoft = Color(0xFFF6F9FC)
    val Cream = Color(0xFFF5E9D4)
    val Lemon = Color(0xFF9B6829)
    val Hairline = Color(0xFFE3E8EE)
    val HairlineInput = Color(0xFFA8C3DE)
    val Ruby = Color(0xFFEA2261)

    /** Highlighted digit box fill (a lighter step of Subdued). */
    val PrimaryTint = Color(0xFFF3F1FF)
}

/** Sohne is proprietary; DESIGN.md names Inter as the substitute and Pretendard is Inter's Hangul companion. */
val Pretendard = FontFamily(
    Font(R.font.pretendard_light, FontWeight.Light),
    Font(R.font.pretendard_regular, FontWeight.Normal),
)

private const val TABULAR = "tnum"

/**
 * Large type and money are thin (300) as DESIGN.md asks; running text is 400 because thin Hangul at
 * phone body sizes is hard to read.
 */
object BrandText {
    val display = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 1.1.em, letterSpacing = (-0.64).sp, fontFeatureSettings = TABULAR)
    val amountLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Light, fontSize = 28.sp, lineHeight = 1.15.em, letterSpacing = (-0.56).sp, fontFeatureSettings = TABULAR)
    val amount = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Light, fontSize = 20.sp, lineHeight = 1.3.em, letterSpacing = (-0.4).sp, fontFeatureSettings = TABULAR)
    val amountSmall = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 1.3.em, letterSpacing = (-0.42).sp, fontFeatureSettings = TABULAR)
    val title = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 1.3.em)
    val headline = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Light, fontSize = 22.sp, lineHeight = 1.2.em, letterSpacing = (-0.22).sp)
    val body = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 1.4.em)
    val label = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 1.4.em)
    val caption = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 1.4.em, fontFeatureSettings = TABULAR)
    val eyebrow = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 1.3.em, letterSpacing = 0.1.sp)
    val button = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 1.0.em)
}

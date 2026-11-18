package com.jstensix.cardspending.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.R
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.ui.theme.Brand

fun Issuer.logoRes(): Int = when (this) {
    Issuer.LOTTE -> R.drawable.logo_lotte
    Issuer.HANA -> R.drawable.logo_hana
}

/** Square logo tile; the hairline keeps the white Hana mark from dissolving into white cards. */
@Composable
fun IssuerLogo(issuer: Issuer, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.22f)
    Image(
        painter = painterResource(issuer.logoRes()),
        contentDescription = issuer.displayName,
        modifier = modifier.size(size).clip(shape).background(Brand.Canvas).border(1.dp, Brand.Hairline, shape),
    )
}

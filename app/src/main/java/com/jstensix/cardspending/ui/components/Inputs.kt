package com.jstensix.cardspending.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText

/**
 * Four boxes for a card's last 4 digits. [highlighted] boxes are the digits card alerts actually show
 * (1st and 3rd), so the user sees why two cards can look the same.
 */
@Composable
fun DigitBoxes(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Set<Int> = setOf(0, 2),
) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(4)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { index ->
                        val shape = RoundedCornerShape(8.dp)
                        val lit = index in highlighted
                        Box(
                            modifier = Modifier
                                .size(width = 48.dp, height = 56.dp)
                                .clip(shape)
                                .background(if (lit) Brand.PrimaryTint else Brand.Canvas)
                                .border(1.dp, if (lit) Brand.Primary else Brand.HairlineInput, shape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(value.getOrNull(index)?.toString().orEmpty(), style = BrandText.amount.copy(fontSize = 24.sp), color = Brand.Ink)
                        }
                    }
                }
                // The real field stays invisible on top of the boxes so taps focus it and the IME works normally.
                Box(Modifier.matchParentSize().alpha(0f)) { innerTextField() }
            }
        },
    )
}

/** Shows "12000" as "12,000" while the field keeps raw digits. */
object ThousandsSeparator : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = StringBuilder()
        val toOut = IntArray(digits.length + 1)
        digits.forEachIndexed { index, c ->
            toOut[index] = out.length
            out.append(c)
            val remaining = digits.length - index - 1
            if (remaining > 0 && remaining % 3 == 0) out.append(',')
        }
        toOut[digits.length] = out.length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = toOut[offset.coerceIn(0, digits.length)]
            override fun transformedToOriginal(offset: Int): Int =
                (0..digits.length).lastOrNull { toOut[it] <= offset } ?: 0
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}

/** text-input: 6dp, hairline-input border that turns indigo on focus. */
@Composable
fun BrandTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = BrandText.label) },
        singleLine = true,
        textStyle = BrandText.body.copy(color = Brand.Ink),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Brand.Primary,
            unfocusedBorderColor = Brand.HairlineInput,
            focusedLabelColor = Brand.Primary,
            unfocusedLabelColor = Brand.InkMute,
            cursorColor = Brand.Primary,
            focusedContainerColor = Brand.Canvas,
            unfocusedContainerColor = Brand.Canvas,
        ),
        modifier = modifier,
    )
}

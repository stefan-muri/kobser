package com.kobser.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Single-line text that scrolls horizontally when it overflows its width, mirroring
 * the web app's Marquee component. Text that fits stays static; overflowing text
 * loops continuously after a short initial pause.
 *
 * Backed by Compose's `Modifier.basicMarquee`, which only animates when the content
 * is wider than its constraints — so it's a no-op for short titles.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = text,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            initialDelayMillis = 1500,
            repeatDelayMillis = 1200,
            spacing = MarqueeSpacing(48.dp),
            velocity = 40.dp,
        ),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        style = style,
        maxLines = 1,
        softWrap = false,
    )
}

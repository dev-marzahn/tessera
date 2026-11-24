package dev.marzahn.sankaku.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.ui.theme.Amber

val FocusRingGap: Dp = 2.dp
val FocusRingWidth: Dp = 5.dp

fun Modifier.focusRing(shape: Shape): Modifier = composed {
  var focused by remember { mutableStateOf(false) }
  onFocusChanged { focused = it.hasFocus }.drawWithContent {
    drawContent()
    if (!focused) return@drawWithContent
    val ring = FocusRingWidth.toPx()
    val out = FocusRingGap.toPx() + ring / 2
    val outline = shape.createOutline(Size(size.width + 2 * out, size.height + 2 * out), layoutDirection, this)
    translate(-out, -out) { drawOutline(outline, Amber, style = Stroke(ring)) }
  }
}

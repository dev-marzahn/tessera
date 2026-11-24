package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import kotlin.math.roundToInt

fun TextStyle.centredOnLine(): TextStyle =
  copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both))

private const val CAP_HEIGHT = 0.7f

@Composable
fun BadgeLabel(
  text: String,
  modifier: Modifier = Modifier,
  style: TextStyle = LocalTextStyle.current,
  color: Color = Color.Unspecified,
  fontWeight: FontWeight? = null,
) {
  Layout(content = { Text(text, style = style, color = color, fontWeight = fontWeight, maxLines = 1) }, modifier = modifier) {
    measurables, constraints ->
    val label = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = maxOf(label.width, constraints.minWidth)
    val height = maxOf(label.height, constraints.minHeight)
    val baseline = label[FirstBaseline]
    val cap = style.fontSize.toPx() * CAP_HEIGHT
    layout(width, height) {
      val y = if (baseline == androidx.compose.ui.layout.AlignmentLine.Unspecified) {
        (height - label.height) / 2
      } else {
        (height / 2f + cap / 2f - baseline).roundToInt()
      }
      label.placeRelative((width - label.width) / 2, y)
    }
  }
}

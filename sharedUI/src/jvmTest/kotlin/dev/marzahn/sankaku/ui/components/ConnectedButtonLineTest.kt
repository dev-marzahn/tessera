package dev.marzahn.sankaku.ui.components

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectedButtonLineTest {
  private val direction = LayoutDirection.Ltr
  private val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), direction)
  private val typography = Typography()

  private val labelSets = listOf(
    listOf("Sort 2 new", "Clear viewed · 3", "Continue"),
    listOf("Sort 2 new", "Undo · cleared 3", "Continue"),
    listOf("Sort 14 new", "Clear viewed · 118", "Go through later · 2", "Continue"),
    listOf("Sort 1,204 new", "Undo · cleared 12", "Go through later · 12", "Continue"),
  )

  private fun layout(labels: List<String>, widthDp: Int, fontScale: Float) =
    lineLayout(labels, widthDp.dp, measurer, Density(1f, fontScale), direction, typography)

  @Test
  fun noChosenSlotIsNarrowerThanAWordOfItsLabels() {
    for (labels in labelSets) for (fontScale in listOf(1f, 1.15f, LARGE_FONT_SCALE, 1.5f, 2f)) for (width in 240..1000 step 4) {
      val density = Density(1f, fontScale)
      val chosen = layout(labels, width, fontScale)
      val style = chosen.spec.labelStyle(typography)
      val slot = with(density) { chosen.spec.labelWidth(width.dp, chosen.buttonsPerRow(labels.size), direction).toPx() }
      labels.flatMap { it.split(' ') }.forEach { word ->
        val wordWidth = measurer.measure(word, style, layoutDirection = direction, density = density).size.width
        assertTrue(wordWidth <= slot, "\"$word\" ($wordWidth px) fits $chosen's slot ($slot px) at $width dp, $fontScale×")
      }
    }
  }

  @Test
  fun threeShortLabelsShareOneConnectedLineOnAPhone() {
    assertEquals(LineLayout.Connected, layout(labelSets.first(), 380, 1f))
  }

  @Test
  fun pastTheLargeFontEachButtonGetsItsOwnRow() {
    assertEquals(LineLayout.Rows(1), layout(labelSets.first(), 1000, 1.5f))
  }
}

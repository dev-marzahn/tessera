package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object BottomRowLayout {
  val Height: Dp = 56.dp

  val CentreLine: Dp = 32.dp

  fun roomAbove(navigationBar: Dp): Dp = navigationBar + CentreLine - Height / 2

  val WindowRoom: Dp
    @Composable get() = with(LocalDensity.current) { roomAbove(WindowInsets.navigationBars.getBottom(this).toDp()) }

  val CornerCentre: Dp = 44.dp

  val CornerButton: Dp = 56.dp

  val PieceTarget: Dp = 48.dp

  val PieceDrawn: Dp = 40.dp

  fun topButtonPadding(room: Dp): PaddingValues =
    PaddingValues(end = CornerCentre - PieceTarget / 2, bottom = room + (Height - PieceTarget) / 2)

  val SideInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
}

@Composable
fun PillRow(
  modifier: Modifier = Modifier,
  bottom: Dp = 0.dp,
  start: @Composable () -> Unit = {},
  undo: @Composable () -> Unit = {},
  pill: @Composable () -> Unit,
) {
  Layout(contents = listOf(start, undo, pill), modifier = modifier.windowInsetsPadding(BottomRowLayout.SideInsets)) { (starts, undos, pills), constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
    val placedStarts = starts.map { it.measure(loose) }
    val width = constraints.maxWidth
    val corner = BottomRowLayout.CornerCentre.roundToPx()
    val startRight = placedStarts.maxOfOrNull { corner - it.width / 2 + it.width }
    val undoRoom = (EdgeMargin + BottomRowLayout.PieceTarget + PillGap).roundToPx()
    val cornerRoom = corner + BottomRowLayout.PieceTarget.roundToPx() / 2
    val side = maxOf(cornerRoom, startRight ?: undoRoom)
    val pillWidth = (width - 2 * side).coerceAtLeast(0)
    val placedPills = pills.map { it.measure(loose.copy(maxWidth = pillWidth)) }
    val pillLeft = (width - (placedPills.maxOfOrNull { it.width } ?: 0)) / 2
    val placedUndos = undos.map { it.measure(loose) }
    val bottomPx = bottom.roundToPx()
    val tallest = (placedStarts + placedPills + placedUndos).maxOfOrNull { it.height } ?: 0
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else tallest + bottomPx
    val line = (height - bottomPx) / 2
    layout(width, height) {
      placedStarts.forEach { it.placeRelative(corner - it.width / 2, line - it.height / 2) }
      placedPills.forEach { it.placeRelative((width - it.width) / 2, line - it.height / 2) }
      placedUndos.forEach {
        val x = pillLeft - PillGap.roundToPx() - it.width
        if (startRight == null || x >= startRight) {
          it.placeRelative(x, line - it.height / 2)
        } else {
          val above = (startRight - corner) * 2
          it.placeRelative(corner - it.width / 2, line - above / 2 - PillGap.roundToPx() - it.height)
        }
      }
    }
  }
}

val PillGap: Dp = 8.dp

private val EdgeMargin: Dp = 8.dp

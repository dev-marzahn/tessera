package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import dev.marzahn.sankaku.ui.components.AlreadyTab
import dev.marzahn.sankaku.ui.components.FavouriteBadgeButton
import dev.marzahn.sankaku.ui.components.InTabChip
import dev.marzahn.sankaku.ui.components.KbdBadge
import dev.marzahn.sankaku.ui.components.SeenBadge
import dev.marzahn.sankaku.ui.components.Shortcuts

fun isFavouritesSearch(tags: List<String>): Boolean = tags.any { it.startsWith("fav:") }

@Composable
fun CellOverlay(
  favourited: Boolean,
  unfavourited: Boolean,
  hovered: Boolean,
  onToggleFavourite: () -> Unit,
  modifier: Modifier = Modifier,
  openInTab: Boolean = false,
  alreadyTab: AlreadyTab? = null,
  seen: Boolean = false,
  keyHints: Boolean = false,
) {
  Box(modifier.fillMaxSize()) {
    if (unfavourited) UnfavouritedScrim(onUndo = onToggleFavourite)
    if (seen || openInTab) {
      Row(
        Modifier.align(Alignment.BottomStart).padding(6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (seen) SeenBadge()
        if (openInTab) InTabChip(Modifier, alreadyTab)
      }
    }
    if (keyHints) {
      Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.inverseSurface,
        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
      ) {
        Row(Modifier.height(24.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          KbdBadge(Shortcuts.OpenInTab.label)
          KbdBadge("␣")
        }
      }
    }
    FavouriteBadgeButton(
      favourited = favourited,
      expanded = hovered,
      onToggle = onToggleFavourite,
      modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
    )
  }
}

@Composable
private fun UnfavouritedScrim(onUndo: () -> Unit) {
  Box(
    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)).padding(4.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.inverseSurface) {
      val style = MaterialTheme.typography.labelLarge
      UndoPillLayout(
        label = { Text("Unfavourited", style = style, maxLines = 1, softWrap = false) },
        dot = { Text(" ·", style = style, maxLines = 1, softWrap = false, modifier = Modifier.clearAndSetSemantics {}) },
        undo = {
          TextButton(onClick = onUndo) {
            val size = LocalTextStyle.current.fontSize
            Text(
              "Undo",
              color = MaterialTheme.colorScheme.inversePrimary,
              maxLines = 1,
              autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = size),
            )
          }
        },
      )
    }
  }
}

internal enum class UndoPillShape { Row, Stacked, UndoOnly }

private val PILL_START = 14.dp
private val PILL_STACK_TOP = 8.dp

@Composable
private fun UndoPillLayout(label: @Composable () -> Unit, dot: @Composable () -> Unit, undo: @Composable () -> Unit) {
  Layout(contents = listOf(label, dot, undo)) { (labels, dots, undos), constraints ->
    val free = Constraints(maxHeight = constraints.maxHeight)
    val l = labels.first().measure(free)
    val d = dots.first().measure(free)
    val u = undos.first().measure(free.copy(maxWidth = constraints.maxWidth))
    val start = PILL_START.roundToPx()
    val top = PILL_STACK_TOP.roundToPx()
    val shape = undoPillShape(
      maxWidth = constraints.maxWidth,
      maxHeight = constraints.maxHeight,
      rowWidth = start + l.width + d.width + u.width,
      rowHeight = maxOf(l.height, u.height),
      stackWidth = maxOf(2 * start + l.width, u.width),
      stackHeight = top + l.height + u.height,
    )
    when (shape) {
      UndoPillShape.Row -> {
        val height = maxOf(l.height, u.height)
        layout(start + l.width + d.width + u.width, height) {
          l.place(start, (height - l.height) / 2)
          d.place(start + l.width, (height - d.height) / 2)
          u.place(start + l.width + d.width, (height - u.height) / 2)
        }
      }
      UndoPillShape.Stacked -> {
        val width = maxOf(2 * start + l.width, u.width)
        layout(width, top + l.height + u.height) {
          l.place((width - l.width) / 2, top)
          u.place((width - u.width) / 2, top + l.height)
        }
      }
      UndoPillShape.UndoOnly -> layout(u.width, u.height) { u.place(0, 0) }
    }
  }
}

internal fun undoPillShape(
  maxWidth: Int,
  maxHeight: Int,
  rowWidth: Int,
  rowHeight: Int,
  stackWidth: Int,
  stackHeight: Int,
): UndoPillShape = when {
  rowWidth <= maxWidth && rowHeight <= maxHeight -> UndoPillShape.Row
  stackWidth <= maxWidth && stackHeight <= maxHeight -> UndoPillShape.Stacked
  else -> UndoPillShape.UndoOnly
}

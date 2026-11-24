package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.ui.Tip
import dev.marzahn.sankaku.ui.TipAction
import dev.marzahn.sankaku.ui.TipBubble
import dev.marzahn.sankaku.ui.Tips
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Composable
fun TipAnchor(
  tip: Tip,
  tips: Tips,
  visible: Boolean,
  modifier: Modifier = Modifier,
  text: String = tip.text,
  action: TipAction? = null,
) {
  val showing = visible && !tips.covered
  var anchor by remember { mutableStateOf<Rect?>(null) }
  val currentText by rememberUpdatedState(text)
  val currentAction by rememberUpdatedState(action)
  DisposableEffect(tip, tips) { onDispose { tips.dismissed(tip) } }
  SideEffect { anchor?.let { tips.moved(tip, text, action, it) } }
  LaunchedEffect(showing) {
    if (!showing) return@LaunchedEffect
    try {
      delay(TIP_SETTLE_MS)
      val at = snapshotFlow { anchor }.first { it != null }!!
      if (!tips.appeared(tip, currentText, currentAction, at)) return@LaunchedEffect
      snapshotFlow { tips.bubble?.tip == tip }.first { !it }
    } finally {
      tips.dismissed(tip)
    }
  }
  Box(
    modifier.onGloballyPositioned {
      val bounds = it.boundsInRoot()
      anchor = bounds
      tips.moved(tip, currentText, currentAction, bounds)
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipLayer(tips: Tips, modifier: Modifier = Modifier) {
  val bubble = tips.bubble ?: return
  var origin by remember { mutableStateOf(Offset.Zero) }
  val colors = TooltipDefaults.richTooltipColors()
  val shape = MaterialTheme.shapes.medium
  Layout(
    modifier = modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() },
    content = {
      Column(
        Modifier
          .widthIn(max = TIP_MAX_WIDTH)
          .shadow(TIP_ELEVATION, shape)
          .background(colors.containerColor, shape)
          .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
      ) {
        Text("Tip", style = MaterialTheme.typography.titleSmall, color = colors.titleContentColor)
        Text(
          bubble.text,
          Modifier.padding(top = 4.dp, end = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
          style = MaterialTheme.typography.bodyMedium,
          color = colors.contentColor,
        )
        Row(Modifier.padding(top = 4.dp)) {
          TextButton(onClick = { tips.dismissed(bubble.tip) }) { Text("Got it", color = colors.actionContentColor) }
          bubble.action?.let { action ->
            TextButton(onClick = { tips.dismissed(bubble.tip); action.onClick() }) {
              Text(action.label, color = colors.actionContentColor)
            }
          }
        }
      }
      Caret(colors.containerColor, up = true)
      Caret(colors.containerColor, up = false)
    },
  ) { measurables, constraints ->
    val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
    val body = measurables[0].measure(loose)
    val caretUp = measurables[1].measure(loose)
    val caretDown = measurables[2].measure(loose)
    layout(constraints.maxWidth, constraints.maxHeight) {
      val place = tipPlacement(
        anchor = bubble.anchor.translate(-origin),
        bodyWidth = body.width,
        bodyHeight = body.height,
        caretWidth = caretUp.width,
        caretHeight = caretUp.height,
        windowWidth = constraints.maxWidth,
        windowHeight = constraints.maxHeight,
        topLimit = tips.topBarBottom - origin.y,
        margin = TIP_MARGIN.toPx(),
        corner = 12.dp.toPx(),
      )
      body.place(place.bodyX, place.bodyY)
      (if (place.above) caretDown else caretUp).place(place.caretX, place.caretY)
    }
  }
}

internal class TipPlacement(val bodyX: Int, val bodyY: Int, val above: Boolean, val caretX: Int, val caretY: Int)

internal fun tipPlacement(
  anchor: Rect,
  bodyWidth: Int,
  bodyHeight: Int,
  caretWidth: Int,
  caretHeight: Int,
  windowWidth: Int,
  windowHeight: Int,
  topLimit: Float,
  margin: Float,
  corner: Float,
): TipPlacement {
  val x = (anchor.center.x - bodyWidth / 2f).coerceIn(margin, (windowWidth - margin - bodyWidth).coerceAtLeast(margin))
  val aboveY = anchor.top - caretHeight - bodyHeight
  val belowY = anchor.bottom + caretHeight
  val above = aboveY >= topLimit + margin
  val y = when {
    above -> aboveY
    belowY + bodyHeight <= windowHeight - margin -> belowY
    else -> (windowHeight - margin - bodyHeight).coerceAtLeast(topLimit + margin)
  }
  val caretX = (anchor.center.x - caretWidth / 2f)
    .coerceIn(x + corner, (x + bodyWidth - corner - caretWidth).coerceAtLeast(x + corner))
  val caretY = if (above) y + bodyHeight else y - caretHeight
  return TipPlacement(x.roundToInt(), y.roundToInt(), above, caretX.roundToInt(), caretY.roundToInt())
}

@Composable
private fun Caret(color: Color, up: Boolean) {
  Canvas(Modifier.size(TIP_CARET_WIDTH, TIP_CARET_HEIGHT)) {
    val path = Path().apply {
      if (up) {
        moveTo(0f, size.height)
        lineTo(size.width / 2, 0f)
        lineTo(size.width, size.height)
      } else {
        moveTo(0f, 0f)
        lineTo(size.width / 2, size.height)
        lineTo(size.width, 0f)
      }
      close()
    }
    drawPath(path, color)
  }
}

fun Modifier.tipGestures(tips: Tips): Modifier = pointerInput(tips) {
  awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    tips.gestureStarted()
    do {
      val event = awaitPointerEvent(PointerEventPass.Initial)
    } while (event.changes.any { it.pressed })
    tips.gestureEnded()
  }
}

private const val TIP_SETTLE_MS = 600L

private val TIP_MAX_WIDTH = 320.dp
private val TIP_MARGIN = 8.dp
private val TIP_ELEVATION = 3.dp
private val TIP_CARET_WIDTH = 16.dp
private val TIP_CARET_HEIGHT = 8.dp

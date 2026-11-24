package dev.marzahn.sankaku.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun Modifier.reflowWhenSettled(settle: Duration = 200.milliseconds): Modifier = composed {
  var settled by remember { mutableStateOf<Int?>(null) }
  val current = remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    snapshotFlow { current.intValue }.collectLatest { width ->
      if (width <= 0 || width == settled) return@collectLatest
      if (settled != null) delay(settle)
      settled = width
    }
  }
  layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
      val placeable = measurable.measure(constraints)
      return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    current.intValue = width
    val laidOut = settled ?: width
    if (laidOut == width) {
      val placeable = measurable.measure(constraints)
      return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val scale = width.toFloat() / laidOut
    val placeable = measurable.measure(Constraints.fixed(laidOut, (height / scale).roundToInt()))
    layout(width, height) {
      placeable.placeWithLayer(0, 0) {
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0f, 0f)
      }
    }
  }
}

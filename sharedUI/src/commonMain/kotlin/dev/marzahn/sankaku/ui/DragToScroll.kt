package dev.marzahn.sankaku.ui

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import kotlin.math.abs

fun Modifier.dragToScroll(state: ScrollableState): Modifier = composed {
  val scope = rememberCoroutineScope()
  val fling by rememberUpdatedState(ScrollableDefaults.flingBehavior())
  pointerInput(state) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
      val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
      var dragging = false
      while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) {
          if (dragging) {
            change.consume()
            val velocity = tracker.calculateVelocity().y
            scope.launch { state.scroll { with(fling) { performFling(-velocity) } } }
          }
          break
        }
        tracker.addPosition(change.uptimeMillis, change.position)
        if (!dragging && abs(change.position.y - down.position.y) > viewConfiguration.touchSlop) {
          dragging = true
          scope.launch { state.stopScroll() }
        }
        if (dragging) {
          state.dispatchRawDelta(-(change.position.y - change.previousPosition.y))
          change.consume()
        }
      }
    }
  }
}

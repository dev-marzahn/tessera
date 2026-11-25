package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange

internal enum class BarSpotTaker { Nobody, Bar, Stats }

internal fun barSpotTaker(closing: Boolean, barDocked: Boolean, barDragging: Boolean): BarSpotTaker = when {
  closing -> BarSpotTaker.Nobody
  !barDocked || barDragging -> BarSpotTaker.Bar
  else -> BarSpotTaker.Stats
}

internal interface ViewerDrag {
  fun start(at: Offset)

  fun dragBy(dy: Float)

  fun stop(velocity: Float)
}

internal class SheetDrag(private val sheet: ViewerSheetState, private val onMoved: () -> Unit) : ViewerDrag {
  override fun start(at: Offset) = Unit

  override fun dragBy(dy: Float) {
    if (sheet.dragBy(dy) != 0f) onMoved()
  }

  override fun stop(velocity: Float) = sheet.settle(velocity)
}

internal class PictureDrag(
  private val sheet: ViewerDrag,
  private val dismiss: CardDismiss,
  private val closes: (dy: Float, fromSeekRow: Boolean) -> Boolean,
  private val onSeekRow: (Offset) -> Boolean,
  private val onLifting: () -> Unit,
  private val letGo: (velocity: Float) -> Unit,
) : ViewerDrag {
  private var dismissing: Boolean? = null
  private var fromSeekRow = false

  override fun start(at: Offset) {
    dismissing = null
    fromSeekRow = onSeekRow(at)
  }

  override fun dragBy(dy: Float) {
    if (dismissing ?: closes(dy, fromSeekRow).also { dismissing = it }) {
      dismiss.dragBy(dy)
    } else {
      if (dy < 0f) onLifting()
      sheet.dragBy(dy)
    }
  }

  override fun stop(velocity: Float) {
    if (dismissing == true) letGo(velocity) else sheet.stop(velocity)
    dismissing = null
  }
}

internal fun Modifier.holdsTouches(): Modifier = pointerInput(Unit) {}

internal suspend fun PointerInputScope.handOnBarDrags(dragAt: (Offset) -> ViewerDrag?, onDragging: (Boolean) -> Unit) = awaitEachGesture {
  val down = awaitFirstDown(requireUnconsumed = false)
  val drag = dragAt(down.position) ?: return@awaitEachGesture
  val velocity = VelocityTracker().apply { addPointerInputChange(down) }
  var pastSlop = 0f
  val first = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
    change.consume()
    pastSlop = over
  } ?: return@awaitEachGesture
  var pointer: PointerId = first.id
  fun follow(change: PointerInputChange) {
    pointer = change.id
    velocity.addPointerInputChange(change)
    drag.dragBy(change.positionChange().y)
    change.consume()
  }
  var lifted = false
  drag.start(down.position)
  onDragging(true)
  try {
    velocity.addPointerInputChange(first)
    drag.dragBy(pastSlop)
    lifted = verticalDrag(first.id, ::follow)
    if (lifted) currentEvent.changes.firstOrNull { it.id == pointer }?.let(::follow)
  } finally {
    drag.stop(if (lifted) velocity.calculateVelocity().y else 0f)
    onDragging(false)
  }
}

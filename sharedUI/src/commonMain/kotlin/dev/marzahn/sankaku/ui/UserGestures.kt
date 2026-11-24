package dev.marzahn.sankaku.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.first

@Stable
class UserGestures {
  private var pressed by mutableIntStateOf(0)
  private var ended by mutableIntStateOf(0)
  private var current = 0

  internal fun press() {
    current = ++pressed
  }

  internal fun release() {
    ended = current
  }

  suspend fun awaitNext() {
    val from = pressed
    snapshotFlow { ended }.first { it > from }
  }
}

fun Modifier.observeGestures(gestures: UserGestures): Modifier = pointerInput(gestures) {
  awaitPointerEventScope {
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Final)
      when (event.type) {
        PointerEventType.Press -> gestures.press()
        PointerEventType.Release -> if (event.changes.none { it.pressed }) gestures.release()
        PointerEventType.Scroll -> {
          gestures.press()
          gestures.release()
        }
        else -> Unit
      }
    }
  }
}

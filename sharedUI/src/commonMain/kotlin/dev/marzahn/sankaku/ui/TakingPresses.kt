package dev.marzahn.sankaku.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.takingPresses(taking: Boolean): Modifier = pointerInput(taking) {
  if (taking) return@pointerInput
  awaitPointerEventScope {
    while (true) {
      awaitPointerEvent(PointerEventPass.Initial).changes.forEach { if (it.changedToDownIgnoreConsumed()) it.consume() }
    }
  }
}

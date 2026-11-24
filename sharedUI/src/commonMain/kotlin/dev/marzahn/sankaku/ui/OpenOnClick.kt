package dev.marzahn.sankaku.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.marzahn.sankaku.platformIsTouch

fun Modifier.openOnClick(
  onOpen: () -> Unit,
  onOpenInNewTab: () -> Unit,
  interactionSource: MutableInteractionSource? = null,
  touch: Boolean = platformIsTouch,
  onLongPress: () -> Unit = onOpenInNewTab,
  longPressLabel: String? = null,
  inTab: Boolean = false,
  focusFill: Boolean = true,
): Modifier = composed {
  val focusless = if (!focusFill && interactionSource == null) remember { FocuslessInteractions() } else null
  val source = interactionSource ?: focusless
  val indication = if (interactionSource == null) LocalIndication.current else null
  var modified by remember { mutableStateOf(false) }
  var middlePressed by remember { mutableStateOf(false) }
  val openInNewTab by rememberUpdatedState(onOpenInNewTab)
  val haptics = LocalHapticFeedback.current
  val click = { if (modified) onOpenInNewTab() else onOpen() }
  this
    .pointerInput(Unit) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          when (event.type) {
            PointerEventType.Press -> {
              modified = event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed
              middlePressed = event.buttons.isTertiaryPressed
            }
            PointerEventType.Release -> if (middlePressed) {
              middlePressed = false
              openInNewTab()
            }
          }
        }
      }
    }
    .then(
      when {
        touch -> Modifier.combinedClickable(
          interactionSource = source,
          indication = indication,
          hapticFeedbackEnabled = false,
          onLongClickLabel = longPressLabel,
          onLongClick = {
            haptics.performHapticFeedback(if (inTab) HapticFeedbackType.Reject else HapticFeedbackType.Confirm)
            onLongPress()
          },
          onClick = click,
        )
        else -> Modifier.clickable(source, indication, onClick = click)
      },
    )
}

fun Modifier.onRightClick(onMenu: (Offset) -> Unit): Modifier = composed {
  val handler by rememberUpdatedState(onMenu)
  pointerInput(Unit) {
    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
          event.changes.forEach { it.consume() }
          handler(event.changes.first().position)
        }
      }
    }
  }
}

private class FocuslessInteractions(
  private val inner: MutableInteractionSource = MutableInteractionSource(),
) : MutableInteractionSource by inner {
  override val interactions: Flow<Interaction> = inner.interactions.filter { it !is FocusInteraction }
}

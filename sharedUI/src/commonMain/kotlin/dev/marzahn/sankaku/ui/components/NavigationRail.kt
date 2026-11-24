package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModalNavigationRail(
  open: Boolean,
  onClose: () -> Unit,
  containerColor: Color,
  closeKey: Key? = null,
  focusColumn: Boolean = true,
  closeKeyHeld: Boolean = false,
  onClosedByKey: () -> Unit = onClose,
  onCloseKeyUp: () -> Unit = {},
  content: @Composable ColumnScope.() -> Unit,
) {
  val state = rememberWideNavigationRailState(if (open) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed)
  val close by rememberUpdatedState(onClose)
  val closeByKey by rememberUpdatedState(onClosedByKey)
  val closeKeyUp by rememberUpdatedState(onCloseKeyUp)
  val leaving = remember(open) { LeaveWatch() }
  LaunchedEffect(open) { if (open) state.expand() else state.collapse() }
  LaunchedEffect(state) {
    snapshotFlow { state.targetValue == WideNavigationRailValue.Expanded }.drop(1).collect { expanded ->
      if (!expanded) close()
    }
  }
  ModalWideNavigationRail(
    state = state,
    hideOnCollapse = true,
    colors = WideNavigationRailDefaults.colors(modalContainerColor = containerColor),
    contentPadding = PaddingValues(0.dp),
    modifier = Modifier.pointerInput(leaving) {
      awaitPointerEventScope {
        while (true) leaving.pressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
      }
    },
  ) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var columnFocused by remember { mutableStateOf(false) }
    var hadFocus by remember(open) { mutableStateOf(false) }
    Column(
      Modifier
        .width(RailWidth)
        .fillMaxHeight()
        .onGloballyPositioned { coordinates ->
          val left = coordinates.localToWindow(Offset.Zero).x
          val right = coordinates.localToWindow(Offset(coordinates.size.width.toFloat(), 0f)).x
          val settled = state.currentValue == WideNavigationRailValue.Expanded
          if (leaving.startsLeaving(left, scaled = abs(right - left - coordinates.size.width) > 1f, settled)) close()
        }
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        .onPreviewKeyEvent { event ->
          if (event.key == closeKey && event.type == KeyEventType.KeyUp) closeKeyUp()
          railKey(
            event,
            closeKey.takeIf { !closeKeyHeld },
            columnFocused,
            onMove = { focusManager.moveFocus(it) },
            onClose = closeByKey,
          )
        }
        .onFocusChanged {
          columnFocused = it.isFocused
          if (it.hasFocus) hadFocus = true
          if (!it.hasFocus && hadFocus && state.targetValue == WideNavigationRailValue.Expanded) focus.requestFocus()
        }
        .focusRequester(focus)
        .focusable(),
      content = content,
    )
    LaunchedEffect(Unit) { if (focusColumn) focus.requestFocus() }
  }
}

internal fun railKey(
  event: KeyEvent,
  closeKey: Key?,
  columnFocused: Boolean,
  onMove: (FocusDirection) -> Unit,
  onClose: () -> Unit,
): Boolean {
  if (event.type != KeyEventType.KeyDown || event.isCommand || event.isAltPressed) return false
  val key = event.key
  return when {
    Shortcuts.Up.matches(key) || Shortcuts.Down.matches(key) -> true.also {
      onMove(
        when {
          columnFocused -> FocusDirection.Enter
          Shortcuts.Up.matches(key) -> FocusDirection.Up
          else -> FocusDirection.Down
        },
      )
    }
    key == closeKey && event.isPlain -> true.also { onClose() }
    else -> false
  }
}

internal class LeaveWatch {
  var pressed = false
  private var settledOpen = false
  private var lastLeft = 0f

  fun startsLeaving(left: Float, scaled: Boolean, settled: Boolean): Boolean {
    val starts = settledOpen && !pressed && !scaled && left < lastLeft && left < -SLACK
    if (settled && left >= 0f) settledOpen = true
    if (starts) settledOpen = false
    lastLeft = left
    return starts
  }

  private companion object {
    const val SLACK = 1f
  }
}

private val RailWidth = 280.dp

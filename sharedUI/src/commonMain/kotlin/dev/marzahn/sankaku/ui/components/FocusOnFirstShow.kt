package dev.marzahn.sankaku.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

class FocusOnFirstShow(val key: Long, val isFirstShow: () -> Boolean)

@Composable
fun rememberFocusOnFirstShow(focusOnFirstShow: FocusOnFirstShow): FocusRequester {
  val focus = remember { FocusRequester() }
  LaunchedEffect(focusOnFirstShow.key) { if (focusOnFirstShow.isFirstShow()) focus.requestFocus() }
  return focus
}

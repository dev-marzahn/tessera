package dev.marzahn.sankaku.ui.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Stable
class HideOnScroll internal constructor(
  internal val behavior: FloatingToolbarScrollBehavior,
  private val scope: CoroutineScope,
) {
  val connection: NestedScrollConnection get() = behavior

  val hiddenBy: Float get() = 0f - behavior.state.offset

  private var sliding: Job? = null

  fun show() {
    sliding?.cancel()
    val state = behavior.state
    state.contentOffset = 0f
    if (state.offset == 0f) return
    sliding = scope.launch {
      var placed = state.offset
      AnimationState(placed).animateTo(0f, behavior.snapAnimationSpec) {
        if (state.offset != placed) {
          cancelAnimation()
        } else {
          state.offset = value
          placed = state.offset
        }
      }
    }
  }

  fun showAtOnce() {
    sliding?.cancel()
    behavior.state.offset = 0f
    behavior.state.contentOffset = 0f
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberHideOnScroll(): HideOnScroll {
  val behavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(FloatingToolbarExitDirection.Bottom)
  val scope = rememberCoroutineScope()
  return remember(behavior, scope) { HideOnScroll(behavior, scope) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Modifier.hidesOnScroll(hideOnScroll: HideOnScroll?): Modifier =
  if (hideOnScroll == null) this else with(hideOnScroll.behavior) { this@hidesOnScroll.floatingScrollBehavior() }

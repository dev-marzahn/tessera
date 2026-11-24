package dev.marzahn.sankaku.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val KeyScrollLine = 120.dp

const val KEY_SCROLL_SCREEN = 0.85f

class KeyScroll(private val state: ScrollableState) {
  private var left = 0f
  private var job: Job? = null

  fun by(step: Float, scope: CoroutineScope) {
    left += step
    job?.cancel()
    job = scope.launch {
      val total = left
      var moved = 0f
      state.scroll {
        animate(0f, total) { value, _ ->
          scrollBy(value - moved)
          moved = value
          left = total - moved
        }
      }
      left = 0f
    }
  }
}

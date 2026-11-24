package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@Stable
internal class CardDismiss(private val scope: CoroutineScope, private val spec: AnimationSpec<Float>) {
  var offset by mutableFloatStateOf(0f)
    private set

  private var job: Job? = null

  val atRest: Boolean get() = offset == 0f

  fun dragBy(dy: Float) {
    job?.cancel()
    offset = (offset + dy).coerceAtLeast(0f)
  }

  fun pullTo(px: Float) {
    job?.cancel()
    offset = px.coerceAtLeast(0f)
  }

  fun springBack() {
    job?.cancel()
    job = scope.launch {
      animate(offset, 0f, animationSpec = spec) { value, _ ->
        offset = if (value < LAND_SLOP) 0f else value
        if (offset == 0f) this@launch.cancel()
      }
    }
  }

  fun fraction(range: Float): Float = if (range > 0f) (offset / range).coerceIn(0f, 1f) else 0f

  fun stop() {
    job?.cancel()
  }
}

private const val LAND_SLOP = 1f

@Composable
internal fun rememberCardDismiss(opening: Int, spec: AnimationSpec<Float>): CardDismiss {
  val scope = rememberCoroutineScope()
  val dismiss = remember(opening) { CardDismiss(scope, spec) }
  DisposableEffect(dismiss) { onDispose { dismiss.stop() } }
  return dismiss
}

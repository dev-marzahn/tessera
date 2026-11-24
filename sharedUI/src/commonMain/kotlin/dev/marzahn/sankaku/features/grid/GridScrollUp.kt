package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Stable
internal class ScrollUpWatch {
  var backUp by mutableStateOf(false)
    private set

  private var quiet by mutableIntStateOf(0)

  suspend fun quietly(block: suspend () -> Unit) {
    quiet++
    try {
      block()
    } catch (e: CancellationException) {
      withContext(NonCancellable) { withFrameNanos {} }
      throw e
    } finally {
      quiet--
    }
  }

  suspend fun watch(state: LazyStaggeredGridState) {
    var lastIndex = 0
    var lastOffset = 0
    snapshotFlow { Triple(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, quiet > 0) }.collect { (index, offset, ours) ->
      val up = index < lastIndex || (index == lastIndex && offset < lastOffset)
      val down = index > lastIndex || (index == lastIndex && offset > lastOffset)
      when {
        index < 10 -> backUp = false
        ours -> Unit
        up -> backUp = true
        down -> backUp = false
      }
      lastIndex = index
      lastOffset = offset
    }
  }
}

@Composable
internal fun rememberScrollUpWatch(state: LazyStaggeredGridState): ScrollUpWatch {
  val watch = remember { ScrollUpWatch() }
  LaunchedEffect(state, watch) { watch.watch(state) }
  return watch
}

internal suspend fun LazyStaggeredGridState.scrollBackForClose(index: Int, scrollUp: ScrollUpWatch) {
  scrollUp.quietly {
    if (scrollFullyIntoView(index)) repeat(2) { withFrameNanos {} }
  }
}

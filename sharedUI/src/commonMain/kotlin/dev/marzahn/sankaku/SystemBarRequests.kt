package dev.marzahn.sankaku

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

internal class SystemBarRequests<S : Any>(
  private val apply: (S) -> Unit,
  private val restore: (S) -> Unit,
) {
  private val open = LinkedHashMap<Any, S>()
  private var applied: S? = null

  val isEmpty: Boolean get() = open.isEmpty()

  fun request(owner: Any, state: S) {
    open[owner] = state
    applyNewest()
  }

  fun release(owner: Any) {
    val state = open.remove(owner) ?: return
    if (open.isEmpty()) {
      applied = null
      restore(state)
    } else {
      applyNewest()
    }
  }

  private fun applyNewest() {
    val newest = open.values.last()
    if (newest == applied) return
    applied = newest
    apply(newest)
  }
}

@Composable
internal fun <S : Any> SystemBarRequest(requests: SystemBarRequests<S>, state: S) {
  val owner = remember { Any() }
  DisposableEffect(requests) { onDispose { requests.release(owner) } }
  SideEffect { requests.request(owner, state) }
}

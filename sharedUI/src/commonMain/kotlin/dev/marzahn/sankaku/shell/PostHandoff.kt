package dev.marzahn.sankaku.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.features.post.SheetStrip
import dev.marzahn.sankaku.tabs.LocalQueuePictureSwipe
import dev.marzahn.sankaku.tabs.TabId
import dev.marzahn.sankaku.tabs.TabState
import dev.marzahn.sankaku.ui.components.spatialFadeSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@Stable
class PostHandoff {
  var current by mutableStateOf<PostOpening?>(null)
    private set

  suspend fun open(from: TabId, postId: String, active: () -> TabId, strip: SheetStrip? = null, navigate: suspend () -> Unit) {
    if (current != null || animationsOff()) return navigate()
    val opening = PostOpening(from, postId, strip = strip)
    current = opening
    try {
      repeat(MATCH_FRAMES) { withFrameNanos {} }
      navigate()
    } catch (e: Throwable) {
      finish(opening)
      throw e
    }
    val to = active()
    if (current == opening) current = if (to == from) null else opening.copy(to = to)
  }

  fun sharing(tab: TabId, postId: String, strip: SheetStrip? = null): Boolean =
    current?.let { it.from == tab && it.postId == postId && it.strip == strip } == true

  fun end() {
    current = null
  }

  internal fun finish(opening: PostOpening) {
    if (current == opening) current = null
  }
}

data class PostOpening(val from: TabId, val postId: String, val to: TabId? = null, val strip: SheetStrip? = null)

val LocalPostHandoff = staticCompositionLocalOf<PostHandoff?> { null }

val LocalItemVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

val LocalHandoffEntering = compositionLocalOf<AnimatedVisibilityScope?> { null }

val LocalHandoffLeaving = compositionLocalOf { false }

@Composable
internal fun PostHandoffHost(
  active: TabState,
  items: List<TabState>,
  handoff: PostHandoff?,
  content: @Composable (TabState) -> Unit,
) {
  val running = handoff?.current?.takeIf { it.to == active.id }
  val leaving = running
    ?.takeIf { (active.top as? Route.Post)?.id == it.postId }
    ?.let { r -> items.firstOrNull { it.id == r.from } }
  val states = remember { HashMap<TabId, MutableTransitionState<Boolean>>() }
  val fade = spatialFadeSpec<Float>()
  Box(Modifier.fillMaxSize()) {
    for (item in listOfNotNull(active, leaving)) {
      key(item.id) {
        val isLeaving = item.id == leaving?.id
        val entering = leaving != null && item.id == active.id
        val state = remember { MutableTransitionState(!entering).apply { targetState = true } }
        SideEffect { state.targetState = !isLeaving }
        DisposableEffect(state) {
          states[item.id] = state
          onDispose { if (states[item.id] === state) states.remove(item.id) }
        }
        AnimatedVisibility(
          state,
          enter = EnterTransition.None,
          exit = fadeOut(fade),
          modifier = if (isLeaving) Modifier.swallowTouches() else Modifier,
        ) {
          CompositionLocalProvider(
            LocalItemVisibility provides this,
            LocalHandoffEntering provides this.takeIf { entering },
            LocalHandoffLeaving provides isLeaving,
            LocalQueuePictureSwipe provides if (isLeaving) null else LocalQueuePictureSwipe.current,
          ) { content(item) }
        }
      }
    }
  }
  val overtaken = handoff?.current?.takeIf { it.to != null && active.id != it.to && active.id != it.from }
  if (handoff != null && overtaken != null) LaunchedEffect(overtaken) { handoff.finish(overtaken) }
  if (handoff != null && running != null) {
    LaunchedEffect(running, leaving?.id) {
      if (leaving != null) {
        snapshotFlow {
          val shown = states[active.id]
          val left = states[leaving.id]
          shown?.isIdle != false && left?.isIdle != false && left?.currentState != true
        }.first { it }
      }
      handoff.finish(running)
    }
  }
}

private fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
  awaitPointerEventScope {
    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
  }
}

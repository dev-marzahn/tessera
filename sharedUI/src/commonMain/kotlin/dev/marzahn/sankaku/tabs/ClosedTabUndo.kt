package dev.marzahn.sankaku.tabs

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.Tip
import dev.marzahn.sankaku.ui.Tips
import dev.marzahn.sankaku.ui.UserGestures
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class ClosedTabUndo(
  private val controller: TabsController,
  private val tips: Tips? = null,
  private val messages: Messages? = null,
  private val gestures: UserGestures? = null,
) {
  data class Pending(val closed: ClosedTab, val serial: Int, val alsoUndo: (() -> Unit)? = null)

  private val _pending = MutableStateFlow<Pending?>(null)
  val pending: StateFlow<Pending?> = _pending.asStateFlow()

  private var serial = 0

  private val hiddenBy = mutableStateMapOf<Any, Unit>()

  val hidden: Boolean get() = hiddenBy.isNotEmpty()

  fun hide(by: Any, hidden: Boolean) {
    if (hidden) hiddenBy[by] = Unit else hiddenBy.remove(by)
  }

  suspend fun awaitNextAction() {
    merge(flow { emit(controller.awaitNextAction()) }, flow { emit(awaitShownGesture()) }).first()
  }

  private suspend fun awaitShownGesture() {
    val gestures = gestures ?: awaitCancellation()
    while (true) {
      snapshotFlow { hidden }.first { !it }
      val tapped = merge(flow { gestures.awaitNext(); emit(true) }, snapshotFlow { hidden }.filter { it }.map { false }).first()
      if (tapped) return
    }
  }

  fun close(id: TabId, alsoUndo: (() -> Unit)? = null) {
    opened(controller.close(id) ?: return, alsoUndo)
  }

  fun closeBack(id: TabId): Boolean = controller.closeBackSaying(id, messages)

  fun dragClose(id: TabId, upNext: UpNextState) {
    if (controller.keptRunOver() && upNext.card != UpNextCard.KeptEnd) controller.endKeptRun()
    val ahead = upNext.ahead()
    val toCard = controller.activeTab.id == id && controller.neighbour(forward = false) != null && ahead == Ahead.End
    opened(controller.close(id, toPrevious = toCard) ?: return)
    if (toCard) upNext.showAhead(ahead) else if (controller.keptRunOver()) upNext.showKeptEnd()
  }

  fun opened(closed: ClosedTab, alsoUndo: (() -> Unit)? = null) {
    _pending.value = Pending(closed, ++serial, alsoUndo)
  }

  private var lineUndos by mutableIntStateOf(0)

  val undoOnLine: Boolean get() = lineUndos > 0

  fun offerOnLine(closed: ClosedTab, text: String) {
    val messages = messages ?: return
    val until: suspend () -> Unit = {
      lineUndos++
      try {
        awaitNextAction()
      } finally {
        lineUndos--
      }
    }
    messages.show(Message(text, actionLabel = "Undo", until = until) { controller.reopen(closed, inPlace = true) })
  }

  fun undo() {
    val pending = _pending.value ?: return
    end(pending)
    controller.reopen(pending.closed)
    pending.alsoUndo?.invoke()
  }

  fun reopenLatest(): Boolean {
    if (controller.closed.value.isEmpty()) return false
    controller.reopenClosed()
    tips?.used(Tip.ReopenHold)
    return true
  }

  private var grownSerial = 0

  internal fun hasGrown(pending: Pending): Boolean = grownSerial == pending.serial

  internal fun grown(pending: Pending) {
    grownSerial = pending.serial
  }

  internal fun end(pending: Pending) {
    _pending.update { if (it == pending) null else it }
  }
}

const val UNKEPT = "Removed from queue"

const val KEEP_FOR_LATER = "Keep for later"

fun backClosedMessage(closed: ClosedTab, tabs: TabsController): Message =
  Message(closed.closedLabel, actionLabel = KEEP_FOR_LATER, until = tabs::awaitNextAction) { tabs.keepForLater(closed) }

fun TabsController.closeBackSaying(id: TabId, messages: Messages?): Boolean {
  val closed = closeBack(id) ?: return false
  messages?.show(backClosedMessage(closed, this))
  return true
}

@Composable
fun EndUndoAtNextAction(undo: ClosedTabUndo = koinInject(), controller: TabsController = koinInject()) {
  val pending by undo.pending.collectAsStateWithLifecycle()
  val current = pending ?: return
  LaunchedEffect(current) {
    val cameBack = launch { controller.closed.first { current.closed !in it }.let { undo.end(current) } }
    undo.awaitNextAction()
    cameBack.cancel()
    undo.end(current)
  }
}

@Composable
fun UndoHiddenWhile(hidden: Boolean, undo: ClosedTabUndo = koinInject()) {
  val key = remember { Any() }
  SideEffect { undo.hide(key, hidden) }
  DisposableEffect(undo, key) { onDispose { undo.hide(key, false) } }
}

@Composable
internal fun rememberUndoShown(pending: ClosedTabUndo.Pending?, undo: ClosedTabUndo): MutableTransitionState<Boolean> {
  val shown = remember { MutableTransitionState(pending != null && undo.hasGrown(pending)) }
  shown.targetState = pending != null
  LaunchedEffect(pending) {
    val current = pending ?: return@LaunchedEffect
    if (shown.currentState) return@LaunchedEffect
    snapshotFlow { shown.currentState }.first { it }
    undo.grown(current)
  }
  return shown
}

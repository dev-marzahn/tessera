package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.components.Shortcuts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import org.koin.mp.KoinPlatform

class BlockOffer {
  var pending: TabId? by mutableStateOf(null)
    internal set

  var title: String? by mutableStateOf(null)
    internal set

  internal val offered = mutableSetOf<TabId>()

  internal var message: Message? = null

  fun take(controller: TabsController, messages: Messages): Boolean {
    val (closed, title) = close(controller) ?: return false
    messages.show(Message("Closed “$title” · ${closed.size}", actionLabel = "Undo", short = true) { controller.reopen(closed) })
    return true
  }

  fun take(undo: ClosedTabUndo): Boolean {
    val head = pending ?: return false
    pending = null
    undo.close(head)
    return true
  }

  private fun close(controller: TabsController): Pair<ClosedTab, String>? {
    val head = pending ?: return null
    pending = null
    return controller.closeBlock(head)
  }
}

@Composable
internal fun blockOfferOrNull(): BlockOffer? = remember { KoinPlatform.getKoinOrNull()?.getOrNull<BlockOffer>() }

@Composable
fun LeavingBlockMessages(
  offer: BlockOffer,
  controller: TabsController = koinInject(),
  seen: SeenStore = koinInject(),
  clearViewed: ClearViewed = koinInject(),
  messages: Messages = koinInject(),
) {
  LaunchedEffect(offer.pending) {
    if (offer.pending == null) return@LaunchedEffect
    delay(OFFER_MILLIS)
    offer.pending = null
  }
  LaunchedEffect(messages) {
    messages.messages.collect { if (it !== offer.message) offer.pending = null }
  }
  OnLeavingBlock(offer, controller, seen) { said ->
    when (said) {
      LeavingBlock.None -> Unit
      LeavingBlock.QueueDone -> {
        offer.pending = null
        val viewed = clearViewed.status().count
        messages.show(
          if (viewed > 0) {
            Message("Queue done · nothing kept is left", actionLabel = "Clear viewed · $viewed") { clearViewed() }
          } else {
            Message("Queue done · nothing kept is left")
          },
        )
      }
      is LeavingBlock.Done -> {
        offer.offered += said.head
        val message = Message(
          "Done with ${said.title}?${if (said.seen > 0) " · ${said.seen} seen" else ""}",
          actionLabel = "Close ${Shortcuts.CloseBlock.label}",
        ) { offer.take(controller, messages) }
        offer.message = message
        offer.pending = said.head
        messages.show(message)
      }
    }
  }
}

@Composable
fun LeavingBlockOffers(
  offer: BlockOffer,
  quiet: Boolean,
  controller: TabsController = koinInject(),
  seen: SeenStore = koinInject(),
  undo: ClosedTabUndo = koinInject(),
  blockFinds: BlockFinds? = blockFindsOrNull(),
) {
  val isQuiet by rememberUpdatedState(quiet)
  OnLeavingBlock(offer, controller, seen, judge = { moved -> leftSource(moved.tabs, moved.left, moved.hadKept, moved.now, offer.offered) }) { said ->
    if (said is LeavingBlock.Done && !isQuiet) {
      offer.offered += said.head
      offer.title = said.title
      offer.pending = said.head
    }
  }
  val pending = offer.pending ?: return
  LaunchedEffect(pending) {
    if (blockFinds != null) snapshotFlow { blockFinds.offer }.first { it == null }
    undo.awaitNextAction()
    if (offer.pending == pending) offer.pending = null
  }
}

internal fun leftSource(tabs: List<TabState>, left: TabId?, hadKept: Boolean, now: TabId, offered: Set<TabId>): LeavingBlock {
  if (left == null || !hadKept || left in offered || blockHeadOf(tabs, now) == left) return LeavingBlock.None
  val head = tabs.firstOrNull { it.id == left && it.kind == QueueKind.Source } ?: return LeavingBlock.None
  return LeavingBlock.Done(head.id, head.title, seen = 0)
}

private class Moved(val tabs: List<TabState>, val left: TabId?, val hadKept: Boolean, val keptBefore: Int, val now: TabId, val seen: (TabState) -> Boolean)

@Composable
private fun OnLeavingBlock(
  offer: BlockOffer,
  controller: TabsController,
  seen: SeenStore,
  judge: (Moved) -> LeavingBlock = { leavingBlock(it.tabs, it.left, it.hadKept, it.keptBefore, it.now, offer.offered, it.seen) },
  said: (LeavingBlock) -> Unit,
) {
  val last = remember { Shown() }
  val active by controller.active.collectAsStateWithLifecycle()
  val onSaid by rememberUpdatedState(said)
  LaunchedEffect(active) {
    withFrameNanos {}
    val tabs = controller.tabs.value
    val seenNow = { tab: TabState -> !tab.later && tab.seenSinceKept(seen) }
    val quiet = controller.backClosedTo == active || controller.clearedTo == active
    val result = if (quiet) LeavingBlock.None else judge(Moved(tabs, last.head, last.hadKept, last.keptLeft, active, seenNow))
    val head = blockHeadOf(tabs, active)
    val hasKept = blockProgress(tabs, active, seenNow) != null
    last.hadKept = hasKept || (head == last.head && last.hadKept)
    last.head = head
    last.keptLeft = keptLeft(tabs, seenNow)
    if (offer.pending != null && offer.pending == head) offer.pending = null
    onSaid(result)
  }
}

private class Shown(var head: TabId? = null, var hadKept: Boolean = false, var keptLeft: Int = 0)

private const val OFFER_MILLIS = 10_000L

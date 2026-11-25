package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.sankaku.data.AccessibilitySettings
import dev.marzahn.sankaku.isLone
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.ui.Tip
import dev.marzahn.sankaku.ui.Tips
import dev.marzahn.sankaku.ui.components.BlockEndLine
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import dev.marzahn.sankaku.ui.components.DoneWithChip
import dev.marzahn.sankaku.ui.components.doneWithLabel
import dev.marzahn.sankaku.ui.components.CLOSE_LABEL
import dev.marzahn.sankaku.ui.components.CloseCorner
import dev.marzahn.sankaku.ui.components.CountPill
import dev.marzahn.sankaku.ui.components.HideOnScroll
import dev.marzahn.sankaku.ui.components.LiveAnnouncement
import dev.marzahn.sankaku.ui.components.PillRow
import dev.marzahn.sankaku.ui.components.PillTap
import dev.marzahn.sankaku.ui.components.PillWithLine
import dev.marzahn.sankaku.ui.components.TipAnchor
import dev.marzahn.sankaku.ui.TipAction
import dev.marzahn.sankaku.ui.keptWaitsText
import dev.marzahn.sankaku.ui.openedInFrontText
import dev.marzahn.sankaku.ui.components.UndoChip
import dev.marzahn.sankaku.ui.components.hidesOnScroll
import kotlin.math.roundToInt
import org.koin.compose.koinInject

@Composable
fun QueueBar(
  onOpenQueue: () -> Unit,
  modifier: Modifier = Modifier,
  hideOnScroll: HideOnScroll? = null,
  bottom: Dp = 0.dp,
  controller: TabsController = koinInject(),
  undo: ClosedTabUndo = koinInject(),
  settings: AccessibilitySettings = koinInject(),
  tips: Tips = koinInject(),
  upNext: UpNextState = koinInject(),
  blockFinds: BlockFinds? = blockFindsOrNull(),
  blockOffer: BlockOffer? = blockOfferOrNull(),
  openOnly: Boolean = false,
  swipesQueue: Boolean = true,
  overPicture: Boolean = false,
) {
  val tabs by controller.tabs.collectAsStateWithLifecycle()
  val active by controller.active.collectAsStateWithLifecycle()
  val closed by controller.closed.collectAsStateWithLifecycle()
  val pending by undo.pending.collectAsStateWithLifecycle()
  val undoWords = pending?.let { it.closing.words(it.closed) }
  val wordsLeaving = remember { arrayOfNulls<Closing.Words>(1) }
  val chipWords = undoWords ?: wordsLeaving[0]
  SideEffect { if (undoWords != null) wordsLeaving[0] = undoWords }
  val showButtons by settings.showQueueButtons.collectAsStateWithLifecycle()
  val buttons = showButtons && !openOnly
  val index = tabs.indexOfFirst { it.id == active }.coerceAtLeast(0)
  val previous = controller.neighbour(forward = false)
  val cardUp = upNext.card != null
  val ahead = upNext.ahead()
  val goPrevious = { if (cardUp) upNext.dismiss() else controller.previous() }
  val goNext = {
    tips.used(Tip.QueueSwipe)
    upNext.forward()
  }
  val close: () -> Unit = { undo.close(active) }
  val current = tabs.getOrNull(index)
  val feed = current?.takeIf { it.kind == QueueKind.Feed }?.title
  val closable = current == null || controller.canClose(current)
  val closeName = current?.let { queueCloseLabel(it, controller.keptPostsOf(it)) } ?: CLOSE_LABEL
  val doneWith = blockOffer?.title?.takeIf {
    blockOffer.pending != null && !openOnly && !cardUp && upNext.sortGoneInto == null && blockFinds?.offer == null
  }
  val doneWithName = doneWith?.let { title ->
    val keptStay = tabs.firstOrNull { it.id == blockOffer?.pending }?.let { controller.keptPostsOf(it) } ?: 0
    "${doneWithLabel(title)}${keptStaySuffix(keptStay)}"
  }
  val takeDoneWith = { blockOffer?.take(undo) == true }
  val actions = if (openOnly) emptyList() else listOfNotNull(
    doneWithName?.let { CustomAccessibilityAction(it) { takeDoneWith() } },
    if (cardUp) CustomAccessibilityAction(PREVIOUS_ITEM) { upNext.dismiss(); true } else previous?.let { CustomAccessibilityAction(PREVIOUS_ITEM) { controller.previous() } },
    aheadAction(ahead, upNext),
    CustomAccessibilityAction(closeName) { close(); true }.takeIf { closable },
    undoWords?.let { CustomAccessibilityAction(it.undo) { undo.undo(); true } },
  )
  val slide = Modifier.offset { IntOffset(0, hideOnScroll?.hiddenBy?.roundToInt() ?: 0) }
  val place = blockPlace(tabs, index)
  val aheadName = if (cardUp || openOnly) null else ahead.title
  val tapBacksToSort = upNext.sortGoneInto != null && !openOnly
  val pillTap = remember(tapBacksToSort, upNext, onOpenQueue) {
    if (tapBacksToSort) PillTap(BACK_TO_SORT, upNext::backToSort) else PillTap(SHOW_QUEUE, onOpenQueue)
  }
  val findsText = if (openOnly) null else blockFinds?.pillText()
  val runKind = controller.keptRunKind()
  val keptText = if (openOnly || cardUp || runKind == null) null else controller.runProgress()?.let { runPillText(runKind, it) }
  val decisions = if (place is BlockPlace.Kept) controller.blockDecisions(tabs, index) else null
  val text = findsText ?: keptText ?: pillText(
    place, countedPosition(tabs, index), countedItems(tabs), feed, upNext.card, aheadName, decisions = decisions, run = runKind,
  )
  val endLine = blockEndLine(place, aheadName)
  val fullPill = !cardUp && !openOnly
  val keptAfter = (place as? BlockPlace.Head)?.source?.takeIf { fullPill && swipesQueue }
  val pillTip = if (overPicture) null else rememberPillTip(
    tips,
    swipeDue = ahead is Ahead.Item && fullPill && swipesQueue,
    twoTabs = tabs.size >= 2,
    reopenable = closed.isNotEmpty() && pending == null && !openOnly,
    barOut = { (hideOnScroll?.hiddenBy ?: 0f) == 0f },
    openedInFront = current?.takeIf { fullPill && it.top?.isLone != true && controller.openerOf(it) != null }?.title,
    keptAfter = keptAfter,
    keptBooks = keptAfter != null && current != null && keptBooks(tabs, current),
    onOpenQueue = onOpenQueue,
  )
  val undoPresses = remember { MutableInteractionSource() }
  PillRow(
    modifier.fillMaxWidth().height(BottomRowLayout.Height + bottom),
    bottom = bottom,
    start = {
      if (buttons && closable) {
        CloseCorner(canReopen = closed.isNotEmpty(), onClose = close, onReopen = undo::reopenLatest, modifier = slide, closeLabel = closeName)
      }
    },
    undo = undo@{
      if (openOnly) return@undo
      UndoChip(
        visibleState = rememberUndoShown(pending, undo),
        onUndo = undo::undo,
        label = chipWords?.undo ?: UNDO_CLOSE_LABEL,
        interactionSource = undoPresses,
        modifier = slide,
      )
      LiveAnnouncement(undoWords?.let { "${it.spoken}. Undo available" } ?: "")
      LiveAnnouncement(doneWith?.let { "Done with $it? Close available" } ?: "")
    },
    pill = {
      val line: (@Composable () -> Unit)? = when {
        pillTip != null -> null
        doneWith != null -> { { DoneWithChip(doneWith, onClose = { takeDoneWith() }, label = doneWithName ?: doneWithLabel(doneWith)) } }
        endLine != null -> { { BlockEndLine(endLine) } }
        else -> null
      }
      PillWithLine(line = line, modifier = Modifier.hidesOnScroll(hideOnScroll)) {
        CountPill(
          text = text,
          tap = pillTap,
          canReopen = closed.isNotEmpty() && !openOnly,
          onReopen = undo::reopenLatest,
          actions = actions,
          arrows = buttons,
          onPrevious = { goPrevious() },
          onNext = { goNext() },
          previousLabel = if (cardUp) backToLabel(current?.title ?: PREVIOUS_ITEM) else previous?.let { "$PREVIOUS_ITEM: ${it.title}" } ?: PREVIOUS_ITEM,
          nextLabel = aheadLabel(ahead),
          previousEnabled = cardUp || previous != null,
          nextEnabled = ahead != Ahead.Nothing,
          modifier = Modifier.testTag(COUNT_PILL),
          tip = { pillTip?.let { TipAnchor(it.tip, tips, it.shown, Modifier.matchParentSize(), text = it.text, action = it.action) } },
        )
      }
    },
  )
}

internal fun queueCloseLabel(tab: TabState, keptStay: Int): String = closeLabel(tab, keptStay)

internal fun aheadAction(ahead: Ahead, upNext: UpNextState): CustomAccessibilityAction? = when (ahead) {
  is Ahead.Item -> CustomAccessibilityAction(NEXT_ITEM) { upNext.forward() }
  is Ahead.Pane -> CustomAccessibilityAction(ahead.label) { upNext.forward(); true }
  Ahead.Nothing -> null
}

internal fun aheadLabel(ahead: Ahead): String = when (ahead) {
  is Ahead.Item -> "$NEXT_ITEM: ${ahead.title}"
  is Ahead.Pane -> ahead.label
  Ahead.Nothing -> NEXT_ITEM
}

private class PillTip(val tip: Tip, val shown: Boolean, val text: String = tip.text, val action: TipAction? = null)

@Composable
private fun rememberPillTip(
  tips: Tips,
  swipeDue: Boolean,
  twoTabs: Boolean,
  reopenable: Boolean,
  barOut: () -> Boolean,
  openedInFront: String? = null,
  keptAfter: String? = null,
  keptBooks: Boolean = false,
  onOpenQueue: () -> Unit = {},
): PillTip? {
  val currentTip = tips.current
  val listDue = twoTabs && tips.secondTabAppeared
  val openedDue = openedInFront != null
  val keptDue = keptAfter != null
  LaunchedEffect(openedDue, keptDue, swipeDue, listDue, twoTabs, reopenable, currentTip, tips.resting, tips.visitSpent) {
    if (currentTip == Tip.OpenedInFront && !openedDue) tips.dismissed(Tip.OpenedInFront)
    if (currentTip == Tip.KeptWaits && !keptDue) tips.dismissed(Tip.KeptWaits)
    if (currentTip == Tip.QueueSwipe && !swipeDue) tips.dismissed(Tip.QueueSwipe)
    if (currentTip == Tip.TabList && !twoTabs) tips.dismissed(Tip.TabList)
    if (currentTip == Tip.ReopenHold && !reopenable) tips.dismissed(Tip.ReopenHold)
    if (currentTip != null || tips.resting) return@LaunchedEffect
    if (openedDue && tips.offer(Tip.OpenedInFront)) return@LaunchedEffect
    if (keptDue && tips.offer(Tip.KeptWaits)) return@LaunchedEffect
    if (swipeDue && tips.offer(Tip.QueueSwipe)) return@LaunchedEffect
    if (listDue && tips.offer(Tip.TabList)) return@LaunchedEffect
    if (reopenable) tips.offer(Tip.ReopenHold)
  }
  val currentBarOut by rememberUpdatedState(barOut)
  val out by remember { derivedStateOf { currentBarOut() } }
  return when {
    currentTip == Tip.OpenedInFront && openedInFront != null ->
      PillTip(Tip.OpenedInFront, out, openedInFrontText(openedInFront), TipAction(SHOW_QUEUE, onOpenQueue))
    currentTip == Tip.KeptWaits && keptAfter != null -> PillTip(Tip.KeptWaits, out, keptWaitsText(keptAfter, keptBooks))
    currentTip == Tip.QueueSwipe && swipeDue -> PillTip(Tip.QueueSwipe, out)
    currentTip == Tip.TabList && twoTabs -> PillTip(Tip.TabList, out)
    currentTip == Tip.ReopenHold && reopenable -> PillTip(Tip.ReopenHold, out)
    else -> null
  }
}

val LocalOpenTabList = staticCompositionLocalOf<() -> Unit> { {} }

internal const val COUNT_PILL = "Count pill"

private fun keptBooks(tabs: List<TabState>, head: TabState): Boolean =
  tabs.firstOrNull { it.block == head.id && it.kind.isKept }?.kind == QueueKind.KeptBook

internal const val SHOW_QUEUE = "Show queue"

internal val BACK_TO_SORT = backToLabel(SORT)

package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.queue.QueueKind

internal class QueueBlock(val head: TabId, val headIndex: Int?, val kept: List<Int>, val closedSource: List<String>?) {
  val indices: List<Int> get() = listOfNotNull(headIndex) + kept
}

internal fun queueBlocks(tabs: List<TabState>): List<QueueBlock> {
  val blocks = mutableListOf<QueueBlock>()
  var head: TabId? = null
  var headIndex: Int? = null
  var closedSource: List<String>? = null
  val kept = mutableListOf<Int>()
  fun flush() {
    head?.let { blocks += QueueBlock(it, headIndex, kept.toList(), closedSource) }
    kept.clear()
  }
  for ((index, tab) in tabs.withIndex()) {
    val previous = tabs.getOrNull(index - 1)
    val closed = tab.closedSource
    val member = if (closed != null) {
      previous?.closedSource != null && previous.block == tab.block
    } else {
      previous != null && tab.block != null && previous.head == tab.block
    }
    if (member) {
      kept += index
      continue
    }
    flush()
    if (closed != null) {
      head = tab.head
      headIndex = null
      closedSource = closed
      kept += index
    } else {
      head = tab.id
      headIndex = index
      closedSource = null
    }
  }
  flush()
  return blocks
}

internal data class BlockProgress(
  val title: String,
  val position: Int?,
  val kept: Int,
  val left: Int,
  val closed: Boolean,
  val feed: Boolean,
)

internal fun blockProgress(tabs: List<TabState>, id: TabId, seen: (TabState) -> Boolean): BlockProgress? {
  val index = tabs.indexOfFirst { it.id == id }
  if (index < 0) return null
  val block = queueBlocks(tabs).firstOrNull { index in it.indices } ?: return null
  val kept = block.kept.filter { tabs[it].kind.isKept }
  if (kept.isEmpty()) return null
  val head = block.headIndex?.let(tabs::get)
  val first = tabs[block.kept.first()]
  val title = block.closedSource?.let { closedSourceName(it, first.tagNames) } ?: head?.title ?: return null
  return BlockProgress(
    title = title,
    position = kept.indexOf(index).takeIf { it >= 0 }?.plus(1),
    kept = kept.size,
    left = kept.count { !seen(tabs[it]) },
    closed = block.closedSource != null,
    feed = head?.kind == QueueKind.Feed,
  )
}

internal fun blockChipText(progress: BlockProgress): String = when (val position = progress.position) {
  null -> "${progress.title} · ${leftText(progress.left)}"
  else -> "$position / ${progress.kept} · from ${progress.title}"
}

internal fun blockChipSpoken(progress: BlockProgress): String = when (val position = progress.position) {
  null -> "${progress.title}, ${progress.kept} kept, ${leftText(progress.left)}"
  else -> "Kept item $position of ${progress.kept} from ${progress.title}, ${leftText(progress.left)}"
}

internal fun leftText(left: Int): String = if (left == 0) "all seen" else "$left left"

internal fun keptLeft(tabs: List<TabState>, seen: (TabState) -> Boolean): Int = tabs.count { it.kind.isKept && !seen(it) }

internal fun blockIds(tabs: List<TabState>, head: TabId): List<TabId> {
  val block = queueBlocks(tabs).firstOrNull { it.head == head } ?: return emptyList()
  return block.indices.map { tabs[it].id }
}

internal fun TabsController.closeBlock(id: TabId): Pair<ClosedTab, String>? {
  val all = tabs.value
  val head = blockHeadOf(all, id) ?: id
  val ids = blockIds(all, head)
  val title = all.firstOrNull { it.id == head }?.title
    ?: all.firstOrNull { it.id in ids }?.let { kept -> kept.closedSource?.let { closedSourceName(it, kept.tagNames) } }
    ?: return null
  return closeAll(ids)?.let { it to title }
}

internal data class StripBlock(val block: Int, val underlined: Boolean, val startsBlock: Boolean, val joinsNext: Boolean)

internal fun stripBlocks(tabs: List<TabState>): List<StripBlock> {
  val out = arrayOfNulls<StripBlock>(tabs.size)
  var ordinal = 0
  var previousUnderlined = false
  for (block in queueBlocks(tabs)) {
    val underlined = block.kept.isNotEmpty()
    val indices = block.indices
    indices.forEachIndexed { at, index ->
      out[index] = StripBlock(
        block = ordinal,
        underlined = underlined,
        startsBlock = at == 0 && (underlined || previousUnderlined),
        joinsNext = underlined && at < indices.lastIndex,
      )
    }
    if (underlined) ordinal++
    previousUnderlined = underlined
  }
  return out.map { it ?: StripBlock(0, underlined = false, startsBlock = false, joinsNext = false) }
}

internal fun blockHeadOf(tabs: List<TabState>, id: TabId): TabId? {
  val index = tabs.indexOfFirst { it.id == id }
  if (index < 0) return null
  return queueBlocks(tabs).firstOrNull { index in it.indices }?.head
}

internal sealed interface LeavingBlock {
  data class Done(val head: TabId, val title: String, val seen: Int) : LeavingBlock
  data object QueueDone : LeavingBlock
  data object None : LeavingBlock
}

internal fun leavingBlock(
  tabs: List<TabState>,
  left: TabId?,
  hadKept: Boolean,
  leftBefore: Int,
  now: TabId,
  offered: Set<TabId>,
  seen: (TabState) -> Boolean,
): LeavingBlock {
  if (leftBefore > 0 && keptLeft(tabs, seen) == 0) return LeavingBlock.QueueDone
  if (left == null || blockHeadOf(tabs, now) == left) return LeavingBlock.None
  if (!hadKept || left in offered) return LeavingBlock.None
  val block = queueBlocks(tabs).firstOrNull { it.head == left && it.headIndex != null } ?: return LeavingBlock.None
  val head = tabs[block.headIndex!!]
  if (head.kind != QueueKind.Source) return LeavingBlock.None
  val kept = block.kept.map(tabs::get).filter { it.kind.isKept }
  if (kept.any { !seen(it) }) return LeavingBlock.None
  return LeavingBlock.Done(head.id, head.title, kept.size)
}

package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.isBooks
import dev.marzahn.sankaku.features.grid.Rating
import dev.marzahn.sankaku.queue.QueueKind
import kotlin.time.Instant

internal sealed interface QueueRow {
  class Item(
    val tab: TabState,
    val position: Int,
    val indented: Boolean,
    val adult: Boolean,
    val from: String? = null,
    val line: BlockLine? = null,
    val fold: Fold? = null,
    val lastKept: Instant? = null,
    val number: Int? = null,
    val headsBlock: Boolean = !indented,
  ) : QueueRow {
    val shownNumber: Int? get() = number.takeIf { headsBlock }

    fun copy(
      indented: Boolean = this.indented,
      from: String? = this.from,
      line: BlockLine? = this.line,
      fold: Fold? = this.fold,
      lastKept: Instant? = this.lastKept,
    ) = Item(tab, position, indented, adult, from, line, fold, lastKept, number, headsBlock)
  }

  class ClosedBlock(
    val head: TabId,
    val title: String,
    val count: Int,
    val fold: Fold? = null,
    val lastKept: Instant? = null,
    val number: Int? = null,
  ) : QueueRow {
    fun copy(fold: Fold? = this.fold, lastKept: Instant? = this.lastKept) = ClosedBlock(head, title, count, fold, lastKept, number)
  }

  class Inline(val closed: ClosedTab) : QueueRow

  class HiddenByGOnly(val count: Int) : QueueRow
}

internal enum class BlockLine { Head, Through, End }

internal class Fold(val head: TabId, val kept: Int, val seen: Int, val pictures: List<Route>, val open: Boolean, val later: Int = 0) {
  val notSeen: Int get() = (kept - later - seen).coerceAtLeast(0)

  val parts: List<String>
    get() = listOfNotNull(
      later.takeIf { it > 0 }?.let { "${grouped(it)} later" },
      seen.takeIf { it > 0 }?.let { "${grouped(it)} seen" },
      notSeen.takeIf { it > 0 }?.let { "${grouped(it)} not seen" },
    )

  val label: String get() = parts.joinToString(" · ")
}

internal enum class QueueFilter {
  All,
  Kept,

  Later,
  New,
  Started,
  ;

  fun lists(head: TabState): Boolean = when (this) {
    All, Kept, Later -> true
    New -> isNewSource(head)
    Started -> isStartedSource(head)
  }
}

internal fun lastKeptByBlock(tabs: List<TabState>): Map<TabId, Instant> {
  val out = mutableMapOf<TabId, Instant>()
  for (tab in tabs) {
    val block = tab.block ?: continue
    if (block == tab.id || !tab.kind.isKept) continue
    val at = tab.keptAt ?: Instant.fromEpochSeconds(0)
    out[block] = out[block]?.let { maxOf(it, at) } ?: at
  }
  return out
}

internal fun isStartedSource(tab: TabState): Boolean = hasPreview(tab.top) && !isNewSource(tab)

internal fun queueRows(
  tabs: List<TabState>,
  closed: List<ClosedTab>,
  inline: ClosedTab?,
  query: String?,
  gOnly: Boolean,
  filter: QueueFilter = QueueFilter.All,
  open: Set<TabId>? = null,
  seen: (TabState) -> Boolean = { false },
  post: (TabState) -> TabPost?,
): List<QueueRow> {
  val inlineAt = inline?.let { closed.first { c -> c == it }.index } ?: 0
  val main = mutableListOf<QueueRow>()
  val lastKept = if (filter == QueueFilter.Kept) {
    lastKeptByBlock(tabs.filterNot { gOnly && it.kind.isKept && isAdult(it, post(it)) })
  } else {
    emptyMap()
  }
  val laterBlocks = if (filter == QueueFilter.Later) {
    laterPosts(tabs, gOnly, post).mapTo(mutableSetOf()) { it.block ?: it.id }
  } else {
    emptySet()
  }
  var unplaced = inline
  var hidden = 0
  var counted = 0
  val numbers = queueNumbers(tabs)
  var headListed = false
  var closedRow: QueueRow.ClosedBlock? = null
  var closedRowListed = false
  var headName: String? = null
  var blockHead: TabState? = null
  for ((index, tab) in tabs.withIndex()) {
    val position = counted
    if (tab.kind != QueueKind.Feed) counted++
    val closedSource = tab.closedSource
    val member: Boolean
    if (closedSource != null) {
      member = true
      val previous = tabs.getOrNull(index - 1)
      if (previous?.closedSource == null || previous.block != tab.block) {
        val count = tabs.drop(index).takeWhile { it.closedSource != null && it.block == tab.block }.size
        closedRow = QueueRow.ClosedBlock(tab.head, closedBlockTitle(closedSource, tab.tagNames), count, number = numbers[index])
        closedRowListed = false
      }
    } else {
      member = tab.block != null && index > 0 && tabs[index - 1].head == tab.block
      if (!member) headListed = false
    }
    if (!member) blockHead = tab
    val number = numbers[index]
    val known = post(tab)
    val adult = isAdult(tab, known)
    if (gOnly && adult) {
      hidden++
      continue
    }
    if (filter == QueueFilter.Kept) {
      val block = if (closedSource != null) tab.block else blockHead?.id
      if (block == null || block !in lastKept) continue
    } else if (filter == QueueFilter.Later) {
      val block = if (closedSource != null) tab.block else blockHead?.id
      val laterHere = tab.kind.isKept && tab.later
      if ((block == null || block !in laterBlocks) && !laterHere) continue
      if (member && !laterHere) continue
    } else if (filter != QueueFilter.All) {
      val head = if (closedSource != null) null else blockHead
      if (head == null || !filter.lists(head)) continue
    }
    if (query != null && !matchesFind(query, tab, known)) continue
    val indented = closedSource != null || (member && headListed)
    val from = when {
      closedSource != null -> closedSourceName(closedSource, tab.tagNames)
      indented -> headName
      member && query != null -> blockHead?.title
      else -> null
    }
    val item = QueueRow.Item(tab, position, indented = indented, adult = adult, from = from, number = number, headsBlock = !member)
    if (!member) {
      headListed = true
      headName = tab.title
    }
    if (unplaced != null && index >= inlineAt) {
      main += QueueRow.Inline(unplaced)
      unplaced = null
    }
    if (closedSource != null && !closedRowListed) {
      closedRow?.let { main += it }
      closedRowListed = true
    }
    main += item
  }
  if (unplaced != null) main += QueueRow.Inline(unplaced)
  val listed = when {
    filter == QueueFilter.Kept -> newestKeptFirst(main, lastKept)
    query != null -> sourcesFirst(main)
    else -> main
  }
  if (hidden > 0) listed += QueueRow.HiddenByGOnly(hidden)
  return withBlockLines(if (open == null) listed else folded(listed, open, seen))
}

internal fun queueNumbers(tabs: List<TabState>): List<Int?> {
  var blocks = 0
  var feedBlock = false
  return tabs.mapIndexed { index, tab ->
    val previous = tabs.getOrNull(index - 1)
    when {
      tab.closedSource != null -> {
        if (previous?.closedSource == null || previous.block != tab.block) blocks++
        feedBlock = false
        blocks
      }
      tab.block != null && previous?.head == tab.block -> blocks.takeUnless { feedBlock }
      else -> {
        feedBlock = tab.kind == QueueKind.Feed
        if (!feedBlock) blocks++
        blocks.takeUnless { feedBlock }
      }
    }
  }
}

internal fun numberedBlocks(tabs: List<TabState>): Int = queueNumbers(tabs).lastOrNull { it != null } ?: 0

private fun newestKeptFirst(rows: List<QueueRow>, lastKept: Map<TabId, Instant>): MutableList<QueueRow> {
  val lead = mutableListOf<QueueRow>()
  val blocks = mutableListOf<Pair<Instant, MutableList<QueueRow>>>()
  for (row in rows) {
    val head = when (row) {
      is QueueRow.ClosedBlock -> row.head
      is QueueRow.Item -> row.tab.head.takeUnless { row.indented }
      else -> null
    }
    if (head == null) {
      (blocks.lastOrNull()?.second ?: lead) += row
      continue
    }
    val at = lastKept[head] ?: Instant.fromEpochSeconds(0)
    val carried = when (row) {
      is QueueRow.ClosedBlock -> row.copy(lastKept = at)
      is QueueRow.Item -> row.copy(lastKept = at)
      else -> row
    }
    blocks += at to mutableListOf(carried)
  }
  return (lead + blocks.sortedByDescending { it.first }.flatMap { it.second }).toMutableList()
}

private fun sourcesFirst(rows: List<QueueRow>): MutableList<QueueRow> {
  val (sources, rest) = rows.partition { it is QueueRow.Item && !it.indented && !it.tab.kind.isKept }
  val headsUp = sources.mapTo(mutableSetOf()) { (it as QueueRow.Item).tab.id }
  val after = rest.map { row ->
    if (row is QueueRow.Item && row.indented && row.tab.closedSource == null && row.tab.block in headsUp) row.copy(indented = false) else row
  }
  return (sources + after).toMutableList()
}

private fun folded(rows: List<QueueRow>, open: Set<TabId>, seen: (TabState) -> Boolean): List<QueueRow> {
  val out = ArrayList<QueueRow>(rows.size)
  var at = 0
  while (at < rows.size) {
    val row = rows[at]
    val head = when (row) {
      is QueueRow.ClosedBlock -> row.head
      is QueueRow.Item -> row.tab.id.takeUnless { row.indented }
      else -> null
    }
    if (head == null) {
      out += row
      at++
      continue
    }
    var end = at + 1
    val kept = mutableListOf<QueueRow.Item>()
    while (end < rows.size) {
      val next = rows[end]
      if (next is QueueRow.Item && next.indented) kept += next else if (next !is QueueRow.Inline) break
      end++
    }
    if (kept.isEmpty()) {
      out += row
      at++
      continue
    }
    val fold = Fold(
      head,
      kept = kept.size,
      seen = kept.count { it.tab.kind.isKept && !it.tab.later && seen(it.tab) },
      pictures = kept.take(FOLD_PICTURES).mapNotNull { it.tab.top },
      open = head in open,
      later = kept.count { it.tab.kind.isKept && it.tab.later },
    )
    out += when (row) {
      is QueueRow.ClosedBlock -> row.copy(fold = fold)
      is QueueRow.Item -> row.copy(fold = fold)
      else -> row
    }
    val under = rows.subList(at + 1, end)
    out += if (fold.open) under else under.filterIsInstance<QueueRow.Inline>()
    at = end
  }
  return out
}

private const val FOLD_PICTURES = 3

private fun withBlockLines(rows: List<QueueRow>): List<QueueRow> {
  val kept = rows.map { it is QueueRow.Item && it.indented }
  val listed = rows.indices.filter { rows[it] !is QueueRow.Inline }
  val nextKept = BooleanArray(rows.size)
  listed.zipWithNext { at, next -> nextKept[at] = kept[next] }
  return rows.mapIndexed { index, row ->
    if (row !is QueueRow.Item) return@mapIndexed row
    val line = when {
      row.indented -> if (nextKept[index]) BlockLine.Through else BlockLine.End
      nextKept[index] -> BlockLine.Head
      else -> null
    }
    if (line == null) row else row.copy(line = line)
  }
}

internal class ClosedRow(val closed: ClosedTab, val shown: ClosedTab.Member, val adult: Boolean)

internal data class ClosedRows(val rows: List<ClosedRow>, val hidden: Int)

internal fun closedRows(closed: List<ClosedTab>, gOnly: Boolean, post: (Route.Post) -> TabPost?): ClosedRows {
  var hidden = 0
  val rows = closed.asReversed().mapNotNull { entry ->
    val adult = { member: ClosedTab.Member -> isAdult(member.top, post) }
    val shown = if (gOnly) entry.members.firstOrNull { !adult(it) } else entry.members.first()
    if (shown == null) {
      hidden++
      null
    } else {
      ClosedRow(entry, shown, adult(shown))
    }
  }
  return ClosedRows(rows, hidden)
}

internal fun closedPosts(closed: List<ClosedTab>): Set<String> =
  closed.flatMapTo(mutableSetOf()) { entry -> entry.members.mapNotNull { (it.top as? Route.Post)?.id } }

internal fun isAdult(tab: TabState, post: TabPost?): Boolean = isAdult(tab.top) { post }

internal fun isAdult(top: Route?, post: (Route.Post) -> TabPost?): Boolean = when (top) {
  is Route.Post -> postRating(top, post(top)) != Rating.G
  is Route.Grid -> Rating.G !in searchRatings(top.tags)
  is Route.Books -> Rating.G !in searchRatings(top.tags)
  is Route.BookSeries -> Rating.G !in searchRatings(top.tags)
  is Route.Book -> Rating.ofCode(top.rating) != Rating.G
  null -> false
}

internal fun postRating(tab: TabState, post: TabPost?): Rating? = (tab.top as? Route.Post)?.let { postRating(it, post) }

private fun postRating(top: Route.Post, post: TabPost?): Rating? = Rating.ofCode(top.rating ?: post?.rating)

internal fun searchRatings(tags: List<String>): Set<Rating> =
  tags.fold(Rating.entries.toSet()) { ratings, tag -> Rating.of(tag)?.let(ratings::intersect) ?: ratings }

internal fun sourceLine(tab: TabState, progress: SourceProgress?, current: Boolean, rating: Rating?): String {
  val parts = mutableListOf<String>()
  val tags = (tab.top as? Route.Grid)?.tags.orEmpty()
  parts += orderLead(tags)
  parts += notesButOrder(tags)
  val position = progress?.position
  val size = progress?.size
  when {
    position != null && size != null -> parts += "${grouped(position)} of ${sizeWords(size, alone = false)}"
    position != null || tab.place != null -> {
      size?.let { parts += sizeWords(it, alone = true) }
      parts += "place kept"
    }
    size != null -> parts += sizeWords(size, alone = true)
    else -> tab.mosaic?.posts?.takeIf { it > 0 }?.let { parts += "${grouped(it)} posts" }
  }
  originText(tab)?.let { parts += it }
  if (rating != null && rating != Rating.G) parts += rating.badge
  if (current) parts += "current"
  return parts.joinToString(" · ")
}

internal fun originText(tab: TabState): String? =
  tab.refinedFrom?.let { "from $it" } ?: tab.foundOn?.takeIf { tab.backStack.size == 1 }?.let { foundRowText(it, tab.tagNames) }

internal fun sizeWords(size: SourceSize, alone: Boolean): String {
  val (count, number) = when (size) {
    is SourceSize.About -> size.posts to "about ${grouped(size.posts)}"
    is SourceSize.Counted -> size.posts to "${grouped(size.posts)}${if (size.more) "+" else ""}"
  }
  val exact = size is SourceSize.Counted && !size.more
  return when {
    !alone -> number
    exact && count == 0 -> "no posts"
    exact && count == 1 -> "1 post"
    else -> "$number posts"
  }
}

internal fun progressFraction(progress: SourceProgress?): Float? {
  val position = progress?.position ?: return null
  val total = when (val size = progress.size ?: return null) {
    is SourceSize.About -> size.posts
    is SourceSize.Counted -> if (size.more) return null else size.posts
  }
  if (total <= 0) return null
  return (position.toFloat() / total).coerceIn(0f, 1f)
}

internal fun grouped(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

internal fun queueSubtitle(tab: TabState, current: Boolean, seen: Boolean, rating: Rating?, from: String? = null): String {
  val parts = mutableListOf<String>()
  when (tab.kind) {
    QueueKind.Source -> {
      parts += if (tab.top?.isBooks == true) BOOKS_TITLE else "Grid"
      (tab.top as? Route.Grid)?.let { parts += filterNotes(it.tags) }
      val loaded = tab.mosaic?.posts?.takeIf { it > 0 }
      if (tab.place != null) parts += "place kept" else if (loaded != null) parts += "$loaded posts"
      originText(tab)?.let { parts += it }
    }
    QueueKind.Feed -> parts += "Feed"
    QueueKind.KeptPost, QueueKind.KeptBook -> {
      val kept = if (tab.kind == QueueKind.KeptBook) "Kept book" else "Kept"
      parts += if (from != null) "$kept from $from" else kept
      if (tab.later) parts += "later" else if (seen) parts += "seen"
    }
    QueueKind.Other -> parts += "Item"
  }
  if (rating != null && rating != Rating.G) parts += rating.badge
  if (current) parts += "current"
  return parts.joinToString(" · ")
}

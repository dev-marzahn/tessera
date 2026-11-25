package dev.marzahn.sankaku.tabs

import kotlin.time.Instant
import kotlinx.datetime.TimeZone

internal fun keptRunRows(tabs: List<TabState>, gOnly: Boolean, post: (TabState) -> TabPost?): List<QueueRow.Item> =
  queueRows(tabs, emptyList(), null, null, gOnly, QueueFilter.Kept, null, post = post)
    .filterIsInstance<QueueRow.Item>().filter { it.tab.kind.isKept }

internal fun keptRunStart(posts: List<TabState>, seen: (TabState) -> Boolean): TabState =
  posts.firstOrNull { !seen(it) && !it.later } ?: posts.first()

internal class EndLeftovers(
  val kept: List<TabState>,
  val keptStart: TabState?,
  val keptUnseen: Int,
  val keptWhere: String?,
  val newSources: Int,
  val foundToday: Int,
  val later: List<TabState> = emptyList(),
  val laterWhere: String? = null,
) {
  val none: Boolean get() = keptUnseen == 0 && later.isEmpty() && newSources == 0

  companion object {
    val NONE = EndLeftovers(emptyList(), null, 0, null, 0, 0)
  }
}

internal fun endLeftovers(
  tabs: List<TabState>,
  gOnly: Boolean,
  post: (TabState) -> TabPost?,
  seen: (TabState) -> Boolean,
  foundToday: Int,
  now: Instant,
  zone: TimeZone,
): EndLeftovers {
  val rows = keptRunRows(tabs, gOnly, post)
  val unseen = rows.filterNot { seen(it.tab) || it.tab.later }
  val kept = rows.map { it.tab }
  return EndLeftovers(
    kept = kept,
    keptStart = kept.takeIf { it.isNotEmpty() }?.let { keptRunStart(it, seen) },
    keptUnseen = unseen.size,
    keptWhere = keptWhere(unseen, tabs, now, zone),
    newSources = newSources(tabs, gOnly).size,
    foundToday = foundToday,
    later = laterPosts(tabs, gOnly, post),
    laterWhere = keptWhere(rows.filter { it.tab.later }, tabs, now, zone, withTime = false),
  )
}

private fun keptWhere(unseen: List<QueueRow.Item>, tabs: List<TabState>, now: Instant, zone: TimeZone, withTime: Boolean = true): String? {
  if (unseen.isEmpty()) return null
  val blocks = unseen.groupBy { it.tab.head }
  val names = blocks.map { (head, rows) -> rows.first().from ?: tabs.firstOrNull { it.id == head }?.title ?: rows.first().tab.title }
  if (blocks.size == 1) {
    val newest = blocks.values.single().mapNotNull { it.tab.keptAt }.maxOrNull()?.takeIf { withTime }
    return listOfNotNull(names.single(), newest?.let { keptWords(it, now, zone) }).joinToString(" · ")
  }
  val shown = names.take(KEPT_WHERE_NAMED).joinToString(", ")
  return if (names.size > KEPT_WHERE_NAMED) "$shown +${names.size - KEPT_WHERE_NAMED}" else shown
}

private const val KEPT_WHERE_NAMED = 2

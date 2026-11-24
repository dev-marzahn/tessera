package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.TodayCounts
import kotlin.time.Instant

internal fun keptListed(tabs: List<TabState>, gOnly: Boolean, post: (TabState) -> TabPost?): List<TabState> =
  keptRunRows(tabs, gOnly, post).map { it.tab }

internal fun keptInBlock(tabs: List<TabState>, head: TabId, gOnly: Boolean, post: (TabState) -> TabPost?): Int =
  tabs.count { it.block == head && it.id != head && it.kind.isKept && !(gOnly && isAdult(it, post(it))) }

internal fun goneThroughToday(tabs: List<TabState>, counts: TodayCounts, seen: SeenStore?, startOfToday: Instant): Int {
  val seenToday = if (seen == null) 0 else tabs.count { it.kind.isKept && it.seenSinceKept(seen, notBefore = startOfToday) }
  return counts.dropped + seenToday
}

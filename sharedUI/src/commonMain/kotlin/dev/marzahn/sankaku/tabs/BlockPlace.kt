package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.ui.components.CountPillText
import dev.marzahn.sankaku.ui.components.ShortSource

sealed interface BlockPlace {
  val source: String

  val short: ShortSource?

  data class Kept(
    override val source: String,
    val position: Int,
    val count: Int,
    val kind: QueueKind,
    val closed: Boolean = false,
    override val short: ShortSource? = null,
  ) : BlockPlace {
    val last: Boolean get() = position == count

    val spoken: String get() = "${if (kind == QueueKind.KeptBook) "kept book" else "kept post"} $position of $count"

    val closedSuffix: String get() = if (closed) ", closed" else ""
  }

  data class Head(override val source: String, val kept: Int, override val short: ShortSource? = null) : BlockPlace {
    val follow: String get() = "$kept kept ${if (kept == 1) "item follows" else "items follow"}"
  }
}

internal fun blockPlace(tabs: List<TabState>, index: Int): BlockPlace? {
  val tab = tabs.getOrNull(index) ?: return null
  if (tab.kind.isKept) {
    val head = tab.block ?: return null
    val members = tabs.filter { it.block == head && it.kind.isKept }
    val position = members.indexOf(tab) + 1
    val source = tabs.firstOrNull { it.id == head }
    val closed = tab.closedSource
    return when {
      source != null && headsKeptPosts(source.kind) ->
        BlockPlace.Kept(source.title, position, members.size, tab.kind, short = source.shortTitle)
      source == null && closed != null -> {
        val route = searchSource(closed)
        val short = shortTitle(route, tab.tagNames)
        BlockPlace.Kept(tabTitle(route, names = tab.tagNames), position, members.size, tab.kind, closed = true, short = short)
      }
      else -> null
    }
  }
  if (!headsKeptPosts(tab.kind)) return null
  val kept = tabs.count { it.block == tab.id && it.kind.isKept }
  return if (kept > 0) BlockPlace.Head(tab.title, kept, tab.shortTitle) else null
}

internal fun blockEndLine(place: BlockPlace?, next: String?): String? {
  if (place !is BlockPlace.Kept || !place.last || next == null) return null
  return nextLine(next)
}

internal fun nextLine(next: String): String = "Next: $next"

internal fun blockEndSpoken(place: BlockPlace?, next: String?): String =
  if (place is BlockPlace.Kept && place.last && next != null) ", last, next $next" else ""

internal fun leavingLabel(place: BlockPlace?): String? = place?.let { "Leaving ${it.source}" }

internal const val KEPT_PILL = "Kept"

internal fun pillText(
  place: BlockPlace?,
  position: Int,
  count: Int,
  feed: String?,
  card: UpNextCard?,
  next: String? = null,
  decisions: KeptProgress? = null,
  run: KeptRunKind? = null,
): CountPillText {
  if (card == UpNextCard.KeptEnd) {
    val kind = run ?: KeptRunKind.Kept
    return CountPillText(kind.pill, "end", "${kind.spoken}, end")
  }
  if (card != null) return CountPillText("End", "$count", "Queue, end, after $count ${if (count == 1) "item" else "items"}")
  val where = if (feed != null) "after $position ${if (position == 1) "item" else "items"}" else "item $position of $count"
  return when (place) {
    is BlockPlace.Kept -> if (decisions != null) {
      val later = laterText(decisions)
      CountPillText(
        place.source,
        decidedText(decisions),
        "Queue, ${place.source}${place.closedSuffix}, ${decisions.decided} of ${decisions.total} kept posts gone through" +
          "${later?.let { ", $it" }.orEmpty()}, ${place.spoken}, $where${blockEndSpoken(place, next)}",
        later = later,
        progress = decisions.fraction,
        short = place.short,
      )
    } else {
      CountPillText(
        place.source,
        "${place.position} / ${place.count}",
        "Queue, ${place.source}${place.closedSuffix}, ${place.spoken}, $where${blockEndSpoken(place, next)}",
        short = place.short,
      )
    }
    is BlockPlace.Head ->
      CountPillText(place.source, "${place.kept} kept", "Queue, ${place.source}, ${place.follow}, $where", short = place.short)
    null -> if (feed != null) CountPillText(null, feed, "Queue, $feed, $where") else CountPillText(null, "$position / $count", "Queue, $where")
  }
}

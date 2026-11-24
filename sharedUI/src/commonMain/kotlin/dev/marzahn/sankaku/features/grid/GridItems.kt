package dev.marzahn.sankaku.features.grid

import androidx.compose.runtime.Immutable
import dev.marzahn.sankaku.queue.FeedHeading
import dev.marzahn.sankaku.queue.PlacedHeading

@Immutable
class GridItems(headings: List<PlacedHeading>) {
  private val placed: List<Pair<Int, PlacedHeading>> =
    headings.sortedBy { it.before }.mapIndexed { k, heading -> heading.before + k to heading }

  fun count(posts: Int): Int = posts + placed.count { (_, heading) -> heading.before < posts }

  fun itemOf(post: Int): Int = post + placed.count { (_, heading) -> heading.before <= post }

  fun postOf(item: Int): Int? {
    var before = 0
    for ((at, _) in placed) {
      if (at == item) return null
      if (at < item) before++
    }
    return item - before
  }

  fun headingAt(item: Int): PlacedHeading? = placed.firstOrNull { it.first == item }?.second

  fun itemOfHeading(which: (FeedHeading) -> Boolean): Int? = placed.firstOrNull { which(it.second.heading) }?.first

  override fun equals(other: Any?): Boolean = other is GridItems && other.placed == placed

  override fun hashCode(): Int = placed.hashCode()

  companion object {
    val None = GridItems(emptyList())
  }
}

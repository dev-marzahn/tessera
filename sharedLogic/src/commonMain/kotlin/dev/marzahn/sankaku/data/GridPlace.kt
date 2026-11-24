package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.api.dto.PostDto
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class GridPlace(
  val post: String,
  val cursor: String? = null,
  val page: Int = 1,
  val time: Long? = null,
  val index: Int? = null,
) {
  internal val pageCursor: PageCursor get() = PageCursor(cursor, page)
}

data class GridStart(val tags: List<String>, val cursor: PageCursor) {
  val midway: Boolean get() = cursor != PageCursor() || tags.any(::isDate)
}

internal const val DATE_PREFIX = "date:"

private fun isDate(tag: String) = tag.removePrefix("-").startsWith(DATE_PREFIX, ignoreCase = true)

data class TopPage(val list: Int, val cursor: PageCursor, val posts: List<PostDto>, val more: Boolean) {
  val isFirst: Boolean get() = cursor == PageCursor()
}

internal fun isForYou(tags: List<String>): Boolean = tags.any { Feed.of(it) == Feed.ForYou }

internal fun timeOrdered(tags: List<String>): Boolean =
  !isForYou(tags) && tags.none { isDate(it) || it.removePrefix("-").startsWith("order:", ignoreCase = true) }

internal fun datedSearch(tags: List<String>, time: Long): List<String> {
  val end = Instant.fromEpochSeconds((time.floorDiv(SECONDS_PER_HOUR) + 1) * SECONDS_PER_HOUR)
  return tags + "${DATE_PREFIX}$DATE_FLOOR..${end.toString().removeSuffix("Z")}"
}

private const val SECONDS_PER_HOUR = 3_600L

private const val DATE_FLOOR = "2000-01-01"

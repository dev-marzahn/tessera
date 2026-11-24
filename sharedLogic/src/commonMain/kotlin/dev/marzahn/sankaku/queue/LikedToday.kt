package dev.marzahn.sankaku.queue

object LikedToday {
  fun applies(tags: List<String>, me: String?): Boolean {
    if (me == null || tags.any(::isOrder)) return false
    val own = "fav:$me"
    return tags.any { it.trim().equals(own, ignoreCase = true) }
  }

  fun headings(tags: List<String>, me: String?, postIds: List<String>, liked: Int, isLiked: (String) -> Boolean): List<PlacedHeading> {
    if (liked == 0 || !applies(tags, me)) return emptyList()
    val last = postIds.indexOfLast(isLiked)
    if (last < 0) return emptyList()
    val count = liked.takeIf { SourceKey.of(tags)?.isOnlyFavourites == true }
    return listOf(PlacedHeading(0, FeedHeading.LikedToday(count)), PlacedHeading(last + 1, FeedHeading.Earlier))
  }
}

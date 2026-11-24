package dev.marzahn.sankaku.queue

import dev.marzahn.sankaku.api.Feed

object FeedItems {
  fun pinned(signedIn: Boolean, forYouInQueue: Boolean): List<Feed> = when {
    !signedIn -> emptyList()
    forYouInQueue -> listOf(Feed.Following, Feed.ForYou)
    else -> listOf(Feed.Following)
  }

  fun leaving(before: List<Feed>, after: List<Feed>): Set<Feed> =
    (before.filterNot { it in after } + listOfNotNull(Feed.Following.takeIf { it !in after })).toSet()

  fun added(pinned: List<Feed>, present: Collection<Feed>): List<Feed> = pinned.filter { it !in present }
}

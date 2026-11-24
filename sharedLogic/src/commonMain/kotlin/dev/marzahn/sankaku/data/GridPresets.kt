package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.Feed

object GridPresets {
  fun favourites(name: String): List<String> = listOf("fav:$name")

  val forYou: List<String> = listOf(Feed.ForYou.tag)

  val following: List<String> = listOf(Feed.Following.tag)

  val popular: List<String> = listOf("order:popularity")

  val top: List<String> = listOf("order:quality")
}

package dev.marzahn.sankaku.api

enum class Feed(val tag: String, val flag: String, val label: String) {
  ForYou("feed:for-you", "is_recommendation", "For you"),
  Following("feed:following", "is_following", "Following"),
  ;

  companion object {
    fun of(tag: String): Feed? = entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
  }
}

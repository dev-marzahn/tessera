package dev.marzahn.sankaku.queue

import kotlin.jvm.JvmInline

@JvmInline
value class SourceKey private constructor(val value: String) {
  val isOnlyFavourites: Boolean get() = value.startsWith(FAVOURITES) && SEPARATOR !in value

  companion object {
    private const val FAVOURITES = "fav:"

    private const val SEPARATOR = ' '

    fun of(tags: List<String>): SourceKey? = tags.asSequence()
      .map { it.trim().lowercase() }
      .filter { it.isNotEmpty() && !isOrder(it) && !isRating(it) }
      .distinct()
      .sorted()
      .joinToString(SEPARATOR.toString())
      .takeIf { it.isNotEmpty() }
      ?.let(::SourceKey)
  }
}

package dev.marzahn.sankaku.queue

import dev.marzahn.sankaku.api.Feed as ApiFeed
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class QueueKind {
  @SerialName("source")
  Source,

  @SerialName("feed")
  Feed,

  @SerialName("post")
  KeptPost,

  @SerialName("book")
  KeptBook,

  @SerialName("other")
  Other,
  ;

  val isKept: Boolean get() = this == KeptPost || this == KeptBook

  companion object {
    fun ofGrid(tags: List<String>): QueueKind = if (feedOf(tags) != null) Feed else Source

    fun feedOf(tags: List<String>): ApiFeed? {
      val words = tags.map { it.trim() }.filter { it.isNotEmpty() }
      val feeds = words.mapNotNull(ApiFeed::of).distinct()
      if (feeds.size != 1) return null
      return feeds.single().takeIf { words.all { ApiFeed.of(it) != null || isRating(it) } }
    }
  }
}

internal fun isRating(tag: String): Boolean = tag.removePrefix("-").startsWith("rating:", ignoreCase = true)

internal fun isOrder(tag: String): Boolean = tag.removePrefix("-").startsWith("order:", ignoreCase = true)

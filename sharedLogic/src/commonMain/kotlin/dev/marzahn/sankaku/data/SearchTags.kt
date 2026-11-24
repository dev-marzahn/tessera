package dev.marzahn.sankaku.data

object SearchTags {
  val FILTER_PREFIXES: List<String> = listOf("fav:", "rating:", "feed:", "order:", "threshold:")

  const val OPENED_TAG_ORDER: String = "order:popularity"

  fun isFilter(tag: String): Boolean {
    val body = tag.removePrefix("-")
    return FILTER_PREFIXES.any { body.startsWith(it, ignoreCase = true) }
  }

  fun filtersFirst(tags: List<String>): List<String> {
    val (filters, content) = tags.partition(::isFilter)
    return filters + content
  }

  fun searchForOpenedTag(tag: String): List<String> = filtersFirst(listOf(tag, OPENED_TAG_ORDER))
}

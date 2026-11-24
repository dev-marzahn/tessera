package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.data.SearchTags
import dev.marzahn.sankaku.api.dto.TagNames
import dev.marzahn.sankaku.features.grid.Favourites
import dev.marzahn.sankaku.features.grid.Filter

internal fun sourceTitle(tags: List<String>, names: TagNameLookup = NoTagNames): String {
  val (filters, content) = tags.partition(SearchTags::isFilter)
  if (content.isEmpty()) return filterOnlyName(filters) ?: "Browse"
  return sourceTitleTags(tags, names).joinToString(" ")
}

internal fun sourceTitleTags(tags: List<String>, names: TagNameLookup = NoTagNames): List<String> =
  tags.filterNot(SearchTags::isFilter).take(3).map { contentName(it, names) }

internal typealias TagNameLookup = (String) -> String?

internal val NoTagNames: TagNameLookup = { null }

private fun contentName(tag: String, names: TagNameLookup): String {
  childPostsLabel(tag)?.let { return it }
  dateRangeLabel(tag)?.let { return it }
  if (':' in tag) return tag
  val sign = if (tag.startsWith("-")) "-" else ""
  val key = tag.removePrefix(sign)
  return sign + (names(key.lowercase())?.takeIf { it.isNotBlank() } ?: TagNames.ofKey(key))
}

private fun childPostsLabel(tag: String): String? =
  if (tag.startsWith("parent:") && tag != "parent:none") "Child posts" else null

private fun filterOnlyName(filters: List<String>): String? {
  filters.firstNotNullOfOrNull { Feed.of(it)?.label }?.let { return it }
  filters.firstNotNullOfOrNull(Favourites::shortLabel)?.let { return it }
  order(filters)?.let { return it.title }
  return filters.firstNotNullOfOrNull { Filter.Threshold.optionFor(it)?.label }
}

internal fun filterNotes(tags: List<String>): List<String> {
  val (filters, content) = tags.partition(SearchTags::isFilter)
  if (content.isEmpty()) return emptyList()
  return listOfNotNull(
    filters.firstNotNullOfOrNull { Feed.of(it)?.label?.lowercase() },
    filters.firstNotNullOfOrNull(Favourites::shortLabel)?.lowercase(),
    order(filters)?.note,
  )
}

internal fun orderLead(tags: List<String>): String =
  order(tags.filter(SearchTags::isFilter))?.note?.replaceFirstChar { it.uppercase() } ?: "Newest"

internal fun notesButOrder(tags: List<String>): List<String> {
  val note = order(tags.filter(SearchTags::isFilter))?.note
  return filterNotes(tags).filter { it != note }
}

private class OrderName(val title: String, val note: String)

private val ORDER_NAMES = mapOf(
  "order:popularity" to OrderName("Popular", "popular"),
  "order:quality" to OrderName("Top", "top quality"),
  "order:score" to OrderName("Top scored", "top scored"),
  "order:favcount" to OrderName("Most favourited", "most favourited"),
  "order:random" to OrderName("Random", "random"),
)

private fun order(filters: List<String>): OrderName? =
  filters.firstNotNullOfOrNull { ORDER_NAMES[it.lowercase()] }

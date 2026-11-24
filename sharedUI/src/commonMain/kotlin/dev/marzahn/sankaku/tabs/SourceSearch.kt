package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.queue.SourceKey

internal fun sourceSearch(route: Route?): List<String>? = when (route) {
  is Route.Grid -> route.tags
  is Route.Books -> listOf(BOOKS_SEARCH) + route.tags
  is Route.BookSeries -> listOf(BOOKS_SEARCH) + route.tags
  else -> null
}

internal fun searchSource(search: List<String>): Route =
  if (search.firstOrNull() == BOOKS_SEARCH) Route.Books(search.drop(1)) else Route.Grid(search)

internal fun sourceKeyOf(route: Route?): SourceKey? = when (route) {
  is Route.Grid -> if (QueueKind.ofGrid(route.tags) == QueueKind.Source) SourceKey.of(route.tags) else null
  is Route.Books -> SourceKey.of(listOf(BOOKS_SEARCH) + route.tags)
  else -> null
}

internal const val BOOKS_SEARCH = ":books"

internal fun bookSeenId(bookId: String): String = "book:$bookId"

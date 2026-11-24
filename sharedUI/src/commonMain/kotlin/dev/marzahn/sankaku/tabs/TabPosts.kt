package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.PostStateStore
import dev.marzahn.sankaku.data.PostsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TabPost(val favourited: Boolean, val tags: List<String>, val rating: String? = null)

fun interface TabPosts {
  fun of(ids: Set<String>): Flow<Map<String, TabPost>>

  companion object {
    val None = TabPosts { flowOf(emptyMap()) }
  }
}

class CachedTabPosts(private val posts: PostsRepository, private val state: PostStateStore) : TabPosts {
  override fun of(ids: Set<String>): Flow<Map<String, TabPost>> = state.overrides.map { overrides ->
    val cached = posts.cachedPosts(ids)
    ids.mapNotNull { id ->
      val post = cached[id]
      val favourited = overrides[id]?.isFavourited ?: post?.isFavorited ?: return@mapNotNull null
      id to TabPost(favourited, post?.tagNames.orEmpty(), post?.rating)
    }.toMap()
  }
}

internal fun matchesFind(query: String, tab: TabState, post: TabPost?): Boolean {
  val words = findText(query).split(' ').filter { it.isNotEmpty() }
  if (words.isEmpty()) return true
  val tags = when (val top = tab.top) {
    is Route.Grid -> top.tags
    is Route.Post -> top.originTags + post?.tags.orEmpty()
    is Route.Books -> top.tags
    is Route.BookSeries -> top.tags
    else -> emptyList()
  }
  val text = (listOf(tab.title) + tags).joinToString(" ", transform = ::findText)
  return words.all { it in text }
}

internal fun findText(text: String) = text.lowercase().replace('_', ' ')

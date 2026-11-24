package dev.marzahn.sankaku

import androidx.navigation3.runtime.NavKey
import dev.marzahn.sankaku.data.SearchTags
import dev.marzahn.sankaku.tabs.TabState
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.tabs.backsLikeALonePost
import dev.marzahn.sankaku.tabs.closeBackSaying
import dev.marzahn.sankaku.tabs.countedPosition
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.components.alreadySpoken
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
  @Serializable
  data class Grid(val tags: List<String>, val at: String? = null) : Route

  @Serializable
  data class Post(
    val id: String,
    val md5: String?,
    val originTags: List<String>,
    val label: String? = null,
    val rating: String? = null,
  ) : Route

  @Serializable
  data class Books(val tags: List<String> = emptyList()) : Route

  @Serializable
  data class BookSeries(val id: String, val title: String, val tags: List<String> = emptyList()) : Route

  @Serializable
  data class Book(val id: String, val title: String? = null, val cover: String? = null, val rating: String? = null) : Route
}

val Route.isBooks: Boolean get() = this is Route.Books || this is Route.BookSeries

val Route.isLone: Boolean get() = this is Route.Post || this is Route.Book

val Route.searchTags: List<String>
  get() = SearchTags.filtersFirst(
    when (this) {
      is Route.Grid -> tags
      is Route.Post -> originTags
      is Route.Books -> tags
      is Route.BookSeries -> tags
      else -> emptyList()
    },
  )

interface Navigator {
  fun goTo(route: Route)

  fun goToTyped(route: Route) = goTo(route)

  fun goBack()

  fun replaceTop(route: Route)

  fun openInNewTab(route: Route): Int?
}

class TabsNavigator(private val tabs: TabsController, private val messages: Messages? = null) : Navigator {
  private val backStack get() = tabs.activeTab.backStack

  private fun alreadyFor(route: Route): TabState? = tabs.exactSourceFor(route)?.takeIf { it !== tabs.activeTab }

  private fun sayAlready(source: TabState) {
    val index = tabs.tabs.value.indexOf(source).takeIf { it >= 0 } ?: return
    messages?.show(alreadySpoken(existingPosition(tabs.tabs.value, index)))
  }

  override fun goTo(route: Route) = goTo(route, typed = false)

  override fun goToTyped(route: Route) = goTo(route, typed = true)

  private fun goTo(route: Route, typed: Boolean) {
    when (route) {
      is Route.Grid -> {
        val already = alreadyFor(route)
        tabs.search(route, typed)
        already?.let(::sayAlready)
      }
      is Route.Post -> tabs.openLone(route)
      is Route.Book -> tabs.openLone(route)
      is Route.Books, is Route.BookSeries -> {
        val already = alreadyFor(route)
        tabs.openBooks(route, typed)
        already?.let(::sayAlready)
      }
    }
  }

  override fun goBack() {
    val tab = tabs.activeTab
    when {
      backStack.size > 1 -> tab.pop()
      tabs.backReturnOf(tab) != null -> if (tabs.backReturn(tab.id)) return
      tabs.openerOf(tab) != null -> if (tabs.closeBackSaying(tab.id, messages)) return
      tab.backsLikeALonePost -> if (tabs.backToPrevious()) return
    }
    tabs.touch(tab.id)
  }

  override fun replaceTop(route: Route) {
    tabs.activeTab.replaceTop(route)
    tabs.touchActive()
  }

  override fun openInNewTab(route: Route): Int? {
    if (route.isLone) {
      tabs.indexOfLone(route)?.let { index ->
        tabs.keepAgain(tabs.tabs.value[index].id)
        return existingPosition(tabs.tabs.value, index)
      }
      tabs.openInBlock(listOf(route))
    } else {
      val already = alreadyFor(route)
      tabs.open(route, background = true)?.let { source ->
        if (already === source) sayAlready(source)
        return existingPosition(tabs.tabs.value, tabs.tabs.value.indexOf(source))
      }
    }
    return null
  }
}

internal fun existingPosition(tabs: List<TabState>, index: Int): Int = countedPosition(tabs, index).coerceAtLeast(1)

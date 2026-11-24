package dev.marzahn.sankaku.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RecentSearches {
  private val _searches = MutableStateFlow<List<List<String>>>(emptyList())
  val searches: StateFlow<List<List<String>>> = _searches.asStateFlow()

  fun add(tags: List<String>) {
    if (tags.isEmpty()) return
    _searches.update { (listOf(tags) + (it - setOf(tags))).take(MAX) }
  }

  fun clear() {
    _searches.value = emptyList()
  }

  companion object {
    const val MAX = 8
  }
}

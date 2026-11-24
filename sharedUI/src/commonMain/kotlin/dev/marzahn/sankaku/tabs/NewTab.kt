package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.marzahn.sankaku.Route

class NewTab(private val tabs: TabsController) {
  var searchFor: TabId? by mutableStateOf(null)
    private set

  fun open() {
    tabs.open(Route.Grid(emptyList()), background = false)
    searchFor = tabs.active.value
  }

  fun take(shown: TabId): Boolean {
    if (searchFor != shown) return false
    searchFor = null
    return true
  }
}

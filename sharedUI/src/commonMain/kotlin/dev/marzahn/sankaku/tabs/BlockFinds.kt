package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.marzahn.sankaku.queue.SourceKey
import dev.marzahn.sankaku.ui.components.CountPillText
import dev.marzahn.sankaku.ui.components.ShortSource
import org.koin.mp.KoinPlatform

class BlockFinds(
  private val tabs: TabsController,
  private val deck: SortDeck,
  private val gOnly: () -> Boolean = { false },
  private val quiet: () -> Boolean = { false },
) {
  class Offer(val search: List<String>, val name: String, val finds: List<TabId>, val next: TabId, val short: ShortSource? = null)

  var offer: Offer? by mutableStateOf(null)
    private set

  private val offered = mutableSetOf<Pair<String, SourceKey>>()

  init {
    tabs.onBlockLeft = ::left
  }

  fun left(block: TabsController.BlockLeft) {
    if (deck.running || quiet()) return
    val key = tabs.sessions.current.value to (sourceKeyOf(searchSource(block.search)) ?: return)
    if (key in offered) return
    val finds = newFinds(block.search)
    if (finds.isEmpty()) return
    offered += key
    offer = Offer(
      block.search,
      foundName(FoundOn(block.search), tabs.tagNames),
      finds.map { it.id },
      block.landing,
      short = shortTitle(searchSource(block.search), tabs.tagNames, prefix = "$FOUND_ON "),
    )
  }

  fun newFinds(search: List<String>): List<TabState> =
    tabs.findsOf(search).filter { isNewSource(it) && !(gOnly() && isAdult(it.top) { null }) }

  val finds: List<TabState>
    get() {
      val shown = offer ?: return emptyList()
      val all = tabs.tabs.value
      return shown.finds.mapNotNull { id -> all.firstOrNull { it.id == id } }
    }

  val next: TabState? get() = offer?.next?.let { id -> tabs.tabs.value.firstOrNull { it.id == id } }

  fun sortNow() {
    val shown = offer ?: return
    val finds = finds
    offer = null
    deck.startFinds(finds, next = shown.next, then = shown.next, foundOn = shown.name)
  }

  fun notNow() {
    offer = null
  }

  fun itemShown(id: TabId) {
    if (offer?.let { it.next != id } == true) offer = null
  }

  fun pillText(): CountPillText? {
    val shown = offer ?: return null
    val count = finds.size
    return CountPillText("$FOUND_ON ${shown.name}", "$count", "$count found on ${shown.name}", short = shown.short)
  }
}

@Composable
internal fun blockFindsOrNull(): BlockFinds? = remember { KoinPlatform.getKoinOrNull()?.getOrNull<BlockFinds>() }

internal const val FOUND_ON = "Found on"

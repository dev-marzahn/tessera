package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.PrivacySettings
import kotlinx.coroutines.flow.StateFlow

sealed interface UpNextCard {
  val search: Route.Grid?

  data object End : UpNextCard {
    override val search: Route.Grid? = null
  }

  data object KeptEnd : UpNextCard {
    override val search: Route.Grid? = null
  }
}

enum class Ahead {
  Item,

  End,

  Nothing,
}

class UpNextState(
  internal val tabs: TabsController,
  private val privacy: PrivacySettings,
) {
  var card: UpNextCard? by mutableStateOf(null)
    private set

  val gOnly: StateFlow<Boolean> get() = privacy.gOnly

  fun ahead(): Ahead = when {
    card != null -> Ahead.Nothing
    tabs.neighbour(forward = true) != null -> Ahead.Item
    else -> Ahead.End
  }

  fun forward(): Boolean = when (ahead()) {
    Ahead.Item -> {
      dismiss()
      tabs.next()
    }
    Ahead.End -> {
      tabs.movedOnPastEnd()
      showEnd()
      false
    }
    Ahead.Nothing -> false
  }

  fun backToStart() {
    dismiss()
    tabs.backToStart(privacy.gOnly.value)
  }

  fun browse() {
    dismiss()
    tabs.showBrowse()
  }

  fun goThroughKept(posts: List<TabState>, at: TabState) {
    if (posts.isEmpty()) return
    dismiss()
    tabs.startKeptRun(posts.map { it.id }, at.id)
  }

  fun sort(deck: SortDeck) {
    dismiss()
    deck.start()
  }

  fun showAhead(ahead: Ahead) {
    if (ahead == Ahead.End) showEnd()
    if (card != null) landedOn = tabs.activeTab.let { it.id to it.top }
  }

  fun itemShown(id: TabId, top: Route?) {
    if (landedOn != id to top) dismiss()
  }

  fun dismiss() {
    card = null
    landedOn = null
    if (tabs.keptRunOver()) tabs.endKeptRun()
  }

  fun endName(): String = tabs.keptPlace()?.let { tabs.keptRunKind() }?.endPane ?: END_PANE

  fun keptTally(): KeptTally? = tabs.keptTally()

  fun endKept() {
    dismiss()
    tabs.endKeptRun()
  }

  fun goThroughLater(ids: List<TabId>) {
    dismiss()
    tabs.startLaterRun(ids)
  }

  fun showKeptEnd() {
    card = UpNextCard.KeptEnd
    landedOn = tabs.activeTab.let { it.id to it.top }
  }

  private fun showEnd() {
    card = if (tabs.keptPlace() != null) UpNextCard.KeptEnd else UpNextCard.End
  }

  private var landedOn: Pair<TabId, Route?>? = null
}

internal fun shownSearch(card: UpNextCard?, top: Route?): Route? =
  if (card == null) top else card.search ?: Route.Grid(emptyList())

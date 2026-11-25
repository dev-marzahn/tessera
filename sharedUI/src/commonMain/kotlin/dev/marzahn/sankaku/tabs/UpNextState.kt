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

sealed interface Ahead {
  val title: String?

  fun closePane(hasPrevious: Boolean): Pane? = when (this) {
    Sort -> Sort
    is End -> takeIf { hasPrevious }
    is Item, Ahead.Nothing -> null
  }

  data class Item(val next: TabState) : Ahead {
    override val title: String get() = next.title
  }

  sealed interface Pane : Ahead {
    override val title: String

    val label: String
  }

  data object Sort : Pane {
    override val title: String = SORT

    override val label: String = nextLine(SORT)
  }

  data class End(override val title: String) : Pane {
    override val label: String get() = title
  }

  data object Nothing : Ahead {
    override val title: String? = null
  }
}

class UpNextState(
  internal val tabs: TabsController,
  private val privacy: PrivacySettings,
  private val deck: SortDeck,
) {
  private var shown: Shown? by mutableStateOf(null)

  val card: UpNextCard? get() = shown?.card

  val gOnly: StateFlow<Boolean> get() = privacy.gOnly

  val sortGoneInto: TabId? get() = deck.goneIn

  fun ahead(): Ahead {
    if (card != null) return Ahead.Nothing
    val next = tabs.neighbour(forward = true)
    return when {
      deck.goneInEndsAt(next?.id) -> Ahead.Sort
      next != null -> Ahead.Item(next)
      else -> Ahead.End(endName())
    }
  }

  fun forward(): Boolean = when (ahead()) {
    is Ahead.Item -> {
      dismiss()
      tabs.next()
    }
    Ahead.Sort -> {
      dismiss()
      tabs.movedOn()
      backToSort()
      false
    }
    is Ahead.End -> {
      tabs.movedOn()
      showEnd()
      false
    }
    Ahead.Nothing -> false
  }

  fun backToSort() {
    deck.backToDeck()
  }

  fun closeGoneIn() {
    deck.closeAndNext()
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

  fun sort() {
    dismiss()
    deck.start()
  }

  fun showAhead(pane: Ahead.Pane) {
    when (pane) {
      is Ahead.End -> showEnd()
      Ahead.Sort -> backToSort()
    }
  }

  fun itemShown(id: TabId, top: Route?) {
    if (shown?.landedOn(id, top) != true) dismiss()
  }

  fun dismiss() {
    shown = null
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

  fun showKeptEnd() = show(UpNextCard.KeptEnd)

  private fun showEnd() = show(if (tabs.keptPlace() != null) UpNextCard.KeptEnd else UpNextCard.End)

  private fun show(card: UpNextCard) {
    shown = tabs.activeTab.let { Shown(card, it.id, it.top) }
  }

  private class Shown(val card: UpNextCard, val id: TabId, val top: Route?) {
    fun landedOn(id: TabId, top: Route?): Boolean = this.id == id && this.top == top
  }
}

internal fun shownSearch(card: UpNextCard?, top: Route?): Route? =
  if (card == null) top else card.search ?: Route.Grid(emptyList())

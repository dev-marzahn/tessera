package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.ui.Tips
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.TestScope

internal fun noTips() = Tips(InMemoryTextFile(), touch = false)

internal fun TestScope.inTheBackgroundOnTheTestThread() = TabsBackground(backgroundScope, snapshotThread = EmptyCoroutineContext)

internal fun noUpNext(
  tabs: TabsController,
  privacy: PrivacySettings = PrivacySettings(InMemoryTextFile()),
  deck: SortDeck = SortDeck(tabs, ClosedTabUndo(tabs)),
) = UpNextState(tabs, privacy, deck)

internal fun keptPost(id: String, rating: String = "s") =
  Route.Post(id, md5 = "m$id", originTags = emptyList(), label = id, rating = rating)

internal fun TabsController.openSource(tag: String, shown: Boolean = false): TabState {
  val route = Route.Grid(listOf(tag))
  open(route, background = !shown)
  return tabs.value.last { it.top == route }
}

internal fun TabsController.keep(vararg ids: String) = ids.forEach { openInBlock(listOf(keptPost(it))) }

internal fun TabsController.keepInto(into: TabState, id: String, rating: String = "s"): TabState {
  activate(into.id)
  openInBlock(listOf(keptPost(id, rating)))
  return tabs.value.first { (it.top as? Route.Post)?.id == id }
}

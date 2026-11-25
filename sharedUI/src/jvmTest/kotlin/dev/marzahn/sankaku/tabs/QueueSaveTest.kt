package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.snapshots.Snapshot
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.GridPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class QueueSaveTest {
  private val sources = (0 until 300).map { Route.Grid(listOf("artist_$it")) }

  private val store = RecordingTabStateStore(SavedTabs(activeIndex = 0, tabs = sources.mapIndexed { i, route -> SavedTab("tab$i", listOf(route)) }))

  private fun TestScope.queue(): TabsController = TabsController(store, backgroundScope).also { runCurrent() }

  private fun TestScope.settle() {
    Snapshot.sendApplyNotifications()
    advanceTimeBy(TabsController.SAVE_DELAY + 100.milliseconds)
  }

  private fun TabsController.item(index: Int) = tabs.value[index]

  private fun TabsController.touch(index: Int) {
    item(index).markTouched(sources[index])
  }

  private fun rebuiltSinceLastSave(): List<Int> {
    val (before, after) = store.saves.takeLast(2)
    return after.tabs.indices.filter { after.tabs[it] !== before.tabs[it] }
  }

  @Test
  fun aChangeToOneItemRebuildsOnlyThatItemsSavedForm() = runTest {
    val queue = queue()
    queue.touch(0)
    settle()
    queue.touch(120)
    settle()

    assertEquals(listOf(120), rebuiltSinceLastSave())
    assertEquals(true, store.saves.last().tabs[120].gridTouched)
  }

  @Test
  fun aBackStackChangeRebuildsOnlyThatItemAndSavesItsRoutes() = runTest {
    val queue = queue()
    queue.touch(0)
    settle()
    val moved = Route.Grid(listOf("artist_70", "blue_sky"))
    queue.item(70).replaceTop(moved)
    settle()

    assertEquals(listOf(70), rebuiltSinceLastSave())
    assertEquals(listOf<Route>(moved), store.saves.last().tabs[70].routes)
  }

  @Test
  fun onlyTheFirstSaveIsSkippedWhenItMatchesTheLaunchState() = runTest {
    val queue = queue()
    settle()
    assertTrue(store.saves.isEmpty())

    queue.item(7).reportPlace(sources[7], GridPlace("p1"))
    settle()
    queue.item(7).reportPlace(sources[7], null)
    settle()

    assertEquals(listOf(GridPlace("p1"), null), store.saves.map { it.tabs[7].place })
  }
}

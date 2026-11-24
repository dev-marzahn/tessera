package dev.marzahn.sankaku.features.grid

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.tabs.ClosedTabUndo
import dev.marzahn.sankaku.tabs.Closing
import dev.marzahn.sankaku.tabs.EndUndoAtNextAction
import dev.marzahn.sankaku.tabs.TabsController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class UnkeepPostTest {
  private var now = Instant.parse("2026-11-16T12:00:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val seen = SeenStore(InMemoryTextFile(), clock)
  private val tabs = TabsController(clock = clock)
  private val navigator = TabsNavigator(tabs)
  private val pill = ClosedTabUndo(tabs)
  private val grid = Route.Grid(listOf("fixture_tag"))

  private fun post(id: String) = PostDto(id = id, md5 = "md5-$id", rating = "s", status = "active")

  private fun keep(id: String) = keepPost(post(id), grid.tags, navigator, tabs, seen)

  private fun tops() = tabs.tabs.value.map { (it.top as? Route.Post)?.id ?: (it.top as Route.Grid).tags.joinToString(" ") }

  @Test
  fun unkeepingAKeptPostRemovesItsItemAndUndoPutsItBackInPlace() {
    navigator.goTo(grid)
    keep("a")
    keep("b")
    keep("c")
    val source = tabs.activeTab

    assertTrue(unkeepPost(post("b"), tabs, pill))

    assertEquals(listOf("fixture_tag", "a", "c"), tops())
    assertEquals(source, tabs.activeTab)
    assertEquals(Closing.Unkept, pill.pending.value?.closing)
    assertEquals(tabs.closed.value.last(), pill.pending.value?.closed)

    pill.undo()
    assertEquals(listOf("fixture_tag", "a", "b", "c"), tops())
    assertEquals(source, tabs.sourceOf(tabs.tabs.value[2]))
    assertEquals(source, tabs.activeTab)
    assertNull(pill.pending.value)
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun thePillsUndoStaysUntilTheNextAction() = runComposeUiTest {
    navigator.goTo(grid)
    keep("a")
    keep("b")
    setContent { EndUndoAtNextAction(pill, tabs) }

    runOnIdle { unkeepPost(post("a"), tabs, pill) }
    mainClock.advanceTimeBy(60_000)
    runOnIdle { assertNotNull(pill.pending.value) }

    runOnIdle { navigator.goTo(Route.Grid(listOf("other_tag"))) }
    runOnIdle { assertNull(pill.pending.value) }
  }

  @Test
  fun aKeptPostSeenSinceItWasKeptIsUnkeptToo() {
    navigator.goTo(grid)
    keep("a")
    now += 1.minutes
    seen.record("a", "md5-a")

    assertTrue(unkeepPost(post("a"), tabs, pill))
    assertEquals(listOf("fixture_tag"), tops())
  }

  @Test
  fun aReuploadOfAKeptPostUnkeepsTheQueuesCopy() {
    navigator.goTo(grid)
    keep("a")
    keep("b")
    val copyOfA = { p: PostDto -> tabs.tabs.value.takeIf { p.id == "a-again" }?.first { (it.top as? Route.Post)?.id == "a" } }

    assertNotNull(keptItemOf(post("a-again"), tabs.tabs.value, copyOfA))
    assertTrue(unkeepPost(post("a-again"), tabs, pill, copyOfA))
    assertEquals(listOf("fixture_tag", "b"), tops())
  }

  @Test
  fun aPostNotKeptOnItsOwnHasNothingToUnkeep() {
    navigator.goTo(grid)
    keep("a")
    tabs.open(listOf(Route.Grid(listOf("other_tag")), Route.Post("over", "md5-over", listOf("other_tag"))), background = true)
    val before = tops()
    assertEquals(listOf("fixture_tag", "a", "over"), before)

    assertNull(keptItemOf(post("never-kept"), tabs.tabs.value))
    assertFalse(unkeepPost(post("never-kept"), tabs, pill))
    assertNull(keptItemOf(post("over"), tabs.tabs.value))
    assertFalse(unkeepPost(post("over"), tabs, pill))
    assertEquals(before, tops())
  }
}

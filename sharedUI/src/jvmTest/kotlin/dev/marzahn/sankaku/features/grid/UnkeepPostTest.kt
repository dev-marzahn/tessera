package dev.marzahn.sankaku.features.grid

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.tabs.ClosedTabUndo
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.tabs.UNKEPT
import dev.marzahn.sankaku.ui.Messages
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

    val closed = assertNotNull(unkeepPost(post("b"), tabs))

    assertEquals(listOf("fixture_tag", "a", "c"), tops())
    assertEquals(source, tabs.activeTab)
    tabs.reopen(closed, inPlace = true)
    assertEquals(listOf("fixture_tag", "a", "b", "c"), tops())
    assertEquals(source, tabs.sourceOf(tabs.tabs.value[2]))
    assertEquals(source, tabs.activeTab)
  }

  @Test
  fun theTopLinesUndoPutsItBackAndKeepsTheReopenTipAwayWhileItsUp() = runTest {
    navigator.goTo(grid)
    keep("a")
    keep("b")
    val source = tabs.activeTab
    val messages = Messages()
    val undo = ClosedTabUndo(tabs, messages = messages)
    val said = async(start = CoroutineStart.UNDISPATCHED) { messages.messages.first() }

    undo.offerOnLine(assertNotNull(unkeepPost(post("a"), tabs)), UNKEPT)
    val line = said.await()
    assertEquals("Removed from queue", line.text)
    assertEquals("Undo", line.actionLabel)
    val up = launch { assertNotNull(line.until).invoke() }
    runCurrent()
    assertTrue(undo.undoOnLine)
    up.cancel()
    runCurrent()
    assertFalse(undo.undoOnLine)

    line.onAction()
    assertEquals(listOf("fixture_tag", "a", "b"), tops())
    assertEquals(source, tabs.activeTab)
  }

  @Test
  fun aKeptPostSeenSinceItWasKeptIsUnkeptToo() {
    navigator.goTo(grid)
    keep("a")
    now += 1.minutes
    seen.record("a", "md5-a")

    assertNotNull(unkeepPost(post("a"), tabs))
    assertEquals(listOf("fixture_tag"), tops())
  }

  @Test
  fun aReuploadOfAKeptPostUnkeepsTheQueuesCopy() {
    navigator.goTo(grid)
    keep("a")
    keep("b")
    val copyOfA = { p: PostDto -> tabs.tabs.value.takeIf { p.id == "a-again" }?.first { (it.top as? Route.Post)?.id == "a" } }

    assertNotNull(keptItemOf(post("a-again"), tabs.tabs.value, copyOfA))
    assertNotNull(unkeepPost(post("a-again"), tabs, copyOfA))
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
    assertNull(unkeepPost(post("never-kept"), tabs))
    assertNull(keptItemOf(post("over"), tabs.tabs.value))
    assertNull(unkeepPost(post("over"), tabs))
    assertEquals(before, tops())
  }
}

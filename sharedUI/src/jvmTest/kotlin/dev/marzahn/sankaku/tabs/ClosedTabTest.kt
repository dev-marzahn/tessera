package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.queue.SeenStore
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ClosedTabTest {
  private var now = Instant.parse("2026-11-16T19:00:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val zone = { TimeZone.of("Europe/Berlin") }
  private val file = InMemoryTextFile()
  private val tabs = TabsController(FileTabStateStore(file, clock = clock), clock = clock, zone = zone)
  private val seen = SeenStore(InMemoryTextFile(), clock)

  private fun grid(tag: String) = Route.Grid(listOf(tag))

  private fun post(id: String) = Route.Post(id, "md5$id", listOf("a"), label = "Post $id")

  private fun tab(title: String) = tabs.tabs.value.first { it.title == title }

  @Test
  fun onlyAClearedBatchIsSavedAsClearedAndBothComeBackAfterAReload() {
    tabs.open(grid("artist_a"), background = false)
    tabs.open(grid("artist_b"), background = true)
    tabs.openInBlock(listOf(post("p1")))
    ClosedTabUndo(tabs).close(tab("Artist B").id, Closing.OnQueuePage)
    now += 1.minutes
    seen.record("p1", "md5p1")
    tabs.clearViewed(seen)

    val saved = tabs.saved()
    assertEquals(listOf(null, true), saved.closed.map { it.cleared })

    FileTabStateStore(file, clock = clock).save(saved)
    val reloaded = TabsController(FileTabStateStore(file, clock = clock), clock = clock, zone = zone)
    assertEquals(listOf(false, true), reloaded.closed.value.map { it.cleared })
    assertEquals(listOf("Artist B", "1 post"), reloaded.closed.value.map { it.title })
  }

  @Test
  fun eachClosingHasItsOwnWords() {
    tabs.open(grid("artist_a"), background = true)
    tabs.open(grid("artist_b"), background = true)
    tabs.open(grid("artist_c"), background = true)
    val one = assertNotNull(tabs.close(tab("Artist A").id))
    val two = assertNotNull(tabs.closeAll(listOf(tab("Artist B").id, tab("Artist C").id)))

    Closing.Closed.words(one).let {
      assertEquals("Closed “Artist A”", it.shown)
      assertEquals("Closed “Artist A”", it.spoken)
      assertEquals("Undo close", it.undo)
    }
    Closing.OnQueuePage.words(two).let {
      assertEquals("Closed 2", it.shown)
      assertEquals("Closed 2 items", it.spoken)
      assertEquals("Undo close", it.undo)
    }
    Closing.Unkept.words(one).let {
      assertEquals("Removed from queue", it.shown)
      assertEquals("Removed from queue", it.spoken)
      assertEquals("Undo remove", it.undo)
    }
  }
}

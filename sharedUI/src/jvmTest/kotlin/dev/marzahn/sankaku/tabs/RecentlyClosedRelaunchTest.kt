package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.snapshots.Snapshot
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.queue.SeenStore
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RecentlyClosedRelaunchTest {
  private var now = Instant.parse("2026-11-16T19:00:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val zone = { TimeZone.of("Europe/Berlin") }
  private val file = InMemoryTextFile()
  private var tabs = TabsController(FileTabStateStore(file, clock = clock), clock = clock, zone = zone)

  private fun grid(tag: String) = Route.Grid(listOf(tag))

  private fun post(id: String) = Route.Post(id, "md5$id", listOf("a"), label = "Post $id")

  private fun tab(title: String) = tabs.tabs.value.first { it.title == title }

  private fun titles() = tabs.tabs.value.map { it.title }

  private fun keep(id: String) = tabs.openInBlock(listOf(post(id)))

  private fun relaunch() {
    FileTabStateStore(file, clock = clock).save(tabs.saved())
    tabs = TabsController(FileTabStateStore(file, clock = clock), clock = clock, zone = zone)
  }

  @Test
  fun anItemClosedTodayIsStillListedAfterARelaunchAndReopensWhereItWas() {
    tabs.open(grid("artist_a"), background = true)
    tabs.open(grid("artist_b"), background = true)
    val place = GridPlace("p1", "c1", 3, 1_700_000_000)
    tab("Artist A").reportPlace(grid("artist_a"), place)
    tabs.close(tab("Artist A").id)

    relaunch()

    assertEquals(listOf("Artist A"), tabs.closed.value.map { it.title })
    tabs.reopenClosed()
    assertEquals(listOf("Browse", "Artist A", "Artist B"), titles())
    assertEquals(place, tabs.activeTab.place)
    assertTrue(tabs.closed.value.isEmpty())
  }

  @Test
  fun itemsClosedOnAnEarlierDayAreNotListedAfterARelaunch() {
    tabs.open(grid("artist_a"), background = true)
    tabs.open(grid("artist_b"), background = true)
    now = Instant.parse("2026-11-16T22:30:00Z")
    tabs.close(tab("Artist A").id)
    now = Instant.parse("2026-11-16T23:10:00Z")
    tabs.close(tab("Artist B").id)
    now += 1.hours

    relaunch()

    assertEquals(listOf("Artist B"), tabs.closed.value.map { it.title })
  }

  @Test
  fun aSaveAfterMidnightLeavesTheEarlierDaysEntriesOutOfTheFile() {
    tabs.open(grid("artist_a"), background = true)
    tabs.open(grid("artist_b"), background = true)
    now = Instant.parse("2026-11-16T22:30:00Z")
    tabs.close(tab("Artist A").id)
    now = Instant.parse("2026-11-16T23:10:00Z")
    tabs.close(tab("Artist B").id)

    assertEquals(2, tabs.closed.value.size)
    assertEquals(1, tabs.saved().closed.size)
  }

  @Test
  fun aClosedSourceComesBackHeadingTheKeptPostsItLeftAfterARelaunch() {
    tabs.open(grid("artist_a"), background = false)
    listOf("p1", "p2").forEach(::keep)
    tabs.close(tab("Artist A").id)

    relaunch()

    assertEquals("Closed “Artist A” · 2 kept stay", Closing.Closed.words(tabs.closed.value.single()).spoken)
    tabs.reopenClosed()
    val source = tab("Artist A")
    assertEquals(listOf("Browse", "Artist A", "Post p1", "Post p2"), titles())
    listOf("Post p1", "Post p2").forEach {
      assertEquals(source.id, tab(it).block)
      assertNull(tab(it).closedSource)
    }
  }

  @Test
  fun aBulkCloseComesBackWholeWithItsCurrentItemAfterARelaunch() {
    listOf("artist_a", "artist_b", "artist_c").forEach { tabs.open(grid(it), background = true) }
    tabs.activate(tab("Artist B").id)
    tabs.closeAll(listOf(tab("Artist A").id, tab("Artist B").id))

    relaunch()

    assertEquals("2 tabs", tabs.closed.value.single().title)
    tabs.reopenClosed()
    assertEquals(listOf("Browse", "Artist A", "Artist B", "Artist C"), titles())
    assertEquals("Artist B", tabs.activeTab.title)
  }

  @Test
  fun whatClearViewedClearedComesBackAsClearedAfterARelaunchWithTheTimesItWasKept() {
    val seen = SeenStore(InMemoryTextFile(), clock)
    tabs.open(grid("artist_a"), background = false)
    listOf("p1", "p2").forEach(::keep)
    val keptAt = tab("Post p1").keptAt
    now += 1.minutes
    seen.record("p1", "md5p1")
    seen.record("p2", "md5p2")
    tabs.clearViewed(seen)

    relaunch()

    val cleared = tabs.closed.value.single()
    assertTrue(cleared.cleared)
    assertEquals("2 posts", cleared.title)
    tabs.reopen(cleared)
    assertEquals(listOf("Browse", "Artist A", "Post p1", "Post p2"), titles())
    assertEquals(keptAt, tab("Post p1").keptAt)
  }

  @Test
  fun aPostComesBackWithTheTitleItLoadedAfterARelaunch() {
    tabs.open(Route.Post("p1", "md5p1", listOf("a")), background = true)
    tabs.loadedTitle(Route.Post("p1", "md5p1", listOf("a")), "A loaded title")
    tabs.close(tab("A loaded title").id)

    relaunch()

    assertEquals("A loaded title", tabs.closed.value.single().title)
  }

  @Test
  fun clearEverythingForgetsTheSavedListToo() {
    tabs.open(grid("artist_a"), background = true)
    tabs.close(tab("Artist A").id)
    tabs.forgetClosed()

    relaunch()

    assertTrue(tabs.closed.value.isEmpty())
  }

  @Test
  fun aFileWithNothingClosedHasNoRecentlyClosedInIt() {
    tabs.open(grid("artist_a"), background = true)

    relaunch()

    assertFalse("\"closed\"" in file.read()!!)
  }

  @Test
  fun aCloseAndForgettingThemAreEachSavedByThemselves() = runTest {
    val saving = TabsController(FileTabStateStore(file, clock = clock), inTheBackgroundOnTheTestThread(), clock, zone = zone)
    runCurrent()
    saving.open(grid("artist_a"), background = true)
    Snapshot.sendApplyNotifications()
    advanceTimeBy(TabsController.SAVE_DELAY + 100.milliseconds)
    saving.close(saving.tabs.value.first { it.title == "Artist A" }.id)
    Snapshot.sendApplyNotifications()
    advanceTimeBy(TabsController.SAVE_DELAY + 100.milliseconds)

    assertEquals(1, FileTabStateStore(file, clock = clock).load()!!.closed.size)

    saving.forgetClosed()
    Snapshot.sendApplyNotifications()
    advanceTimeBy(TabsController.SAVE_DELAY + 100.milliseconds)

    assertTrue(FileTabStateStore(file, clock = clock).load()!!.closed.isEmpty())
  }
}

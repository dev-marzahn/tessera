package dev.marzahn.sankaku.tabs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class KeptChipTest {
  private var now = Instant.parse("2026-11-14T12:00:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val tabs = TabsController(clock = clock)
  private val privacy = PrivacySettings(InMemoryTextFile())
  private val seen = SeenStore(InMemoryTextFile(), clock)

  private fun source(tag: String): TabState {
    val route = Route.Grid(listOf(tag))
    tabs.open(route, background = true)
    return tabs.tabs.value.last { it.top == route }
  }

  private fun keepInto(into: TabState, id: String) {
    tabs.activate(into.id)
    tabs.openInBlock(listOf(Route.Post(id, md5 = "m$id", originTags = emptyList(), label = "Post $id", rating = "s")))
  }

  private fun keptRows() = queueRows(tabs.tabs.value, emptyList(), null, null, gOnly = false, QueueFilter.Kept, emptySet()) { null }

  private fun heads(rows: List<QueueRow>) = rows.mapNotNull {
    when (it) {
      is QueueRow.ClosedBlock -> it.title
      is QueueRow.Item -> it.tab.title.takeUnless { _ -> it.indented }
      else -> null
    }
  }

  @Test
  fun keptListsTheBlocksWithAKeptPostTheOneLastKeptIntoFirst() {
    val older = source("artist_a")
    val newer = source("artist_b")
    source("artist_none")
    keepInto(older, "p1")
    now += 1.hours
    keepInto(newer, "p2")
    now += 1.hours
    keepInto(older, "p3")

    val rows = keptRows()

    assertEquals(listOf("Artist A", "Artist B"), heads(rows))
    val first = rows.first() as QueueRow.Item
    assertEquals(now, first.lastKept)
    assertEquals(2, first.fold?.kept)
  }

  @Test
  fun aClosedSourcesKeptPostsAreListedToo() {
    val closing = source("artist_c")
    val open = source("artist_d")
    keepInto(closing, "p1")
    now += 1.hours
    keepInto(open, "p2")
    tabs.close(closing.id)

    val rows = keptRows()

    assertEquals(listOf("Artist D", "from Artist C (closed)"), heads(rows))
    assertEquals(now - 1.hours, (rows[1] as QueueRow.ClosedBlock).lastKept)
  }

  @Test
  fun theOtherChipsCarryNoKeptTime() {
    val a = source("artist_a")
    keepInto(a, "p1")
    val rows = queueRows(tabs.tabs.value, emptyList(), null, null, gOnly = false, QueueFilter.All, emptySet()) { null }
    assertNull(rows.filterIsInstance<QueueRow.Item>().firstNotNullOfOrNull { it.lastKept })
  }

  @Test
  fun keptWordsSayTheTimeTodayAndTheDayBefore() {
    val zone = TimeZone.UTC
    assertEquals("kept 09:05", keptWords(Instant.parse("2026-11-14T09:05:00Z"), now, zone))
    assertEquals("kept yesterday", keptWords(Instant.parse("2026-11-13T23:00:00Z"), now, zone))
    assertEquals("kept Tuesday", keptWords(Instant.parse("2026-11-10T10:00:00Z"), now, zone))
    assertEquals("kept 2 Nov", keptWords(Instant.parse("2026-11-02T10:00:00Z"), now, zone))
    assertNull(keptWords(Instant.fromEpochSeconds(0), now, zone))
  }

  @Test
  fun theChipShowsItsBlocksAndStaysUntilAStart() = runComposeUiTest {
    val a = source("artist_a")
    source("artist_none")
    keepInto(a, "p1")
    now += 5.minutes
    val start = QueueStart(tabs, privacy)
    setContent {
      SankakuTheme {
        PhoneQueuePage(
          onDismiss = {}, controller = tabs, posts = TabPosts.None, privacy = privacy, seen = seen,
          interacted = { true }, onInteract = {},
          folding = true, chipMemory = start,
        )
      }
    }
    waitForIdle()

    onNodeWithText("Kept · 1").performClick()
    waitForIdle()
    onNodeWithText("Artist A", useUnmergedTree = true).assertExists()
    onNodeWithText("Artist None", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithText("· kept ", substring = true, useUnmergedTree = true).assertExists()
    assertEquals(QueueFilter.Kept, start.chip)

    runOnIdle { start.launched() }
    waitForIdle()
    assertEquals(QueueFilter.All, start.chip)
    onNodeWithText("Artist None", useUnmergedTree = true).assertExists()
  }

  private fun keepRated(into: TabState, id: String, rating: String) {
    tabs.activate(into.id)
    tabs.openInBlock(listOf(Route.Post(id, md5 = "m$id", originTags = emptyList(), label = "Post $id", rating = rating)))
  }

  @Test
  fun withGOnlyABlockWhoseKeptPostsItHidesIsntListed() {
    val hidden = source("artist_h")
    val shown = source("artist_s")
    keepRated(hidden, "p1", "e")
    now += 1.hours
    keepRated(shown, "p2", "s")
    now += 1.hours
    keepRated(shown, "p3", "e")

    val rows = queueRows(tabs.tabs.value, emptyList(), null, null, gOnly = true, QueueFilter.Kept, emptySet()) { null }

    assertEquals(listOf("Artist S"), heads(rows))
    assertEquals(now - 1.hours, (rows.first() as QueueRow.Item).lastKept)
  }

  @Test
  fun aKeptPostFoundWithoutItsHeadIsTimedAsItsBlock() {
    val older = source("artist_a")
    val newer = source("artist_b")
    keepInto(older, "p1")
    now += 1.hours
    keepInto(newer, "p2")

    val rows = queueRows(tabs.tabs.value, emptyList(), null, "Post", gOnly = false, QueueFilter.Kept, null) { null }
    val items = rows.filterIsInstance<QueueRow.Item>()

    assertEquals(listOf("Post p2", "Post p1"), items.map { it.tab.title })
    assertEquals(now, items.first().lastKept)
  }
}

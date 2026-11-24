package dev.marzahn.sankaku.tabs

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.SourceKey
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalTestApi::class)
class EndLeftoversTest {
  private val start = Instant.parse("2026-11-15T16:00:00Z")
  private var now = start
  private val clock = object : Clock {
    override fun now() = now
  }
  private val tabs = TabsController(clock = clock)
  private val privacy = PrivacySettings(InMemoryTextFile())
  private val upNext = UpNextState(tabs, privacy)
  private val seen = SeenStore(InMemoryTextFile(), clock)
  private val today = TodayStore(InMemoryTextFile(), clock, { TimeZone.UTC })
  private val deck = SortDeck(tabs, ClosedTabUndo(tabs))
  private val seenSince = { tab: TabState -> tab.seenSinceKept(seen) }

  private fun source(tag: String, shown: Boolean = false): TabState {
    val route = Route.Grid(listOf(tag))
    tabs.open(route, background = !shown)
    return tabs.tabs.value.single { it.top == route }
  }

  private fun keepInto(into: TabState, id: String, rating: String = "s"): TabState {
    tabs.activate(into.id)
    tabs.openInBlock(listOf(Route.Post(id, md5 = "m$id", originTags = emptyList(), label = "Post $id", rating = rating)))
    return tabs.tabs.value.first { (it.top as? Route.Post)?.id == id }
  }

  private fun TabState.started() = reportPlace(top as Route.Grid, GridPlace("p1", "c1", 0, index = 0))

  private fun leftovers(gOnly: Boolean = false) =
    endLeftovers(tabs.tabs.value, gOnly, { null }, seenSince, sorting = true, today.counts().found, now, TimeZone.UTC)

  private fun queuePageKept(gOnly: Boolean = false) = keptRunRows(tabs.tabs.value, gOnly) { null }.map { it.tab }

  private fun queuePageNew(gOnly: Boolean = false) = newSources(tabs.tabs.value, gOnly).size

  private fun session() {
    val a = source("artist_a")
    val b = source("artist_b")
    a.started()
    b.started()
    keepInto(a, "p1")
    keepInto(a, "p2")
    now += 1.minutes
    keepInto(b, "p3")
    now += 1.minutes
    seen.record("p1", "mp1")
    source("new_one")
    source("new_two")
    source("last", shown = true).started()
    tabs.activate(tabs.tabs.value.last().id)
  }

  @Test
  fun theCountsAreTheQueuePagesKeptNotSeenAndSortsNew() {
    session()
    val waiting = leftovers()

    assertEquals(queuePageKept(), waiting.kept)
    assertEquals(queuePageKept().count { !seenSince(it) }, waiting.keptUnseen)
    assertEquals(2, waiting.keptUnseen)
    assertEquals(queuePageNew(), waiting.newSources)
    assertEquals(2, waiting.newSources)
    assertEquals("Artist B, Artist A", waiting.keptWhere)
    assertEquals(keptRunStart(queuePageKept(), seenSince), waiting.keptStart)
  }

  @Test
  fun oneBlockSaysWhenAPostWasLastKeptThere() {
    val a = source("artist_a")
    a.started()
    keepInto(a, "p1")
    assertEquals("Artist A · kept 16:00", leftovers().keptWhere)
  }

  @Test
  fun nothingWaitingIsNone() {
    source("last", shown = true).started()
    val waiting = leftovers()
    assertTrue(waiting.none)
    assertNull(waiting.keptWhere)
  }

  @Test
  fun gOnlyLeavesOutTheKeptPostsTheQueuePageHides() {
    val a = source("artist_a")
    a.started()
    keepInto(a, "p1")
    keepInto(a, "p2", rating = "e")
    source("last", shown = true).started()

    assertEquals(2, leftovers().keptUnseen)
    assertEquals(queuePageKept(gOnly = true), leftovers(gOnly = true).kept)
    assertEquals(1, leftovers(gOnly = true).keptUnseen)
  }

  @Test
  fun gOnlyCountsWhatTheQueuePageListsThen() {
    source("rating:q").let { tabs.activate(it.id) }
    source("last", shown = true).started()
    assertEquals(queuePageNew(gOnly = true), leftovers(gOnly = true).newSources)
    assertEquals(0, leftovers(gOnly = true).newSources)
  }

  private fun ComposeUiTest.card() {
    upNext.forward()
    assertEquals(UpNextCard.End, upNext.card)
    setContent {
      SankakuTheme {
        UpNextCardView(state = upNext, leftovers = EndLeftoverSources(seen, today = today, sort = deck))
      }
    }
  }

  @Test
  fun eachKindIsARowWithItsCountThatStartsItsRun() = runComposeUiTest {
    session()
    today.added(SourceKey.of(listOf("new_one"))!!)
    card()

    onNodeWithText("Still waiting earlier in the queue:").assertExists()
    onNodeWithText("2 kept posts not seen").assertExists()
    onNodeWithText("Artist B, Artist A").assertExists()
    onNodeWithText("Sort 2 new").assertExists()
    onNodeWithText("found today: 1").assertExists()
    onNodeWithContentDescription("2 kept posts not seen, Artist B, Artist A")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    val heading = onNode(isHeading()).fetchSemanticsNode()
    val actions = heading.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
    assertEquals(
      listOf("2 kept posts not seen, Artist B, Artist A", "Sort 2 new, found today: 1", "Back to the start", "Browse"),
      actions,
    )

    onNodeWithText("2 kept posts not seen").performClick()
    waitForIdle()
    assertNull(upNext.card)
    assertEquals("p3", (tabs.activeTab.top as? Route.Post)?.id)
    assertEquals(1 to 3, tabs.keptPlace())
  }

  @Test
  fun thePostsForLaterAreARowThatStartsGoingThroughLater() = runComposeUiTest {
    session()
    val p2 = tabs.tabs.value.first { (it.top as? Route.Post)?.id == "p2" }
    p2.markLater()
    assertEquals(1, leftovers().keptUnseen)
    assertEquals(listOf(p2), leftovers().later)
    card()

    onNodeWithText("1 kept post not seen").assertExists()
    onNodeWithContentDescription("1 for later, Artist A").assertExists()

    onNodeWithText("1 for later").performClick()
    waitForIdle()
    assertNull(upNext.card)
    assertEquals(p2.id, tabs.activeTab.id)
    assertEquals(KeptRunKind.Later, tabs.keptRunKind())
  }

  @Test
  fun sortsRowStartsTheDeck() = runComposeUiTest {
    session()
    card()
    onNodeWithText("Sort 2 new").performClick()
    waitForIdle()
    assertNull(upNext.card)
    assertTrue(deck.running && deck.shown)
    assertEquals(2, deck.total)
  }

  @Test
  fun withNothingWaitingTheCardIsUnchanged() = runComposeUiTest {
    source("last", shown = true).started()
    card()
    onNodeWithText("Nothing else is waiting.").assertExists()
    onNodeWithText("Still waiting earlier in the queue:").assertDoesNotExist()
    onNodeWithContentDescription("Back to the start of the queue").assertExists()
    onNodeWithContentDescription("Browse, a new grid").assertExists()
  }

  @Test
  fun aRowWithNothingIsLeftOut() = runComposeUiTest {
    val a = source("artist_a")
    a.started()
    keepInto(a, "p1")
    now += 1.minutes
    seen.record("p1", "mp1")
    source("new_one")
    source("last", shown = true).started()
    tabs.activate(tabs.tabs.value.last().id)
    card()
    onNodeWithText("Sort 1 new").assertExists()
    onNodeWithText("kept post", substring = true).assertDoesNotExist()
  }
}

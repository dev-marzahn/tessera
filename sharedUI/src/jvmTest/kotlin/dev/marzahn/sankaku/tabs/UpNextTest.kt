package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.TestTimeSource

class UpNextTest {
  private val start = Instant.parse("2026-11-01T12:00:00Z")
  private var now = start
  private val clock = object : Clock {
    override fun now() = now
  }
  private val tabs = TabsController(clock = clock)
  private val privacy = PrivacySettings(InMemoryTextFile())
  private val upNext = noUpNext(tabs, privacy)

  private fun grid(vararg tags: String) = Route.Grid(tags.toList())

  private fun titles() = tabs.tabs.value.map { it.title }

  private fun source(title: String) = tabs.tabs.value.single { it.title == title }

  private fun withFeeds() {
    tabs.pinFeeds(listOf(Feed.Following, Feed.ForYou))
    assertEquals(listOf("Following", "For you"), titles().takeLast(2))
  }

  @Test
  fun aSwipePastTheLastItemShowsTheEndNeverAnItemOfTheQueue() {
    tabs.open(grid("pipoyami", "order:popularity"), background = false)
    tabs.open(grid("scenery"), background = false)
    now = start + 1.days
    tabs.sessions.startNew()
    tabs.activate(tabs.tabs.value.last().id)

    assertIs<Ahead.End>(upNext.ahead())
    assertFalse(upNext.forward())

    assertEquals(UpNextCard.End, upNext.card)
    assertEquals(tabs.tabs.value.last().id, tabs.activeTab.id)
    assertEquals(Ahead.Nothing, upNext.ahead())
  }

  @Test
  fun sourcesUntouchedForDaysAreItemsSoMovingOnReachesThemAndThenTheEnd() {
    tabs.open(grid("oldest"), background = true)
    tabs.open(grid("middle"), background = true)
    now = start + 40.days
    tabs.activate(source("Middle").id)

    assertEquals(listOf("Browse", "Oldest", "Middle"), titles())
    assertIs<Ahead.End>(upNext.ahead())
    upNext.forward()
    assertEquals(UpNextCard.End, upNext.card)
  }

  @Test
  fun theEndStandsPastTheLastItemEvenWhenItIsAFeed() {
    tabs.open(grid("last"), background = false)
    withFeeds()
    tabs.activate(source("Last").id)

    assertIs<Ahead.Item>(upNext.ahead())
    assertTrue(upNext.forward())
    assertEquals("Following", tabs.activeTab.title)
    assertTrue(upNext.forward())
    assertEquals("For you", tabs.activeTab.title)

    assertIs<Ahead.End>(upNext.ahead())
    assertFalse(upNext.forward())
    assertEquals(UpNextCard.End, upNext.card)
  }

  @Test
  fun aLaterSearchAfterTheFeedsIsReachedBeforeTheEnd() {
    withFeeds()
    tabs.activate(source("For you").id)
    tabs.open(grid("later"), background = true)
    assertEquals(listOf("Following", "For you", "Later"), titles().takeLast(3))

    assertTrue(upNext.forward())
    assertEquals("Later", tabs.activeTab.title)
    assertIs<Ahead.End>(upNext.ahead())
    upNext.forward()
    assertEquals(UpNextCard.End, upNext.card)
  }

  @Test
  fun backToTheStartShowsItemOneAndBrowseOpensABrowseGrid() {
    tabs.open(grid("a"), background = false)
    tabs.open(grid("b"), background = false)
    upNext.forward()
    assertEquals(UpNextCard.End, upNext.card)

    upNext.backToStart()

    assertNull(upNext.card)
    assertEquals(tabs.tabs.value.first().id, tabs.activeTab.id)

    tabs.activate(source("B").id)
    upNext.forward()
    upNext.browse()
    assertNull(upNext.card)
    assertEquals(grid(), tabs.activeTab.top)
  }

  @Test
  fun backToTheStartPassesOverWhatGOnlyHides() {
    tabs.open(grid("rating:e", "lace"), background = true)
    tabs.open(grid("b"), background = false)
    tabs.moveInQueue(source("Lace").id, down = false)
    assertEquals(listOf("Lace", "Browse", "B"), titles())
    privacy.setGOnly(true)

    upNext.forward()
    upNext.backToStart()

    assertEquals("Browse", tabs.activeTab.title)
  }

  private fun screenSeesTheSameItemAgain() = upNext.itemShown(tabs.activeTab.id, tabs.activeTab.top)

  @Test
  fun aSwipedOnEndCardStaysWhenTheScreenSeesTheSameItemAgain() {
    tabs.open(grid("scenery"), background = false)
    upNext.forward()
    assertEquals(UpNextCard.End, upNext.card)

    screenSeesTheSameItemAgain()

    assertEquals(UpNextCard.End, upNext.card)
  }

  @Test
  fun aSwipedOnKeptEndCardKeepsItsTallyWhenTheScreenSeesTheSameItemAgain() {
    val source = tabs.openSource("artist_a")
    val kept = listOf("p1", "p2", "p3", "p4").map { tabs.keepInto(source, it) }
    tabs.startKeptRun(kept.map { it.id }, kept.first().id)
    repeat(kept.size) { upNext.forward() }
    assertEquals(UpNextCard.KeptEnd, upNext.card)
    val tally = upNext.keptTally()

    screenSeesTheSameItemAgain()

    assertEquals(UpNextCard.KeptEnd, upNext.card)
    assertEquals(KeptTally(KeptRunKind.Kept, goneThrough = 4, dropped = emptyList(), later = 4), tally)
    assertEquals(tally, upNext.keptTally())
  }

  @Test
  fun aStartTakesTheCardAwaySoItIsNeverTheFirstThingShown() {
    val time = TestTimeSource()
    val queueStart = QueueStart(tabs, privacy, time, upNext)
    tabs.open(grid("a"), background = false)

    queueStart.launched()
    assertNull(upNext.card)

    upNext.forward()
    assertNotNull(upNext.card)
    queueStart.left()
    time += 4.minutes
    queueStart.returned()
    assertEquals(UpNextCard.End, upNext.card)
    queueStart.left()
    time += 6.minutes
    queueStart.returned()
    assertNull(upNext.card)
  }
}

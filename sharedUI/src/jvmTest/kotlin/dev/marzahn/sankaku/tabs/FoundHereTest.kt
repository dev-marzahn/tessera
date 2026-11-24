package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.ui.components.ShortSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class FoundHereTest {
  private val file = InMemoryTextFile(null)
  private val store = FileTabStateStore(file)
  private val tabs = TabsController(store)
  private val navigator = TabsNavigator(tabs)
  private val undo = ClosedTabUndo(tabs)
  private val deck = SortDeck(tabs, undo)
  private val finds = BlockFinds(tabs, deck)

  private val artistX = Route.Grid(listOf("artist_x"))
  private val artistY = Route.Grid(listOf("order:popularity", "artist_y"))
  private val scenery = Route.Grid(listOf("scenery"))
  private val landscape = Route.Grid(listOf("landscape"))

  private fun goingThroughArtistX(): TabState {
    tabs.open(landscape, background = true)
    navigator.goTo(artistX)
    tabs.activeTab.markTouched(artistX)
    return tabs.activeTab
  }

  private fun tab(route: Route) = tabs.tabs.value.single { it.top == route }

  private fun keepPost(id: String): TabState {
    navigator.openInNewTab(Route.Post(id, "m$id", artistX.tags))
    return tabs.tabs.value.single { (it.top as? Route.Post)?.id == id }
  }

  @Test
  fun aSearchOpenedForLaterFromTheGridIsFoundOnIt() {
    goingThroughArtistX()

    navigator.openInNewTab(scenery)

    val found = assertNotNull(tab(scenery).foundOn)
    assertEquals(listOf("artist_x"), found.search)
    assertEquals(FindWay.Grid, found.way)
    assertEquals("Grid · from Artist X", queueSubtitle(tab(scenery), current = false, seen = false, rating = null))
    assertNull(tab(landscape).foundOn)
  }

  @Test
  fun aTagOnThePostOverTheGridIsFoundOnAPostThere() {
    val x = goingThroughArtistX()
    x.coverChrome(artistX, true)

    navigator.openInNewTab(artistY)

    assertEquals(FindWay.Post, tab(artistY).foundOn?.way)
    assertEquals("from a post in Artist X", originText(tab(artistY)))
  }

  @Test
  fun aSearchFromAKeptPostIsFoundOnItsSourceNowAndLater() {
    goingThroughArtistX()
    val kept = keepPost("1")
    tabs.activate(kept.id)

    navigator.openInNewTab(scenery)
    navigator.goTo(artistY)

    assertEquals(FoundOn(listOf("artist_x"), FindWay.Post, tab(scenery).foundOn!!.at), tab(scenery).foundOn)
    assertEquals(listOf("artist_x"), tab(artistY).foundOn?.search)
    assertEquals(FindWay.Post, tab(artistY).foundOn?.way)
    assertEquals(setOf(tab(artistY), tab(scenery)), tabs.findsOf(artistX.tags).toSet())
  }

  @Test
  fun aRefinementKeepsItsWordingAndIsAFindToo() {
    goingThroughArtistX()

    navigator.goTo(Route.Grid(listOf("artist_x", "sky")))

    val refined = tabs.activeTab
    assertEquals("from ${refined.refinedFrom}", originText(refined))
    assertEquals(listOf(refined), tabs.findsOf(artistX.tags))
  }

  @Test
  fun browseAFeedOrAPostOnItsOwnFindNothing() {
    navigator.openInNewTab(scenery)
    navigator.goTo(Route.Post("9", "m9", emptyList()))
    navigator.openInNewTab(artistY)

    assertNull(tab(scenery).foundOn)
    assertNull(tab(artistY).foundOn)
  }

  @Test
  fun theOriginHoldsAfterARelaunch() {
    goingThroughArtistX()
    navigator.openInNewTab(scenery)
    store.save(tabs.saved())
    assertTrue(file.read()!!.contains(""""foundOn":{"search":["artist_x"]"""), file.read())

    val relaunched = TabsController(FileTabStateStore(file))

    assertEquals(listOf("Scenery"), relaunched.findsOf(artistX.tags).map { it.title })
    assertEquals("from Artist X", originText(relaunched.tabs.value.single { it.title == "Scenery" }))
  }

  @Test
  fun theOriginHoldsAfterItsBlockClosesAndASourceOnItsSearchFindsThemAgain() {
    val x = goingThroughArtistX()
    navigator.openInNewTab(scenery)
    tabs.close(x.id)

    assertEquals("from Artist X", originText(tab(scenery)))
    assertEquals(listOf(tab(scenery)), tabs.findsOf(artistX.tags))

    navigator.goTo(Route.Grid(listOf("artist_x", "order:popularity")))
    assertEquals(listOf(tab(scenery)), tabs.findsOf((tabs.activeTab.top as Route.Grid).tags))
  }

  @Test
  fun aFindKeepsItsOriginThroughUndo() {
    goingThroughArtistX()
    navigator.openInNewTab(scenery)
    val closed = tabs.close(tab(scenery).id)!!

    tabs.reopen(closed)

    assertEquals(listOf("artist_x"), tab(scenery).foundOn?.search)
  }

  @Test
  fun movingOnPastTheBlockWithNewFindsShowsTheCard() {
    goingThroughArtistX()
    navigator.openInNewTab(scenery)
    navigator.openInNewTab(artistY)

    tabs.next()

    val offer = assertNotNull(finds.offer)
    assertEquals("Artist X", offer.name)
    assertEquals(tab(landscape).id, offer.next)
    assertEquals(listOf(tab(scenery), tab(artistY)), finds.finds)
    assertEquals(tab(landscape), tabs.activeTab)
    assertEquals("Found on Artist X", finds.pillText()?.source)
    assertEquals("2", finds.pillText()?.figures)
    assertNull(finds.pillText()?.short)
  }

  @Test
  fun theCardsPillShortensASourceOfSeveralTags() {
    val lighthouseNight = Route.Grid(listOf("lighthouse", "night"))
    tabs.open(landscape, background = true)
    navigator.goTo(lighthouseNight)
    tabs.activeTab.markTouched(lighthouseNight)
    navigator.openInNewTab(scenery)

    tabs.next()

    assertEquals("Found on Lighthouse Night", finds.pillText()?.source)
    assertEquals(ShortSource("Found on Lighthouse", 1), finds.pillText()?.short)
  }

  @Test
  fun aBlockWithNoNewFindsMovesOnAsToday() {
    goingThroughArtistX()
    navigator.openInNewTab(scenery)
    tab(scenery).reportPlace(scenery, GridPlace("p", "c", 0, index = 0))

    tabs.next()

    assertNull(finds.offer)
    assertEquals(tab(landscape), tabs.activeTab)
  }

  @Test
  fun theCardComesOnceABlock() {
    val x = goingThroughArtistX()
    navigator.openInNewTab(scenery)
    tabs.next()
    finds.notNow()

    tabs.activate(x.id)
    tabs.next()

    assertNull(finds.offer)
  }

  @Test
  fun movingBackDoesNotShowTheCard() {
    goingThroughArtistX()
    navigator.openInNewTab(scenery)
    tabs.activate(tab(landscape).id)

    tabs.previous()

    assertNull(finds.offer)
  }

  @Test
  fun closingTheBlocksLastKeptPostShowsTheCard() {
    goingThroughArtistX()
    val kept = keepPost("1")
    tabs.activate(kept.id)
    navigator.openInNewTab(scenery)

    tabs.close(kept.id)

    assertNotNull(finds.offer)
  }

  @Test
  fun notNowGoesOnToTheNextItem() {
    goingThroughArtistX()
    navigator.openInNewTab(scenery)
    tabs.next()

    finds.notNow()

    assertNull(finds.offer)
    assertEquals(tab(landscape), tabs.activeTab)
    assertFalse(deck.running)
  }

  @Test
  fun anotherItemComingForwardTakesTheCardAway() {
    val x = goingThroughArtistX()
    navigator.openInNewTab(scenery)
    tabs.next()

    finds.itemShown(x.id)

    assertNull(finds.offer)
  }

  @Test
  fun goInPutsTheFindAfterTheBlockAndFinishingItReturnsToTheDeck() {
    goingThroughArtistX()
    navigator.openInNewTab(scenery)
    navigator.openInNewTab(artistY)
    tabs.next()

    finds.sortNow()
    assertTrue(deck.running)
    assertEquals(tab(scenery), deck.card)
    deck.goIn()

    assertEquals(listOf("Artist X", "Scenery", "Landscape"), tabs.tabs.value.map { it.title }.take(3))
    assertEquals(tab(scenery), tabs.activeTab)
    tabs.next()
    deck.itemShown(tabs.activeTab.id)
    assertEquals(tab(landscape), tabs.activeTab)
    assertTrue(deck.shown)
    assertEquals(tab(scenery), deck.card)
  }

  @Test
  fun afterTheLastCardTheRunEndsOnTheTallyAndNextGoesOnToTheItemThatWasNext() {
    goingThroughArtistX()
    navigator.openInNewTab(scenery)
    navigator.openInNewTab(artistY)
    tabs.next()
    finds.sortNow()
    deck.goIn()
    deck.backToDeck()

    deck.later()
    deck.later()

    assertTrue(deck.stopped)
    assertEquals(SortDeck.Tally(later = 2, laterWentIn = 1), deck.tally)
    assertEquals("Artist X", deck.foundOn)
    assertFalse(deck.goesBack)
    deck.end()
    assertFalse(deck.running)
    assertEquals(tab(landscape), tabs.activeTab)
  }

  @Test
  fun aDoneOnTheLastCardKeepsTheTallyWithItsUndoAndNextGoesOn() {
    goingThroughArtistX()
    navigator.openInNewTab(scenery)
    tabs.next()
    finds.sortNow()

    deck.done()

    assertTrue(deck.stopped)
    assertEquals(tab(landscape), deck.goesOnTo)
    deck.end()
    assertFalse(deck.running)
    assertEquals(tab(landscape), tabs.activeTab)
  }

  @Test
  fun clearViewedStartsASessionThatOffersTheBlockAgain() {
    val x = goingThroughArtistX()
    navigator.openInNewTab(scenery)
    tabs.next()
    finds.notNow()

    tabs.sessions.startNew()
    tabs.activate(x.id)
    tabs.next()

    assertNotNull(finds.offer)
  }

  @Test
  fun foundHereSortsTheseAndComesBackToTheSource() {
    val x = goingThroughArtistX()
    navigator.openInNewTab(scenery)

    deck.startFinds(tabs.findsOf(artistX.tags), next = null, then = x.id)
    assertEquals(tab(scenery), deck.card)
    assertEquals(x, deck.goesOnTo)
    deck.goIn()
    deck.backToDeck()
    deck.later()

    assertTrue(deck.stopped)
    assertTrue(deck.goesBack)
    deck.end()
    assertFalse(deck.running)
    assertEquals(x, tabs.activeTab)
  }

  @Test
  fun theSortCardSaysWhereAndWhen() {
    val zone = TimeZone.UTC
    val now = Instant.parse("2026-11-14T20:00:00Z")
    val post = FoundOn(listOf("artist_x"), FindWay.Post, Instant.parse("2026-11-14T08:00:00Z").epochSeconds)
    val grid = FoundOn(listOf("artist_x"), FindWay.Grid, Instant.parse("2026-11-10T08:00:00Z").epochSeconds)

    assertEquals("Found on a post in Artist X · today", foundCardText(post, now, zone))
    assertEquals("Found on Artist X · Tuesday", foundCardText(grid, now, zone))
    assertEquals("yesterday", foundDay(Instant.parse("2026-11-13T23:00:00Z"), now, zone))
    assertEquals("2 Nov", foundDay(Instant.parse("2026-11-02T10:00:00Z"), now, zone))
  }

  @Test
  fun aFindsLineSaysHowItsItemAndItsSize() {
    goingThroughArtistX()
    navigator.openInNewTab(scenery)

    assertEquals("from the grid · item 3", findLine(tab(scenery), 3))
  }
}

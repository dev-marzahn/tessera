package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.GridPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SortDeckTest {
  private val tabs = TabsController()
  private val undo = ClosedTabUndo(tabs)
  private val deck = SortDeck(tabs, undo)

  private fun source(tag: String): TabState {
    val route = Route.Grid(listOf(tag))
    tabs.open(route, background = true)
    return tabs.tabs.value.single { it.top == route }
  }

  private fun titles(): List<String> = tabs.tabs.value.map { it.title }

  private fun TabState.started() = reportPlace(top as Route.Grid, GridPlace("p1", "c1", 0, index = 0))

  @Test
  fun theDeckIsTheNewSourcesInQueueOrder() {
    val a = source("artist_a")
    val b = source("artist_b").apply { started() }
    val c = source("artist_c")
    tabs.open(Route.Grid(listOf("order:popularity")), background = true)

    deck.start()

    assertTrue(deck.running)

    assertEquals(a, deck.card)
    assertEquals(2, deck.total)
    deck.later()
    assertEquals(c, deck.card)
    assertFalse(b in newSources(tabs.tabs.value))
  }

  @Test
  fun aNewSourceInsideAnotherBlockIsNoCardSinceLaterCouldNotMoveIt() {
    val a = source("artist_a")
    val b = source("artist_b")
    b.block = a.id

    deck.start()

    assertEquals(1, deck.total)
    assertEquals(a, deck.card)
    deck.later()
    assertEquals(listOf(a, b), tabs.tabs.value.takeLast(2))
    assertEquals(1, deck.tally.later)
  }

  @Test
  fun noNewSourcesStartsNothing() {
    source("artist_a").started()

    deck.start()

    assertFalse(deck.running)
  }

  @Test
  fun laterMovesToTheEndAndTheNextRunMeetsItAfterTheNeverSorted() {
    val a = source("artist_a")
    val b = source("artist_b")
    val c = source("artist_c")
    deck.start()

    deck.later()

    assertEquals(a, tabs.tabs.value.last())
    assertTrue(a in newSources(tabs.tabs.value))
    assertEquals(b, deck.card)
    assertEquals(1, deck.tally.later)
    deck.later()
    deck.later()
    assertTrue(deck.stopped)
    assertNull(deck.card)
    assertEquals(listOf(a, b, c), tabs.tabs.value.filter { it in listOf(a, b, c) })
  }

  @Test
  fun aLaterSourceComesAfterTheOnesNeverSorted() {
    val a = source("artist_a")
    val b = source("artist_b")
    val c = source("artist_c")
    deck.start()
    deck.later()
    deck.later()
    deck.done()
    deck.end()

    deck.start()

    assertEquals(listOf(a, b), (0 until deck.total).map { deck.card.also { deck.later() } })
    assertFalse(c in tabs.tabs.value)
  }

  @Test
  fun sortFromTheQueuePageIsANewRunStartingAtTheCardsNotReached() {
    val a = source("artist_a")
    val b = source("artist_b")
    val c = source("artist_c")
    deck.start()
    deck.later()
    deck.later()
    deck.stop()
    deck.end()

    assertFalse(deck.running)
    assertEquals(3, newSources(tabs.tabs.value).size)
    deck.start()

    assertEquals(c, deck.card)
    assertEquals("1 of 3", "${deck.position + 1} of ${deck.total}")
    assertEquals(SortDeck.Tally(), deck.tally)
    deck.later()
    assertEquals(listOf(a, b), listOf(deck.card, deck.nextCard))
  }

  @Test
  fun sortFromTheQueuePageWhileASourceIsGoneIntoStartsAgainAtItsCard() {
    source("artist_a")
    val b = source("artist_b")
    source("artist_c")
    deck.start()
    deck.later()
    deck.goIn()

    deck.start()

    assertTrue(deck.shown)
    assertNull(deck.goneIn)
    assertEquals(b, deck.card)
    assertEquals(0, deck.position)
    assertEquals(3, deck.total)
    assertNull(deck.wentIn)
  }

  @Test
  fun aNewRunLeavesTheLastRunsDoneToThePillsUndo() {
    source("artist_a")
    val b = source("artist_b")
    deck.start()
    deck.done()
    deck.stop()
    deck.end()

    deck.start()

    assertEquals(b, deck.card)
    assertFalse(deck.canUndoDone)
    assertTrue(undo.pending.value != null)
  }

  @Test
  fun aSourceGoneIntoAndClosedDuringAFindsRunIsADoneOnceTheSortRunIsBack() {
    val a = source("artist_a")
    val b = source("artist_b")
    deck.start()
    deck.goIn()
    a.markTouched(a.top as Route.Grid)
    tabs.open(Route.Grid(listOf("tag_t")), background = true)
    deck.startFinds(tabs.findsOf((a.top as Route.Grid).tags), next = null, then = a.id)
    tabs.close(a.id)

    deck.later()
    deck.end()

    assertTrue(deck.shown)
    assertEquals(b, deck.card)
    assertEquals(1, deck.tally.done)
  }

  @Test
  fun aRunWithEveryCardAnsweredStartsAgainFromTheTop() {
    val a = source("artist_a")
    deck.start()
    deck.later()
    deck.end()
    val b = source("artist_b")

    deck.start()

    assertEquals(a, deck.card)
    assertEquals(b, deck.nextCard)
    assertEquals(0, deck.position)
    assertEquals(SortDeck.Tally(), deck.tally)
  }

  @Test
  fun backFromAnItemWanderedOnToReturnsToTheDeck() {
    assertEquals(QueueBack.Deck, queueBack(cardUp = false, lonePost = false, previous = true, deckWaiting = true))
    assertEquals(QueueBack.Deck, queueBack(cardUp = false, lonePost = true, previous = false, deckWaiting = true))
    assertEquals(QueueBack.Close, queueBack(cardUp = false, lonePost = false, previous = true, opened = true, deckWaiting = true))
    assertEquals(QueueBack.Previous, queueBack(cardUp = false, lonePost = true, previous = true, deckWaiting = true))
    assertEquals(QueueBack.Previous, queueBack(cardUp = true, lonePost = false, previous = true, deckWaiting = true))
  }

  @Test
  fun foundHereOnTheSourceGoneIntoGivesTheSortRunBackOnceItEnds() {
    val a = source("artist_a")
    source("artist_b")
    deck.start()
    deck.goIn()
    a.markTouched(a.top as Route.Grid)
    tabs.open(Route.Grid(listOf("tag_t")), background = true)
    tabs.open(Route.Grid(listOf("tag_u")), background = true)
    val found = tabs.findsOf((a.top as Route.Grid).tags)
    assertEquals(2, found.size)

    deck.startFinds(found, next = null, then = a.id)
    deck.later()
    deck.later()
    assertTrue(deck.stopped)
    deck.end()

    assertTrue(deck.running)
    assertEquals(a.id, tabs.active.value)
    assertEquals(a.id, deck.goneIn)
    assertEquals(0, deck.position)
    assertEquals(2, deck.total)
    deck.backToDeck()
    assertEquals(a, deck.card)
    assertEquals(SortDeck.WentIn(sourceProgress(a), kept = 0, liked = 0), deck.wentIn)
  }

  @Test
  fun sortOnTheQueuePageDuringAFindsRunStartsANewRun() {
    val a = source("artist_a")
    val b = source("artist_b")
    deck.start()
    deck.goIn()
    a.markTouched(a.top as Route.Grid)
    tabs.open(Route.Grid(listOf("tag_t")), background = true)
    deck.startFinds(tabs.findsOf((a.top as Route.Grid).tags), next = null, then = a.id)
    deck.goIn()

    deck.start()

    assertTrue(deck.shown)
    assertNull(deck.goneIn)
    assertNull(deck.foundOn)
    assertEquals(b, deck.card)
    assertEquals(0, deck.position)
    assertEquals(SortDeck.Tally(), deck.tally)
  }

  @Test
  fun doneIsOneRecentlyClosedEntryAndUndoRestoresTheSourceAndItsCard() {
    val a = source("artist_a")
    val b = source("artist_b")
    val before = titles()
    deck.start()

    deck.done()

    assertEquals(1, tabs.closed.value.size)
    assertFalse(tabs.tabs.value.any { it.id == a.id })
    assertEquals(b, deck.card)
    assertEquals(1, deck.tally.done)
    assertTrue(deck.canUndoDone)

    deck.undoDone()

    assertEquals(before, titles())
    assertEquals(a.top, deck.card?.top)
    assertEquals(0, deck.position)
    assertEquals(0, deck.tally.done)
    assertTrue(tabs.closed.value.isEmpty())
  }

  @Test
  fun laterIsSaidAndUndoPutsTheSourceBackWithItsCard() {
    val a = source("artist_a")
    val b = source("artist_b")
    source("artist_c")
    val before = titles()
    deck.start()

    deck.later()

    assertEquals(SortDeck.Answer(SortDeck.Answer.Kind.Later, a.title), deck.lastAnswer)

    deck.undoAnswer()

    assertEquals(before, titles())
    assertEquals(a, deck.card)
    assertEquals(0, deck.position)
    assertEquals(SortDeck.Tally(undone = listOf(SortDeck.Undone(SortDeck.Answer.Kind.Later, a.title))), deck.tally)
    assertNull(deck.lastAnswer)
    deck.later()
    assertEquals(a, tabs.tabs.value.last())
    assertEquals(b, deck.card)
  }

  @Test
  fun laterOnASourceAlreadyLastIsUndoneInPlace() {
    source("artist_a").started()
    val b = source("artist_b")
    deck.start()

    deck.later()
    deck.undoAnswer()

    assertEquals(b, deck.card)
    assertEquals(b, tabs.tabs.value.last())
    assertEquals(0, deck.tally.later)
  }

  @Test
  fun aLaterOnTheLastCardKeepsTheTallyWithItsUndo() {
    source("artist_a")
    val b = source("artist_b")
    deck.start()
    deck.later()

    deck.later()

    assertTrue(deck.stopped)
    assertEquals(SortDeck.Answer(SortDeck.Answer.Kind.Later, b.title), deck.lastAnswer)
    deck.undoAnswer()
    assertFalse(deck.stopped)
    assertEquals(b, deck.card)
  }

  @Test
  fun undoingALaterWhoseNextItemClosedPutsItBeforeTheNextStillOpen() {
    val a = source("artist_a")
    val b = source("artist_b")
    val c = source("artist_c")
    deck.start()
    deck.later()

    tabs.close(b.id)
    deck.undoAnswer()

    assertEquals(listOf(a, c), tabs.tabs.value.filter { it in listOf(a, c) })
    assertEquals(a, deck.card)
  }

  @Test
  fun laterFromTheGridIsSaidAndUndoBringsItsCardBack() {
    val a = source("artist_a")
    val b = source("artist_b")
    deck.start()
    deck.goIn()

    deck.laterFromGrid()

    assertTrue(deck.shown)
    assertEquals(b, deck.card)
    assertEquals(SortDeck.Answer(SortDeck.Answer.Kind.Later, a.title), deck.lastAnswer)
    deck.undoAnswer()
    assertEquals(a, deck.card)
    assertEquals(SortDeck.Tally(undone = listOf(SortDeck.Undone(SortDeck.Answer.Kind.Later, a.title))), deck.tally)
    assertTrue(deck.wentIn != null)
  }

  @Test
  fun theLinesUndoIsOnlyTheLastAnswers() {
    val a = source("artist_a")
    val b = source("artist_b")
    source("artist_c")
    deck.start()

    deck.later()
    deck.done()

    assertEquals(SortDeck.Answer(SortDeck.Answer.Kind.Done, b.title), deck.lastAnswer)
    deck.undoAnswer()
    assertEquals(b.top, deck.card?.top)
    assertNull(deck.lastAnswer)
    assertEquals(a, tabs.tabs.value.last())

    deck.goIn()
    assertNull(deck.lastAnswer)
  }

  @Test
  fun goneInTheDeckComesBackByThePillWithTheCardUnanswered() {
    val a = source("artist_a")
    source("artist_b")
    deck.start()

    deck.goIn()

    assertEquals(a.id, tabs.active.value)
    assertFalse(deck.shown)
    assertEquals(a.id, deck.goneIn)
    assertEquals(0, deck.tally.sorted)

    deck.backToDeck()

    assertTrue(deck.shown)
    assertEquals(a, deck.card)
    assertNull(deck.goneIn)
  }

  @Test
  fun backOnTheSourceGoneIntoGoesToTheDeck() {
    assertEquals(QueueBack.Deck, queueBack(cardUp = false, lonePost = false, previous = true, opened = true, sorting = true))
    assertEquals(QueueBack.Tab, queueBack(cardUp = false, lonePost = false, previous = true, sorting = false))
  }

  @Test
  fun movingOnPastTheSourcesBlockReturnsToTheDeck() {
    val a = source("artist_a")
    val b = source("artist_b")
    deck.start()
    deck.goIn()

    tabs.next()
    deck.itemShown(tabs.active.value)

    assertEquals(b.id, tabs.active.value)
    assertTrue(deck.shown)
    assertEquals(a, deck.card)
  }

  @Test
  fun aTagOpenedForLaterFromTheSourceIsPastItsBlockAndReturnsToTheDeck() {
    val a = source("artist_a")
    val b = source("artist_b")
    tabs.sessions.startNew()
    deck.start()
    deck.goIn()
    tabs.openInBlock(listOf(Route.Post("p1", md5 = "m1", originTags = emptyList(), label = "Post 1", rating = "s")))
    tabs.activate(a.id)
    tabs.open(Route.Grid(listOf("tag_t")), background = true)
    assertEquals(listOf("browse", "artist a", "post 1", "tag t", "artist b"), titles().map { it.lowercase() })

    tabs.next()
    deck.itemShown(tabs.active.value)
    assertFalse(deck.shown)
    tabs.next()
    deck.itemShown(tabs.active.value)

    assertEquals("tag t", tabs.activeTab.title.lowercase())
    assertTrue(deck.shown)
    assertEquals(a, deck.card)
    assertEquals(b, deck.nextCard)
  }

  @Test
  fun aSearchOpenedFromTheSourceDoesNotReturnToTheDeck() {
    val a = source("artist_a")
    source("artist_b")
    deck.start()
    deck.goIn()
    a.markTouched(a.top as Route.Grid)

    tabs.search(Route.Grid(listOf("tag_t")))
    deck.itemShown(tabs.active.value)

    assertFalse(deck.shown)
    assertEquals(a.id, deck.goneIn)
  }

  @Test
  fun aCardGoneIntoAndLeftUnscrolledDoesNotComeRoundAgain() {
    val a = source("artist_a")
    val b = source("artist_b")
    deck.start()
    deck.goIn()
    deck.backToDeck()

    assertTrue(a in newSources(tabs.tabs.value))
    deck.later()
    assertEquals(b, deck.card)
    deck.later()
    assertTrue(deck.stopped)
    assertEquals(0, deck.left)
  }

  @Test
  fun closingTheSourceGoneIntoIsADoneAndTheDeckShowsTheNextCard() {
    val a = source("artist_a")
    val b = source("artist_b")
    deck.start()
    deck.goIn()

    undo.close(a.id)
    deck.itemShown(tabs.active.value)

    assertTrue(deck.shown)
    assertEquals(b, deck.card)
    assertEquals(1, deck.tally.done)
    assertTrue(deck.canUndoDone)
  }

  @Test
  fun doneWithsCloseAndLaterCarryTheRunOn() {
    val a = source("artist_a")
    val b = source("artist_b")
    val c = source("artist_c")
    deck.start()
    deck.goIn()

    deck.closeAndNext()

    assertFalse(tabs.tabs.value.any { it.id == a.id })
    assertEquals(1, tabs.closed.value.size)
    assertTrue(deck.shown)
    assertEquals(b, deck.card)

    deck.goIn()
    deck.laterFromGrid()

    assertEquals(b, tabs.tabs.value.last())
    assertEquals(c, deck.card)
    assertEquals(SortDeck.Tally(later = 1, done = 1, laterWentIn = 1, doneWentIn = 1), deck.tally)
  }

  @Test
  fun undoIsOfferedOnlyUntilTheNextCardIsAnswered() {
    source("artist_a")
    val b = source("artist_b")
    val c = source("artist_c")
    deck.start()
    deck.done()

    deck.later()

    assertFalse(deck.canUndoDone)
    deck.undoDone()
    assertEquals(c, deck.card)
    assertEquals(b, tabs.tabs.value.last())
  }

  @Test
  fun movingBackFromTheSourceGoneIntoDoesNotReturnToTheDeck() {
    val a = source("artist_a")
    val b = source("artist_b")
    deck.start()
    deck.later()
    deck.goIn()

    tabs.previous()
    deck.itemShown(tabs.active.value)

    assertFalse(deck.shown)
    assertEquals(b.id, deck.goneIn)
    tabs.activate(b.id)
    tabs.next()
    deck.itemShown(tabs.active.value)
    assertEquals(a.id, tabs.active.value)
    assertTrue(deck.shown)
  }

  @Test
  fun theTallyCountsEachCardOnceThoughGoneIntoAndAnswered() {
    source("artist_a")
    source("artist_b")
    deck.start()
    deck.goIn()
    deck.backToDeck()
    deck.later()
    deck.done()

    assertEquals(SortDeck.Tally(later = 1, done = 1, laterWentIn = 1), deck.tally)
    assertEquals(2, deck.tally.sorted)
  }

  @Test
  fun theTallyKeepsSortingFromWhereItStopped() {
    source("artist_a")
    val b = source("artist_b")
    source("artist_c")
    deck.start()
    deck.later()

    deck.stop()

    assertNull(deck.card)
    assertEquals(1, deck.tally.sorted)
    assertEquals(2, deck.left)
    deck.keepSorting()
    assertEquals(b, deck.card)
  }

  @Test
  fun aCardSaysItsOrderAndSize() {
    assertEquals("Newest · about 1,900 posts", cardMeta(listOf("artist_a"), SourceSize.About(1900)))
    assertEquals("Popular · about 85 posts", cardMeta(listOf("order:popularity", "artist_a"), SourceSize.About(85)))
    assertEquals("Newest · 120+ posts", cardMeta(listOf("a", "b"), SourceSize.Counted(120, more = true)))
    assertEquals("Newest · 1 post", cardMeta(listOf("a", "b"), SourceSize.Counted(1, more = false)))
    assertEquals("Newest", cardMeta(listOf("a"), null))
    assertEquals("1,234,567", grouped(1234567))
  }

  @Test
  fun aCardSwipedFarEnoughLeftOrFlungLeftIsLater() {
    assertTrue(sortSwipeLater(offset = -400f, velocity = 0f, width = 1000f, flingPx = 1000f))
    assertTrue(sortSwipeLater(offset = -50f, velocity = -2000f, width = 1000f, flingPx = 1000f))
    assertFalse(sortSwipeLater(offset = -200f, velocity = 0f, width = 1000f, flingPx = 1000f))
    assertFalse(sortSwipeLater(offset = 100f, velocity = 2000f, width = 1000f, flingPx = 1000f))
  }

  @Test
  fun aCardGoneIntoSaysWhatWasDoneInside() {
    val likes = mutableSetOf("liked_before")
    val deck = SortDeck(tabs, undo, liked = { likes.toSet() })
    val a = source("artist_a")
    source("artist_b")
    a.reportPreview(a.top as Route.Grid, SourcePreview(emptyList(), about = 104_000))
    deck.start()
    assertNull(deck.wentIn)

    deck.goIn()
    a.reportPlace(a.top as Route.Grid, GridPlace("p40", "c1", 2, index = 39))
    tabs.openInBlock(listOf(Route.Post("p1", md5 = "m1", originTags = emptyList(), label = "Post 1", rating = "s")))
    tabs.openInBlock(listOf(Route.Post("p2", md5 = "m2", originTags = emptyList(), label = "Post 2", rating = "s")))
    likes += "p40"
    tabs.activate(a.id)
    deck.backToDeck()

    val went = deck.wentIn
    assertEquals(SortDeck.WentIn(SourceProgress(40, SourceSize.About(104_000)), kept = 2, liked = 1), went)
    assertEquals("You went in · 40 of about 104,000 · 2 kept · 1 liked", wentInText(went!!))
    deck.goIn()
    likes += "p41"
    deck.backToDeck()
    assertEquals(2, deck.wentIn?.liked)

    deck.later()
    val later = deck.lastAnswer!!
    assertFalse(later.stillNew)
    assertEquals("Artist A moved to the end of the queue", answerText(later))
  }

  @Test
  fun aCardGoneIntoAndLeftUntouchedSaysOnlyThatAndIsStillNewOnLater() {
    source("artist_a")
    source("artist_b")
    deck.start()
    deck.goIn()
    deck.backToDeck()

    assertEquals("You went in", wentInText(deck.wentIn!!))
    deck.later()
    assertEquals("Artist A is last in the queue, still new", answerText(deck.lastAnswer!!))
  }

  @Test
  fun aDoneLeavingKeptPostsSaysTheyStay() {
    val a = source("artist_a")
    source("artist_b")
    deck.start()
    deck.goIn()
    tabs.openInBlock(listOf(Route.Post("p1", md5 = "m1", originTags = emptyList(), label = "Post 1", rating = "s")))
    tabs.openInBlock(listOf(Route.Post("p2", md5 = "m2", originTags = emptyList(), label = "Post 2", rating = "s")))
    tabs.activate(a.id)
    deck.backToDeck()

    deck.done()

    assertEquals("Artist A closed · 2 kept stay", answerText(deck.lastAnswer!!))
    assertEquals(SortDeck.Tally(done = 1, doneWentIn = 1), deck.tally)
    assertEquals("Artist B closed · in Recently closed", answerText(SortDeck.Answer(SortDeck.Answer.Kind.Done, "Artist B")))
  }

  @Test
  fun theTallysAnswersSumToItsTotalAndAnUndoneAnswerIsNotCounted() {
    source("artist_a")
    val b = source("artist_b")
    source("artist_c")
    source("artist_d")
    deck.start()
    deck.done()
    deck.goIn()
    deck.backToDeck()
    deck.later()

    deck.undoAnswer()

    assertEquals(b, deck.card)
    assertEquals(SortDeck.Tally(done = 1, undone = listOf(SortDeck.Undone(SortDeck.Answer.Kind.Later, b.title))), deck.tally)
    assertEquals("undone: Artist B, back in its place", undoneText(deck.tally.undone.single()))
    deck.later()
    deck.done()
    deck.later()
    assertTrue(deck.stopped)
    val tally = deck.tally
    assertEquals(SortDeck.Tally(later = 2, done = 2, laterWentIn = 1), tally)
    assertEquals(4, tally.sorted)
    assertEquals(tally.sorted, SortDeck.Answer.Kind.entries.sumOf { tally.count(it) })
    assertEquals("undone: Artist A, back in the queue", undoneText(SortDeck.Undone(SortDeck.Answer.Kind.Done, "Artist A")))
    assertEquals("Sort 6 more", sortMoreLabel(6))
  }

  @Test
  fun theCurrentItemIsWhatItWasBeforeTheDeck() {
    val current = source("artist_x").apply { started() }
    tabs.activate(current.id)
    source("artist_a")
    source("artist_b")
    deck.start()
    deck.goIn()
    deck.laterFromGrid()
    deck.goIn()
    deck.backToDeck()
    deck.stop()

    deck.end()

    assertEquals(current.id, tabs.active.value)
  }

  @Test
  fun aSourceClosedSinceIsPassedOver() {
    source("artist_a")
    val b = source("artist_b")
    val c = source("artist_c")
    deck.start()
    tabs.close(b.id)

    deck.later()

    assertEquals(c, deck.card)
  }
}

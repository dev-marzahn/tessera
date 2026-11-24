package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.data.AccessibilitySettings
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.ui.components.doneWithLabel
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SortGoneInTest {
  private val tabs = TabsController()
  private val undo = ClosedTabUndo(tabs)
  private val deck = SortDeck(tabs, undo)
  private val upNext = noUpNext(tabs, deck = deck)
  private val settings = AccessibilitySettings(InMemoryTextFile())
  private val blockOffer = BlockOffer()

  private fun goTo(title: String) {
    tabs.activate(tabs.tabs.value.single { it.title == title }.id)
    deck.itemShown(tabs.active.value)
  }

  private fun goneIntoTheFirstOf(vararg tags: String) {
    tags.forEach { tabs.openSource(it) }
    deck.start()
    deck.goIn()
  }

  private fun place(): BlockPlace? = blockPlace(tabs.tabs.value, tabs.tabs.value.indexOfFirst { it.id == tabs.active.value })

  @Test
  fun aSourceWithNothingKeptSaysNextSortAndMovingOnReturnsToTheSortPage() {
    goneIntoTheFirstOf("artist_a", "artist_b")

    assertEquals(Ahead.Sort, upNext.ahead())
    assertEquals("Next: Sort", aheadLabel(upNext.ahead()))

    upNext.forward()

    assertTrue(deck.shown)
  }

  @Test
  fun goneIntoTheQueuesLastBlockSaysNextSortAndMovingOnReturnsToTheSortPage() {
    goneIntoTheFirstOf("artist_a")
    tabs.keep("k1")
    goTo("k1")

    assertEquals(Ahead.Sort, upNext.ahead())
    assertEquals("Next: Sort", blockEndLine(place(), upNext.ahead().title))

    upNext.forward()

    assertTrue(deck.shown)
  }

  @Test
  fun theLastKeptPostSaysNextSortOnItsLineAndToTalkBackAndMovingOnReturns() {
    goneIntoTheFirstOf("artist_a", "artist_b")
    tabs.keep("k1", "k2")

    assertIs<Ahead.Item>(upNext.ahead())
    goTo("k1")
    assertIs<Ahead.Item>(upNext.ahead())
    goTo("k2")

    assertFalse(deck.shown)
    assertEquals(Ahead.Sort, upNext.ahead())
    assertEquals("Next: Sort", blockEndLine(place(), upNext.ahead().title))
    val pill = pillText(place(), 3, 4, null, null, upNext.ahead().title)
    assertTrue(pill.spoken.endsWith(", last, next Sort"), pill.spoken)

    upNext.forward()

    assertTrue(deck.shown)
    assertTrue(tabs.tabs.value.single { it.title == "k2" }.later)
  }

  @Test
  fun afterCloseToItsKeptPostsTheLastSaysNextSortAndMovingOnReturns() {
    goneIntoTheFirstOf("artist_a", "artist_b")
    tabs.keep("k1", "k2")
    deck.closeAndNext()

    assertIs<Ahead.Item>(upNext.ahead())
    goTo("k2")

    assertEquals(Ahead.Sort, upNext.ahead())
    assertEquals("Next: Sort", blockEndLine(place(), upNext.ahead().title))

    upNext.forward()

    assertTrue(deck.shown)
    assertEquals(1, deck.position)
  }

  @Test
  fun dragClosingTheSourceGoneIntoCountsAsDoneAndItsUndoBringsTheCardBack() {
    goneIntoTheFirstOf("artist_a", "artist_b")
    val a = tabs.activeTab

    undo.dragClose(a.id, upNext)

    assertTrue(deck.shown)
    assertEquals(SortDeck.Tally(done = 1, doneWentIn = 1), deck.tally)
    assertEquals(SortDeck.Answer.Kind.Done, deck.lastAnswer?.kind)
    deck.undoDone()
    assertEquals("Artist A", deck.card?.title)
    assertEquals(0, deck.tally.sorted)
  }

  @Test
  fun dragClosingTheSourceGoneIntoWithKeptPostsLandsOnThemCountedDoneOnce() {
    goneIntoTheFirstOf("artist_a", "artist_b")
    val a = tabs.activeTab
    tabs.keep("k1", "k2")

    undo.dragClose(a.id, upNext)

    assertTrue(deck.inKept)
    assertFalse(deck.shown)
    assertEquals("k1", tabs.activeTab.title)
    assertEquals(SortDeck.Tally(done = 1, doneWentIn = 1), deck.tally)
    assertEquals(1, undo.pending.value?.serial)
    undo.undo()
    assertEquals("Artist A", deck.card?.title)
    assertEquals(0, deck.tally.sorted)
  }

  @Test
  fun dragClosingTheLastKeptPostReturnsToTheSortPageWithTheCardUnanswered() {
    goneIntoTheFirstOf("artist_a", "artist_b")
    tabs.keep("k1", "k2")
    goTo("k2")

    undo.dragClose(tabs.active.value, upNext)

    assertTrue(deck.shown)
    assertEquals("Artist A", deck.card?.title)
    assertEquals(0, deck.tally.sorted)
    assertEquals(listOf("k1"), tabs.tabs.value.filter { it.kind.isKept }.map { it.title })
  }

  @Test
  fun outsideSortTheNextSourceIsNamed() {
    tabs.openSource("artist_a")
    tabs.openSource("artist_b")
    goTo("Artist A")

    assertEquals("Artist B", assertIs<Ahead.Item>(upNext.ahead()).title)
  }

  private fun ComposeUiTest.showBar() {
    setContent {
      SankakuTheme {
        Box(Modifier.size(412.dp, 300.dp)) {
          QueueBar(
            onOpenQueue = {},
            controller = tabs,
            undo = undo,
            settings = settings,
            tips = noTips(),
            upNext = upNext,
            blockOffer = blockOffer,
            modifier = Modifier.align(Alignment.BottomCenter),
          )
        }
      }
    }
  }

  private fun ComposeUiTest.pill(): SemanticsNodeInteraction = onNodeWithContentDescription("Queue, ", substring = true)

  private fun ComposeUiTest.actions(): List<String> =
    pill().fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

  private fun SemanticsNodeInteraction.tapsSay(label: String) =
    assert(SemanticsMatcher("taps say $label") { it.config[SemanticsActions.OnClick].label == label })

  @Test
  fun goneInThePillShowsTheBlocksCountAndATapReturnsToTheSortPage() = runComposeUiTest {
    goneIntoTheFirstOf("artist_a", "artist_b")
    tabs.keep("k1", "k2")
    showBar()

    onNodeWithText("2 kept").assertExists()
    onNodeWithText(SORT).assertDoesNotExist()
    pill().tapsSay(BACK_TO_SORT).performClick()

    runOnIdle { assertTrue(deck.shown) }
  }

  @Test
  fun onAKeptPostGoneIntoThePillShowsHowManyAreGoneThrough() = runComposeUiTest {
    goneIntoTheFirstOf("artist_a", "artist_b")
    tabs.keep("k1", "k2")
    goTo("k1")
    showBar()

    onNodeWithContentDescription("Queue, Artist A, 0 of 2 kept posts gone through, kept post 1 of 2", substring = true).assertExists()
    onNodeWithText(SORT).assertDoesNotExist()
  }

  @Test
  fun onTheLastKeptPostTheLineTheNextArrowAndTalkBackSayNextSort() = runComposeUiTest {
    goneIntoTheFirstOf("artist_a", "artist_b")
    tabs.keep("k1", "k2")
    goTo("k2")
    settings.setShowQueueButtons(true)
    showBar()

    onNodeWithText("Next: Sort", useUnmergedTree = true).assertExists()
    onNodeWithContentDescription(Ahead.Sort.label).assertExists()
    assertTrue(Ahead.Sort.label in actions(), "${actions()}")
  }

  @Test
  fun goneInDoneWithIsNotOfferedOnTheBar() = runComposeUiTest {
    goneIntoTheFirstOf("artist_a", "artist_b")
    blockOffer.pending = tabs.active.value
    blockOffer.title = "Artist A"
    showBar()

    assertFalse(actions().any { it.startsWith(doneWithLabel("Artist A")) }, "${actions()}")
    runOnIdle { deck.end() }
    assertTrue(actions().any { it.startsWith(doneWithLabel("Artist A")) }, "${actions()}")
  }

  @Test
  fun aRunOverABlocksFindsShowsTheBlocksPillAndReturnsToItsDeck() = runComposeUiTest {
    val a = tabs.openSource("artist_a")
    val b = tabs.openSource("artist_b")
    val then = tabs.openSource("cat")
    deck.startFinds(listOf(a, b), next = then.id, then = then.id)
    deck.goIn()
    tabs.keep("k1")
    showBar()

    assertEquals(Ahead.Item(tabs.tabs.value.single { it.title == "k1" }), upNext.ahead())
    onNodeWithText("1 kept").assertExists()
    pill().tapsSay(BACK_TO_SORT).performClick()
    runOnIdle {
      assertTrue(deck.shown)
      deck.goIn()
      goTo("k1")
      assertEquals(Ahead.Sort, upNext.ahead())
      upNext.forward()
      assertTrue(deck.shown)
    }
  }

  private fun ComposeUiTest.showHost(content: @Composable (TabState) -> Unit) {
    setContent {
      val activeId by tabs.active.collectAsState()
      val tabList by tabs.tabs.collectAsState()
      val active = tabList.first { it.id == activeId }
      Box(Modifier.size(400.dp)) {
        QueueSwipeHost(active, tabs = tabs, upNext = upNext, undo = undo, preload = { _, _ -> emptyMap() }, content = {
          content(active)
        })
      }
    }
  }

  @Test
  fun onTheLastKeptPostTheClosePeekIsTheSortPage() = runComposeUiTest {
    goneIntoTheFirstOf("artist_a", "artist_b")
    tabs.keep("k1", "k2")
    goTo("k2")
    showHost {
      LocalQueueClosePeek.current?.let { peek ->
        peek.behind()
        peek.Line()
      }
    }

    onNodeWithText("Next: Sort", useUnmergedTree = true).assertExists()
    onNodeWithText("Leaving Artist A", useUnmergedTree = true).assertExists()
  }

  @Test
  fun onTheLastKeptPostTheSwipePeekIsTheSortPage() = runComposeUiTest {
    goneIntoTheFirstOf("artist_a", "artist_b")
    tabs.keep("k1", "k2")
    goTo("k2")
    showHost { active -> Box(Modifier.fillMaxSize().testTag(SWIPED).queueSwipe(enabled = true, from = active.id)) }

    onNodeWithTag(SWIPED).performTouchInput {
      down(center)
      repeat(10) { moveBy(Offset(-15f, 0f)) }
    }

    onNodeWithText(SORT, useUnmergedTree = true).assertExists()
    onNodeWithText("Leaving Artist A", useUnmergedTree = true).assertExists()
  }
}

private const val SWIPED = "swiped"

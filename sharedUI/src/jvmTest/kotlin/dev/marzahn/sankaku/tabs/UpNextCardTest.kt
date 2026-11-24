package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.AccessibilitySettings
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.ui.Tips
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class UpNextCardTest {
  private val start = Instant.parse("2026-11-01T12:00:00Z")
  private var now = start
  private val clock = object : Clock {
    override fun now() = now
  }
  private val tabs = TabsController(clock = clock)
  private val upNext = noUpNext(tabs)

  private fun grid(vararg tags: String) = Route.Grid(tags.toList())

  private fun untouchedForWeeks() {
    tabs.open(grid("oldest"), background = true)
    tabs.open(grid("middle"), background = true)
    tabs.open(grid("last"), background = false)
    now = start + 40.days
    assertEquals(listOf("Browse", "Oldest", "Middle", "Last"), tabs.tabs.value.map { it.title })
  }

  private fun ComposeUiTest.shell() {
    setContent {
      SankakuTheme {
        val activeId by tabs.active.collectAsState()
        val tabList by tabs.tabs.collectAsState()
        val active = tabList.first { it.id == activeId }
        LaunchedEffect(active.id) { upNext.dismiss() }
        Box(Modifier.size(400.dp).testTag("shell")) {
          QueueSwipeHost(active, tabs = tabs, upNext = upNext, undo = ClosedTabUndo(tabs), preload = { _, _ -> emptyMap() }, content = {
            Box(Modifier.size(400.dp).testTag("grid").queueSwipe(enabled = true, from = active.id)) {
              Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { Box(Modifier.height(4000.dp)) }
            }
          })
        }
      }
    }
  }

  private fun ComposeUiTest.swipeTowardsTheEnd() {
    onNodeWithTag("grid").performTouchInput { swipe(Offset(width - 20f, centerY), Offset(20f, centerY), 300) }
    waitForIdle()
  }

  private fun ComposeUiTest.swipeOnShown(back: Boolean) {
    onNodeWithTag("shell").performTouchInput {
      if (back) swipe(Offset(20f, centerY), Offset(width - 20f, centerY), 300) else swipe(Offset(width - 20f, centerY), Offset(20f, centerY), 300)
    }
    waitForIdle()
  }

  private fun ComposeUiTest.titleActions() =
    onNode(isHeading()).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

  private fun ComposeUiTest.paneTitle() =
    onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).fetchSemanticsNode().config[SemanticsProperties.PaneTitle]

  @Test
  fun aSwipePastTheLastItemShowsTheEndOfTheQueueNeverAnUpNextOffer() = runComposeUiTest {
    untouchedForWeeks()
    shell()

    swipeTowardsTheEnd()

    assertEquals(UpNextCard.End, upNext.card)
    onNodeWithTag(END_CARD).assertExists()
    onNodeWithText("You've reached the end").assertExists()
    onNodeWithText("Back to the start").assertExists()
    onNodeWithText("Browse").assertExists()
    onNodeWithText("Pick up", substring = true).assertDoesNotExist()
    onNodeWithText("Last chance", substring = true).assertDoesNotExist()
    onNodeWithTag("grid").assertDoesNotExist()
    assertEquals("Last", tabs.activeTab.title)
  }

  @Test
  fun theSearchLineShowsNoSearchOnTheCardNeverTheGridsBehind() {
    tabs.open(grid("a"), background = false)
    val top = tabs.activeTab.top
    assertEquals(top, shownSearch(null, top))

    upNext.forward()
    assertEquals(UpNextCard.End, upNext.card)
    assertEquals(grid(), shownSearch(upNext.card, top))
  }

  @Test
  fun backToTheStartGoesToItemOne() = runComposeUiTest {
    tabs.open(grid("a"), background = false)
    shell()
    swipeTowardsTheEnd()

    onNodeWithContentDescription("Back to the start of the queue").performClick()
    waitForIdle()

    assertNull(upNext.card)
    assertEquals(tabs.tabs.value.first().id, tabs.activeTab.id)
    onNodeWithTag("grid").assertExists()
  }

  @Test
  fun theEndOfTheQueueTitleIsAHeadingTheFocusMovesToAndTheActionsAreButtonsAndCustomActions() = runComposeUiTest {
    tabs.open(grid("a"), background = false)
    shell()
    swipeTowardsTheEnd()

    onNode(isHeading()).assertIsFocused()
    assertEquals(END_PANE, paneTitle())
    assertEquals(listOf("Back to the start", "Browse", "Back to A"), titleActions())
    onNodeWithContentDescription("Browse, a new grid").assert(hasClickAction())

    val browse = onNode(isHeading()).fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Browse" }
    runOnIdle { browse.action() }
    waitForIdle()
    assertNull(upNext.card)
    assertEquals(grid(), tabs.activeTab.top)
  }

  @Test
  fun aSwipeBackOnTheEndOfTheQueueShowsTheLastItem() = runComposeUiTest {
    tabs.open(grid("a"), background = false)
    shell()
    swipeTowardsTheEnd()
    assertEquals(UpNextCard.End, upNext.card)

    swipeOnShown(back = true)

    assertNull(upNext.card)
    onNodeWithTag("grid").assertExists()
    assertEquals("A", tabs.activeTab.title)
  }

  @Test
  fun aSwipeForwardOnTheCardDoesNothing() = runComposeUiTest {
    untouchedForWeeks()
    shell()
    swipeTowardsTheEnd()
    assertEquals(UpNextCard.End, upNext.card)

    swipeOnShown(back = false)

    assertEquals(UpNextCard.End, upNext.card)
    assertEquals("Last", tabs.activeTab.title)
  }

  @Test
  fun theSwipeBackIsACustomActionOnTheCardsTitle() = runComposeUiTest {
    tabs.open(grid("a"), background = false)
    shell()
    swipeTowardsTheEnd()

    val back = onNode(isHeading()).fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Back to A" }
    runOnIdle { back.action() }
    waitForIdle()

    assertNull(upNext.card)
    assertEquals("A", tabs.activeTab.title)
  }

  private fun ComposeUiTest.pill(buttons: Boolean) {
    val settings = AccessibilitySettings(InMemoryTextFile()).apply { setShowQueueButtons(buttons) }
    setContent {
      SankakuTheme {
        Box(Modifier.size(412.dp, 300.dp)) {
          QueueBar(
            onOpenQueue = {},
            controller = tabs,
            undo = ClosedTabUndo(tabs),
            settings = settings,
            tips = Tips(InMemoryTextFile(), touch = false),
            upNext = upNext,
          )
        }
      }
    }
  }

  @Test
  fun theCountPillsArrowsGoToTheEndAndBack() = runComposeUiTest {
    untouchedForWeeks()
    pill(buttons = true)

    onNodeWithContentDescription(END_PANE).performClick()
    waitForIdle()
    assertEquals(UpNextCard.End, upNext.card)
    onNodeWithContentDescription(NEXT_ITEM, substring = true).assertIsNotEnabled()

    onNodeWithContentDescription("Back to Last").performClick()
    waitForIdle()
    assertNull(upNext.card)
    assertEquals("Last", tabs.activeTab.title)
  }

  @Test
  fun theCountPillOffersTheEndToTalkBack() = runComposeUiTest {
    untouchedForWeeks()
    pill(buttons = false)
    fun actions() = onNodeWithContentDescription("Queue, ", substring = true)
      .fetchSemanticsNode().config[SemanticsActions.CustomActions]

    runOnIdle { actions().single { it.label == END_PANE }.action() }
    waitForIdle()
    assertEquals(UpNextCard.End, upNext.card)
    assertTrue(actions().none { it.label == NEXT_ITEM || it.label == END_PANE })
  }
}

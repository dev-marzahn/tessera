package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.data.AccessibilitySettings
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.ui.Tip
import dev.marzahn.sankaku.ui.Tips
import dev.marzahn.sankaku.ui.keptWaitsText
import dev.marzahn.sankaku.ui.openedInFrontText
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import dev.marzahn.sankaku.ui.components.TipLayer
import dev.marzahn.sankaku.ui.components.CLOSE_LABEL
import dev.marzahn.sankaku.ui.components.REOPEN_LABEL
import dev.marzahn.sankaku.ui.UserGestures
import dev.marzahn.sankaku.ui.observeGestures
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val AROUND_THE_BAR = "aroundTheBar"

@OptIn(ExperimentalTestApi::class)
class QueueBarTest {
  private val tabs = TabsController().apply {
    open(Route.Grid(listOf("a")), background = true)
    open(Route.Grid(listOf("b")), background = true)
  }
  private val gestures = UserGestures()
  private val undo = ClosedTabUndo(tabs, gestures = gestures)
  private val settings = AccessibilitySettings(InMemoryTextFile())

  private fun ComposeUiTest.pill(): SemanticsNodeInteraction = onNodeWithContentDescription("Queue, item", substring = true)

  private fun ComposeUiTest.actions(): List<String> =
    pill().fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

  private fun ComposeUiTest.runAction(label: String) {
    val action = pill().fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
    runOnIdle { action.action() }
  }

  private fun ComposeUiTest.showBar(
    controller: TabsController = tabs,
    closedUndo: ClosedTabUndo = undo,
    onOpen: () -> Unit = {},
    width: Dp = 412.dp,
    endsAtNextAction: Boolean = true,
    fontScale: Float = 1f,
  ) {
    setContent {
      SankakuTheme {
        if (endsAtNextAction) EndUndoAtNextAction(closedUndo, controller)
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
          Box(Modifier.size(width, 300.dp).testTag(AROUND_THE_BAR).observeGestures(gestures)) {
            QueueBar(
              onOpenQueue = onOpen,
              bottom = BottomRowLayout.roomAbove(navigationBar = 24.dp),
              controller = controller,
              undo = closedUndo,
              settings = settings,
              tips = noTips(),
              upNext = noUpNext(controller),
              modifier = Modifier.align(Alignment.BottomCenter),
            )
          }
        }
      }
    }
  }

  @Test
  fun undoIsTheSameCircleBesideAnyPillAtAnyTextSize() {
    for (width in listOf(360.dp, 412.dp)) for (fontScale in listOf(1f, 2f)) for (titled in listOf(false, true)) {
      runComposeUiTest {
        val queue = if (titled) blocks().apply { show("p1") } else TabsController().apply {
          open(Route.Grid(listOf("a")), background = true)
          open(Route.Grid(listOf("b")), background = true)
        }
        val closed = ClosedTabUndo(queue)
        showBar(controller = queue, closedUndo = closed, width = width, endsAtNextAction = false, fontScale = fontScale)
        runOnIdle { closed.close(queue.activeTab.id) }

        val case = "$width at ${fontScale}×, ${if (titled) "titled" else "plain"} pill"
        if (titled) onNodeWithText("Landscape").assertExists() else onNodeWithText("Landscape").assertDoesNotExist()
        val chip = onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertHasClickAction().getUnclippedBoundsInRoot()
        val pill = onNodeWithTag(COUNT_PILL).getUnclippedBoundsInRoot()
        assertEquals(48f, (chip.right - chip.left).value, 0.5f, "$case: $chip")
        assertEquals(48f, (chip.bottom - chip.top).value, 0.5f, "$case: $chip")
        assertEquals(8f, (pill.left - chip.right).value, 0.5f, "$case: undo $chip, pill $pill")
        assertEquals(pill.centre.y.value, chip.centre.y.value, 0.5f, "$case: undo $chip off the pill's line $pill")
        assertTrue(chip.left >= 8.dp, "$case: undo $chip within 8 dp of the edge")
        val figures = onNodeWithText(if (titled) "1 of 2" else "1 / 2").getUnclippedBoundsInRoot()
        assertTrue(figures.left >= pill.left && figures.right <= pill.right, "$case: figures $figures, pill $pill")
        val before = queue.tabs.value.size
        onNodeWithContentDescription(UNDO_CLOSE_LABEL).performClick()
        assertEquals(before + 1, queue.tabs.value.size, "$case: the tap didn't undo")
      }
    }
  }

  @Test
  fun thePillShowsThePlaceInTheQueueOnTheRowsLineAndOpensTheQueuePage() = runComposeUiTest {
    var opened = 0
    showBar(onOpen = { opened++ })

    onNodeWithText("1 / 3").assertExists()
    val pill = onNodeWithTag(COUNT_PILL).getUnclippedBoundsInRoot()
    assertEquals(206f, pill.centre.x.value, 1f, "$pill")
    assertEquals((300.dp - 24.dp - 32.dp).value, pill.centre.y.value, 0.5f)
    pill().performClick()
    assertEquals(1, opened)
  }

  @Test
  fun withoutTheSettingThereIsNoCloseAndNoArrows() = runComposeUiTest {
    showBar()

    onNodeWithContentDescription(CLOSE_LABEL).assertDoesNotExist()
    onNodeWithContentDescription(PREVIOUS_ITEM, substring = true).assertDoesNotExist()
    onNodeWithContentDescription(NEXT_ITEM, substring = true).assertDoesNotExist()
  }

  @Test
  fun oneItemStillReadsOneOfOne() = runComposeUiTest {
    val one = TabsController()
    showBar(controller = one, closedUndo = ClosedTabUndo(one))

    onNodeWithText("1 / 1").assertExists()
    pill().assertHasClickAction()
  }

  @Test
  fun followingIsNotCountedAndCannotBeClosed() = runComposeUiTest {
    tabs.pinFeeds(listOf(Feed.Following))
    settings.setShowQueueButtons(true)
    showBar(endsAtNextAction = false)

    onNodeWithText("1 / 3").assertExists()
    runOnIdle { tabs.activate(tabs.tabs.value.last().id) }

    onNodeWithText("Following").assertExists()
    onNodeWithContentDescription("Queue, Following, after 3 items").assertExists()
    onNodeWithContentDescription(CLOSE_LABEL).assertDoesNotExist()
    val actions = onNodeWithContentDescription("Queue, Following", substring = true)
      .fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
    assertFalse(CLOSE_LABEL in actions, "$actions")
    assertTrue(PREVIOUS_ITEM in actions, "$actions")
  }

  @Test
  fun talkBackHasPreviousNextAndCloseWithoutTheSetting() = runComposeUiTest {
    tabs.activate(tabs.tabs.value[1].id)
    showBar(endsAtNextAction = false)

    assertEquals(listOf(PREVIOUS_ITEM, NEXT_ITEM, CLOSE_LABEL), actions())
    runAction(NEXT_ITEM)
    assertEquals("B", tabs.activeTab.title)
    assertEquals(listOf(PREVIOUS_ITEM, END_PANE, CLOSE_LABEL), actions())
    runAction(PREVIOUS_ITEM)
    assertEquals("A", tabs.activeTab.title)

    runAction(CLOSE_LABEL)
    assertEquals(listOf("Browse", "B"), tabs.tabs.value.map { it.title })
    assertEquals(listOf(PREVIOUS_ITEM, END_PANE, CLOSE_LABEL, UNDO_CLOSE_LABEL, REOPEN_LABEL), actions())
    runAction(UNDO_CLOSE_LABEL)
    assertEquals(listOf("Browse", "A", "B"), tabs.tabs.value.map { it.title })
    assertEquals("A", tabs.activeTab.title)
  }

  @Test
  fun aCloseOfSeveralIsAnnouncedWithItsCount() = runComposeUiTest {
    showBar(endsAtNextAction = false)
    runOnIdle { tabs.closeAll(tabs.tabs.value.drop(1).map { it.id })?.let { undo.opened(it) } }

    onNode(liveRegion("Closed 2 items. Undo available")).assertExists()
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertExists()
  }

  @Test
  fun theUndoAfterAnUnkeepSaysWhatItUndoes() = runComposeUiTest {
    showBar(endsAtNextAction = false)
    runOnIdle { undo.close(tabs.activeTab.id, Closing.Unkept) }

    onNode(liveRegion("Removed from queue. Undo available")).assertExists()
    onNodeWithContentDescription("Undo remove").assertHasClickAction()
    assertTrue("Undo remove" in actions())
  }

  @Test
  fun theUndoKeepsItsWordsWhileItLeaves() = runComposeUiTest {
    mainClock.autoAdvance = false
    showBar(endsAtNextAction = false)
    runOnIdle { undo.close(tabs.activeTab.id, Closing.Unkept) }
    mainClock.advanceTimeBy(1_000)

    runOnIdle { undo.undo() }
    mainClock.advanceTimeByFrame()

    onNodeWithContentDescription("Undo remove").assertExists()
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertDoesNotExist()
  }

  @Test
  fun undoGrowsOutOfThePillsLeftAndReopensWhatClosed() = runComposeUiTest {
    tabs.activate(tabs.tabs.value[1].id)
    showBar(endsAtNextAction = false)
    val before = onNodeWithTag(COUNT_PILL).getUnclippedBoundsInRoot()
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertDoesNotExist()

    runOnIdle { undo.dragClose(tabs.activeTab.id, noUpNext(tabs)) }

    val chip = onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertHasClickAction()
    chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    val bounds = chip.getUnclippedBoundsInRoot()
    val after = onNodeWithTag(COUNT_PILL).getUnclippedBoundsInRoot()
    assertEquals(before, after, "the pill moved")
    assertEquals(after.left - 8.dp, bounds.right)
    assertEquals(after.centre.y, bounds.centre.y)
    assertTrue(bounds.bottom - bounds.top >= 48.dp)
    onNode(liveRegion("Closed “A”. Undo available")).assertExists()

    chip.performClick()
    assertEquals(listOf("Browse", "A", "B"), tabs.tabs.value.map { it.title })
    waitForIdle()
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertDoesNotExist()
  }

  private fun ComposeUiTest.undoWidths(): List<Float> {
    val chips = onAllNodesWithContentDescription(UNDO_CLOSE_LABEL)
    return List(chips.fetchSemanticsNodes().size) { i -> chips[i].getBoundsInRoot().let { it.right - it.left }.value }
  }

  private fun ComposeUiTest.showTwoBars(second: () -> Boolean) {
    mainClock.autoAdvance = false
    setContent {
      SankakuTheme {
        Column {
          val bar = @Composable {
            QueueBar(onOpenQueue = {}, controller = tabs, undo = undo, settings = settings, tips = noTips(), upNext = noUpNext(tabs))
          }
          bar()
          if (second()) bar()
        }
      }
    }
  }

  private fun ComposeUiTest.lastUndoWidths(name: String, frames: Int = 30): List<Float> = List(frames) {
    mainClock.advanceTimeByFrame()
    undoWidths().last()
  }.also { println("$name, undo width per frame (dp): ${it.map { w -> w.roundToInt() }}") }

  private fun assertGrows(widths: List<Float>, full: Float) {
    assertTrue(widths.first() < full / 2, "$widths")
    assertTrue(widths.count { it > 1f && it < full - 1f } >= 4, "$widths")
    assertEquals(full, widths.last(), 0.5f, "$widths")
  }

  @Test
  fun undoGrowsInABarComposedAfterTheCloseAndALaterBarShowsItAtOnce() = runComposeUiTest {
    runOnIdle { undo.dragClose(tabs.activeTab.id, noUpNext(tabs)) }
    var second by mutableStateOf(false)
    showTwoBars { second }

    val widths = lastUndoWidths("bar composed after the close")
    val full = BottomRowLayout.PieceTarget.value
    assertGrows(widths, full)

    mainClock.advanceTimeBy(1_000)
    second = true
    mainClock.advanceTimeByFrame()
    val both = undoWidths()
    println("later bar's first frame (dp): ${both.map { it.roundToInt() }}")
    assertEquals(2, both.size)
    both.forEach { assertEquals(full, it, 0.5f) }
  }

  @Test
  fun aSecondCloseWhileUndoShowsStillGrowsInTheNextBar() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("c")), background = true)
    var second by mutableStateOf(false)
    showTwoBars { second }
    runOnIdle { undo.close(tabs.activeTab.id) }
    val full = lastUndoWidths("first close, in place").last()
    mainClock.advanceTimeBy(1_000)

    runOnIdle { undo.dragClose(tabs.activeTab.id, noUpNext(tabs)) }
    mainClock.advanceTimeBy(1_000)
    second = true
    assertGrows(lastUndoWidths("second close, the next bar"), full)
  }

  @Test
  fun undoStaysUntilTheNextActionHoweverLongThatTakes() = runComposeUiTest {
    showBar()
    val queue = runOnIdle { tabs.tabs.value.map { it.top } }
    runOnIdle { undo.close(tabs.activeTab.id) }

    mainClock.advanceTimeBy(60_000)
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).performClick()
    runOnIdle {
      assertEquals(queue, tabs.tabs.value.map { it.top })
      assertNull(undo.pending.value)
    }
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertDoesNotExist()
  }

  @Test
  fun aTapWhileThePillIsHiddenDoesntEndUndo() = runComposeUiTest {
    showBar()
    val chrome = Any()
    runOnIdle { undo.close(tabs.activeTab.id) }
    runOnIdle { undo.hide(chrome, true) }
    onNodeWithTag(AROUND_THE_BAR).performTouchInput { click(Offset(10f, 10f)) }
    onNodeWithTag(AROUND_THE_BAR).performTouchInput { down(Offset(10f, 10f)) }
    runOnIdle { undo.hide(chrome, false) }
    onNodeWithTag(AROUND_THE_BAR).performTouchInput { up() }
    mainClock.advanceTimeBy(60_000)
    runOnIdle { assertTrue(undo.pending.value != null) }
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertExists()

    onNodeWithTag(AROUND_THE_BAR).performTouchInput { click(Offset(10f, 10f)) }
    runOnIdle { assertNull(undo.pending.value) }
  }

  @Test
  fun theNextActionEndsUndo() = runComposeUiTest {
    showBar()
    runOnIdle { undo.close(tabs.activeTab.id) }
    mainClock.advanceTimeBy(60_000)
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertExists()

    onNodeWithTag(AROUND_THE_BAR).performTouchInput { click(Offset(10f, 10f)) }
    runOnIdle { assertNull(undo.pending.value) }
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertDoesNotExist()
    assertTrue(REOPEN_LABEL in actions())

    runOnIdle { undo.close(tabs.activeTab.id) }
    mainClock.advanceTimeBy(60_000)
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertExists()
    runOnIdle { tabs.open(Route.Grid(listOf("c")), background = true) }
    runOnIdle { assertNull(undo.pending.value) }
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertDoesNotExist()
  }

  @Test
  fun theReopenTipShowsOnThePillOnceUndoHasGoneAndNotAgain() = runComposeUiTest {
    mainClock.autoAdvance = false
    val file = InMemoryTextFile()
    val tips = Tips(file, touch = true).apply { used(Tip.QueueSwipe) }
    val tipped = ClosedTabUndo(tabs, tips)
    setContent {
      SankakuTheme {
        EndUndoAtNextAction(tipped, tabs)
        QueueBar(onOpenQueue = {}, controller = tabs, undo = tipped, settings = settings, tips = tips, upNext = noUpNext(tabs))
        TipLayer(tips)
      }
    }
    runOnIdle { tipped.close(tabs.activeTab.id) }
    mainClock.advanceTimeBy(1_000)
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertExists()
    onNodeWithText(Tip.ReopenHold.text).assertDoesNotExist()

    runOnIdle { tabs.open(Route.Grid(listOf("c")), background = true) }
    mainClock.advanceTimeByFrame()
    assertEquals(null, tipped.pending.value)
    mainClock.advanceTimeBy(1_000)
    onNodeWithText(Tip.ReopenHold.text).assertExists()
    assertEquals(Tip.ReopenHold, tips.current)

    onNodeWithText("Got it").performClick()
    mainClock.advanceTimeBy(1_000)
    onNodeWithText(Tip.ReopenHold.text).assertDoesNotExist()
    assertEquals(null, tips.current)
    runOnIdle { tipped.close(tabs.activeTab.id) }
    mainClock.advanceTimeBy(1_000)
    runOnIdle { tabs.open(Route.Grid(listOf("d")), background = true) }
    mainClock.advanceTimeBy(1_000)
    assertEquals(null, tipped.pending.value)
    onNodeWithText(Tip.ReopenHold.text).assertDoesNotExist()
    assertFalse(Tips(file, touch = true).pending(Tip.ReopenHold))
  }

  @Test
  fun aReopenTipWhoseMomentPassedBeforeItsAnchorCameFreesItsPlace() = runComposeUiTest {
    val tips = Tips(InMemoryTextFile(), touch = true).apply { used(Tip.QueueSwipe) }
    val tipped = ClosedTabUndo(tabs, tips)
    tipped.close(tabs.activeTab.id)
    assertTrue(tips.offer(Tip.ReopenHold))
    setContent {
      SankakuTheme {
        QueueBar(onOpenQueue = {}, controller = tabs, undo = tipped, settings = settings, tips = tips, upNext = noUpNext(tabs))
      }
    }
    waitForIdle()
    assertEquals(null, tips.current)
    assertTrue(tips.pending(Tip.ReopenHold))
  }

  @Test
  fun anOpenOnlyPillOffersNoReopenTipAndNoUndo() = runComposeUiTest {
    val tips = Tips(InMemoryTextFile(), touch = true).apply { used(Tip.QueueSwipe) }
    val tipped = ClosedTabUndo(tabs, tips)
    tipped.close(tabs.activeTab.id)
    setContent {
      SankakuTheme {
        QueueBar(onOpenQueue = {}, controller = tabs, undo = tipped, settings = settings, tips = tips, upNext = noUpNext(tabs), openOnly = true)
      }
    }
    mainClock.advanceTimeBy(1_000)
    onNodeWithContentDescription(UNDO_CLOSE_LABEL).assertDoesNotExist()
    runOnIdle { tipped.end(tipped.pending.value!!) }
    mainClock.advanceTimeBy(2_000)
    onNodeWithText(Tip.ReopenHold.text).assertDoesNotExist()
    assertTrue(tips.current != Tip.ReopenHold)
  }

  @Test
  fun movingOnWithThePillsNextButtonDoesWhatTheSwipeTipTeaches() = runComposeUiTest {
    val file = InMemoryTextFile()
    val tips = Tips(file, touch = true).apply { offer(Tip.GridGestures) }
    settings.setShowQueueButtons(true)
    setContent {
      SankakuTheme {
        QueueBar(onOpenQueue = {}, controller = tabs, undo = undo, settings = settings, tips = tips, upNext = noUpNext(tabs))
      }
    }
    assertTrue(tips.pending(Tip.QueueSwipe))
    onNodeWithContentDescription("$NEXT_ITEM: A").performSemanticsAction(SemanticsActions.OnClick)

    assertEquals("A", tabs.activeTab.title)
    assertFalse(Tips(file, touch = true).pending(Tip.QueueSwipe))
  }

  @Test
  fun aSearchOpenedInFrontOfAnItemGetsItsTipFirstWithShowQueue() = runComposeUiTest {
    mainClock.autoAdvance = false
    val queue = TabsController().apply { open(Route.Grid(listOf("landscape")), background = false) }
    queue.activeTab.markTouched(queue.activeTab.top as Route.Grid)
    dev.marzahn.sankaku.TabsNavigator(queue).goTo(Route.Grid(listOf("tree")))
    assertEquals(listOf("Browse", "Tree", "Landscape"), queue.tabs.value.map { it.title })
    val tips = Tips(InMemoryTextFile(), touch = true)
    var queuePage = false
    setContent {
      SankakuTheme {
        QueueBar(onOpenQueue = { queuePage = true }, controller = queue, undo = ClosedTabUndo(queue), settings = settings, tips = tips, upNext = noUpNext(queue))
        TipLayer(tips)
      }
    }
    mainClock.advanceTimeBy(1_000)
    onNodeWithText(openedInFrontText("Tree")).assertExists()
    assertTrue(tips.pending(Tip.QueueSwipe))

    onNodeWithText(SHOW_QUEUE).performClick()
    mainClock.advanceTimeBy(1_000)
    assertTrue(queuePage)
    onNodeWithText(openedInFrontText("Tree")).assertDoesNotExist()
    assertFalse(tips.pending(Tip.OpenedInFront))
  }

  @Test
  fun aSourceWithKeptPostsGetsTheKeptTip() = runComposeUiTest {
    mainClock.autoAdvance = false
    val queue = blocks()
    queue.show("Landscape")
    val tips = Tips(InMemoryTextFile(), touch = true)
    setContent {
      SankakuTheme {
        QueueBar(onOpenQueue = {}, controller = queue, undo = ClosedTabUndo(queue), settings = settings, tips = tips, upNext = noUpNext(queue))
        TipLayer(tips)
      }
    }
    mainClock.advanceTimeBy(1_000)
    onNodeWithText(keptWaitsText("Landscape")).assertExists()

    runOnIdle { queue.show("p1") }
    mainClock.advanceTimeBy(1_000)
    onNodeWithText(keptWaitsText("Landscape")).assertDoesNotExist()
  }

  private fun blocks() = TabsController().apply {
    open(Route.Grid(listOf("landscape")), background = false)
    openInBlock(listOf(Route.Post("p1", md5 = null, originTags = emptyList(), label = "p1", rating = "s")))
    openInBlock(listOf(Route.Post("p2", md5 = null, originTags = emptyList(), label = "p2", rating = "s")))
    open(Route.Grid(listOf("cat")), background = true)
  }

  private fun TabsController.show(title: String) = activate(tabs.value.single { it.title == title }.id)

  @Test
  fun inABlockThePillCountsTheBlockAndTalkBackHearsBoth() = runComposeUiTest {
    val queue = blocks()
    queue.show("p1")
    showBar(controller = queue, closedUndo = ClosedTabUndo(queue), endsAtNextAction = false)

    onNodeWithText("Landscape").assertExists()
    onNodeWithText("0 of 2").assertExists()
    onNodeWithContentDescription("Queue, Landscape, 0 of 2 kept posts gone through, kept post 1 of 2, item 3 of 5").assertExists()
    onNodeWithText("Next:", substring = true, useUnmergedTree = true).assertDoesNotExist()

    runOnIdle { queue.show("Landscape") }
    onNodeWithText("2 kept").assertExists()
    onNodeWithContentDescription("Queue, Landscape, 2 kept items follow, item 2 of 5").assertExists()
  }

  @Test
  fun onABlocksLastItemALineOverThePillSaysTheNextSwipeLeavesIt() = runComposeUiTest {
    val queue = blocks()
    queue.show("p2")
    showBar(controller = queue, closedUndo = ClosedTabUndo(queue), endsAtNextAction = false)

    onNodeWithText("0 of 2").assertExists()
    onNodeWithText("Next: Cat", useUnmergedTree = true).assertExists()
    onNodeWithText("Next: Cat").assertDoesNotExist()
    onNodeWithContentDescription("Queue, Landscape, 0 of 2 kept posts gone through, kept post 2 of 2, item 4 of 5, last, next Cat").assertExists()
    val pill = onNodeWithTag(COUNT_PILL).getUnclippedBoundsInRoot()
    val line = onNodeWithText("Next: Cat", useUnmergedTree = true).getUnclippedBoundsInRoot()
    assertTrue(line.bottom <= pill.top, "$line over $pill")
    assertEquals(pill.centre.x.value, line.centre.x.value, 1f)
    assertEquals((300.dp - 24.dp - 32.dp).value, pill.centre.y.value, 0.5f)

    runOnIdle { queue.next() }
    onNodeWithText("5 / 5").assertExists()
    onNodeWithText("Next:", substring = true, useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun onEndOfTheQueueThePillSaysEndNotTheItemBehindIt() = runComposeUiTest {
    val queue = blocks()
    val upNext = noUpNext(queue)
    queue.show("Cat")
    setContent {
      SankakuTheme {
        QueueBar(onOpenQueue = {}, controller = queue, undo = ClosedTabUndo(queue), settings = settings, tips = noTips(), upNext = upNext)
      }
    }
    runOnIdle { upNext.forward() }

    onNodeWithText("End").assertExists()
    onNodeWithText("5").assertExists()
    onNodeWithContentDescription("Queue, end, after 5 items").assertExists()
  }

  @Test
  fun theSwipeTipComesFirstWhenThereIsANextItemEvenInAQueueRestoredAtLaunch() = runComposeUiTest {
    val tips = Tips(InMemoryTextFile(), touch = true)
    val queue = TabsController()
    tips.counted(2)
    queue.open(Route.Grid(listOf("cat")), background = true)
    queue.show("Cat")
    setContent {
      SankakuTheme {
        QueueBar(onOpenQueue = {}, controller = queue, undo = ClosedTabUndo(queue), settings = settings, tips = tips, upNext = noUpNext(queue))
        TipLayer(tips)
      }
    }
    mainClock.advanceTimeBy(1_000)
    assertEquals(null, tips.current)

    runOnIdle { queue.show("Browse") }
    mainClock.advanceTimeBy(1_000)
    assertEquals(Tip.QueueSwipe, tips.current)
    onNodeWithText(Tip.QueueSwipe.text).assertExists()

    val used = Tips(InMemoryTextFile(), touch = true).apply { used(Tip.QueueSwipe) }
    assertFalse(used.pending(Tip.QueueSwipe))
  }

  @Test
  fun aPillWhereASwipeStepsPostsOffersNoSwipeTip() = runComposeUiTest {
    val tips = Tips(InMemoryTextFile(), touch = true)
    val queue = TabsController()
    tips.counted(2)
    queue.open(Route.Grid(listOf("cat")), background = true)
    var swipesQueue by mutableStateOf(false)
    setContent {
      SankakuTheme {
        QueueBar(
          onOpenQueue = {},
          controller = queue,
          undo = ClosedTabUndo(queue),
          settings = settings,
          tips = tips,
          upNext = noUpNext(queue),
          swipesQueue = swipesQueue,
        )
        TipLayer(tips)
      }
    }
    mainClock.advanceTimeBy(1_000)
    onNodeWithText(Tip.QueueSwipe.text).assertDoesNotExist()
    assertTrue(tips.current != Tip.QueueSwipe)
    assertTrue(tips.pending(Tip.QueueSwipe))

    runOnIdle { swipesQueue = true }
    mainClock.advanceTimeBy(1_000)
    assertEquals(Tip.QueueSwipe, tips.current)
    onNodeWithText(Tip.QueueSwipe.text).assertExists()
  }

  @Test
  fun afterGotItOnTheSwipeTipTheQueueTipWaitsForTheNextVisit() = runComposeUiTest {
    mainClock.autoAdvance = false
    val tips = Tips(InMemoryTextFile(), touch = true)
    val queue = TabsController()
    tips.counted(1)
    setContent {
      SankakuTheme {
        QueueBar(onOpenQueue = {}, controller = queue, undo = ClosedTabUndo(queue), settings = settings, tips = tips, upNext = noUpNext(queue))
        TipLayer(tips)
      }
    }
    runOnIdle {
      queue.open(Route.Grid(listOf("cat")), background = true)
      tips.counted(2)
    }
    mainClock.advanceTimeBy(1_000)
    onNodeWithText(Tip.QueueSwipe.text).assertExists()

    onNodeWithText("Got it").performClick()
    mainClock.advanceTimeBy(2_000)
    onNodeWithText(Tip.TabList.text).assertDoesNotExist()
    assertEquals(null, tips.current)

    runOnIdle { tips.gestureEnded() }
    mainClock.advanceTimeBy(1_000)
    onNodeWithText(Tip.TabList.text).assertDoesNotExist()

    runOnIdle { tips.visiting("cat") }
    mainClock.advanceTimeBy(1_000)
    onNodeWithText(Tip.TabList.text).assertExists()
  }

  @Test
  fun aPillOverAPictureOffersNoTip() = runComposeUiTest {
    val tips = Tips(InMemoryTextFile(), touch = true)
    val queue = TabsController()
    tips.counted(1)
    queue.open(Route.Grid(listOf("cat")), background = true)
    tips.counted(2)
    setContent {
      SankakuTheme {
        QueueBar(onOpenQueue = {}, controller = queue, undo = ClosedTabUndo(queue), settings = settings, tips = tips, upNext = noUpNext(queue), overPicture = true)
        TipLayer(tips)
      }
    }
    mainClock.advanceTimeBy(2_000)
    assertEquals(null, tips.current)
    onNodeWithText(Tip.QueueSwipe.text).assertDoesNotExist()
    assertTrue(tips.pending(Tip.QueueSwipe))
  }

  @Test
  fun aHoldThatReopensMeansTheReopenTipNeverShows() {
    val file = InMemoryTextFile()
    val tipped = ClosedTabUndo(tabs, Tips(file, touch = true))
    tipped.close(tabs.activeTab.id)
    assertTrue(tipped.reopenLatest())
    assertFalse(Tips(file, touch = true).pending(Tip.ReopenHold))
  }

  @Test
  fun aHoldWithNothingToReopenLeavesTheReopenTip() {
    val file = InMemoryTextFile()
    val tipped = ClosedTabUndo(tabs, Tips(file, touch = true))
    assertFalse(tipped.reopenLatest())
    assertTrue(Tips(file, touch = true).pending(Tip.ReopenHold))
  }

  @Test
  fun holdingThePillReopensTheMostRecentlyClosedItemAtAnyTime() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("c")), background = true)
    showBar()
    pill().assert(!hasAction(REOPEN_LABEL))
    assertFalse(pill().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single().contains("reopen"))

    runOnIdle { undo.close(tabs.activeTab.id) }
    runOnIdle { undo.close(tabs.activeTab.id) }
    assertEquals(listOf("B", "C"), tabs.tabs.value.map { it.title })
    pill().assert(hasAction(REOPEN_LABEL))
    assertEquals(REOPEN_LABEL, pill().fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
    assertEquals("Queue, item 1 of 2, closed items to reopen", pill().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())

    pill().performTouchInput { longClick() }
    assertEquals(listOf("A", "B", "C"), tabs.tabs.value.map { it.title })
    assertEquals("A", tabs.activeTab.title)
    pill().performTouchInput { longClick() }
    assertEquals(listOf("Browse", "A", "B", "C"), tabs.tabs.value.map { it.title })

    pill().performTouchInput { longClick() }
    assertEquals(4, tabs.tabs.value.size)
  }

  @Test
  fun showQueueButtonsBringsBackCloseAndTheArrows() = runComposeUiTest {
    settings.setShowQueueButtons(true)
    showBar(endsAtNextAction = false)

    val close = onNodeWithContentDescription(CLOSE_LABEL).assertHasClickAction()
    val corner = close.getUnclippedBoundsInRoot()
    assertEquals(44f, corner.centre.x.value, 0.5f)
    assertEquals(56.dp, corner.right - corner.left)
    val pill = onNodeWithTag(COUNT_PILL).getUnclippedBoundsInRoot()
    assertEquals(pill.centre.y, corner.centre.y)

    onNodeWithContentDescription("$PREVIOUS_ITEM", substring = true).assertIsNotEnabled()
    onNodeWithContentDescription("$NEXT_ITEM: A").performClick()
    onNodeWithContentDescription("$NEXT_ITEM: B").performClick()
    onNodeWithText("3 / 3").assertExists()
    onNodeWithContentDescription(END_PANE).assertIsEnabled()
    onNodeWithContentDescription("$PREVIOUS_ITEM: A").assertIsEnabled()

    close.performClick()
    assertEquals(listOf("Browse", "A"), tabs.tabs.value.map { it.title })
    val chip = onNodeWithContentDescription(UNDO_CLOSE_LABEL).getUnclippedBoundsInRoot()
    val after = onNodeWithTag(COUNT_PILL).getUnclippedBoundsInRoot()
    assertTrue(corner.right <= chip.left, "✕ ${corner.right}, ↶ ${chip.left}")
    assertTrue(chip.right <= after.left, "↶ ${chip.right}, pill ${after.left}")
    assertTrue(chip.right - chip.left >= 48.dp)

    close.performTouchInput { longClick() }
    assertEquals(listOf("Browse", "A", "B"), tabs.tabs.value.map { it.title })
  }

  @Test
  fun onANarrowPhoneWithALongCountUndoGoesAboveCloseAndCoversNothing() = runComposeUiTest {
    settings.setShowQueueButtons(true)
    val many = TabsController().apply { repeat(249) { open(Route.Grid(listOf("t$it")), background = true) } }
    val manyUndo = ClosedTabUndo(many)
    many.activate(many.tabs.value[192].id)
    showBar(controller = many, closedUndo = manyUndo, width = 360.dp, endsAtNextAction = false)
    val before = onNodeWithTag(COUNT_PILL).getUnclippedBoundsInRoot()

    runOnIdle { manyUndo.close(many.activeTab.id) }

    onNodeWithText("193 / 249").assertExists()
    val pill = onNodeWithTag(COUNT_PILL).getUnclippedBoundsInRoot()
    assertEquals(before, pill, "the pill moved")
    val close = onNodeWithContentDescription(CLOSE_LABEL).getUnclippedBoundsInRoot()
    val chip = onNodeWithContentDescription(UNDO_CLOSE_LABEL).getUnclippedBoundsInRoot()
    assertTrue(close.right <= pill.left, "✕ ${close.right}, pill ${pill.left}")
    assertTrue(chip.bottom <= close.top, "↶ $chip, ✕ $close")
  }

  @Test
  fun everyPieceIsAButtonOfAtLeast48Dp() = runComposeUiTest {
    settings.setShowQueueButtons(true)
    tabs.activate(tabs.tabs.value[1].id)
    showBar(endsAtNextAction = false)
    runOnIdle { undo.close(tabs.activeTab.id) }

    listOf(
      onNodeWithContentDescription(CLOSE_LABEL),
      onNodeWithContentDescription(PREVIOUS_ITEM, substring = true),
      onNodeWithContentDescription(END_PANE),
      onNodeWithContentDescription(UNDO_CLOSE_LABEL),
      pill(),
    ).forEach { node ->
      node.assertHasClickAction()
      node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
      val bounds = node.getUnclippedBoundsInRoot()
      assertTrue(bounds.right - bounds.left >= 48.dp && bounds.bottom - bounds.top >= 48.dp, "$bounds")
    }
  }

  @Test
  fun closingTheLastItemLeavesBrowse() = runComposeUiTest {
    val only = TabsController().apply { activeTab.backStack.add(Route.Grid(listOf("x"))) }
    showBar(controller = only, closedUndo = ClosedTabUndo(only))

    runAction(CLOSE_LABEL)

    assertEquals(listOf(listOf<Route>(Route.Grid(emptyList()))), only.tabs.value.map { it.backStack.toList() })
    onNodeWithText("1 / 1").assertExists()
  }

  @Test
  fun theRowsLineStays32DpAboveTheNavigationBar() {
    assertEquals(28.dp, BottomRowLayout.roomAbove(navigationBar = 24.dp))
    assertEquals(52.dp, BottomRowLayout.roomAbove(navigationBar = 48.dp))
    assertEquals(BottomRowLayout.CentreLine, BottomRowLayout.roomAbove(navigationBar = 0.dp) + BottomRowLayout.Height / 2)
  }

  @Test
  fun theQueueTipShowsOnceASecondItemAppearsAndNotAgain() = runComposeUiTest {
    val file = InMemoryTextFile()
    val tips = Tips(file, touch = true).apply { used(Tip.QueueSwipe) }
    val tabs = TabsController().apply { activeTab.backStack.add(Route.Grid(listOf("a"))) }
    setContent {
      SankakuTheme {
        QueueBar(onOpenQueue = {}, controller = tabs, undo = ClosedTabUndo(tabs), settings = settings, tips = tips, upNext = noUpNext(tabs))
        TipLayer(tips)
      }
    }
    onNodeWithText(Tip.TabList.text).assertDoesNotExist()

    tips.counted(1)
    tabs.open(Route.Grid(listOf("b")), background = true)
    tips.counted(2)
    mainClock.advanceTimeBy(1_000)
    onNodeWithText(Tip.TabList.text).assertExists()
    onNodeWithText("Got it").performClick()
    waitForIdle()
    onNodeWithText(Tip.TabList.text).assertDoesNotExist()
    assertEquals(null, tips.current)
    assertFalse(Tips(file, touch = true).pending(Tip.TabList))
  }

  @Test
  fun theQueueTipNeverShowsOnceTheQueueWasOpened() = runComposeUiTest {
    val tips = Tips(InMemoryTextFile(), touch = true).apply {
      used(Tip.TabList)
      counted(1)
      counted(2)
    }
    val tabs = TabsController().apply { open(Route.Grid(listOf("a")), background = true) }
    setContent { SankakuTheme { QueueBar(onOpenQueue = {}, controller = tabs, undo = ClosedTabUndo(tabs), settings = settings, tips = tips, upNext = noUpNext(tabs)) } }
    mainClock.advanceTimeBy(1_000)
    onNodeWithText(Tip.TabList.text).assertDoesNotExist()
  }
}

private fun hasAction(label: String) = SemanticsMatcher("has $label") { node ->
  node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
}

private fun liveRegion(text: String) = SemanticsMatcher("live region \"$text\"") { node ->
  node.config.getOrNull(SemanticsProperties.LiveRegion) != null &&
    node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true
}

private val DpRect.centre get() = DpCenter((left + right) / 2, (top + bottom) / 2)

private data class DpCenter(val x: Dp, val y: Dp)

private fun noTips() = Tips(InMemoryTextFile(), touch = false)

package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.shell.MenuBadge
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class QueuePageTest {
  private var now = Instant.parse("2026-11-01T12:00:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val seen = SeenStore(InMemoryTextFile(), clock)
  private val tabs = TabsController(clock = clock).apply {
    open(Route.Grid(listOf("a")), background = true)
    open(Route.Grid(listOf("b")), background = true)
  }
  private val pill = ClosedTabUndo(tabs)
  private val clearViewed = ClearViewed(tabs, seen, ClearedUndo { pill.opened(it, Closing.Cleared) })

  private fun titles() = tabs.tabs.value.map { it.title }

  private fun ComposeUiTest.show(
    onDismiss: () -> Unit = {},
    onMenu: (() -> Unit)? = null,
    menuBadge: MenuBadge = MenuBadge.None,
    onSort: (() -> Unit)? = null,
    fontScale: Float = 1f,
    size: DpSize = DpSize(PHONE_WIDTH, Dp.Unspecified),
  ) {
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
        SankakuTheme {
          Box(Modifier.width(size.width).height(size.height)) {
            PhoneQueuePage(controller = tabs, onDismiss = onDismiss, onMenu = onMenu, menuBadge = menuBadge, seen = seen, undo = pill, clearViewed = clearViewed, folding = false, onSort = onSort)
          }
        }
      }
    }
    waitForIdle()
  }

  @Test
  fun sourcesUntouchedForWeeksAreRowsAndThereIsNoFadedSection() = runComposeUiTest {
    now += 40.days
    show()

    onNodeWithText("A").assertExists()
    onNodeWithText("B").assertExists()
    onNodeWithText("Faded", substring = true).assertDoesNotExist()
    onNodeWithText("Deleted in", substring = true).assertDoesNotExist()
    assertEquals(listOf("Browse", "A", "B"), titles())
  }

  @Test
  fun theHeadersMenuOpensTheSidebarAndItsChevronClosesThePage() = runComposeUiTest {
    var menus = 0
    var dismissed = 0
    val active = tabs.active.value
    show(onDismiss = { dismissed++ }, onMenu = { menus++ })

    onNodeWithContentDescription("Menu").assertHasClickAction().performClick()
    waitForIdle()
    assertEquals(1, menus)
    assertEquals(0, dismissed)

    onNodeWithContentDescription("Close queue").assertHasClickAction().performClick()
    waitForIdle()
    assertEquals(1, dismissed)
    assertEquals(active, tabs.active.value)
    assertEquals(1, menus)
  }

  @Test
  fun theMenuStartsTheHeaderAndTheOverflowEndsIt() = runComposeUiTest {
    show(onMenu = {})

    val menu = onNodeWithContentDescription("Menu").getBoundsInRoot()
    val title = onNodeWithText("Queue").getBoundsInRoot()
    val shield = onNodeWithContentDescription("G only").getBoundsInRoot()
    val more = onNodeWithContentDescription("More").getBoundsInRoot()
    val close = onNodeWithContentDescription("Close queue").getBoundsInRoot()
    assertTrue(menu.right <= title.left, "☰ $menu, title $title")
    assertTrue(shield.right <= close.left && close.right <= more.left, "🛡 $shield, ⌄ $close, ⋮ $more")
    for (button in listOf(menu, more)) {
      assertTrue(button.top < title.bottom && title.top < button.bottom, "$button against $title")
    }
  }

  @Test
  fun theMenuSaysWhatItsBadgeShows() = runComposeUiTest {
    show(onMenu = {}, menuBadge = MenuBadge.Expired)

    onNodeWithContentDescription("Menu, session expired").assertExists()
  }

  @Test
  fun withoutASidebarThereIsNoMenu() = runComposeUiTest {
    show()

    onNodeWithContentDescription("Menu").assertDoesNotExist()
    onNodeWithContentDescription("Close queue").assertExists()
  }

  @Test
  fun aSeenKeptPostSaysSeenAndMarkUnseenTakesItBack() = runComposeUiTest {
    tabs.open(Route.Post("p1", "m1", listOf("a"), label = "Rosehip"), background = true)
    val kept = tabs.tabs.value.last()
    now += 5.minutes
    seen.record("p1", "m1")
    show()

    fun actions() =
      onNodeWithText("Rosehip").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
    onNodeWithText("Kept · seen").assertExists()
    assertTrue(kept.seenSinceKept(seen))

    now += 1.minutes
    actions().first { it.label == "Mark unseen" }.action()
    waitForIdle()

    assertFalse(kept.seenSinceKept(seen))
    onNodeWithText("Kept").assertExists()
    assertFalse(actions().any { it.label == "Mark unseen" })
  }

  @Test
  fun followingIsAddedLastUncountedAndCannotBeClosed() = runComposeUiTest {
    tabs.pinFeeds(listOf(Feed.Following))
    show()

    onNodeWithText("Feeds").assertDoesNotExist()
    onNodeWithText("Feed").assertExists()
    onNodeWithContentDescription("3 items").assertExists()
    val row = onNodeWithText("Following")
    val actions = row.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
    assertFalse(actions.any { it.startsWith("Close") }, "$actions")
    row.performTouchInput { swipeLeft() }
    waitForIdle()

    assertEquals(listOf("Browse", "A", "B", "Following"), titles())
    assertTrue(tabs.closed.value.isEmpty())
  }

  private fun ComposeUiTest.handle(index: Int): SemanticsNodeInteraction =
    onAllNodesWithContentDescription("Move", useUnmergedTree = true)[index]

  private fun SemanticsNodeInteraction.thumbSwipeLeft(restLongPresses: Int, stepMillis: Long) = performTouchInput {
    down(center)
    advanceEventTime(viewConfiguration.longPressTimeoutMillis * restLongPresses)
    val slop = viewConfiguration.touchSlop * 1.05f
    repeat(4) { moveBy(Offset(-0.6f * slop / 4, 0.8f * slop / 4), delayMillis = stepMillis) }
    repeat(20) { moveBy(Offset(-30f, 1f), delayMillis = stepMillis) }
    up()
  }

  @Test
  fun aQuickSwipeToTheLeftThatStartsOnTheHandleClosesTheTab() = runComposeUiTest {
    show()

    handle(1).thumbSwipeLeft(restLongPresses = 0, stepMillis = 5)
    waitForIdle()

    assertEquals(listOf("Browse", "B"), titles())
    onNodeWithText("Closed “A”").assertExists()
  }

  @Test
  fun aSlowSwipeToTheLeftAfterTheFingerRestsOnTheHandleStillClosesTheTab() = runComposeUiTest {
    show()

    handle(1).thumbSwipeLeft(restLongPresses = 3, stepMillis = 40)
    waitForIdle()

    assertEquals(listOf("Browse", "B"), titles())
    onNodeWithText("Closed “A”").assertExists()
  }

  private fun SemanticsNodeInteraction.slowDragLeft(fraction: Float, heldStill: Boolean) = performTouchInput {
    val slop = viewConfiguration.touchSlop * 1.5f
    down(Offset(width * 0.8f, centerY))
    moveBy(Offset(-slop, 0f), delayMillis = 16)
    val steps = 375
    repeat(steps) { moveBy(Offset(-(width * fraction - slop) / steps, 0f), delayMillis = 16) }
    if (heldStill) advanceEventTime(300)
    up()
  }

  private fun ComposeUiTest.assertClosedA() {
    assertEquals(listOf("Browse", "B"), titles())
    onNodeWithText("Closed “A”").assertExists()
  }

  private fun ComposeUiTest.assertSprangBack() {
    assertEquals(listOf("Browse", "A", "B"), titles())
    assertTrue(tabs.closed.value.isEmpty())
    assertEquals(onNodeWithText("Browse").getBoundsInRoot().left, onNodeWithText("A").getBoundsInRoot().left)
  }

  @Test
  fun aSlowDragPastHalfTheRowClosesIt() = runComposeUiTest {
    show()

    onNodeWithText("A").slowDragLeft(0.6f, heldStill = true)
    waitForIdle()

    assertClosedA()
  }

  @Test
  fun aSlowDragPastHalfTheRowThatLetsGoMovingClosesIt() = runComposeUiTest {
    show()

    onNodeWithText("A").slowDragLeft(0.6f, heldStill = false)
    waitForIdle()

    assertClosedA()
  }

  @Test
  fun aSlowDragUnderHalfTheRowSpringsBack() = runComposeUiTest {
    show()

    onNodeWithText("A").slowDragLeft(0.4f, heldStill = true)
    waitForIdle()

    assertSprangBack()
  }

  @Test
  fun aSlowDragUnderHalfTheRowThatLetsGoMovingSpringsBack() = runComposeUiTest {
    show()

    onNodeWithText("A").slowDragLeft(0.4f, heldStill = false)
    waitForIdle()

    assertSprangBack()
  }

  @Test
  fun aShortFlingClosesTheRow() = runComposeUiTest {
    show()

    onNodeWithText("A").performTouchInput {
      down(Offset(width * 0.8f, centerY))
      repeat(6) { moveBy(Offset(-width / 18f, 0f), delayMillis = 10) }
      up()
    }
    waitForIdle()

    assertClosedA()
  }

  @Test
  fun aVerticalDragOnTheHandleMovesTheTab() = runComposeUiTest {
    show()

    handle(0).performTouchInput { swipeDown(startY = centerY, endY = centerY + height * 2f, durationMillis = 600) }
    waitForIdle()

    assertEquals(listOf("A", "Browse", "B"), titles())
  }

  @Test
  fun draggingAHeadMovesItsBlockTogether() = runComposeUiTest {
    tabs.activate(tabs.tabs.value[1].id)
    tabs.openInBlock(listOf(Route.Grid(listOf("a"), at = "p1"), Route.Post("p1", null, listOf("a"), label = "p1")))
    show()

    handle(0).performTouchInput { swipeDown(startY = centerY, endY = centerY + height * 3.5f, durationMillis = 800) }
    waitForIdle()

    assertEquals(listOf("A", "p1", "Browse", "B"), titles())
  }

  @Test
  fun talkBackHearsWhichSourceAKeptPostIsUnderWhileTheScreenSaysKept() = runComposeUiTest {
    tabs.activate(tabs.tabs.value[1].id)
    tabs.openInBlock(listOf(Route.Grid(listOf("a"), at = "p1"), Route.Post("p1", null, listOf("a"), label = "p1")))
    show()

    fun spoken(title: String) =
      onNodeWithText(title).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    assertEquals(listOf("Blurred", "Kept from A", "Move"), spoken("p1"))
    assertTrue("Item 2 of 3" in spoken("A"), "${spoken("A")}")
    onNodeWithText("Kept").assertExists()
    assertFalse(spoken("A").any { "from" in it }, "${spoken("A")}")

    tabs.close(tabs.tabs.value[1].id)
    waitForIdle()
    assertTrue("Kept from A (closed) · current" in spoken("p1"), "${spoken("p1")}")
  }

  @Test
  fun undoOfASwipedKeptPostPutsItBackWithoutMakingItCurrent() = runComposeUiTest {
    tabs.open(Route.Post("p1", "m1", listOf("a"), label = "Rosehip"), background = true)
    val keptAt = tabs.tabs.value.last().keptAt
    tabs.open(Route.Grid(listOf("c")), background = true)
    show()
    assertEquals("Browse", tabs.tabs.value.first { it.id == tabs.active.value }.title)

    closeRow("Rosehip")
    assertEquals(listOf("Browse", "A", "B", "C"), titles())
    onNodeWithText("Undo").performClick()
    waitForIdle()

    assertEquals("Browse", tabs.tabs.value.first { it.id == tabs.active.value }.title)
    assertEquals(listOf("Browse", "A", "B", "Rosehip", "C"), titles())
    val back = tabs.tabs.value[3]
    assertEquals(keptAt, back.keptAt)
    assertFalse(back.seenSinceKept(seen))
    onNodeWithText("Kept").assertExists()
    onNodeWithText("Kept · current").assertDoesNotExist()
  }

  @Test
  fun thePillsUndoAfterASwipeBringsTheItemBackInPlace() = runComposeUiTest {
    tabs.open(Route.Post("p1", "m1", listOf("a"), label = "Rosehip"), background = true)
    show()

    closeRow("Rosehip")
    runOnIdle { pill.undo() }
    waitForIdle()

    assertEquals(listOf("Browse", "A", "B", "Rosehip"), titles())
    assertEquals("Browse", tabs.tabs.value.first { it.id == tabs.active.value }.title)
    assertNull(pill.pending.value)
  }

  @Test
  fun aClearAfterASwipeTakesOverTheUndo() = runComposeUiTest {
    tabs.open(Route.Post("p1", "m1", listOf("b"), label = "Rosehip"), background = true)
    now += 1.minutes
    seen.record("p1", "m1")
    show()

    closeRow("A")
    onNodeWithText("Closed “A”").assertExists()
    onNodeWithContentDescription("Clear viewed, 1 kept post").performClick()
    waitForIdle()

    onNodeWithText("Closed “A”").assertDoesNotExist()
    onNodeWithContentDescription(UNDO_ONE).assertExists()
    assertEquals(listOf("Browse", "B"), titles())
  }

  @Test
  fun theUndoTakesFocusOnlyTheFirstTimeItShows() = runComposeUiTest {
    tabs.open(Route.Post("p1", "m1", listOf("a"), label = "Rosehip"), background = true)
    now += 1.minutes
    seen.record("p1", "m1")
    show()

    onNodeWithContentDescription("Clear viewed, 1 kept post").performClick()
    waitForIdle()
    onNodeWithContentDescription(UNDO_ONE).assertIsFocused()

    onNodeWithContentDescription("Find in queue").performClick()
    waitForIdle()
    onNodeWithContentDescription("Close find").performClick()
    waitForIdle()

    onNodeWithContentDescription(UNDO_ONE).assertExists()
    onNodeWithContentDescription(UNDO_ONE).assertIsNotFocused()
  }

  @Test
  fun aClearWhileFindIsOpenFocusesTheUndoOnceFindCloses() = runComposeUiTest {
    tabs.open(Route.Post("p1", "m1", listOf("a"), label = "Rosehip"), background = true)
    now += 1.minutes
    seen.record("p1", "m1")
    show()

    onNodeWithContentDescription("Find in queue").performClick()
    waitForIdle()
    runOnIdle { clearViewed() }
    waitForIdle()
    onNodeWithContentDescription("Close find").performClick()
    waitForIdle()

    onNodeWithContentDescription(UNDO_ONE).assertIsFocused()
  }

  @Test
  fun theUndoSaysWhatWasClearedAndAnnouncesIt() = runComposeUiTest {
    tabs.open(Route.Post("p1", "m1", listOf("a"), label = "Rosehip"), background = true)
    now += 1.minutes
    seen.record("p1", "m1")
    show()

    onNodeWithContentDescription("Clear viewed, 1 kept post").performClick()
    waitForIdle()

    val undo = onNodeWithContentDescription(UNDO_ONE)
    undo.assertHasClickAction()
    val config = undo.fetchSemanticsNode().config
    assertFalse(SemanticsProperties.Text in config, "the label \"Undo · cleared 1\" isn't spoken as a second name")
    assertEquals("Cleared 1 kept post", config[SemanticsProperties.StateDescription])
    assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
  }

  @Test
  fun eachButtonHasOneName() = runComposeUiTest {
    showOneViewedBesideSort()

    LINE.forEach { name ->
      val config = onNodeWithContentDescription(name).fetchSemanticsNode().config
      val names = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
      assertEquals(listOf(name), names)
    }
  }

  @Test
  fun clearViewedTurnsIntoTheUndoAndPutsTheWholeClearOnThePill() = runComposeUiTest {
    tabs.open(Route.Post("p1", "m1", listOf("a"), label = "Rosehip"), background = true)
    tabs.open(Route.Post("p2", "m2", listOf("a"), label = "Yarrow"), background = true)
    now += 1.minutes
    seen.record("p1", "m1")
    seen.record("p2", "m2")
    show()

    onNodeWithContentDescription("Clear viewed, 2 kept posts").performClick()
    waitForIdle()

    assertEquals(listOf("Browse", "A", "B"), titles())
    onNodeWithContentDescription("Clear viewed", substring = true).assertDoesNotExist()
    onNodeWithContentDescription(UNDO_TWO).assertExists()
    assertEquals(tabs.closed.value.last(), pill.pending.value?.closed)

    onNodeWithContentDescription(UNDO_TWO).performClick()
    waitForIdle()

    assertEquals(listOf("Browse", "A", "B", "Rosehip", "Yarrow"), titles())
    assertNull(pill.pending.value)
    onNodeWithContentDescription(UNDO_TWO).assertDoesNotExist()
    onNodeWithContentDescription("Clear viewed, 2 kept posts").assertExists()
  }

  private fun ComposeUiTest.showOneViewedBesideSort(fontScale: Float = 1f) {
    tabs.open(Route.Post("p1", "m1", listOf("a"), label = "Rosehip"), background = true)
    now += 1.minutes
    seen.record("p1", "m1")
    show(onSort = {}, fontScale = fontScale)
  }

  private fun ComposeUiTest.showFourButtons(size: DpSize, fontScale: Float = 1f) {
    tabs.open(Route.Post("p1", "m1", listOf("a"), label = "Rosehip"), background = true)
    tabs.open(Route.Post("p2", "m2", listOf("a"), label = "Yarrow"), background = true)
    tabs.tabs.value.last().markLater()
    now += 1.minutes
    seen.record("p1", "m1")
    show(onSort = {}, fontScale = fontScale, size = size)
  }

  private fun ComposeUiTest.assertOnePerRow(names: List<String>) {
    val line = onNodeWithTag(QUEUE_ACTIONS_TAG).getBoundsInRoot()
    val buttons = names.map { onNodeWithContentDescription(it).getBoundsInRoot() }
    buttons.forEach {
      assertEquals((line.left + 16.dp).value, it.left.value, 1f, "$it starts its line")
      assertEquals((line.right - 16.dp).value, it.right.value, 1f, "$it ends its line")
      assertTrue((it.bottom - it.top).value >= 48f, "$it is at least 48 dp")
    }
    buttons.zipWithNext().forEach { (above, below) -> assertTrue(below.top >= above.bottom, "$below under $above") }
  }

  private fun ComposeUiTest.assertOneLine(names: List<String>, height: Float) {
    val line = onNodeWithTag(QUEUE_ACTIONS_TAG).getBoundsInRoot()
    assertEquals(height, (line.bottom - line.top).value, 1f, "the line $line")
    val buttons = names.map { onNodeWithContentDescription(it).getBoundsInRoot() }
    buttons.forEach { assertTrue(it.top >= line.top && it.bottom <= line.bottom, "$it inside $line") }
    buttons.zipWithNext().forEach { (before, after) -> assertTrue(after.left >= before.right, "$after after $before") }
  }

  private fun ComposeUiTest.assertTwoPerRow(names: List<String>) {
    val rows = names.map { onNodeWithContentDescription(it).getBoundsInRoot() }.chunked(2)
    rows.forEach { (start, end) ->
      assertEquals(start.top, end.top, "$end beside $start")
      assertTrue(end.left >= start.right, "$end after $start")
      assertTrue((start.bottom - start.top).value >= 48f, "$start is at least 48 dp")
    }
    rows.zipWithNext().forEach { (above, below) -> assertTrue(below.first().top >= above.first().bottom, "$below under $above") }
  }

  @Test
  fun sortClearViewedAndContinueShareOne72DpLine() = runComposeUiTest {
    showOneViewedBesideSort()

    assertOneLine(LINE, height = 72f)
  }

  @Test
  fun afterAClearTheUndoTakesClearViewedsPlaceAndTheLineStays72Dp() = runComposeUiTest {
    showOneViewedBesideSort()
    val clear = onNodeWithContentDescription(CLEAR_VIEWED).getBoundsInRoot()

    onNodeWithContentDescription(CLEAR_VIEWED).performClick()
    waitForIdle()

    assertOneLine(listOf(SORT, UNDO_ONE, CONTINUE), height = 72f)
    assertEquals(clear, onNodeWithContentDescription(UNDO_ONE).getBoundsInRoot())
  }

  @Test
  fun upToALargeFontTheLabelsWrapAndTheLineGrowsRatherThanCutThem() = runComposeUiTest {
    showOneViewedBesideSort(fontScale = 1.3f)

    val line = onNodeWithTag(QUEUE_ACTIONS_TAG).getBoundsInRoot()
    assertTrue((line.bottom - line.top).value > 72f, "the line $line grows")
    LINE.map { onNodeWithContentDescription(it).getBoundsInRoot() }.zipWithNext().forEach { (before, after) ->
      assertEquals(before.top, after.top, "$after on $before's line")
      assertEquals(before.bottom, after.bottom, "$after as tall as $before")
    }
  }

  @Test
  fun pastALargeFontEachButtonTakesAWholeLine() = runComposeUiTest {
    showOneViewedBesideSort(fontScale = 1.5f)

    assertOnePerRow(LINE)
  }

  @Test
  fun fourButtonsOnANarrowPhoneGoTwoPerRowRatherThanWrapToThreeLines() = runComposeUiTest {
    showFourButtons(DpSize(360.dp, Dp.Unspecified))

    assertTwoPerRow(FOUR)
  }

  @Test
  fun fourButtonsInTheLandscapeSideColumnAllShowWholeAndNeverFourAcross() = runComposeUiTest {
    showFourButtons(LANDSCAPE)

    val column = onNodeWithTag(SIDE_COLUMN_TAG).getBoundsInRoot()
    val line = onNodeWithTag(QUEUE_ACTIONS_TAG).getBoundsInRoot()
    val halfLine = (line.right - line.left - 32.dp - 8.dp) / 2
    FOUR.map { onNodeWithContentDescription(it).getBoundsInRoot() }.forEach {
      assertTrue(it.top >= column.top && it.bottom <= column.bottom, "$it whole in $column")
      assertTrue(it.right - it.left >= halfLine - 1.dp, "$it at least half the line, not four across")
    }
  }

  @Test
  fun inTheLandscapeSideColumnAt130PercentContinueGetsAWholeLineRatherThanBreak() = runComposeUiTest {
    showFourButtons(LANDSCAPE, fontScale = 1.3f)

    onNodeWithTag(SIDE_COLUMN_TAG).assertExists()
    assertOnePerRow(FOUR)
  }

  @Test
  fun undoOfTheCurrentRowsCloseMakesItCurrentAgain() = runComposeUiTest {
    tabs.activate(tabs.tabs.value[1].id)
    show()

    closeRow("A")
    assertEquals("B", tabs.tabs.value.first { it.id == tabs.active.value }.title)
    onNodeWithText("Undo").performClick()
    waitForIdle()

    assertEquals(listOf("Browse", "A", "B"), titles())
    assertEquals("A", tabs.tabs.value.first { it.id == tabs.active.value }.title)
  }

  @Test
  fun recentlyClosedStillMakesWhatItReopensCurrent() = runComposeUiTest {
    tabs.close(tabs.tabs.value[1].id)
    show()

    onNodeWithText("Recently closed").assertExists()
    onNodeWithText("A").performClick()
    waitForIdle()

    assertEquals(listOf("Browse", "A", "B"), titles())
    assertEquals("A", tabs.tabs.value.first { it.id == tabs.active.value }.title)
  }

  @Test
  fun talkBackOffersCloseAndTheMovesThatFit() = runComposeUiTest {
    show()

    fun actions(title: String) =
      onNodeWithText(title).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
    assertEquals(listOf("Close", "Move down"), actions("Browse").map { it.label })
    assertEquals(listOf("Close", "Move up", "Move down"), actions("A").map { it.label })
    assertEquals(listOf("Close", "Move up"), actions("B").map { it.label })

    actions("A").first { it.label == "Move up" }.action()
    waitForIdle()
    assertEquals(listOf("A", "Browse", "B"), titles())

    actions("Browse").first { it.label == "Move down" }.action()
    waitForIdle()
    assertEquals(listOf("A", "B", "Browse"), titles())

    actions("B").first { it.label == "Close" }.action()
    waitForIdle()
    assertEquals(listOf("A", "Browse"), titles())
  }
}

private val PHONE_WIDTH = 412.dp

private val LANDSCAPE = DpSize(915.dp, 388.dp)

private const val SORT = "Sort 2 new"

private const val CLEAR_VIEWED = "Clear viewed, 1 kept post"

private const val CONTINUE = "Continue · Browse"

private const val UNDO_ONE = "Undo · cleared 1, Undo clear"

private const val UNDO_TWO = "Undo · cleared 2, Undo clear"

private val LINE = listOf(SORT, CLEAR_VIEWED, CONTINUE)

private val FOUR = listOf(SORT, "Clear viewed, 2 kept posts", "Go through later · 1", CONTINUE)

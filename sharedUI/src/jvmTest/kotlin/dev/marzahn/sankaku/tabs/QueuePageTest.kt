package dev.marzahn.sankaku.tabs

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.geometry.Offset
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.shell.MenuBadge
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

  private fun titles() = tabs.tabs.value.map { it.title }

  private fun ComposeUiTest.show(
    onDismiss: () -> Unit = {},
    onMenu: (() -> Unit)? = null,
    menuBadge: MenuBadge = MenuBadge.None,
  ) {
    val privacy = PrivacySettings(InMemoryTextFile())
    setContent {
      SankakuTheme {
        QueuePage(onDismiss = onDismiss, onMenu = onMenu, menuBadge = menuBadge, controller = tabs, posts = TabPosts.None, privacy = privacy, seen = seen, clearViewed = ClearViewed(tabs, seen, Messages()), interacted = { false }, onInteract = {}, folding = false)
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

  private fun ComposeUiTest.closeRow(title: String) {
    onNodeWithText(title).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
      .first { it.label == "Close" }.action()
    waitForIdle()
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

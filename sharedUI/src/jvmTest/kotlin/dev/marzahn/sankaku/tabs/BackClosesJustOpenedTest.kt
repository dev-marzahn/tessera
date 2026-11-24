package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.ui.Messages
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class BackClosesJustOpenedTest {
  private val file = InMemoryTextFile(null)
  private val store = FileTabStateStore(file)
  private val tabs = TabsController(store)
  private val messages = Messages()
  private val navigator = TabsNavigator(tabs, messages)
  private val undo = ClosedTabUndo(tabs, messages = messages)
  private val browse = Route.Grid(emptyList())
  private val artist = Route.Grid(listOf("artist_x"))
  private val tag = Route.Grid(listOf("tag_t"))

  private fun post(id: String) = Route.Post(id, "m$id", artist.tags, label = id)

  private fun ids() = tabs.tabs.value.map { it.id }

  private fun tops() = tabs.tabs.value.map { it.top }

  private fun onALonePost(
    tabs: TabsController = this.tabs,
    navigator: TabsNavigator = this.navigator,
    check: Boolean = true,
  ): TabState {
    tabs.open(artist, background = false)
    navigator.openInNewTab(post("k1"))
    navigator.goTo(post("p"))
    if (check) assertEquals(listOf(browse, artist, post("p"), post("k1")), tabs.tabs.value.map { it.top })
    return tabs.activeTab
  }

  @Test
  fun backStraightAfterOpeningAFindClosesItAndSaysSo() = runTest {
    val p = onALonePost()
    val before = ids()
    navigator.goTo(tag)
    val grid = tabs.activeTab
    assertSame(p, tabs.openerOf(grid))
    val said = async(start = CoroutineStart.UNDISPATCHED) { messages.messages.first() }

    navigator.goBack()

    assertSame(p, tabs.activeTab)
    assertEquals(before, ids())
    assertEquals(tag, tabs.closed.value.last().top)
    val message = said.await()
    assertEquals("Closed “${grid.title}”", message.text)
    assertEquals(KEEP_FOR_LATER, message.actionLabel)
    assertNotNull(message.until)
  }

  @Test
  fun onAPhoneBacksCloseSaysTheSameLineInsteadOfThePillsUndo() = runTest {
    val p = onALonePost()
    navigator.goTo(tag)
    val said = async(start = CoroutineStart.UNDISPATCHED) { messages.messages.first() }

    assertTrue(undo.closeBack(tabs.activeTab.id))

    assertSame(p, tabs.activeTab)
    assertNull(undo.pending.value)
    assertEquals(KEEP_FOR_LATER, said.await().actionLabel)
  }

  @Test
  fun keepForLaterPutsTheFindAtTheEndOfTheQueueAndTheItemStaysShown() {
    val p = onALonePost()
    navigator.goTo(tag)
    val closed = assertNotNull(tabs.closeBack(tabs.activeTab.id))

    backClosedMessage(closed, tabs).onAction()

    assertSame(p, tabs.activeTab)
    assertEquals(listOf(browse, artist, post("p"), post("k1"), tag), tops())
    assertTrue(tabs.closed.value.isEmpty())
    val kept = tabs.tabs.value.last()
    assertNull(kept.openedFrom)
    assertNull(tabs.openerOf(kept))
    assertNull(tabs.backReturnOf(kept))
  }

  @Test
  fun keepForLaterOnAPostKeepsTheWayBackOfItsBlock() {
    val p = onALonePost()
    navigator.goTo(tag)
    val grid = tabs.activeTab
    grid.markTouched(tag)
    navigator.goTo(post("r"))
    val closed = assertNotNull(tabs.closeBack(tabs.activeTab.id))

    backClosedMessage(closed, tabs).onAction()

    assertEquals(listOf(browse, tag, post("r"), artist, post("p"), post("k1")), tops())
    assertSame(grid, tabs.activeTab)
    tabs.activate(tabs.tabs.value.single { it.top == post("r") }.id)
    assertSame(p, tabs.neighbour(forward = true))
  }

  @Test
  fun aPostActedOnIsUsedAndBackGoesToTheItemBefore() {
    onALonePost()
    navigator.goTo(post("r"))
    tabs.touchActive()

    assertNull(tabs.openerOf(tabs.activeTab))
    navigator.goBack()

    assertEquals(post("p"), tabs.activeTab.top)
    assertTrue(tabs.closed.value.isEmpty())
  }

  @Test
  fun aPostClosedAnotherWayAndBroughtBackIsntJustOpened() {
    onALonePost()
    navigator.goTo(post("r"))
    val r = tabs.activeTab
    tabs.reopen(assertNotNull(tabs.close(r.id)))

    assertEquals(post("r"), tabs.activeTab.top)
    assertNull(tabs.openerOf(tabs.activeTab))
  }

  @Test
  fun backsCloseIsntABlockLeft() {
    val left = mutableListOf<TabsController.BlockLeft>()
    tabs.onBlockLeft = { left += it }
    onALonePost()
    navigator.goTo(tag)

    navigator.goBack()

    assertTrue(left.isEmpty())
  }

  @Test
  fun keepForLaterAfterTheFindCameBackAnotherWayDoesNothing() {
    onALonePost()
    navigator.goTo(tag)
    val closed = assertNotNull(tabs.closeBack(tabs.activeTab.id))
    tabs.reopenClosed()
    val before = ids()

    backClosedMessage(closed, tabs).onAction()

    assertEquals(before, ids())
  }

  @Test
  fun recentlyClosedBringsItBackAsJustOpenedAndBackClosesItAgain() {
    val p = onALonePost()
    navigator.goTo(tag)
    val opened = tops()
    assertTrue(undo.closeBack(tabs.activeTab.id))

    tabs.reopenClosed()

    assertEquals(opened, tops())
    assertEquals(tag, tabs.activeTab.top)
    assertSame(p, tabs.openerOf(tabs.activeTab))
    assertSame(p, tabs.neighbour(forward = true))
    assertTrue(undo.closeBack(tabs.activeTab.id))
    assertSame(p, tabs.activeTab)
  }

  @Test
  fun usedFirstBackStepsBackAndTheSourceStays() {
    val uses = mapOf<String, (TabsController, TabsNavigator) -> Unit>(
      "scrolled or a post opened in its viewer" to { tabs, _ -> tabs.activeTab.markTouched(tag) },
      "a post kept from it" to { _, navigator -> navigator.openInNewTab(post("t1")) },
      "a post opened alone from it" to { _, navigator ->
        navigator.goTo(post("t1"))
        navigator.goBack()
      },
      "a search opened for later" to { _, navigator -> navigator.openInNewTab(Route.Grid(listOf("tag_u"))) },
    )
    for ((use, act) in uses) {
      val tabs = TabsController()
      val navigator = TabsNavigator(tabs)
      val p = onALonePost(tabs, navigator, check = false)
      navigator.goTo(tag)
      val grid = tabs.activeTab
      act(tabs, navigator)
      assertSame(grid, tabs.activeTab, use)
      val before = tabs.tabs.value.map { it.id }

      assertNull(tabs.openerOf(grid), use)
      navigator.goBack()

      assertSame(p, tabs.activeTab, use)
      assertEquals(before, tabs.tabs.value.map { it.id }, use)
      assertTrue(grid in tabs.tabs.value, use)
    }
  }

  @Test
  fun aUsedSourceReturnsAtTheQueuesLevel() {
    val p = onALonePost()
    navigator.goTo(tag)
    val grid = tabs.activeTab
    grid.markTouched(tag)

    assertSame(p, tabs.backReturnOf(grid))
    assertTrue(tabs.backReturn(grid.id))
    assertSame(p, tabs.activeTab)
    assertTrue(grid in tabs.tabs.value)
  }

  @Test
  fun reachedFromTheQueuePageAChipMovingOnContinueAStartOrARelaunchBackNeverClosesIt() {
    val privacy = PrivacySettings(InMemoryTextFile())
    val ways = mapOf<String, (TabsController, TabsNavigator, TabState) -> Unit>(
      "a queue page row from another item" to { tabs, _, grid ->
        tabs.activate(tabs.tabs.value.single { it.top == post("k1") }.id)
        tabs.jumpTo(grid.id)
      },
      "its tag tapped again on another item" to { tabs, navigator, _ ->
        tabs.activate(tabs.tabs.value.single { it.top == post("k1") }.id)
        navigator.goTo(tag)
      },
      "moving on and back" to { tabs, _, _ ->
        tabs.previous()
        tabs.next()
      },
      "Continue on the queue page" to { tabs, _, _ -> QueueStart(tabs, privacy).openPage() },
      "a start" to { tabs, _, _ -> QueueStart(tabs, privacy).launched() },
    )
    for ((way, reach) in ways) {
      val tabs = TabsController()
      val navigator = TabsNavigator(tabs)
      onALonePost(tabs, navigator, check = false)
      navigator.goTo(tag)
      val grid = tabs.activeTab
      reach(tabs, navigator, grid)
      assertSame(grid, tabs.activeTab, way)
      val before = tabs.tabs.value.map { it.id }

      assertNull(tabs.openerOf(grid), way)
      navigator.goBack()

      assertEquals(before, tabs.tabs.value.map { it.id }, way)
      assertTrue(tabs.closed.value.isEmpty(), way)
    }
  }

  @Test
  fun afterARelaunchBackGoesBackToTheItemItWasOpenedFromWithoutClosingIt() {
    onALonePost()
    navigator.goTo(tag)
    store.save(tabs.saved())

    val relaunched = TabsController(FileTabStateStore(file))

    val grid = relaunched.activeTab
    assertEquals(tag, grid.top)
    assertNull(relaunched.openerOf(grid))
    val before = relaunched.tabs.value.map { it.id }
    TabsNavigator(relaunched).goBack()
    assertEquals(post("p"), relaunched.activeTab.top)
    assertEquals(before, relaunched.tabs.value.map { it.id })
  }

  @Test
  fun aTappedRecommendedPostBackedOutOfUntouchedGoesWithTheSameLine() = runTest {
    val p = onALonePost()
    navigator.goTo(post("r"))
    val recommended = tabs.activeTab
    assertEquals(listOf(browse, artist, post("p"), post("r"), post("k1")), tops())
    val said = async(start = CoroutineStart.UNDISPATCHED) { messages.messages.first() }

    navigator.goBack()

    assertSame(p, tabs.activeTab)
    assertEquals(listOf(browse, artist, post("p"), post("k1")), tops())
    assertEquals(p.id, tabs.backClosedTo)
    val message = said.await()
    assertEquals("Closed “${recommended.title}”", message.text)
    message.onAction()
    assertEquals(listOf(browse, artist, post("p"), post("k1"), post("r")), tops())
    assertSame(p, tabs.activeTab)
  }

  @Test
  fun aLongPressedRecommendedPostStaysKeptAndBackClosesNothing() {
    val p = onALonePost()
    navigator.openInNewTab(post("r"))
    val before = ids()
    assertEquals(listOf(browse, artist, post("p"), post("k1"), post("r")), tops())

    assertNull(tabs.openerOf(p))
    navigator.goBack()

    assertEquals(artist, tabs.activeTab.top)
    assertEquals(before, ids())
  }

  @Test
  fun theLineLastsUntilTheNextAction() = runTest {
    onALonePost()
    navigator.goTo(tag)
    val closed = assertNotNull(tabs.closeBack(tabs.activeTab.id))
    val until = assertNotNull(backClosedMessage(closed, tabs).until)
    val ended = launch(start = CoroutineStart.UNDISPATCHED) { until() }
    runCurrent()
    assertTrue(ended.isActive)

    tabs.previous()
    runCurrent()

    assertTrue(ended.isCompleted)
  }

  @Test
  fun aSearchOpenedFromTheFindIsSomethingDoneInItSoItUnwindsOneStepAtATime() {
    val p = onALonePost()
    navigator.goTo(tag)
    val grid = tabs.activeTab
    grid.markTouched(tag)
    navigator.goTo(Route.Grid(listOf("tag_u")))
    val before = ids()

    navigator.goBack()
    assertSame(grid, tabs.activeTab)
    assertTrue(tabs.closed.value.single().top == Route.Grid(listOf("tag_u")))

    navigator.goBack()
    assertSame(p, tabs.activeTab)
    assertTrue(grid in tabs.tabs.value)
    assertEquals(before.size - 1, ids().size)
  }

  @Test
  fun fromATouchedGridOrAFeedAndForSearchOnlyThisAndChildPostsTheSame() {
    val feed = Route.Grid(listOf("feed:following"))
    val searchOnlyThis = Route.Grid(listOf("order:popularity", "tag_t"))
    val childPosts = Route.Grid(listOf("parent:123"))
    for (from in listOf(artist, feed)) {
      for (opened in listOf(tag, searchOnlyThis, childPosts)) {
        val tabs = TabsController()
        val navigator = TabsNavigator(tabs)
        tabs.open(from, background = false)
        val grid = tabs.activeTab
        grid.markTouched(from)
        navigator.openInNewTab(post("k1"))
        val before = tabs.tabs.value.map { it.id }

        navigator.goTo(opened)
        assertEquals(opened, tabs.activeTab.top, "$from → $opened")
        navigator.goBack()

        assertSame(grid, tabs.activeTab, "$from → $opened")
        assertEquals(before, tabs.tabs.value.map { it.id }, "$from → $opened")
      }
    }
  }

  @Test
  fun aSearchRunInTheGridInPlaceGoesBackFirstThenTheGridCloses() {
    val p = onALonePost()
    navigator.goTo(tag)
    val grid = tabs.activeTab
    val other = Route.Grid(listOf("tag_u"))
    navigator.goTo(other)
    assertSame(grid, tabs.activeTab)
    assertNull(tabs.openerOf(grid))

    navigator.goBack()
    assertSame(grid, tabs.activeTab)
    assertEquals(tag, grid.top)

    navigator.goBack()
    assertSame(p, tabs.activeTab)
  }

  @Test
  fun aSourceShownBecauseItsSearchWasOpenAgainIsntClosedByBack() {
    tabs.open(tag, background = true)
    val p = onALonePost(check = false)

    navigator.goTo(tag)
    val source = tabs.activeTab
    assertNull(tabs.openerOf(source))
    val before = ids()

    navigator.goBack()

    assertSame(p, tabs.activeTab)
    assertEquals(before, ids())
  }

  @Test
  fun fromALonePostOnAFeedBackReturnsToThePost() {
    val feed = Route.Grid(listOf("feed:following"))
    tabs.open(feed, background = false)
    navigator.goTo(post("f1"))
    val lone = tabs.activeTab
    val before = ids()

    navigator.goTo(tag)
    navigator.goBack()

    assertSame(lone, tabs.activeTab)
    assertEquals(before, ids())
  }

  @Test
  fun withTheItemItCameFromGoneBackKeepsTodaysBack() {
    val p = onALonePost()
    navigator.goTo(tag)
    val grid = tabs.activeTab
    tabs.close(p.id)
    val before = ids()

    navigator.goBack()

    assertSame(grid, tabs.activeTab)
    assertEquals(before, ids())
  }

  @Test
  fun theItemItCameFromReopenedUnderANewIdIsStillWhereBackGoes() {
    onALonePost()
    navigator.goTo(tag)
    val grid = tabs.activeTab
    val p = tabs.tabs.value.single { it.top == post("p") }
    tabs.reopen(assertNotNull(tabs.close(p.id)), inPlace = true)
    assertSame(grid, tabs.activeTab)

    navigator.goBack()

    assertEquals(post("p"), tabs.activeTab.top)
  }

  @Test
  fun aGridFromBeforeTheLastClearViewedKeepsTodaysBack() {
    onALonePost()
    navigator.goTo(tag)
    val grid = tabs.activeTab
    tabs.sessions.startNew()

    assertNull(tabs.openerOf(grid))
  }

  @Test
  fun startOnBrowseIsNotOpenedFromAnItem() {
    onALonePost()
    tabs.showBrowse()

    assertNull(tabs.openerOf(tabs.activeTab))
  }

  @Test
  fun aFileWithoutItLoadsAndSavesBackUnchangedAndItsGridsKeepTodaysBack() {
    val saved = """{"version":2,"session":"s1","activeIndex":0,"tabs":[""" +
      """{"id":"a","routes":[{"type":"dev.marzahn.sankaku.Route.Grid","tags":["tag_t"]}],"kind":"source","touched":1000,"addedIn":"s1"},""" +
      """{"id":"b","routes":[{"type":"dev.marzahn.sankaku.Route.Post","id":"p","md5":"mp","originTags":["artist_x"]}],"kind":"post","touched":1000,"addedIn":"s1","keptAt":1000}""" +
      """]}"""
    val existing = InMemoryTextFile(saved)
    val existingStore = FileTabStateStore(existing)
    val before = existingStore.load()
    val clock = object : Clock {
      override fun now() = Instant.fromEpochSeconds(1000)
    }
    val controller = TabsController(existingStore, clock = clock)

    assertEquals(before, controller.saved())
    assertNull(controller.openerOf(controller.activeTab))
    existingStore.save(controller.saved())
    assertFalse(existing.read()!!.contains("openedFrom"))
    assertEquals(before, existingStore.load())
    val fresh = InMemoryTextFile(null)
    FileTabStateStore(fresh).save(TabsController().saved())
    assertFalse(fresh.read()!!.contains("openedFrom"))
  }

  @Test
  fun backOnAFindJustOpenedClosesItAtTheQueuesLevel() {
    assertEquals(QueueBack.Close, queueBack(cardUp = false, lonePost = false, previous = false, opened = true))
    assertEquals(QueueBack.Close, queueBack(cardUp = false, lonePost = false, previous = true, opened = true))
    assertEquals(QueueBack.Close, queueBack(cardUp = false, lonePost = true, previous = true, opened = true))
    assertEquals(QueueBack.Previous, queueBack(cardUp = true, lonePost = false, previous = false, opened = true))
    assertEquals(QueueBack.Tab, queueBack(cardUp = false, lonePost = false, previous = true, opened = false))
  }
}

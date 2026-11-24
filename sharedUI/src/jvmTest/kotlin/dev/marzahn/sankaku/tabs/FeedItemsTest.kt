package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.queue.FeedItems
import dev.marzahn.sankaku.queue.QueueKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FeedItemsTest {
  private val tabs = TabsController()
  private val following = Route.Grid(listOf(Feed.Following.tag))
  private val forYou = Route.Grid(listOf(Feed.ForYou.tag))

  private fun grid(vararg tags: String) = Route.Grid(tags.toList())

  private fun post(id: String) = Route.Post(id, "m$id", listOf("a"), label = "Post $id", rating = "s")

  private fun titles(controller: TabsController = tabs) = controller.tabs.value.map { it.title }

  private fun tab(title: String) = tabs.tabs.value.single { it.title == title }

  private fun signIn(forYouInQueue: Boolean = false) = tabs.pinFeeds(FeedItems.pinned(signedIn = true, forYouInQueue))

  private fun signOut() = tabs.pinFeeds(FeedItems.pinned(signedIn = false, forYouInQueue = false))

  private fun queue() {
    tabs.open(grid("a"), background = false)
    tabs.openInBlock(listOf(post("p1")))
    tabs.open(grid("b"), background = true)
    tabs.activate(tabs.tabs.value.first().id)
  }

  @Test
  fun signedInFollowingIsTheLastItemAndSwipingPastTheLastItemReachesIt() {
    queue()
    signIn()

    assertEquals(listOf("Browse", "A", "Post p1", "B", "Following"), titles())
    assertTrue(tabs.isFeedItem(tab("Following")))
    tabs.activate(tab("B").id)
    assertTrue(tabs.next())
    assertEquals("Following", tabs.activeTab.title)
    assertFalse(tabs.next())
  }

  @Test
  fun signedOutThereIsNoFeedItemAndSigningOutRemovesIt() {
    queue()
    signOut()
    assertEquals(listOf("Browse", "A", "Post p1", "B"), titles())

    signIn()
    tabs.activate(tab("Following").id)
    signOut()

    assertEquals(listOf("Browse", "A", "Post p1", "B"), titles())
    assertEquals("B", tabs.activeTab.title)
    assertTrue(tabs.closed.value.isEmpty())
  }

  @Test
  fun aFollowingRestoredWhileSignedOutLeaves() {
    val saved = SavedTabs(1, listOf(SavedTab("x", listOf(grid("x"))), SavedTab("f", listOf(following))))
    val restored = TabsController(FakeStore(saved))
    restored.pinFeeds(FeedItems.pinned(signedIn = false, forYouInQueue = false))

    assertEquals(listOf("X"), titles(restored))
    assertEquals("X", restored.activeTab.title)
  }

  @Test
  fun reopeningAFollowingClosedBeforeSigningInGoesToTheItem() {
    queue()
    tabs.open(following, background = true)
    val closed = tabs.close(tab("Following").id)!!
    signIn()
    val feed = tab("Following")

    tabs.reopen(closed)

    assertEquals(listOf("Browse", "A", "Post p1", "B", "Following"), titles())
    assertSame(feed, tabs.activeTab)
  }

  @Test
  fun reopeningAFollowingWhileSignedOutBringsNothingBack() {
    queue()
    signIn()
    tabs.open(grid("C"), background = true)
    val closed = tabs.close(tab("C").id)!!
    signOut()
    tabs.open(following, background = true)
    val followingClosed = tabs.close(tab("Following").id)!!

    tabs.reopen(followingClosed)
    assertTrue(tabs.tabs.value.none { it.feed == Feed.Following })
    tabs.reopen(closed)
    assertEquals(listOf("Browse", "A", "Post p1", "B", "C"), titles())
  }

  @Test
  fun clearEverythingMakesTheFeedItemsAgainWithoutTheirPlace() {
    queue()
    signIn(forYouInQueue = true)
    val feed = tab("Following")
    feed.reportPlace(following, GridPlace("p9", cursor = "c", page = 3))
    tabs.activate(feed.id)

    tabs.clearAll()

    assertEquals(listOf("Browse", "Following", "For you"), titles())
    assertEquals("Browse", tabs.activeTab.title)
    val again = tab("Following")
    assertTrue(again !== feed)
    assertNull(again.place)
    assertEquals(listOf(following), again.backStack.toList())
    assertTrue(tabs.isFeedItem(again))
    assertTrue(tabs.closed.value.flatMap { it.members }.none { m -> m.routes.any { it == following || it == forYou } })
  }

  @Test
  fun theCountLeavesTheFeedsOut() {
    queue()
    signIn(forYouInQueue = true)

    assertEquals(6, tabs.tabs.value.size)
    assertEquals(4, countedItems(tabs.tabs.value))
  }

  @Test
  fun aFeedItemCannotBeClosed() {
    queue()
    signIn()
    val feed = tab("Following")
    tabs.activate(feed.id)

    assertFalse(tabs.canClose(feed))
    assertNull(tabs.close(feed.id))
    assertNull(tabs.close(feed.id, toPrevious = true))
    assertEquals("Following", tabs.activeTab.title)
    assertTrue(feed.id !in tabs.otherTabs())
    tabs.activate(tab("A").id)
    assertTrue(feed.id !in tabs.tabsToTheRight())
    tabs.closeAll(tabs.tabs.value.map { it.id })
    assertEquals(listOf("Following"), titles())
    assertEquals("Following", tabs.activeTab.title)
  }

  @Test
  fun openingFollowingGoesToTheItemAndAddsNoSecond() {
    queue()
    signIn()
    val feed = tab("Following")

    tabs.search(following)
    assertSame(feed, tabs.activeTab)
    tabs.activate(tab("A").id)
    tabs.open(following, background = false)
    assertSame(feed, tabs.activeTab)
    assertEquals(1, tabs.tabs.value.count { it.feed == Feed.Following })
  }

  @Test
  fun forYouIsAnItemOnlyWithItsSettingOn() {
    queue()
    signIn()
    assertTrue(tabs.tabs.value.none { it.feed == Feed.ForYou })

    signIn(forYouInQueue = true)
    assertEquals(listOf("Browse", "A", "Post p1", "B", "Following", "For you"), titles())
    assertFalse(tabs.canClose(tab("For you")))

    signIn(forYouInQueue = false)
    assertEquals(listOf("Browse", "A", "Post p1", "B", "Following"), titles())
  }

  @Test
  fun forYouOpenedWhileItIsNoItemCanStillBeClosed() {
    queue()
    signIn()
    tabs.open(forYou, background = true)

    assertEquals(listOf("Browse", "A", "Post p1", "B", "Following", "For you"), titles())
    assertTrue(tabs.canClose(tab("For you")))
    assertEquals(4, countedItems(tabs.tabs.value))
  }

  @Test
  fun aFollowingAlreadyOpenBecomesTheItemWithItsPlaceWhereItStands() {
    tabs.open(following, background = true)
    tabs.open(grid("a"), background = true)
    val feed = tab("Following")
    feed.reportPlace(following, GridPlace("p9", cursor = "c", page = 3))
    assertEquals(listOf("Browse", "Following", "A"), titles())

    signIn()

    assertEquals(listOf("Browse", "Following", "A"), titles())
    assertSame(feed, tab("Following"))
    assertEquals(GridPlace("p9", cursor = "c", page = 3), feed.place)
  }

  @Test
  fun aSavedQueueWithFollowingInTheMiddleLoadsAsSavedWithItsPlaceAndKeptPosts() {
    val place = GridPlace("p9", cursor = "c", page = 3)
    val saved = SavedTabs(
      activeIndex = 1,
      tabs = listOf(
        SavedTab("x", listOf(grid("x"))),
        SavedTab("f", listOf(following), place = place),
        SavedTab("k", listOf(post("k")), block = "f"),
        SavedTab("y", listOf(grid("y"))),
      ),
    )
    val restored = TabsController(FakeStore(saved))
    restored.pinFeeds(listOf(Feed.Following))

    assertEquals(listOf("X", "Following", "Post k", "Y"), titles(restored))
    assertEquals("Following", restored.activeTab.title)
    assertEquals(place, restored.activeTab.place)
    assertEquals(TabId("f"), restored.tabs.value[2].block)
    assertEquals(1, restored.tabs.value.count { it.feed == Feed.Following })
  }

  @Test
  fun aFeedSavedOverAnotherGridBecomesTheFeedAlone() {
    val saved = SavedTabs(0, listOf(SavedTab("f", listOf(grid(), following))))
    val restored = TabsController(FakeStore(saved))
    restored.pinFeeds(listOf(Feed.Following))

    assertEquals(listOf(following), restored.activeTab.backStack.toList())
    TabsNavigator(restored).goBack()
    assertEquals(QueueKind.Feed, restored.activeTab.kind)
  }

  @Test
  fun laterGoesAfterTheFeedsAndASearchOnAFeedOpensInFrontOfIt() {
    queue()
    signIn()
    tabs.open(grid("c"), background = true)
    assertEquals("C", titles().last())

    tabs.activate(tab("Following").id)
    tabs.search(grid("artist_x"))

    assertEquals(listOf("Browse", "A", "Post p1", "B", "Artist X", "Following", "C"), titles())
    assertEquals("Artist X", tabs.activeTab.title)
    assertEquals(following, tab("Following").top)
    assertTrue(tabs.next())
    assertEquals("Following", tabs.activeTab.title)
  }

  @Test
  fun aPostKeptFromAFeedGoesInItsBlock() {
    queue()
    signIn()
    val feed = tab("Following")
    tabs.activate(feed.id)

    tabs.openInBlock(listOf(post("f1")))

    assertEquals(listOf("Browse", "A", "Post p1", "B", "Following", "Post f1"), titles())
    assertEquals(feed.id, tab("Post f1").block)
    assertSame(feed, tabs.sourceOf(tab("Post f1")))
    assertEquals("Following", tabs.activeTab.title)
  }

  @Test
  fun backOnALonePostOpenedFromAFeedReturnsToTheFeed() {
    queue()
    signIn()
    val feed = tab("Following")
    tabs.activate(feed.id)

    tabs.openLone(post("r1"))
    assertEquals(listOf("Browse", "A", "Post p1", "B", "Following", "Post r1"), titles())
    assertSame(feed, tabs.neighbour(forward = false))
    assertTrue(tabs.previous())
    assertSame(feed, tabs.activeTab)

    tabs.activate(tab("Post r1").id)
    val upNext = noUpNext(tabs)
    ClosedTabUndo(tabs).dragClose(tabs.active.value, upNext)
    assertSame(feed, tabs.activeTab)
    assertEquals(UpNextCard.End, upNext.card)
  }

  private class FakeStore(val saved: SavedTabs?) : TabStateStore {
    override fun load() = saved

    override fun save(tabs: SavedTabs) = Unit

    override fun clear() = Unit
  }
}

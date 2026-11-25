package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.queue.FeedItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock

class FeedIsASourceTest {
  private val file = InMemoryTextFile()
  private val tabs = TabsController(FileTabStateStore(file))

  private fun grid(vararg tags: String) = Route.Grid(tags.toList())

  private fun post(id: String) = Route.Post(id, "m$id", listOf("a"), label = "Post $id", rating = "s")

  private fun titles(controller: TabsController = tabs) = controller.tabs.value.map { it.title }

  private fun tab(title: String) = tabs.tabs.value.single { it.title == title }

  private fun pin(forYou: Boolean) = tabs.pinFeeds(FeedItems.pinned(signedIn = true, forYouInQueue = forYou))

  private fun ownersQueue() {
    pin(forYou = true)
    tabs.activate(tab("Following").id)
    TabsNavigator(tabs).openInNewTab(post("f1"))
    TabsNavigator(tabs).openInNewTab(post("f2"))
    assertEquals(listOf("Browse", "Following", "Post f1", "Post f2", "For you"), titles())
  }

  @Test
  fun aSearchForLaterFromAFeedsKeptPostGoesAfterForYouAndMovingOnReachesIt() {
    ownersQueue()
    tabs.activate(tab("Post f1").id)

    assertNull(TabsNavigator(tabs).openInNewTab(grid("artist_x")))

    assertEquals(listOf("Browse", "Following", "Post f1", "Post f2", "For you", "Artist X"), titles())
    assertEquals("Post f1", tabs.activeTab.title)
    val reached = generateSequence { if (tabs.next()) tabs.activeTab.title else null }.toList()
    assertEquals(listOf("Post f2", "For you", "Artist X"), reached)
  }

  @Test
  fun aTagTappedOnAFeedsKeptPostGoesInFrontOfTheFeedsBlockAndUnwindsToThePost() {
    ownersQueue()
    tabs.activate(tab("Post f2").id)

    TabsNavigator(tabs).goTo(grid("artist_x"))

    assertEquals(listOf("Browse", "Artist X", "Following", "Post f1", "Post f2", "For you"), titles())
    assertEquals("Artist X", tabs.activeTab.title)
    assertEquals("Post f2", tabs.neighbour(forward = true)?.title)
    assertTrue(tabs.next())
    assertEquals("Post f2", tabs.activeTab.title)
  }

  @Test
  fun signingInOrTurningOnForYouAddsTheFeedAtTheEnd() {
    tabs.open(grid("a"), background = true)
    pin(forYou = false)
    assertEquals(listOf("Browse", "A", "Following"), titles())

    tabs.open(grid("b"), background = true)
    pin(forYou = true)
    assertEquals(listOf("Browse", "A", "Following", "B", "For you"), titles())

    tabs.pinFeeds(FeedItems.pinned(signedIn = false, forYouInQueue = true))
    assertEquals(listOf("Browse", "A", "B"), titles())
  }

  @Test
  fun aFeedComingBackTakesItsKeptPostsToTheEndWithIt() {
    ownersQueue()
    tabs.open(grid("b"), background = true)
    tabs.pinFeeds(emptyList())
    assertEquals(listOf("Browse", "Post f1", "Post f2", "B"), titles())

    pin(forYou = false)

    val feed = tab("Following")
    assertEquals(listOf("Browse", "B", "Following", "Post f1", "Post f2"), titles())
    assertSame(feed, tabs.sourceOf(tab("Post f1")))
    assertSame(feed, tabs.sourceOf(tab("Post f2")))
  }

  @Test
  fun openingAnOpenFeedInANewTabGivesAPositionOnThePillsScaleNeverZero() {
    pin(forYou = false)
    tabs.moveInQueue(tab("Following").id, down = false)
    assertEquals(listOf("Following", "Browse"), titles())

    assertEquals(1, TabsNavigator(tabs).openInNewTab(grid(Feed.Following.tag)))
    tabs.open(grid("a"), background = true)
    assertEquals(2, TabsNavigator(tabs).openInNewTab(grid("a")))
    tabs.moveInQueue(tab("Following").id, down = true)
    assertEquals(listOf("Browse", "Following", "A"), titles())
    assertEquals(1, TabsNavigator(tabs).openInNewTab(grid(Feed.Following.tag)))
  }

  @Test
  fun endOfTheQueueStandsPastTheLastItemWhateverItIs() {
    ownersQueue()
    val upNext = noUpNext(tabs)
    tabs.activate(tab("For you").id)
    assertIs<Ahead.End>(upNext.ahead())
    assertNull(tabs.neighbour(forward = true))
    tabs.activate(tab("Post f2").id)
    assertIs<Ahead.Item>(upNext.ahead())

    tabs.open(grid("later"), background = true)
    tabs.activate(tab("For you").id)
    assertIs<Ahead.Item>(upNext.ahead())
    tabs.activate(tab("Later").id)
    assertIs<Ahead.End>(upNext.ahead())
  }

  @Test
  fun aSavedQueueWithItemsAfterTheFeedsRoundTrips() {
    ownersQueue()
    tabs.open(grid("later"), background = true)
    val saved = tabs.saved()
    FileTabStateStore(file).save(saved)

    val restored = TabsController(FileTabStateStore(file))
    restored.pinFeeds(FeedItems.pinned(signedIn = true, forYouInQueue = true))

    assertEquals(listOf("Browse", "Following", "Post f1", "Post f2", "For you", "Later"), titles(restored))
    assertEquals(saved.tabs.map { it.id }, restored.saved().tabs.map { it.id })
    assertEquals(saved.tabs.map { it.block }, restored.saved().tabs.map { it.block })
  }

  @Test
  fun anOldFileWithAFeedInTheMiddleLoadsAsItWasAndIsNotRewritten() {
    val t = Clock.System.now().epochSeconds
    val text = """{"version":2,"session":"s","activeIndex":0,"tabs":[""" +
      """{"id":"x","routes":[{"type":"dev.marzahn.sankaku.Route.Grid","tags":["x"]}],"kind":"source","touched":$t,"addedIn":"s"},""" +
      """{"id":"f","routes":[{"type":"dev.marzahn.sankaku.Route.Grid","tags":["${Feed.Following.tag}"]}],"kind":"feed","touched":$t,"addedIn":"s"},""" +
      """{"id":"k","routes":[{"type":"dev.marzahn.sankaku.Route.Post","id":"k","md5":"mk","originTags":["a"],"label":"Post k"}],"block":"f","kind":"post","touched":$t,"addedIn":"s","keptAt":$t},""" +
      """{"id":"y","routes":[{"type":"dev.marzahn.sankaku.Route.Grid","tags":["y"]}],"kind":"source","touched":$t,"addedIn":"s"}]}"""
    val old = InMemoryTextFile(text)
    val store = FileTabStateStore(old)
    val loaded = store.load()!!

    val restored = TabsController(RecordingTabStateStore(loaded))
    restored.pinFeeds(listOf(Feed.Following))

    assertEquals(listOf("X", "Following", "Post k", "Y"), titles(restored))
    assertEquals(loaded.tabs.map { it.id }, restored.saved().tabs.map { it.id })
    assertEquals(text, old.read())
  }
}

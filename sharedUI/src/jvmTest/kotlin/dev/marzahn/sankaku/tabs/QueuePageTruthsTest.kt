package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.queue.FeedGap
import dev.marzahn.sankaku.queue.FeedMark
import dev.marzahn.sankaku.queue.FeedMarks
import dev.marzahn.sankaku.queue.FeedPost
import dev.marzahn.sankaku.queue.QueueKind
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class QueuePageTruthsTest {
  private val tabs = TabsController()
  private val navigator = TabsNavigator(tabs)

  private fun post(id: String, label: String = id) = Route.Post(id, md5 = "m$id", originTags = emptyList(), label = label, rating = "s")

  private fun keep(id: String, from: String, label: String = id) =
    tabs.openInBlock(listOf(Route.Grid(listOf(from), at = id), post(id, label)))

  private fun rows(query: String? = null, open: Set<TabId>? = emptySet()) =
    queueRows(tabs.tabs.value, emptyList(), inline = null, query, gOnly = false, open = open) { null }

  private fun List<QueueRow>.described() = map { row ->
    when (row) {
      is QueueRow.Item -> (if (row.indented) "  " else "") + (row.shownNumber?.let { "$it " } ?: "") + row.tab.title
      is QueueRow.ClosedBlock -> "${row.number} [${row.title}]"
      is QueueRow.HiddenByGOnly -> "Hidden ${row.count}"
      is QueueRow.Inline -> "Closed"
    }
  }

  @Test
  fun numbersCountBlocksSoFoldedPostsTakeNone() {
    tabs.open(Route.Grid(listOf("a")), background = false)
    keep("p1", "a")
    keep("p2", "a")
    tabs.open(Route.Grid(listOf("b")), background = false)
    keep("p3", "b")
    tabs.open(Route.Grid(listOf("c")), background = true)

    assertEquals(listOf("1 Browse", "2 A", "3 B", "4 C"), rows().described())
    val a = tabs.tabs.value.single { it.title == "A" }.id
    assertEquals(listOf("1 Browse", "2 A", "  p1", "  p2", "3 B", "4 C"), rows(open = setOf(a)).described())
    assertEquals(4, numberedBlocks(tabs.tabs.value))
  }

  @Test
  fun aClosedBlockAndAPostOnItsOwnAreBlocksButAFeedIsNot() {
    tabs.open(Route.Grid(listOf("a")), background = false)
    keep("p1", "a")
    tabs.open(Route.Grid(listOf("b")), background = false)
    tabs.pinFeeds(listOf(Feed.Following))
    tabs.close(tabs.tabs.value.single { it.title == "A" }.id)

    assertEquals(listOf("1 Browse", "2 [from A (closed)]", "  p1", "3 B", "Following"), rows(open = null).described())
    assertEquals(listOf(1, 2, 3, null), queueNumbers(tabs.tabs.value))
  }

  @Test
  fun theKeptChipCountsPostsNotBlocks() {
    tabs.open(Route.Grid(listOf("a")), background = false)
    keep("p1", "a")
    keep("p2", "a")
    keep("p3", "a")
    tabs.open(Route.Grid(listOf("b")), background = false)
    keep("p4", "b")

    assertEquals(listOf("p4", "p1", "p2", "p3"), keptListed(tabs.tabs.value, gOnly = false) { null }.map { it.title })
  }

  @Test
  fun findListsTheSourcesBeforeTheKeptPosts() {
    tabs.open(Route.Grid(listOf("a")), background = false)
    keep("p1", "a", label = "Cloth one")
    keep("p2", "a", label = "Cloth two")
    tabs.open(Route.Grid(listOf("clothing")), background = true)

    assertEquals(listOf("3 Clothing", "Cloth one", "Cloth two"), rows(query = "cloth", open = null).described())
    val first = rows(query = "cloth", open = null)[1] as QueueRow.Item
    assertEquals("Kept from A", queueSubtitle(first.tab, current = false, seen = false, rating = null, from = first.from))
  }

  @Test
  fun aSourceWhoseKeptPostsMatchTooStaysTheirHead() {
    tabs.open(Route.Grid(listOf("cloth")), background = false)
    keep("p1", "cloth", label = "Cloth one")

    assertEquals(listOf("2 Cloth", "Cloth one"), rows(query = "cloth", open = null).described())
  }

  @Test
  fun theBarDrawsOnlyAgainstATotal() {
    assertNull(progressFraction(SourceProgress(161, SourceSize.Counted(209, more = true))))
    assertEquals(0.5f, progressFraction(SourceProgress(120, SourceSize.About(240))))
    assertEquals(0.5f, progressFraction(SourceProgress(15, SourceSize.Counted(30, more = false))))
  }

  @Test
  fun keptTimesFollowTheClock() {
    val now = Instant.parse("2026-11-15T20:00:00Z")
    val at = Instant.parse("2026-11-15T17:21:00Z")
    assertEquals("kept 17:21", keptWords(at, now, TimeZone.UTC, hours24 = true))
    assertEquals("kept 5:21 PM", keptWords(at, now, TimeZone.UTC, hours24 = false))
    assertEquals("12:05 AM", clockTime(0, 5, hours24 = false))
    assertEquals("12:30 PM", clockTime(12, 30, hours24 = false))
  }

  @Test
  fun aFindMadeOnAPostInTheFeedIsFromAPostInFollowing() {
    tabs.pinFeeds(listOf(Feed.Following))
    val following = tabs.tabs.value.single { it.kind == QueueKind.Feed }
    tabs.activate(following.id)
    following.coverChrome(following.top!!, true)
    navigator.goTo(Route.Grid(listOf("artist_y")))

    val find = tabs.tabs.value.single { it.title == "Artist Y" }
    assertEquals(FindWay.Post, find.foundOn?.way)
    assertEquals("from a post in Following", originText(find))
    assertEquals(emptyList(), tabs.findsOf(following.top.let { (it as Route.Grid).tags }))
  }

  @Test
  fun aFindFromAPostKeptFromTheFeedIsFromAPostInFollowing() {
    tabs.pinFeeds(listOf(Feed.Following))
    val following = tabs.tabs.value.single { it.kind == QueueKind.Feed }
    tabs.activate(following.id)
    navigator.openInNewTab(post("f1"))
    tabs.activate(tabs.tabs.value.single { it.title == "f1" }.id)
    navigator.openInNewTab(Route.Grid(listOf("scenery")))

    assertEquals("from a post in Following", originText(tabs.tabs.value.single { it.title == "Scenery" }))
  }

  @Test
  fun aSearchOnTheFeedsOwnGridIsNoFind() {
    tabs.pinFeeds(listOf(Feed.Following))
    tabs.activate(tabs.tabs.value.single { it.kind == QueueKind.Feed }.id)
    navigator.goTo(Route.Grid(listOf("artist_y")))

    assertNull(tabs.tabs.value.single { it.title == "Artist Y" }.foundOn)
  }

  @Test
  fun aSearchTypedOverAGridIsNoFindOfIt() {
    val cityscape = Route.Grid(listOf("cityscape"))
    tabs.open(cityscape, background = false)
    tabs.activeTab.markTouched(cityscape)

    navigator.goToTyped(Route.Grid(listOf("sky")))
    assertNull(tabs.tabs.value.single { it.title == "Sky" }.foundOn)
    assertNull(originText(tabs.tabs.value.single { it.title == "Sky" }))

    tabs.activate(tabs.tabs.value.single { it.title == "Cityscape" }.id)
    navigator.goTo(Route.Grid(listOf("night")))
    assertEquals("from Cityscape", originText(tabs.tabs.value.single { it.title == "Night" }))
  }

  @Test
  fun aSearchRunInPlaceOverAFindIsNotWhereItCameFrom() {
    tabs.pinFeeds(listOf(Feed.Following))
    val following = tabs.tabs.value.single { it.kind == QueueKind.Feed }
    tabs.activate(following.id)
    following.coverChrome(following.top!!, true)
    navigator.goTo(Route.Grid(listOf("artist_y")))
    val find = tabs.activeTab
    following.coverChrome(following.top!!, false)

    navigator.goTo(Route.Grid(listOf("order:popularity")))
    assertEquals(find, tabs.activeTab)
    assertNull(originText(find))
    find.pop()
    assertEquals("from a post in Following", originText(find))
  }

  @Test
  fun followingSaysWhatItsMarksAndFirstPageHold() {
    val now = Instant.parse("2026-11-15T17:00:00Z")
    val top = FeedMark("p3", 300)
    val gap = FeedGap(stop = FeedMark("p1", 100), until = FeedMark("p0", 50), left = 30, since = 0)
    val marks = FeedMarks(top, gap, at = 0)
    val posts = listOf(FeedPost("n2", 500), FeedPost("n1", 400), FeedPost("p3", 300), FeedPost("p2", 200))

    val news = FollowingNews.of(marks, posts, moreAfter = true, now)
    assertEquals(FollowingNews(2, left = 30, hasGap = true), news)
    assertEquals(listOf("2 new", "30 left from last time", "4 kept"), followingWords(news, kept = 4))
    assertEquals(FeedCatchUp.At.Top, news.catchUpAt)

    val caughtUp = FollowingNews.of(marks, posts.drop(2), moreAfter = true, now)
    assertEquals(listOf("Caught up", "30 left from last time"), followingWords(caughtUp, kept = 0))
    assertEquals(FeedCatchUp.At.Gap, caughtUp.catchUpAt)

    assertEquals(listOf("2+ new"), followingWords(FollowingNews.of(FeedMarks(FeedMark("old", 10), at = 0), posts.take(2), true, now), 0))
    assertEquals(listOf("30 left from last time"), followingWords(FollowingNews.of(marks), kept = 0))
    assertEquals(listOf("Feed"), followingWords(FollowingNews.of(null), kept = 0))
    assertNull(FollowingNews.of(marks).catchUpAt)
  }
}

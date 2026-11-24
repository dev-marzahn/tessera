package dev.marzahn.sankaku.queue

import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.data.PageCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class FeedMarksTest {
  private val now = Instant.parse("2026-11-12T12:00:00Z")

  private fun feed(newer: Int, old: Int): List<FeedPost> =
    (List(newer) { "n$it" } + List(old) { "p$it" }).mapIndexed { i, id ->
      FeedPost(id, TOP_TIME - i * 60L, PageCursor(if (i < 40) null else "c${i / 40}", i / 40 + 1))
    }

  private fun mark(posts: List<FeedPost>, id: String) = posts.first { it.id == id }.mark

  private fun marks(top: FeedMark, gap: FeedGap? = null, at: Instant = now - 1.days) = FeedMarks(top, gap, at.epochSeconds)

  private fun FeedMarks?.plain() = this?.copy(reached = FeedReached())

  private fun headings(posts: List<FeedPost>, marks: FeedMarks?, atTop: Boolean = true, feed: Feed = Feed.Following) =
    FeedReading.headings(feed, posts, atTop, marks, now, CONTEXT)

  private fun late(posts: List<FeedPost>, atTop: Boolean, marks: FeedMarks?, context: String? = CONTEXT): Set<String> =
    FeedReading.late(Feed.Following, posts, atTop, marks, context)

  @Test
  fun aFirstVisitHasNoHeadings() {
    assertEquals(emptyList(), headings(feed(0, 60), null))
  }

  @Test
  fun newPostsGetNNewAtTheTopAndCaughtUpAboveTheNewestPostReached() {
    val posts = feed(12, 60)

    val shown = headings(posts, marks(mark(posts, "p0")))

    assertEquals(
      listOf(PlacedHeading(0, FeedHeading.New(12, more = false, gapLeft = null)), PlacedHeading(12, FeedHeading.CaughtUp)),
      shown,
    )
  }

  @Test
  fun nothingNewSaysCaughtUpAtTheTop() {
    val posts = feed(0, 60)
    assertEquals(listOf(PlacedHeading(0, FeedHeading.UpToDate(gapLeft = null))), headings(posts, marks(mark(posts, "p0"))))
  }

  @Test
  fun nothingNewWithAGapOffersContinueAtTheTop() {
    val posts = feed(0, 60)
    val gap = FeedGap(stop = mark(posts, "p3"), until = mark(posts, "p20"), left = 16, since = now.epochSeconds)

    val shown = headings(posts, marks(mark(posts, "p0"), gap))

    assertEquals(PlacedHeading(0, FeedHeading.UpToDate(gapLeft = 16)), shown.first())
  }

  @Test
  fun nothingNewOpenedMidwayHasNoCaughtUp() {
    val posts = feed(0, 60)
    assertEquals(emptyList(), headings(posts.drop(20), marks(mark(posts, "p0")), atTop = false))
  }

  private fun before(posts: List<FeedPost>, vararg late: String) = posts.filterNot { it.id in late }

  private fun visited(seen: List<FeedPost>, first: Int, last: Int, start: FeedMarks? = null): FeedMarks =
    visit(start).apply { reach(seen, atTop = true, first, last) }.marks!!

  private fun withLate(posts: List<FeedPost>, index: Int, id: String): List<FeedPost> {
    val time = (posts[index - 1].time + posts[index].time) / 2
    return posts.take(index) + FeedPost(id, time, posts[index].cursor) + posts.drop(index)
  }

  @Test
  fun aPostThatTurnsUpBelowTheTopIsNew() {
    val seen = feed(0, 60)
    val marks = visited(seen, 0, 20)
    val posts = withLate(seen, 5, "late")

    assertEquals(setOf("late"), late(posts, atTop = true, marks))
    assertEquals(listOf(PlacedHeading(0, FeedHeading.New(1, more = false, gapLeft = null, late = 1, firstLate = 5))), headings(posts, marks))
  }

  @Test
  fun newPostsOnTopAndALateOneBelowAreCountedTogether() {
    val seen = feed(0, 60)
    val marks = visited(seen, 0, 20)
    val posts = feed(3, 0).map { it.copy(time = it.time + 600) } + withLate(seen, 5, "late")

    val shown = headings(posts, marks)

    assertEquals(
      listOf(
        PlacedHeading(0, FeedHeading.New(4, more = false, gapLeft = null, late = 1, firstLate = 8)),
        PlacedHeading(3, FeedHeading.CaughtUp),
      ),
      shown,
    )
  }

  @Test
  fun aPostBelowWhatWasReachedIsntNew() {
    val seen = feed(0, 60)
    val marks = visited(seen, 0, 20)
    val posts = withLate(seen, 30, "deep")

    assertEquals(emptySet(), late(posts, atTop = true, marks))
    assertEquals(listOf(PlacedHeading(0, FeedHeading.UpToDate(gapLeft = null))), headings(posts, marks))
  }

  @Test
  fun aLatePostOnceReachedIsntNewNextTime() {
    val seen = feed(0, 60)
    val posts = withLate(seen, 5, "late")
    val next = visited(posts, 0, 20, start = visited(seen, 0, 20))

    assertEquals(emptySet(), late(posts, atTop = true, next))
  }

  @Test
  fun aLatePostIsMarkedInAListOpenedMidwayToo() {
    val seen = feed(0, 60)
    val marks = visited(seen, 0, 30)
    val posts = withLate(seen, 25, "late")

    assertEquals(setOf("late"), late(posts.drop(20), atTop = false, marks))
  }

  @Test
  fun marksFromBeforeLatePostsWereTrackedFindNone() {
    val seen = feed(0, 60)
    val old = marks(mark(seen, "p0"))

    assertEquals(emptySet(), late(withLate(seen, 5, "late"), atTop = true, old))
  }

  @Test
  fun aVisitKeepsWhatItReachedUpToALimit() {
    val posts = feed(0, FeedReached.MAX_POSTS + 100)

    val marks = visited(posts, 0, posts.lastIndex)

    assertEquals(FeedReached.MAX_POSTS, marks.reached.posts.size)
    assertTrue("p0" in marks.reached.posts)
    assertTrue(!marks.reached.lateAt(posts.last().time, CONTEXT))
    assertTrue(marks.reached.lateAt(posts[10].time, CONTEXT))
  }

  @Test
  fun aPostShownSinceFollowingShowsMoreIsntNew() {
    val seen = feed(0, 60)
    val marks = visited(seen, 0, 20)
    val posts = withLate(seen, 5, "hidden")

    assertEquals(emptySet(), late(posts, atTop = true, marks, context = FeedReading.context(listOf("rating:s"), listOf("t1", "t2"))))
    assertEquals(emptySet(), late(posts, atTop = true, marks, context = null))
    assertTrue(headings(posts, marks.copy(reached = marks.reached.copy(spans = marks.reached.spans.map { it.copy(context = "other") })))
      .none { it.heading is FeedHeading.New })
  }

  @Test
  fun theContextIsTheSearchAndTheFollows() {
    val context = FeedReading.context(listOf("rating:s", "feed:following"), listOf("t2", "t1"))
    assertEquals(context, FeedReading.context(listOf("feed:following", "rating:s"), listOf("t1", "t2")))
    assertTrue(context != FeedReading.context(listOf("feed:following"), listOf("t1", "t2")))
    assertTrue(context != FeedReading.context(listOf("rating:s", "feed:following"), listOf("t1", "t2", "t3")))
  }

  @Test
  fun stretchesReadFarApartKeepTheirOwnReadTimes() {
    val posts = feed(0, 60)
    val old = FeedReached().with(posts.subList(0, 21), now.epochSeconds - 3 * 86_400L, CONTEXT)
    val both = old.with(posts.subList(10, 31), now.epochSeconds, CONTEXT)

    assertEquals(2, both.spans.size)
    assertEquals(setOf(now.epochSeconds - 3 * 86_400L, now.epochSeconds), both.spans.map { it.read }.toSet())
  }

  @Test
  fun anOldPostAppearingInAReadStretchIsntNew() {
    val seen = feed(0, 60).map { it.copy(time = it.time - 3 * 86_400L) }
    val marks = visited(seen, 0, 20)

    assertEquals(emptySet(), late(withLate(seen, 5, "old"), atTop = true, marks))
  }

  @Test
  fun aDroppedPostWithTheSameTimeAsAKeptOneIsntTakenForLate() {
    val posts = feed(0, FeedReached.MAX_POSTS + 10)
    val twin = posts[FeedReached.MAX_POSTS - 1]
    val marks = visited(posts + FeedPost("twin", twin.time), 0, posts.size)

    assertTrue(twin.id in marks.reached.posts)
    assertTrue("twin" !in marks.reached.posts)
    assertTrue(!marks.reached.lateAt(twin.time, CONTEXT))
  }

  @Test
  fun forYouKeepsNoReachedPosts() {
    val posts = feed(0, 60)
    val marks = visit(null, Feed.ForYou).apply { reach(posts, atTop = true, 0, 10) }.marks!!
    assertTrue(marks.reached.posts.isEmpty())
  }

  @Test
  fun whileTheNewestPostReachedIsntLoadedTheCountIsAtLeastWhatIsLoaded() {
    val posts = feed(80, 60).take(80)
    val top = feed(80, 60).first { it.id == "p0" }.mark

    assertEquals(listOf(PlacedHeading(0, FeedHeading.New(80, more = true, gapLeft = null))), headings(posts, marks(top)))
  }

  @Test
  fun aDeletedNewestPostReachedIsFoundByItsTime() {
    val all = feed(5, 60)
    val top = mark(all, "p0")
    val posts = all.filterNot { it.id == "p0" }

    assertEquals(PlacedHeading(5, FeedHeading.CaughtUp), headings(posts, marks(top)).last())
  }

  @Test
  fun aGapShowsContinueAfterTheStopMarkAndCaughtUpWhereItEnds() {
    val posts = feed(46, 60)
    val gap = FeedGap(stop = mark(posts, "n8"), until = mark(posts, "p0"), left = 37, since = (now - 1.days).epochSeconds)

    val shown = headings(posts, marks(mark(posts, "n5"), gap))

    assertEquals(
      listOf(
        PlacedHeading(0, FeedHeading.New(5, more = false, gapLeft = 37)),
        PlacedHeading(5, FeedHeading.CaughtUp),
        PlacedHeading(9, FeedHeading.Continue(37, offer = false)),
        PlacedHeading(46, FeedHeading.CaughtUp),
      ),
      shown,
    )
  }

  @Test
  fun aGapsLeftCountsTheLoadedPostsWhenItsEndIsLoaded() {
    val posts = feed(46, 60)
    val gap = FeedGap(stop = mark(posts, "n8"), until = mark(posts, "p0"), left = 99, since = now.epochSeconds)

    val shown = headings(posts, marks(mark(posts, "n5"), gap))

    assertTrue(PlacedHeading(9, FeedHeading.Continue(37, offer = false)) in shown)
  }

  @Test
  fun aGapWithNoPostsLeftShowsNoContinue() {
    val all = feed(5, 60)
    val gap = FeedGap(stop = mark(all, "p2"), until = mark(all, "p3"), left = 4, since = now.epochSeconds)
    val posts = all.filterNot { it.id == "p2" || it.id == "p3" }

    val shown = headings(posts, marks(mark(all, "p0"), gap))

    assertEquals(
      listOf(PlacedHeading(0, FeedHeading.New(5, more = false, gapLeft = null)), PlacedHeading(5, FeedHeading.CaughtUp)),
      shown,
    )
  }

  @Test
  fun aGapOpenedMidwayShowsItsHeadingsButNoNNew() {
    val all = feed(46, 60)
    val posts = all.drop(40)
    val gap = FeedGap(stop = mark(all, "n41"), until = mark(all, "p0"), left = 4, since = now.epochSeconds)

    val shown = headings(posts, marks(mark(all, "n5"), gap), atTop = false)

    assertEquals(
      listOf(PlacedHeading(2, FeedHeading.Continue(4, offer = false)), PlacedHeading(6, FeedHeading.CaughtUp)),
      shown,
    )
  }

  @Test
  fun aFollowingGapNeverExpiresAndIsOfferedOnceAfterSevenDays() {
    val posts = feed(46, 60)
    val gap = FeedGap(stop = mark(posts, "n8"), until = mark(posts, "p0"), left = 37, since = (now - 7.days).epochSeconds)

    val offered = headings(posts, marks(mark(posts, "n5"), gap, at = now - 30.days))
    val answered = headings(posts, marks(mark(posts, "n5"), gap.copy(offered = true), at = now - 30.days))
    val young = headings(posts, marks(mark(posts, "n5"), gap.copy(since = (now - 7.days + 1.hours).epochSeconds)))

    assertTrue(PlacedHeading(9, FeedHeading.Continue(37, offer = true)) in offered)
    assertTrue(PlacedHeading(9, FeedHeading.Continue(37, offer = false)) in answered)
    assertTrue(PlacedHeading(9, FeedHeading.Continue(37, offer = false)) in young)
  }

  @Test
  fun forYouHasNoHeadings() {
    val posts = feed(12, 60)
    assertEquals(emptyList(), headings(posts, marks(mark(posts, "p0")), feed = Feed.ForYou))
  }

  @Test
  fun forYouMarksAreDroppedAfterThreeDaysAndFollowingsNever() {
    val posts = feed(0, 10)
    val top = mark(posts, "p0")

    assertTrue(FeedReading.expired(Feed.ForYou, marks(top, at = now - 3.days - 1.hours), now))
    assertTrue(!FeedReading.expired(Feed.ForYou, marks(top, at = now - 3.days + 1.hours), now))
    assertTrue(!FeedReading.expired(Feed.Following, marks(top, at = now - 300.days), now))
  }

  private fun visit(start: FeedMarks?, feed: Feed = Feed.Following) = FeedVisit(feed, start, now, CONTEXT)

  @Test
  fun aFirstVisitMarksTheNewestPostReached() {
    val posts = feed(0, 60)
    val visit = visit(null)

    visit.reach(posts, atTop = true, first = 0, last = 11)
    visit.reach(posts, atTop = true, first = 20, last = 31)

    assertEquals(marks(mark(posts, "p0"), at = now), visit.marks.plain())
  }

  @Test
  fun readingAllTheNewPostsMovesTheTopAndLeavesNoGap() {
    val posts = feed(12, 60)
    val visit = visit(marks(mark(posts, "p0")))

    visit.reach(posts, atTop = true, first = 0, last = 8)
    visit.reach(posts, atTop = true, first = 6, last = 14)

    assertEquals(marks(mark(posts, "n0"), at = now), visit.marks.plain())
  }

  @Test
  fun leavingPartWayThroughTheNewPostsLeavesAGap() {
    val posts = feed(41, 60)
    val visit = visit(marks(mark(posts, "p0")))

    visit.reach(posts, atTop = true, first = 0, last = 3)

    val gap = FeedGap(stop = mark(posts, "n3"), until = mark(posts, "p0"), left = 37, since = now.epochSeconds)
    assertEquals(marks(mark(posts, "n0"), gap, at = now), visit.marks.plain())
  }

  @Test
  fun scrollingBackUpOrRefreshingNeverMovesAMark() {
    val posts = feed(41, 60)
    val visit = visit(marks(mark(posts, "p0")))
    visit.reach(posts, atTop = true, first = 0, last = 9)
    visit.reach(posts, atTop = true, first = 10, last = 20)
    val before = visit.marks.plain()

    visit.reach(posts, atTop = true, first = 2, last = 12)
    visit.reach(posts, atTop = true, first = 0, last = 0)

    assertEquals(before, visit.marks.plain())
    assertEquals(mark(posts, "n20"), visit.marks!!.gap!!.stop)
    assertEquals(20, visit.marks!!.gap!!.left)
  }

  @Test
  fun aSecondGapMergesWithTheFirst() {
    val posts = feed(46, 60)
    val first = FeedGap(stop = mark(posts, "n8"), until = mark(posts, "p0"), left = 37, since = (now - 2.days).epochSeconds)
    val visit = visit(marks(mark(posts, "n5"), first))

    visit.reach(posts, atTop = true, first = 0, last = 1)

    val merged = FeedGap(
      stop = mark(posts, "n1"), until = mark(posts, "p0"), left = 3 + 37, since = first.since, read = 4, readEnd = mark(posts, "n8"),
    )
    assertEquals(marks(mark(posts, "n0"), merged, at = now), visit.marks.plain())
  }

  @Test
  fun aMergedGapCountsOnlyItsUnreadPostsOnceBothEndsAreLoaded() {
    val posts = feed(46, 60)
    val first = FeedGap(stop = mark(posts, "n8"), until = mark(posts, "p0"), left = 37, since = (now - 2.days).epochSeconds)
    val visit = visit(marks(mark(posts, "n5"), first))
    visit.reach(posts, atTop = true, first = 0, last = 1)

    val shown = headings(posts, visit.marks)

    assertTrue(PlacedHeading(2, FeedHeading.Continue(40, offer = false)) in shown, "$shown")
  }

  @Test
  fun forYouOpenedMidwayStillKeepsWhenItWasRead() {
    val posts = feed(0, 60)
    val visit = visit(null, Feed.ForYou)

    visit.reach(posts.drop(20), atTop = false, first = 0, last = 3)

    assertEquals(now.epochSeconds, visit.marks?.at)
  }

  @Test
  fun readingIntoTheGapMovesTheStopMarkAndReachingItsEndClosesIt() {
    val posts = feed(46, 60)
    val gap = FeedGap(stop = mark(posts, "n8"), until = mark(posts, "p0"), left = 37, since = now.epochSeconds)
    val start = marks(mark(posts, "n5"), gap)

    val partly = visit(start).apply { reach(posts.drop(40), atTop = false, first = 0, last = 1) }
    val through = visit(start).apply { reach(posts, atTop = true, first = 0, last = 50) }

    assertEquals(gap.copy(stop = mark(posts, "n41"), left = 4), partly.marks!!.gap)
    assertEquals(marks(mark(posts, "n0"), at = now), through.marks.plain())
  }

  @Test
  fun aJumpIntoTheGapDoesntMoveTheTop() {
    val posts = feed(46, 60)
    val gap = FeedGap(stop = mark(posts, "n8"), until = mark(posts, "p0"), left = 37, since = now.epochSeconds)
    val visit = visit(marks(mark(posts, "n5"), gap))

    visit.reach(posts.drop(8), atTop = false, first = 0, last = 5)

    assertEquals(mark(posts, "n5"), visit.marks!!.top)
    assertEquals(mark(posts, "n13"), visit.marks!!.gap!!.stop)
  }

  @Test
  fun anAnsweredOfferIsntOfferedAgainAndLetGoDropsTheGap() {
    val posts = feed(46, 60)
    val gap = FeedGap(stop = mark(posts, "n8"), until = mark(posts, "p0"), left = 37, since = (now - 8.days).epochSeconds)
    val start = marks(mark(posts, "n5"), gap)

    val shown = visit(start).apply { offerShown() }
    val kept = visit(start).apply { offerShown(); keep() }
    val letGo = visit(start).apply { letGo() }

    assertTrue(shown.marks!!.gap!!.offered)
    assertTrue(headings(posts, shown.shown).any { (it.heading as? FeedHeading.Continue)?.offer == true })
    assertTrue(kept.marks!!.gap!!.offered)
    assertTrue(headings(posts, kept.shown).none { (it.heading as? FeedHeading.Continue)?.offer == true })
    assertNull(letGo.marks!!.gap)
    assertTrue(headings(posts, letGo.shown).none { it.heading is FeedHeading.Continue })
  }

  @Test
  fun forYouOnlyKeepsWhenItWasRead() {
    val posts = feed(12, 60)
    val visit = visit(marks(mark(posts, "p0"), at = now - 2.days), Feed.ForYou)

    visit.reach(posts, atTop = true, first = 0, last = 3)

    assertEquals(marks(mark(posts, "n0"), at = now), visit.marks.plain())
  }

  private companion object {
    const val TOP_TIME = 1_794_481_200L

    val CONTEXT = FeedReading.context(listOf("feed:following", "rating:s"), listOf("t1", "t2"))
  }
}

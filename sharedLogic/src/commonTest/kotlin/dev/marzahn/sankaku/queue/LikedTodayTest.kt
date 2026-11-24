package dev.marzahn.sankaku.queue

import dev.marzahn.sankaku.Harness
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.json
import dev.marzahn.sankaku.storedTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class LikedTodayTest {
  private var now = Instant.parse("2026-11-14T22:50:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val berlin = TimeZone.of("Europe/Berlin")
  private val today = TodayStore(InMemoryTextFile(), clock, { berlin })

  private fun headings(vararg ids: String, tags: List<String> = listOf("fav:someone"), me: String? = "someone") =
    LikedToday.headings(tags, me, ids.toList(), today.counts().liked, today::isLiked)

  @Test
  fun itAppliesToTheOwnFavouritesInTheirOwnOrder() {
    assertTrue(LikedToday.applies(listOf("fav:someone"), "someone"))
    assertTrue(LikedToday.applies(listOf("fav:SomeOne", "rating:s", "scenery"), "someone"))
    assertFalse(LikedToday.applies(listOf("fav:someone", "order:popularity"), "someone"))
    assertFalse(LikedToday.applies(listOf("-fav:someone"), "someone"))
    assertFalse(LikedToday.applies(listOf("fav:someone_else"), "someone"))
    assertFalse(LikedToday.applies(listOf("fav:someone"), me = null))
    assertFalse(LikedToday.applies(listOf("scenery"), "someone"))
  }

  @Test
  fun aNarrowerSearchHasTheHeadingsWithoutTheDaysCount() {
    today.liked("p1", on = true)
    today.liked("p2", on = true)

    assertEquals(
      listOf(PlacedHeading(0, FeedHeading.LikedToday(null)), PlacedHeading(1, FeedHeading.Earlier)),
      headings("p1", "old1", tags = listOf("fav:someone", "scenery")),
    )
    assertEquals(FeedHeading.LikedToday(2), headings("p2", "p1", tags = listOf("rating:s", "fav:someone"))[0].heading)
  }

  @Test
  fun otherGridsHaveNeither() {
    today.liked("p1", on = true)

    assertEquals(emptyList(), headings("p1", "old1", tags = listOf("fav:someone", "order:popularity")))
    assertEquals(emptyList(), headings("p1", "old1", tags = listOf("scenery")))
    assertEquals(emptyList(), headings("p1", "old1", me = null))
  }

  @Test
  fun todaysLikesAreCountedAndEarlierFollowsTheLastOfThem() {
    today.liked("p1", on = true)
    today.liked("p2", on = true)

    assertEquals(
      listOf(PlacedHeading(0, FeedHeading.LikedToday(2)), PlacedHeading(2, FeedHeading.Earlier)),
      headings("p2", "p1", "old1", "old2"),
    )
  }

  @Test
  fun aPostFavouritedElsewhereTodayAmongTodaysStaysAboveEarlier() {
    today.liked("p1", on = true)
    today.liked("p3", on = true)

    assertEquals(PlacedHeading(3, FeedHeading.Earlier), headings("p3", "web", "p1", "old1")[1])
  }

  @Test
  fun atLocalMidnightNothingIsLikedToday() {
    today.liked("p1", on = true)

    now += 15.minutes
    assertFalse(today.isLiked("p1"))
    assertEquals(emptyList(), headings("p1", "old1"))
  }

  @Test
  fun anUnfavouritedPostIsNoLongerLikedToday() {
    today.liked("p1", on = true)
    today.liked("p2", on = true)
    today.liked("p2", on = false)

    assertFalse(today.isLiked("p2"))
    assertEquals(
      listOf(PlacedHeading(0, FeedHeading.LikedToday(1)), PlacedHeading(2, FeedHeading.Earlier)),
      headings("p2", "p1", "old1"),
    )
  }

  @Test
  fun clearEverythingForgetsTodaysLikes() {
    today.liked("p1", on = true)
    today.clear()

    assertFalse(today.isLiked("p1"))
    assertEquals(emptyList(), headings("p1"))
  }

  @Test
  fun unfavouritingAllOfTodaysLikesLeavesNoHeading() {
    today.liked("p1", on = true)
    today.liked("p2", on = true)
    today.liked("p1", on = false)
    today.liked("p2", on = false)

    assertEquals(emptyList(), headings("p2", "p1", "old1"))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun signingOutForgetsTodaysLikes() = runTest {
    val harness = Harness(store = InMemoryTokenStore(storedTokens())) { json("{}") }
    today.clearLikedOnSignOut(harness.session, backgroundScope)
    harness.session.restore()
    runCurrent()
    today.liked("p1", on = true)
    assertTrue(today.isLiked("p1"))

    harness.session.signOut()
    runCurrent()

    assertFalse(today.isLiked("p1"))
    assertEquals(0, today.counts().liked)
  }

  @Test
  fun noneOfTodaysLikesListedGivesNeitherHeading() {
    today.liked("p1", on = true)

    assertEquals(emptyList(), headings("old1", "old2"))
  }
}

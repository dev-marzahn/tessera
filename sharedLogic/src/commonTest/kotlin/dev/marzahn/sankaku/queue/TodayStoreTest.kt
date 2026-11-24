package dev.marzahn.sankaku.queue

import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TodayStoreTest {
  private var now = Instant.parse("2026-11-14T22:50:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val berlin = TimeZone.of("Europe/Berlin")
  private val file = InMemoryTextFile()
  private val today = TodayStore(file, clock, { berlin })

  private fun key(tag: String) = checkNotNull(SourceKey.of(listOf(tag)))

  @Test
  fun theCountsStartAgainAtLocalMidnight() {
    today.added(key("artist_x"))
    today.closed(key("artist_y"))
    today.liked("p1", on = true)
    assertEquals(TodayCounts(found = 1, done = 1, liked = 1), today.counts())

    now += 15.minutes
    assertEquals(TodayCounts.NONE, today.counts())
    assertEquals(Instant.parse("2026-11-14T23:00:00Z"), today.startOfToday())

    today.added(key("artist_z"))
    assertEquals(TodayCounts(found = 1, done = 0, liked = 0), today.counts())
    assertEquals(TodayCounts(found = 1, done = 0, liked = 0), TodayStore(file, clock, { berlin }).counts())
  }

  @Test
  fun aSavedDayIsReadBackOnlyOnThatDay() {
    today.added(key("artist_x"))
    assertEquals(TodayCounts(1, 0, 0), TodayStore(file, clock, { berlin }).counts())

    now += 15.minutes
    assertEquals(TodayCounts.NONE, TodayStore(file, clock, { berlin }).counts())
  }

  @Test
  fun aSourceIsCountedOnceAndOneBackFromClosedIsNoLongerDone() {
    val artist = key("artist_x")
    today.added(artist)
    today.added(artist)
    today.closed(artist)
    assertEquals(TodayCounts(found = 1, done = 1, liked = 0), today.counts())

    today.added(artist)
    assertEquals(TodayCounts(found = 1, done = 0, liked = 0), today.counts())
  }

  @Test
  fun aKeptPostThatLeftIsCountedOnceUntilItsBack() {
    today.keptLeft("p1")
    today.keptLeft("p1")
    today.keptLeft("p2")
    assertEquals(2, today.counts().dropped)
    assertEquals(2, TodayStore(file, clock, { berlin }).counts().dropped)

    today.keptBack("p1")
    assertEquals(1, today.counts().dropped)
    today.keptBack("p3")
    assertEquals(TodayCounts(found = 0, done = 0, liked = 0, dropped = 1), today.counts())
  }

  @Test
  fun lookupsRunInPlaceOverEachOtherAreOneFind() {
    today.replaced(null, key("artist_x"))
    today.replaced(key("artist_x"), key("artist_y"))

    assertEquals(1, today.counts().found)
  }

  @Test
  fun favouritesIsNeitherFoundNorDone() {
    val favourites = checkNotNull(SourceKey.of(listOf("order:popularity", "fav:someone")))
    today.added(key("artist_x"))
    today.added(favourites)
    assertEquals(TodayCounts(found = 1, done = 0, liked = 0), today.counts())

    today.closed(favourites)
    assertEquals(TodayCounts(found = 1, done = 0, liked = 0), today.counts())

    today.added(checkNotNull(SourceKey.of(listOf("fav:someone", "artist_y"))))
    assertEquals(2, today.counts().found)
  }

  @Test
  fun aLookupTurnedIntoFavouritesInPlaceFindsNothing() {
    today.replaced(null, key("artist_x"))
    today.replaced(key("artist_x"), key("fav:someone"))

    assertEquals(0, today.counts().found)
  }

  @Test
  fun unfavouritingTakesALikeBack() {
    today.liked("p1", on = true)
    today.liked("p2", on = true)
    today.liked("p1", on = false)

    assertEquals(1, today.counts().liked)
    assertEquals(setOf("p2"), today.likedPosts())
  }

  @Test
  fun clearForgetsTheDay() {
    today.added(key("artist_x"))
    today.clear()

    assertEquals(TodayCounts.NONE, today.counts())
    assertEquals(null, file.read())
  }

  @Test
  fun postsSeenTodayAreTheOnesLastSeenSinceMidnight() {
    val seen = SeenStore(InMemoryTextFile(), clock)
    seen.record("yesterday", null)
    now += 15.minutes
    seen.record("p1", null)
    seen.record("p2", null)
    seen.record("book:b1", null)

    assertEquals(2, seen.countSeenSince(today.startOfToday()))
  }
}

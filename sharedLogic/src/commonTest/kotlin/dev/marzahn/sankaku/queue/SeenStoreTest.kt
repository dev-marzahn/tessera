package dev.marzahn.sankaku.queue

import dev.marzahn.sankaku.Harness
import dev.marzahn.sankaku.NOW
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.storedTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.time.measureTime

@OptIn(ExperimentalCoroutinesApi::class)
class SeenStoreTest {
  private class MovableClock(var now: Instant = Instant.parse("2026-11-01T12:00:00Z")) : Clock {
    override fun now() = now
  }

  private val clock = MovableClock()
  private val file = InMemoryTextFile()

  private fun store(limit: Int = SeenStore.LIMIT) = SeenStore(file, clock, limit = limit)

  @Test
  fun aRecordedPostIsSeenAndOthersAreNot() {
    val seen = store()

    seen.record("p1", "aa11")

    assertTrue(seen.isSeen("p1"))
    assertFalse(seen.isSeen("p2"))
    assertEquals(clock.now, seen.lastSeen("p1"))
  }

  @Test
  fun theRevisionGoesUpWhenAPostIsRecordedOrTheStoreIsCleared() {
    val seen = store()
    val start = seen.revision.value

    seen.record("p1", "aa11")
    assertEquals(start + 1, seen.revision.value)

    seen.clear()
    assertEquals(start + 2, seen.revision.value)
  }

  @Test
  fun anExactCopyCountsAsSeenByItsMd5() {
    val seen = store()

    seen.record("p1", "AA11")

    assertTrue(seen.isSeen("p9", "aa11"))
    assertFalse(seen.isSeen("p9", "bb22"))
    assertFalse(seen.isSeen("p9"))
  }

  @Test
  fun seenSinceKeptIsRightBeforeAndAfterKeptAgain() {
    val seen = store()
    val kept = clock.now
    assertFalse(seen.seenSince("p1", "aa11", kept))

    clock.now += 1.minutes
    seen.record("p1", "aa11")
    assertTrue(seen.seenSince("p1", "aa11", kept))

    clock.now += 1.minutes
    val keptAgain = clock.now
    assertFalse(seen.seenSince("p1", "aa11", keptAgain))

    clock.now += 1.minutes
    seen.record("p1", "aa11")
    assertTrue(seen.seenSince("p1", "aa11", keptAgain))
  }

  @Test
  fun aCopySeenAfterKeepingCountsForSeenSinceKept() {
    val seen = store()
    val kept = clock.now
    clock.now += 1.minutes

    seen.record("p2", "aa11")

    assertTrue(seen.seenSince("p1", "aa11", kept))
  }

  @Test
  fun itIsSavedAndReadBack() {
    store().record("p1", "aa11")
    clock.now += 1.hours

    val again = store()

    assertTrue(again.isSeen("p1"))
    assertTrue(again.isSeen("p7", "aa11"))
    assertEquals(1, again.size)
  }

  @Test
  fun postsNotSeenFor90DaysArePrunedAtLaunch() {
    val seen = store()
    seen.record("old", null)
    clock.now += 60.days
    seen.record("recent", null)

    clock.now += 31.days
    val relaunched = store()

    assertFalse(relaunched.isSeen("old"))
    assertTrue(relaunched.isSeen("recent"))
    assertEquals(1, relaunched.size)
  }

  @Test
  fun pruningRunsAtMostOnceADay() {
    store().record("old", null)

    clock.now += 90.days - 1.hours
    assertTrue(store().isSeen("old"))

    clock.now += 2.hours
    assertTrue(store().isSeen("old"))

    clock.now += 1.days
    assertFalse(store().isSeen("old"))
  }

  @Test
  fun theStoreKeepsOnlyTheNewestPostsUpToItsBound() {
    val seen = store(limit = 3)

    listOf("p1", "p2", "p3").forEach {
      clock.now += 1.minutes
      seen.record(it, null)
    }
    clock.now += 1.minutes
    seen.record("p1", null)
    clock.now += 1.minutes
    seen.record("p4", null)

    assertEquals(listOf("p3", "p1", "p4"), seen.posts.map { it.id })
    assertNull(seen.lastSeen("p2"))
  }

  @Test
  fun clearingEmptiesTheStoreAndItsFile() {
    val seen = store()
    seen.record("p1", "aa11")

    seen.clear()

    assertEquals(0, seen.size)
    assertFalse(seen.isSeen("p1", "aa11"))
    assertEquals(0, store().size)
  }

  @Test
  fun clearingClearsTheOtherCopiesToo() {
    var cleared = 0
    val seen = SeenStore(InMemoryTextFile(), onClear = { cleared++ })
    seen.record("p1", "aa11")

    seen.clear()

    assertEquals(1, cleared)
  }

  @Test
  fun signingOutClearsTheStore() = runTest {
    val h = Harness(InMemoryTokenStore(storedTokens())) { error("no requests") }
    h.session.restore()
    val seen = store()
    seen.clearOnSignOut(h.session, backgroundScope)
    runCurrent()
    seen.record("p1", "aa11")

    h.session.signOut()
    runCurrent()

    assertEquals(0, seen.size)
    assertEquals(0, store().size)
  }

  @Test
  fun aSessionThatRanOutWhileClosedClearsTheStoreAtLaunch() = runTest {
    val h = Harness(InMemoryTokenStore(storedTokens().copy(refreshExpiresAt = NOW - 1.days))) { error("no requests") }
    h.session.restore()
    store().record("p1", "aa11")
    val seen = store()

    seen.clearOnSignOut(h.session, backgroundScope)
    runCurrent()

    assertEquals(0, seen.size)
  }

  @Test
  fun aLaunchWithoutASessionClearsNothing() = runTest {
    val h = Harness(InMemoryTokenStore()) { error("no requests") }
    store().record("p1", "aa11")
    val seen = store()
    seen.clearOnSignOut(h.session, backgroundScope)
    runCurrent()

    h.session.restore()
    runCurrent()

    assertTrue(seen.isSeen("p1"))
  }

  @Test
  fun withASaverTheLaunchPruneIsWrittenAtOnce() = runTest {
    store().record("old", null)
    clock.now += 91.days

    SeenStore(file, clock, backgroundScope)

    assertEquals(0, store().size)
    assertFalse(file.read()!!.contains("old"))
  }

  @Test
  fun withASaverAClearIsWrittenWithoutWaitingForTheDebounce() = runBlocking {
    store().record("p1", "aa11")
    val scope = CoroutineScope(SupervisorJob())
    try {
      val seen = SeenStore(file, clock, scope)
      withTimeout(2_000) { while (!seen.isSeen("p1")) yield() }
      delay(100)

      seen.clear()

      withTimeout(SeenStore.SAVE_DELAY / 2) { while (file.read()!!.contains("p1")) delay(10) }
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun aRecordAndAClearFromTwoThreadsNeverBringTheOldPostsBack() = runBlocking {
    val seen = store()
    repeat(200) { seen.record("old$it", null) }

    val recording = launch(Dispatchers.Default) { repeat(200) { seen.record("new$it", null) } }
    seen.clear()
    recording.join()

    assertTrue(seen.posts.none { it.id.startsWith("old") })
  }

  @Test
  fun anUnreadableFileStartsEmpty() {
    file.write("{not json")

    assertEquals(0, store().size)
  }

  @Test
  fun aFullStoreLoadsAndRecordsQuickly() {
    val at = clock.now.epochSeconds - SeenStore.LIMIT
    val text = (0 until SeenStore.LIMIT).joinToString(",", """{"version":1,"posts":[""", "]}") {
      """{"i":"${10_000_000 + it}","m":"${it.toString().padStart(32, '0')}","t":${at + it}}"""
    }

    val started = TimeSource.Monotonic.markNow()
    val loaded = SeenStore(InMemoryTextFile(text), clock)
    val loading = started.elapsedNow()
    val recording = TimeSource.Monotonic.measureTime { loaded.record("new", null) }
    val looking = TimeSource.Monotonic.measureTime { loaded.isSeen("other", "f".repeat(32)) }

    assertEquals(SeenStore.LIMIT, loaded.size)
    assertTrue(loaded.isSeen("new"))
    assertFalse(loaded.isSeen("10000000"))
    println(
      "seen.json, ${SeenStore.LIMIT} posts: ${text.length / 1024} KB; load ${loading.inWholeMilliseconds} ms, " +
        "record ${recording.inWholeMilliseconds} ms, first md5 lookup ${looking.inWholeMilliseconds} ms",
    )
    assertTrue(loading < 5.seconds, "loading took $loading")
  }
}

package dev.marzahn.sankaku.queue

import dev.marzahn.sankaku.data.HashedPost
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PreviewHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SeenStorePicturesTest {
  private class MovableClock(var now: Instant = Instant.parse("2026-11-01T12:00:00Z")) : Clock {
    override fun now() = now
  }

  private val clock = MovableClock()
  private val file = InMemoryTextFile()

  private fun store() = SeenStore(file, clock)

  private fun hash(bit: Int): PreviewHash {
    val hex = CharArray(64) { '0' }
    hex[63 - bit / 4] = "1248"[bit % 4]
    return PreviewHash.decode(hex.concatToString())!!
  }

  private fun picture(id: String, bit: Int = 0) = HashedPost(id, hash(bit), parentId = "parent-$id", created = Instant.parse("2024-01-01T00:00:00Z"))

  @Test
  fun aRecordedPictureIsKeptAndSurvivesARestart() {
    store().recordPicture(picture("p1", bit = 3))

    assertEquals(picture("p1", bit = 3), store().picture("p1"))
    assertNull(store().picture("p2"))
  }

  @Test
  fun recordingThePictureAgainChangesNothingAndANewOneReplacesIt() {
    val seen = store()
    seen.recordPicture(picture("p1"))
    val revision = seen.revision.value

    seen.recordPicture(picture("p1"))
    assertEquals(revision, seen.revision.value)

    seen.recordPicture(picture("p1", bit = 9))
    assertTrue(seen.revision.value > revision)
    assertEquals(picture("p1", bit = 9), store().picture("p1"))
  }

  @Test
  fun clearingForgetsThePicturesToo() {
    val seen = store()
    seen.record("p1", "aa11")
    seen.recordPicture(picture("p1"))

    seen.clear()

    assertNull(seen.picture("p1"))
    assertNull(store().picture("p1"))
  }

  @Test
  fun picturesAreNotPrunedWithOldSeenPosts() {
    store().recordPicture(picture("p1"))
    clock.now += 200.days

    assertEquals(picture("p1"), store().picture("p1"))
  }

  @Test
  fun onlyTheNewestPicturesAreKept() {
    val seen = store()
    repeat(SeenStore.PICTURES_LIMIT + 1) { seen.recordPicture(picture("p$it", bit = it % 256)) }

    assertNull(seen.picture("p0"))
    assertEquals(picture("p1", bit = 1), seen.picture("p1"))
  }

  @Test
  fun aFileFromBeforePicturesReadsAsNone() {
    file.write("""{"version":1,"posts":[{"i":"p1","t":${clock.now.epochSeconds - 60}}]}""")

    val seen = store()

    assertTrue(seen.isSeen("p1"))
    assertNull(seen.picture("p1"))
    seen.record("p2", null)
    assertFalse("pictures" in file.read()!!)
  }
}

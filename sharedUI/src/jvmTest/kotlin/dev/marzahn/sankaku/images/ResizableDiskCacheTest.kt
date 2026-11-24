package dev.marzahn.sankaku.images

import coil3.disk.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResizableDiskCacheTest {
  private val dir = Files.createTempDirectory("resizable-disk-cache")
  private var built = 0

  @AfterTest
  fun tearDown() {
    dir.toFile().deleteRecursively()
  }

  private fun cache(maxSize: Long) = ResizableDiskCache(maxSize) {
    built++
    DiskCache.Builder()
      .directory(dir.toOkioPath())
      .fileSystem(FileSystem.SYSTEM)
      .cleanupDispatcher(Dispatchers.IO)
      .maxSizeBytes(it)
      .build()
  }

  private fun DiskCache.put(key: String, bytes: Int) {
    val editor = openEditor(key)!!
    fileSystem.write(editor.data) { write(ByteArray(bytes)) }
    editor.commit()
  }

  private fun DiskCache.read(key: String) = openSnapshot(key)?.use { fileSystem.read(it.data) { readByteArray() } }

  private fun awaitSize(cache: DiskCache, atMost: Long) = runBlocking {
    withTimeout(5_000) { while (cache.size > atMost) yield() }
  }

  @Test
  fun aLargerSizeKeepsEveryEntry() {
    val cache = cache(300_000)
    cache.put("a", 100_000)
    cache.put("b", 100_000)

    cache.resize(1_000_000)
    cache.put("c", 500_000)

    assertEquals(1_000_000, cache.maxSize)
    assertNotNull(cache.read("a"))
    assertNotNull(cache.read("b"))
    assertNotNull(cache.read("c"))
  }

  @Test
  fun aSmallerSizeEvictsTheLeastRecentlyUsedAtOnce() {
    val cache = cache(1_000_000)
    cache.put("a", 100_000)
    cache.put("b", 100_000)
    cache.put("c", 100_000)
    cache.read("a")

    cache.resize(250_000)
    awaitSize(cache, 250_000)

    assertEquals(250_000, cache.maxSize)
    assertNull(cache.read("b"), "the least recently used goes first")
    assertNotNull(cache.read("a"))
    assertNotNull(cache.read("c"))
  }

  @Test
  fun theSwapWaitsForWhatIsOpenAndTheOpenEntryStillReads() {
    val cache = cache(1_000_000)
    cache.put("a", 100_000)
    cache.put("b", 100_000)
    val reading = cache.openSnapshot("a")!!
    val writing = cache.openEditor("c")!!

    cache.resize(150_000)

    assertEquals(1, built, "nothing is swapped while entries are open")
    assertEquals(150_000, cache.maxSize, "the size asked for is reported at once")
    assertEquals(100_000, cache.fileSystem.read(reading.data) { readByteArray() }.size)
    reading.close()
    assertEquals(1, built, "the editor is still open")

    cache.fileSystem.write(writing.data) { write(ByteArray(10_000)) }
    writing.commit()

    assertEquals(2, built)
    awaitSize(cache, 150_000)
    assertNotNull(cache.read("c"), "the entry written across the swap is kept")
  }

  @Test
  fun anEditorTurnedSnapshotKeepsCountingUntilTheSnapshotCloses() {
    val cache = cache(1_000_000)
    val editor = cache.openEditor("a")!!
    cache.fileSystem.write(editor.data) { write(ByteArray(1_000)) }

    cache.resize(500_000)
    val snapshot = editor.commitAndOpenSnapshot()!!
    assertEquals(1, built)

    snapshot.close()
    snapshot.close()

    assertEquals(2, built, "closed twice, it swaps once")
    assertNotNull(cache.read("a"))
  }

  @Test
  fun aCacheThatFailsToBuildKeepsTheOldOneWorking() {
    var failing = false
    val cache = ResizableDiskCache(1_000_000) {
      check(!failing) { "no room" }
      DiskCache.Builder().directory(dir.toOkioPath()).fileSystem(FileSystem.SYSTEM).maxSizeBytes(it).build()
    }
    cache.put("a", 1_000)
    failing = true

    cache.resize(500_000)

    assertNotNull(cache.read("a"))
    cache.put("b", 1_000)
    assertNotNull(cache.read("b"))
  }

  @Test
  fun anEditorAbandonedWithoutASnapshotStillLetsTheSwapHappen() {
    val cache = cache(1_000_000)
    cache.put("a", 1_000)
    val snapshot = cache.openSnapshot("a")!!
    cache.resize(500_000)

    val editor = snapshot.closeAndOpenEditor()
    editor?.abort()

    assertEquals(2, built)
    assertNotNull(cache.read("a"))
  }

  @Test
  fun theSameSizeAgainBuildsNothing() {
    val cache = cache(1_000_000)

    cache.resize(1_000_000)

    assertEquals(1, built)
  }

  @Test
  fun theLastSizeAskedForWhileWaitingIsTheOneSwappedTo() {
    val cache = cache(1_000_000)
    assertNull(cache.openSnapshot("missing"), "a miss holds nothing open")
    cache.put("a", 1_000)
    val reading = cache.openSnapshot("a")!!

    cache.resize(500_000)
    cache.resize(2_000_000)
    reading.close()

    assertEquals(2, built)
    assertEquals(2_000_000, cache.maxSize)
    assertTrue(cache.size > 0)
  }
}

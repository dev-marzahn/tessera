package dev.marzahn.sankaku.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheSettingsTest {
  private val mb = 1024L * 1024
  private val gb = mb * 1024

  @Test
  fun theDefaultsAreLargerThanBefore641() {
    val settings = CacheSettings(InMemoryTextFile())

    assertEquals(2 * gb, settings.imageBytes.value)
    assertEquals(1 * gb, settings.videoBytes.value)
  }

  @Test
  fun bothSizesSurviveARestart() {
    val file = InMemoryTextFile()
    CacheSettings(file).apply {
      setImageBytes(8 * gb)
      setVideoBytes(256 * mb)
    }

    val restarted = CacheSettings(file)
    assertEquals(8 * gb, restarted.imageBytes.value)
    assertEquals(256 * mb, restarted.videoBytes.value)
  }

  @Test
  fun aSizeNeverChosenIsNotSavedSoItFollowsTheDefault() {
    val file = InMemoryTextFile()
    CacheSettings(file).setImageBytes(4 * gb)

    val written = file.read()!!
    assertTrue("imageBytes" in written)
    assertFalse("videoBytes" in written)
  }

  @Test
  fun aSizeOffThePresetsSnapsDownToOne() {
    val file = InMemoryTextFile().apply { write("""{"imageBytes":${3 * gb},"videoBytes":1}""") }

    val settings = CacheSettings(file)
    assertEquals(2 * gb, settings.imageBytes.value)
    assertEquals(256 * mb, settings.videoBytes.value)
  }

  @Test
  fun anUnreadableFileIsTheDefaults() {
    val file = InMemoryTextFile().apply { write("not json") }

    assertEquals(CacheSizes.Images.default, CacheSettings(file).imageBytes.value)
    assertEquals(CacheSizes.Videos.default, CacheSettings(file).videoBytes.value)
  }
}

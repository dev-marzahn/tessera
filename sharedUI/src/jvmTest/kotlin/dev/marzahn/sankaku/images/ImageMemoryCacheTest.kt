package dev.marzahn.sankaku.images

import coil3.PlatformContext
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageMemoryCacheTest {
  @Test
  fun desktopKeepsDecodedImagesUpTo256MegabytesWhateverTheHeap() {
    assertEquals(256L * 1024 * 1024, imageMemoryCache(PlatformContext.INSTANCE).maxSize)
  }
}

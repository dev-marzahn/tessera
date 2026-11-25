package dev.marzahn.sankaku.images

import coil3.ImageLoader
import coil3.PlatformContext
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageMemoryJvmTest {
  @Test
  fun desktopKeepsDecodedImagesUpTo256MegabytesWhateverTheHeap() {
    val context = PlatformContext.INSTANCE
    val cache = requireNotNull(ImageLoader.Builder(context).imageMemoryPolicy(context).build().memoryCache)

    assertEquals(256L * 1024 * 1024, cache.maxSize)
  }
}

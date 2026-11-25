package dev.marzahn.sankaku.images

import coil3.PlatformContext
import coil3.memory.MemoryCache

private const val DESKTOP_IMAGE_MEMORY_CACHE_BYTES = 256L * 1024 * 1024

internal actual fun imageMemoryCache(context: PlatformContext): MemoryCache =
  MemoryCache.Builder().maxSizeBytes(DESKTOP_IMAGE_MEMORY_CACHE_BYTES).build()

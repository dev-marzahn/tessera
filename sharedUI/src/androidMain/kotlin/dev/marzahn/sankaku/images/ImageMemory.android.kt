package dev.marzahn.sankaku.images

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.memoryCacheMaxSizePercentWhileInBackground

private const val EMPTY_STRONG_CACHE_WHILE_HIDDEN = 0.0

internal actual fun ImageLoader.Builder.imageMemoryPolicy(context: PlatformContext): ImageLoader.Builder =
  memoryCache { MemoryCache.Builder().maxSizePercent(context).build() }
    .memoryCacheMaxSizePercentWhileInBackground(EMPTY_STRONG_CACHE_WHILE_HIDDEN)

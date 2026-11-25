package dev.marzahn.sankaku.images

import coil3.PlatformContext
import coil3.memory.MemoryCache

internal actual fun imageMemoryCache(context: PlatformContext): MemoryCache = MemoryCache.Builder().maxSizePercent(context).build()

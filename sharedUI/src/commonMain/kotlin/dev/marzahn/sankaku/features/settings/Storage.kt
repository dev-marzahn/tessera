package dev.marzahn.sankaku.features.settings

import coil3.ImageLoader
import dev.marzahn.sankaku.data.CacheSettings
import dev.marzahn.sankaku.features.post.clearVideoCache
import dev.marzahn.sankaku.features.post.resizeVideoCache
import dev.marzahn.sankaku.features.post.videoCacheBytes
import dev.marzahn.sankaku.images.ResizableDiskCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import dev.marzahn.sankaku.platformClearExports
import dev.marzahn.sankaku.tabs.TabStateStore
import dev.marzahn.sankaku.tabs.TabsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

class ClearCache(
  private val images: ImageLoader,
  private val clearVideos: suspend () -> Unit = ::clearVideoCache,
  private val videoBytes: suspend () -> Long = ::videoCacheBytes,
) {
  suspend fun bytesInUse(): Long = withContext(Dispatchers.Default) { (images.diskCache?.size ?: 0) } + videoBytes()

  suspend fun run() {
    images.memoryCache?.clear()
    withContext(Dispatchers.Default) { images.diskCache?.clear() }
    clearVideos()
  }
}

class CacheLimits(
  private val settings: CacheSettings,
  private val images: ImageLoader,
  private val resizeVideos: suspend (Long) -> Unit = ::resizeVideoCache,
  private val videosOnDisk: suspend () -> Long = ::videoCacheBytes,
) {
  val imageBytes: StateFlow<Long> get() = settings.imageBytes
  val videoBytes: StateFlow<Long> get() = settings.videoBytes

  suspend fun imagesInUse(): Long = withContext(Dispatchers.Default) { images.diskCache?.size ?: 0 }

  suspend fun videosInUse(): Long = videosOnDisk()

  suspend fun setImageBytes(bytes: Long) {
    settings.setImageBytes(bytes)
    val cache = images.diskCache as? ResizableDiskCache ?: return
    val maxBytes = settings.imageBytes.value
    withContext(Dispatchers.Default) {
      cache.resize(maxBytes)
      withTimeoutOrNull(2.seconds) { while (cache.size > maxBytes) delay(20.milliseconds) }
    }
  }

  suspend fun setVideoBytes(bytes: Long) {
    settings.setVideoBytes(bytes)
    resizeVideos(settings.videoBytes.value)
  }
}

class ClearSavedTabs(
  private val tabs: TabsController,
  private val tabStore: TabStateStore,
  private val clearExports: () -> Unit = ::platformClearExports,
) {
  fun run() {
    runCatching(clearExports)
    tabs.clearAll()
    tabs.forgetClosed()
    tabs.forgetTitles()
    runCatching { tabStore.clear() }
    runCatching { tabStore.clearBackups() }
  }
}

internal fun storageSize(bytes: Long): String {
  val mb = 1024L * 1024
  val gb = mb * 1024
  return when {
    bytes <= 0 -> "Empty"
    bytes < mb -> "Under 1 MB"
    bytes < gb -> "${(bytes.toDouble() / mb).roundToLong().coerceAtMost(1023)} MB"
    else -> {
      val tenths = (bytes.toDouble() * 10 / gb).roundToLong()
      if (tenths % 10 == 0L) "${tenths / 10} GB" else "${tenths / 10}.${tenths % 10} GB"
    }
  }
}

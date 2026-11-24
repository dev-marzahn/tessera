package dev.marzahn.sankaku.features.post

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

@UnstableApi
internal class ResizableLruEvictor(maxBytes: Long) : CacheEvictor {
  @Volatile
  var maxBytes: Long = maxBytes
    private set

  private val spans = TreeSet<CacheSpan>(compareBy<CacheSpan> { it.lastTouchTimestamp }.thenBy { it })
  private var currentSize = 0L

  fun resize(cache: Cache, bytes: Long) = synchronized(cache) {
    maxBytes = bytes
    evict(cache, 0)
  }

  override fun requiresCacheSpanTouches() = true

  override fun onCacheInitialized() = Unit

  override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
    if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
  }

  override fun onSpanAdded(cache: Cache, span: CacheSpan) {
    spans.add(span)
    currentSize += span.length
    evict(cache, 0)
  }

  override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
    spans.remove(span)
    currentSize -= span.length
  }

  override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
    onSpanRemoved(cache, oldSpan)
    onSpanAdded(cache, newSpan)
  }

  private fun evict(cache: Cache, required: Long) {
    while (currentSize + required > maxBytes && spans.isNotEmpty()) cache.removeSpan(spans.first())
  }
}

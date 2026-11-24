package dev.marzahn.sankaku.features.post

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

@UnstableApi
class ResizableLruEvictorTest {
  private val evictor = ResizableLruEvictor(maxBytes = 300)
  private val removed = mutableListOf<String>()

  private val cache = Proxy.newProxyInstance(Cache::class.java.classLoader, arrayOf(Cache::class.java)) { proxy, method, args ->
    check(method.name == "removeSpan") { "the evictor only removes spans, not ${method.name}" }
    val span = args!![0] as CacheSpan
    removed += span.key
    evictor.onSpanRemoved(proxy as Cache, span)
    null
  } as Cache

  private fun span(key: String, length: Long, touched: Long) = CacheSpan(key, 0, length, touched, File(key))

  @Test
  fun aSmallerSizeEvictsTheLeastRecentlyPlayedNow() {
    evictor.onSpanAdded(cache, span("a", 100, touched = 1))
    evictor.onSpanAdded(cache, span("b", 100, touched = 3))
    evictor.onSpanAdded(cache, span("c", 100, touched = 2))

    evictor.resize(cache, 150)

    assertEquals(listOf("a", "c"), removed)
    assertEquals(150, evictor.maxBytes)
  }

  @Test
  fun aLargerSizeLetsMoreIn() {
    evictor.onSpanAdded(cache, span("a", 200, touched = 1))
    evictor.resize(cache, 1_000)

    evictor.onStartFile(cache, "b", 0, 500)
    evictor.onSpanAdded(cache, span("b", 500, touched = 2))

    assertEquals(emptyList(), removed)
  }

  @Test
  fun aTouchedSpanCountsAsRecentlyPlayed() {
    val a = span("a", 100, touched = 1)
    evictor.onSpanAdded(cache, a)
    evictor.onSpanAdded(cache, span("b", 100, touched = 2))
    evictor.onSpanTouched(cache, a, span("a", 100, touched = 3))

    evictor.onStartFile(cache, "c", 0, 150)

    assertEquals(listOf("b"), removed)
  }
}

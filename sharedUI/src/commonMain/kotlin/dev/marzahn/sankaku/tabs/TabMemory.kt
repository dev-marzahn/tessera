package dev.marzahn.sankaku.tabs

import coil3.memory.MemoryCache
import dev.marzahn.sankaku.images.PostImage

internal const val POST_BYTES = 8L * 1024

fun estimateTabBytes(contents: List<TabContents>, decodedBytes: Map<String, Long>): Long {
  val images = contents.flatMapTo(mutableSetOf()) { it.images.map(PostImage::cacheKey) }
  return images.sumOf { decodedBytes[it] ?: 0L } + contents.sumOf { it.posts } * POST_BYTES
}

fun MemoryCache.decodedBytes(): Map<String, Long> =
  keys.groupingBy { it.key }.fold(0L) { total, key -> total + (this[key]?.image?.size ?: 0L) }

fun formatBytes(bytes: Long): String {
  fun tenths(unit: Long) = (bytes * 10 + unit / 2) / unit
  return when {
    bytes >= GB -> tenths(GB).let { "${it / 10}.${it % 10} GB" }
    bytes >= MB -> tenths(MB).let { "${it / 10}.${it % 10} MB" }
    else -> "${(bytes + KB / 2) / KB} KB"
  }
}

private const val KB = 1024L
private const val MB = KB * 1024
private const val GB = MB * 1024

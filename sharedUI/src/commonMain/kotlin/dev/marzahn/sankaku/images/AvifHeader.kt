package dev.marzahn.sankaku.images

import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8

internal object AvifHeader {
  private val FTYP = "ftyp".encodeUtf8()
  private val BRANDS = listOf("avif", "avis").map { it.encodeUtf8() }
  private val AV1C = "av1C".encodeUtf8()

  private const val HEADER_BYTES = 1024L

  fun isAvif(source: BufferedSource): Boolean {
    val peek = source.peek()
    return peek.rangeEquals(4, FTYP) && BRANDS.any { peek.rangeEquals(8, it) }
  }

  fun bitDepth(source: BufferedSource): Int? {
    if (!isAvif(source)) return null
    val peek = source.peek()
    peek.request(HEADER_BYTES)
    val header = peek.buffer.snapshot(minOf(peek.buffer.size, HEADER_BYTES).toInt())
    val at = header.indexOf(AV1C)
    if (at < 0 || at + AV1C.size + 3 > header.size) return null
    val flags = header[at + AV1C.size + 2].toInt()
    val high = flags and 0x40 != 0
    val twelve = flags and 0x20 != 0
    return when {
      !high -> 8
      twelve -> 12
      else -> 10
    }
  }
}

internal expect val platformMaxAvifBitDepth: Int

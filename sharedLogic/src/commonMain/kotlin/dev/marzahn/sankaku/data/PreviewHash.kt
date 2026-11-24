package dev.marzahn.sankaku.data

import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class PreviewHash private constructor(private val bits: LongArray) {
  fun distance(other: PreviewHash): Int = bits.indices.sumOf { (bits[it] xor other.bits[it]).countOneBits() }

  fun matches(other: PreviewHash): Boolean = distance(other) <= THRESHOLD

  fun encode(): String = bits.joinToString("") { it.toULong().toString(16).padStart(16, '0') }

  override fun equals(other: Any?): Boolean = other is PreviewHash && bits.contentEquals(other.bits)

  override fun hashCode(): Int = bits.contentHashCode()

  override fun toString(): String = "PreviewHash"

  companion object {
    const val THRESHOLD: Int = 7

    private const val SIDE = 16
    private const val COLUMNS = SIDE + 1
    private const val WORDS = SIDE * SIDE / 64

    private const val MIN_CONTRAST = 8

    private const val FLAT = 2

    fun of(argb: IntArray, width: Int, height: Int): PreviewHash? {
      if (width < COLUMNS || height < SIDE || argb.size < width * height) return null
      val grey = IntArray(COLUMNS * SIDE)
      for (row in 0 until SIDE) {
        val y0 = row * height / SIDE
        val y1 = (row + 1) * height / SIDE
        for (column in 0 until COLUMNS) {
          val x0 = column * width / COLUMNS
          val x1 = (column + 1) * width / COLUMNS
          var sum = 0L
          for (y in y0 until y1) {
            val line = y * width
            for (x in x0 until x1) sum += luma(argb[line + x])
          }
          grey[row * COLUMNS + column] = (sum / ((y1 - y0) * (x1 - x0))).toInt()
        }
      }
      if (grey.max() - grey.min() < MIN_CONTRAST) return null
      val bits = LongArray(WORDS)
      var bit = 0
      for (row in 0 until SIDE) {
        for (column in 0 until SIDE) {
          val cell = row * COLUMNS + column
          if (grey[cell] - grey[cell + 1] > FLAT) bits[bit / 64] = bits[bit / 64] or (1L shl (bit % 64))
          bit++
        }
      }
      return PreviewHash(bits)
    }

    fun decode(hex: String): PreviewHash? {
      if (hex.length != WORDS * 16) return null
      val bits = LongArray(WORDS) { hex.substring(it * 16, it * 16 + 16).toULongOrNull(16)?.toLong() ?: return null }
      return PreviewHash(bits)
    }

    private fun luma(pixel: Int): Int {
      val r = (pixel shr 16) and 0xFF
      val g = (pixel shr 8) and 0xFF
      val b = pixel and 0xFF
      return (r * 299 + g * 587 + b * 114) / 1000
    }
  }
}

data class HashedPost(val id: String, val hash: PreviewHash, val parentId: String?, val created: Instant?)

val ONE_BATCH: Duration = 1.days

fun oneFamily(a: HashedPost, b: HashedPost): Boolean =
  a.parentId == b.id || b.parentId == a.id || (a.parentId != null && a.parentId == b.parentId)

fun oneBatch(a: HashedPost, b: HashedPost): Boolean {
  val at = a.created ?: return false
  val bAt = b.created ?: return false
  return abs((at - bAt).inWholeSeconds) <= ONE_BATCH.inWholeSeconds
}

const val IDENTICAL: Int = 2

fun isReupload(post: HashedPost, kept: HashedPost): Boolean {
  if (post.id == kept.id) return false
  val distance = post.hash.distance(kept.hash)
  return distance <= IDENTICAL || (distance <= PreviewHash.THRESHOLD && !oneFamily(post, kept) && !oneBatch(post, kept))
}

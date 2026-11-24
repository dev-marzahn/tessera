package dev.marzahn.sankaku.data

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PreviewHashTest {
  private class Picture(val width: Int, val height: Int, val argb: IntArray) {
    fun hash() = assertNotNull(PreviewHash.of(argb, width, height))
  }

  private class Shape(val x: Int, val y: Int, val radius: Int, val colour: Int, val round: Boolean)

  private fun shapes(seed: Int, count: Int = 14): List<Shape> {
    val random = Random(seed)
    return List(count) {
      Shape(random.nextInt(300), random.nextInt(400), 20 + random.nextInt(60), random.nextInt(0xFFFFFF), random.nextBoolean())
    }
  }

  private fun draw(shapes: List<Shape>, width: Int = 300, height: Int = 400): Picture {
    val argb = IntArray(width * height)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val cx = x * 300 / width
        val cy = y * 400 / height
        var colour = rgb(40 + cy * 160 / 400, 80, 200 - cy * 120 / 400)
        for (shape in shapes) {
          val dx = cx - shape.x
          val dy = cy - shape.y
          val inside = if (shape.round) dx * dx + dy * dy <= shape.radius * shape.radius else max(abs(dx), abs(dy)) <= shape.radius
          if (inside) colour = shape.colour
        }
        argb[y * width + x] = colour or (0xFF shl 24)
      }
    }
    return Picture(width, height, argb)
  }

  private fun abs(value: Int) = if (value < 0) -value else value

  private fun rgb(r: Int, g: Int, b: Int) = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

  private fun Picture.scaledTo(newWidth: Int, newHeight: Int): Picture {
    val out = IntArray(newWidth * newHeight)
    for (y in 0 until newHeight) {
      for (x in 0 until newWidth) {
        val x0 = x * width / newWidth
        val x1 = max(x0 + 1, (x + 1) * width / newWidth)
        val y0 = y * height / newHeight
        val y1 = max(y0 + 1, (y + 1) * height / newHeight)
        var r = 0
        var g = 0
        var b = 0
        for (sy in y0 until y1) for (sx in x0 until x1) {
          val p = argb[sy * width + sx]
          r += (p shr 16) and 0xFF
          g += (p shr 8) and 0xFF
          b += p and 0xFF
        }
        val n = (x1 - x0) * (y1 - y0)
        out[y * newWidth + x] = rgb(r / n, g / n, b / n) or (0xFF shl 24)
      }
    }
    return Picture(newWidth, newHeight, out)
  }

  private fun Picture.recompressed(seed: Int = 7): Picture {
    val random = Random(seed)
    val out = IntArray(argb.size)
    for (y in 0 until height) {
      for (x in 0 until width) {
        fun channel(shift: Int): Int {
          var sum = 0
          var n = 0
          for (sy in max(0, y - 1)..min(height - 1, y + 1)) for (sx in max(0, x - 1)..min(width - 1, x + 1)) {
            sum += (argb[sy * width + sx] shr shift) and 0xFF
            n++
          }
          return sum / n + random.nextInt(-8, 9)
        }
        out[y * width + x] = rgb(channel(16), channel(8), channel(0)) or (0xFF shl 24)
      }
    }
    return Picture(width, height, out)
  }

  private fun Picture.watermarked(): Picture {
    val out = argb.copyOf()
    val boxWidth = width / 4
    val boxHeight = height / 20
    for (y in height - boxHeight - 8 until height - 8) {
      for (x in width - boxWidth - 8 until width - 8) {
        out[y * width + x] = if ((x / 3) % 2 == 0 && y % 4 != 0) 0xFF202020.toInt() else 0xFFF0F0F0.toInt()
      }
    }
    return Picture(width, height, out)
  }

  @Test
  fun theSamePictureRescaledRecompressedOrWatermarkedMatches() {
    val original = draw(shapes(1))
    val hash = original.hash()

    val copies = mapOf(
      "rescaled" to original.scaledTo(150, 200),
      "rescaled and recompressed" to original.scaledTo(225, 300).recompressed(),
      "recompressed" to original.recompressed(3),
      "watermarked" to original.watermarked(),
    )
    for ((name, copy) in copies) {
      val distance = hash.distance(copy.hash())
      assertTrue(distance <= PreviewHash.THRESHOLD, "$name, it's $distance bits away")
      assertTrue(hash.matches(copy.hash()), name)
    }
  }

  @Test
  fun aVariantWithAPartRedrawnDoesNotMatch() {
    val first = shapes(2)
    val moved = first.sortedByDescending { it.radius }.take(3).toSet()
    val variant = first.map { shape ->
      if (shape in moved) Shape(min(299, shape.x + 70), max(0, shape.y - 60), shape.radius, shape.colour xor 0x808080, shape.round) else shape
    }

    val distance = draw(first).hash().distance(draw(variant).hash())

    assertTrue(distance > PreviewHash.THRESHOLD, "a variant is only $distance bits away")
    assertFalse(draw(first).hash().matches(draw(variant).hash()))
  }

  @Test
  fun anotherPictureIsFarAway() {
    val distance = draw(shapes(3)).hash().distance(draw(shapes(4)).hash())

    assertTrue(distance > 4 * PreviewHash.THRESHOLD, "another picture is only $distance bits away")
  }

  @Test
  fun aFlatOrTinyPictureHasNoHash() {
    assertNull(PreviewHash.of(IntArray(100 * 100) { 0xFF808080.toInt() }, 100, 100))
    assertNull(PreviewHash.of(IntArray(16 * 16) { it * 997 }, 16, 16))
  }

  @Test
  fun aHashSurvivesBeingWrittenAndReadBack() {
    val hash = draw(shapes(5)).hash()

    assertEquals(hash, PreviewHash.decode(hash.encode()))
    assertEquals(64, hash.encode().length)
    assertNull(PreviewHash.decode("not a hash"))
    assertEquals("PreviewHash", hash.toString())
  }

  private val at = Instant.parse("2024-03-01T12:00:00Z")

  private fun hashOff(bits: Int): PreviewHash {
    val hex = CharArray(64) { '0' }
    repeat(bits) { hex[63 - it] = '1' }
    return assertNotNull(PreviewHash.decode(hex.concatToString()))
  }

  private fun post(id: String, parent: String? = null, created: Instant? = at, bits: Int = 5) = HashedPost(id, hashOff(bits), parent, created)

  @Test
  fun aMatchUploadedApartIsAReupload() {
    val kept = post("kept", bits = 0)

    assertTrue(isReupload(post("again", created = at + 400.days), kept))
    assertTrue(isReupload(post("again", created = at + 400.days, bits = PreviewHash.THRESHOLD), kept))
    assertTrue(isReupload(post("again", created = null), kept))
    assertFalse(isReupload(post("again", created = at + 400.days, bits = PreviewHash.THRESHOLD + 1), kept))
  }

  @Test
  fun oneFamilyOrOneDaysBatchIsAnArtistsSetNotAReupload() {
    val kept = post("kept", parent = "head", bits = 0)

    assertFalse(isReupload(post("child", parent = "kept", created = at + 400.days), kept))
    assertFalse(isReupload(post("head", created = at + 400.days), kept))
    assertFalse(isReupload(post("sibling", parent = "head", created = at + 400.days), kept))
    assertFalse(isReupload(post("same-day", created = at + 20.hours), kept))
    assertFalse(isReupload(post("kept", bits = 0), kept))
  }

  @Test
  fun oneFamilyOrBatchWithinTwoBitsIsStillThePicture() {
    val kept = post("kept", parent = "head", bits = 0)

    assertTrue(isReupload(post("child", parent = "kept", bits = IDENTICAL), kept))
    assertTrue(isReupload(post("same-day", created = at + 1.hours, bits = 0), kept))
    assertFalse(isReupload(post("child", parent = "kept", bits = IDENTICAL + 1), kept))
  }

  @Test
  fun aDifferentPictureIsNoReupload() {
    val kept = HashedPost("kept", draw(shapes(8)).hash(), null, at)

    assertFalse(isReupload(HashedPost("other", draw(shapes(9)).hash(), null, at + 400.days), kept))
  }
}

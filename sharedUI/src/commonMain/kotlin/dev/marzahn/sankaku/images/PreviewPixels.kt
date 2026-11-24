package dev.marzahn.sankaku.images

import coil3.Image
import dev.marzahn.sankaku.data.PreviewHash

fun previewHashOf(image: Image): PreviewHash? {
  val pixels = argbPixels(image) ?: return null
  return PreviewHash.of(pixels.argb, pixels.width, pixels.height)
}

class ArgbPixels(val width: Int, val height: Int, val argb: IntArray)

internal expect fun argbPixels(image: Image): ArgbPixels?

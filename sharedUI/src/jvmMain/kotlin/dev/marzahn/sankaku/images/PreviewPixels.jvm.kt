package dev.marzahn.sankaku.images

import coil3.BitmapImage
import coil3.Image
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

internal actual fun argbPixels(image: Image): ArgbPixels? {
  val bitmap = (image as? BitmapImage)?.bitmap ?: return null
  val width = bitmap.width
  val height = bitmap.height
  if (width <= 0 || height <= 0) return null
  val bytes = bitmap.readPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), width * 4, 0, 0) ?: return null
  val argb = IntArray(width * height) { i ->
    val at = i * 4
    ((bytes[at + 3].toInt() and 0xFF) shl 24) or ((bytes[at].toInt() and 0xFF) shl 16) or
      ((bytes[at + 1].toInt() and 0xFF) shl 8) or (bytes[at + 2].toInt() and 0xFF)
  }
  return ArgbPixels(width, height, argb)
}

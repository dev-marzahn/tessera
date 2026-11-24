package dev.marzahn.sankaku.images

import android.graphics.Bitmap
import coil3.BitmapImage
import coil3.Image

internal actual fun argbPixels(image: Image): ArgbPixels? {
  val decoded = (image as? BitmapImage)?.bitmap ?: return null
  val bitmap = if (decoded.config == Bitmap.Config.HARDWARE) decoded.copy(Bitmap.Config.ARGB_8888, false) ?: return null else decoded
  return try {
    val argb = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    ArgbPixels(bitmap.width, bitmap.height, argb)
  } finally {
    if (bitmap !== decoded) bitmap.recycle()
  }
}

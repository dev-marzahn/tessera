package dev.marzahn.sankaku.images

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.pxOrElse
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class FilteredSkiaDecoder(private val source: ImageSource, private val options: Options) : Decoder {
  override suspend fun decode(): DecodeResult? {
    val bytes = source.source().peek().readByteArray()
    val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
    return image.use { fittedResult(it, options) }
  }

  object Factory : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder =
      FilteredSkiaDecoder(result.source, options)
  }
}

internal fun fittedResult(image: Image, options: Options): DecodeResult {
  val (width, height) = decodeSize(image.width, image.height, options)
  return DecodeResult(resized(image, width, height).asImage(), isSampled = width < image.width || height < image.height)
}

internal fun fittedResult(bitmap: Bitmap, options: Options): DecodeResult {
  if (decodeSize(bitmap.width, bitmap.height, options) == bitmap.width to bitmap.height) {
    return DecodeResult(bitmap.asImage(), isSampled = false)
  }
  val result = Image.makeFromBitmap(bitmap).use { fittedResult(it, options) }
  bitmap.close()
  return result
}

internal fun decodeSize(srcWidth: Int, srcHeight: Int, options: Options): Pair<Int, Int> {
  val xScale = options.size.width.pxOrElse { -1 }.takeIf { it > 0 }?.let { it.toDouble() / srcWidth }
  val yScale = options.size.height.pxOrElse { -1 }.takeIf { it > 0 }?.let { it.toDouble() / srcHeight }
  val scale = when {
    xScale == null && yScale == null -> 1.0
    xScale == null -> yScale!!
    yScale == null -> xScale
    options.scale == Scale.FIT -> min(xScale, yScale)
    else -> max(xScale, yScale)
  }
  if (scale == 1.0 || scale > 1.0 && options.precision != Precision.EXACT) return srcWidth to srcHeight
  return (srcWidth * scale).roundToInt().coerceAtLeast(1) to (srcHeight * scale).roundToInt().coerceAtLeast(1)
}

internal fun resized(image: Image, width: Int, height: Int): Bitmap {
  val bitmap = Bitmap()
  check(bitmap.allocN32Pixels(width, height)) { "Couldn't allocate a $width × $height bitmap" }
  val sampling = when {
    width == image.width && height == image.height -> FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)
    width < image.width || height < image.height -> FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
    else -> FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
  }
  Canvas(bitmap).use { canvas ->
    val src = Rect.makeWH(image.width.toFloat(), image.height.toFloat())
    canvas.drawImageRect(image, src, Rect.makeWH(width.toFloat(), height.toFloat()), sampling, null, false)
  }
  bitmap.setImmutable()
  return bitmap
}

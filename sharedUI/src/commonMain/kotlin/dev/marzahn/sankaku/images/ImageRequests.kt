package dev.marzahn.sankaku.images

import coil3.PlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import kotlin.math.roundToInt

fun postImageRequest(context: PlatformContext, image: PostImage, configure: ImageRequest.Builder.() -> Unit = {}) =
  ImageRequest.Builder(context)
    .data(image)
    .diskCacheKey(image.cacheKey)
    .memoryCacheKey(image.cacheKey)
    .apply(configure)
    .build()

fun cachedPostImageRequest(context: PlatformContext, image: PostImage) =
  postImageRequest(context, image) { networkCachePolicy(CachePolicy.DISABLED) }

fun fittedRequestSize(ratio: Float, windowWidth: Int, windowHeight: Int): Size? {
  if (windowWidth <= 0 || windowHeight <= 0 || ratio <= 0f) return null
  val (width, height) = if (windowWidth.toFloat() / windowHeight > ratio) {
    (windowHeight * ratio).roundToInt() to windowHeight
  } else {
    windowWidth to (windowWidth / ratio).roundToInt()
  }
  return Size(width.coerceAtLeast(1), height.coerceAtLeast(1))
}

fun ImageRequest.Builder.fittedTo(size: Size?): ImageRequest.Builder =
  if (size == null) this else size(size).precision(Precision.INEXACT)

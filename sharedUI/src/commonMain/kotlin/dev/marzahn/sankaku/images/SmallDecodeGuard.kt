package dev.marzahn.sankaku.images

import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.Size

object SmallDecodeGuard : Interceptor {
  override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
    val request = chain.request
    if (request.data !is PostImage || isShareableSize(chain.size) || !request.memoryCachePolicy.writeEnabled) {
      return chain.proceed()
    }
    val policy = if (request.memoryCachePolicy.readEnabled) CachePolicy.READ_ONLY else CachePolicy.DISABLED
    return chain.withRequest(request.newBuilder().memoryCachePolicy(policy).build()).proceed()
  }
}

internal fun isShareableSize(size: Size): Boolean {
  val sides = listOf(size.width, size.height).map { (it as? Dimension.Pixels)?.px ?: Int.MAX_VALUE }
  return sides.max() >= MIN_SHARED_PX && sides.min() >= MIN_SHARED_SIDE_PX
}

internal const val MIN_SHARED_PX = 160
internal const val MIN_SHARED_SIDE_PX = 16

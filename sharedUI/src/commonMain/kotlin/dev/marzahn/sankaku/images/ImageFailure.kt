package dev.marzahn.sankaku.images

import coil3.compose.AsyncImagePainter
import kotlinx.io.IOException

enum class ImageFailure(val retryable: Boolean) {
  NoImage(false),

  Unreachable(true),

  Failed(true),
}

fun imageFailure(error: Throwable): ImageFailure {
  val causes = generateSequence(error) { it.cause }
  return when {
    causes.any { it is NoImageUrlException } -> ImageFailure.NoImage
    causes.any { it is IOException } -> ImageFailure.Unreachable
    else -> ImageFailure.Failed
  }
}

fun AsyncImagePainter.State.failure(): ImageFailure? =
  (this as? AsyncImagePainter.State.Error)?.result?.throwable?.let(::imageFailure)

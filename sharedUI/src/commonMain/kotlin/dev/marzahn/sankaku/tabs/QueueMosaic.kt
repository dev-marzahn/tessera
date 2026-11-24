package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.features.grid.Rating
import dev.marzahn.sankaku.images.PostImage
import kotlinx.serialization.Serializable

@Serializable
data class QueueThumb(val md5: String, val rating: String? = null) {
  val isG: Boolean get() = Rating.ofCode(rating) == Rating.G

  val isAdult: Boolean get() = !isG

  override fun toString() = "QueueThumb(rating=$rating)"

  val image: PostImage get() = PostImage(postId = "", md5 = md5, variant = Variant.Preview, url = null)
}

data class QueueMosaic(val thumbs: List<QueueThumb>, val posts: Int) {
  companion object {
    private const val SIZE = 4

    fun of(loaded: List<PostDto>, firstShown: Int): QueueMosaic? {
      if (loaded.isEmpty()) return null
      val thumbs = shownFrom(loaded, firstShown, SIZE).map { QueueThumb(it.md5, Rating.ofCode(it.rating)?.code) }
      return QueueMosaic(thumbs, loaded.size)
    }

    fun restored(thumbs: List<QueueThumb>?, posts: Int?): QueueMosaic? =
      posts?.takeIf { it > 0 }?.let { QueueMosaic(thumbs.orEmpty().take(SIZE), it) }
  }
}

internal fun shownFrom(loaded: List<PostDto>, firstShown: Int, count: Int): List<PostDto> =
  loaded.drop(firstShown.coerceIn(0, maxOf(0, loaded.size - count))).filterNot { it.redirectToSignup }.take(count)

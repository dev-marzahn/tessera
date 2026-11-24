package dev.marzahn.sankaku.images

import coil3.key.Keyer
import coil3.request.Options
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.data.url

data class PostImage(val postId: String, val md5: String, val variant: Variant, val url: String?) {
  val cacheKey: String get() = cacheKey(md5, variant)

  companion object {
    fun of(post: PostDto, variant: Variant) = PostImage(post.id, post.md5, variant, post.url(variant))

    fun cacheKey(md5: String, variant: Variant) = "$md5-$variant"
  }
}

object PostImageKeyer : Keyer<PostImage> {
  override fun key(data: PostImage, options: Options): String = data.cacheKey
}

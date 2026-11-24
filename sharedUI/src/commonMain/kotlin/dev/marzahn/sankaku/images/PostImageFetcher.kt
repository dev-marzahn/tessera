package dev.marzahn.sankaku.images

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.network.HttpException
import coil3.network.NetworkFetcher
import coil3.request.CachePolicy
import coil3.request.Options
import coil3.toUri
import dev.marzahn.sankaku.api.ImageRefusedException
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.PostStateStore
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.data.isSignedUrlUsable
import dev.marzahn.sankaku.data.url
import kotlin.time.Clock

class NoImageUrlException(variant: String) : Exception("No $variant URL")

class NotCachedException(variant: String) : Exception("$variant not cached")

class PostImageFetcher(
  private val image: PostImage,
  private val options: Options,
  private val imageLoader: ImageLoader,
  private val network: NetworkFetcher.Factory,
  private val repository: PostsRepository,
  private val store: PostStateStore,
  private val clock: Clock,
  private val maxAvifBitDepth: Int,
) : Fetcher {
  override suspend fun fetch(): FetchResult? {
    val result = cached() ?: fromNetwork(image.variant, image.url, options)
    if (image.variant != Variant.Preview || result !is SourceFetchResult || !isUndecodable(result)) return result
    result.source.close()
    if (!options.networkCachePolicy.readEnabled) throw NotCachedException(image.variant.name)

    val post = repository.cachedPost(image.postId) ?: repository.refetch(image.postId)
    if (!post.hasStillSample()) throw NoImageUrlException(Variant.Sample.name)
    return fromNetwork(Variant.Sample, post.url(Variant.Sample), options.copy(diskCachePolicy = CachePolicy.WRITE_ONLY))
  }

  private suspend fun fromNetwork(variant: Variant, known: String?, options: Options): FetchResult? {
    if (!options.networkCachePolicy.readEnabled) throw NotCachedException(image.variant.name)

    val url = store.overrides.value[image.postId]?.urls?.forVariant(variant) ?: known
    if (url == null || !isSignedUrlUsable(url, clock.now())) return download(freshUrl(variant), options)

    return try {
      download(url, options)
    } catch (e: Exception) {
      if (!e.isRefusal()) throw e
      download(freshUrl(variant), options)
    }
  }

  private fun isUndecodable(result: SourceFetchResult): Boolean {
    val depth = AvifHeader.bitDepth(result.source.source()) ?: return false
    return depth > maxAvifBitDepth
  }

  private fun PostDto.hasStillSample(): Boolean =
    fileType in STILL_TYPES && sampleUrl != null && sampleUrl != fileUrl

  private fun cached(): FetchResult? {
    if (!options.diskCachePolicy.readEnabled) return null
    val cache = imageLoader.diskCache ?: return null
    val snapshot = cache.openSnapshot(image.cacheKey) ?: return null
    return SourceFetchResult(
      source = ImageSource(snapshot.data, cache.fileSystem, image.cacheKey, snapshot),
      mimeType = null,
      dataSource = DataSource.DISK,
    )
  }

  private suspend fun freshUrl(variant: Variant): String =
    repository.refetch(image.postId).url(variant) ?: throw NoImageUrlException(variant.name)

  private suspend fun download(url: String, options: Options): FetchResult? =
    network.create(url.toUri(), options, imageLoader)?.fetch()

  private fun Throwable.isRefusal(): Boolean = generateSequence(this) { it.cause }.any {
    it is ImageRefusedException || (it is HttpException && it.response.code in REFUSED_STATUSES)
  }

  class Factory(
    private val network: NetworkFetcher.Factory,
    private val repository: PostsRepository,
    private val store: PostStateStore,
    private val clock: Clock,
    private val maxAvifBitDepth: Int,
  ) : Fetcher.Factory<PostImage> {
    override fun create(data: PostImage, options: Options, imageLoader: ImageLoader): Fetcher =
      PostImageFetcher(data, options, imageLoader, network, repository, store, clock, maxAvifBitDepth)
  }

  private companion object {
    val REFUSED_STATUSES = setOf(401, 403, 404, 410)
    val STILL_TYPES = setOf("image/png", "image/jpeg", "image/webp")
  }
}

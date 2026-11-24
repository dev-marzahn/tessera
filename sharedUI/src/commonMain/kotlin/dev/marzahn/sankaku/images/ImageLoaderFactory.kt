package dev.marzahn.sankaku.images

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.Decoder
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ConnectivityChecker
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import dev.marzahn.sankaku.data.CacheSizes
import dev.marzahn.sankaku.data.PostStateStore
import dev.marzahn.sankaku.data.PostsRepository
import io.ktor.client.HttpClient
import okio.Path
import kotlin.time.Clock

internal expect fun platformImageDecoders(): List<Decoder.Factory>

private const val MEMORY_CACHE_BYTES = 256L * 1024 * 1024


@OptIn(ExperimentalCoilApi::class)
fun createImageLoader(
  context: PlatformContext,
  mainClient: HttpClient,
  repository: PostsRepository,
  store: PostStateStore,
  clock: Clock,
  cacheDir: Path,
  maxAvifBitDepth: Int = platformMaxAvifBitDepth,
  diskCacheBytes: Long = CacheSizes.Images.default,
): ImageLoader {
  val network = KtorNetworkFetcherFactory(
    httpClient = { mainClient },
    cacheStrategy = { ImmutableCacheStrategy },
    connectivityChecker = { ConnectivityChecker.ONLINE },
  )
  return ImageLoader.Builder(context)
    .serviceLoaderEnabled(false)
    .components {
      add(SmallDecodeGuard)
      add(PostImageKeyer)
      add(PostImageFetcher.Factory(network, repository, store, clock, maxAvifBitDepth))
      add(network)
      platformImageDecoders().forEach(::add)
    }
    .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_BYTES).build() }
    .diskCache {
      ResizableDiskCache(diskCacheBytes) { DiskCache.Builder().directory(cacheDir).maxSizeBytes(it).build() }
    }
    .build()
}

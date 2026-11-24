package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.images.ImageFailure
import dev.marzahn.sankaku.images.PostImage
import dev.marzahn.sankaku.images.imageFailure
import dev.marzahn.sankaku.images.postImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import okio.Path
import org.koin.compose.koinInject

expect val platformAnimatesGifs: Boolean

@Composable
expect fun AnimatedImage(file: Path, modifier: Modifier, onShown: () -> Unit)

fun isAnimated(post: PostDto): Boolean = platformAnimatesGifs && post.fileType == "image/gif"

@Composable
fun AnimatedSample(post: PostDto, modifier: Modifier, onShown: () -> Unit, attempt: Int = 0, onFailed: (ImageFailure) -> Unit = {}) {
  val context = LocalPlatformContext.current
  val imageLoader = koinInject<ImageLoader>()
  var fetched by remember(post.id) { mutableStateOf(false) }
  var file by remember(post.id) { mutableStateOf<Path?>(null) }
  val reportFailure by rememberUpdatedState(onFailed)
  LaunchedEffect(post.id, attempt) {
    if (fetched) return@LaunchedEffect
    val request = postImageRequest(context, PostImage.of(post, Variant.Sample)) {
      memoryCachePolicy(CachePolicy.DISABLED)
      size(1, 1)
    }
    val result = imageLoader.execute(request)
    if (result is ErrorResult) return@LaunchedEffect reportFailure(imageFailure(result.throwable))
    fetched = true
  }
  LaunchedEffect(post.id, fetched) {
    if (!fetched) return@LaunchedEffect
    val diskCache = imageLoader.diskCache
    var snapshot: DiskCache.Snapshot? = null
    try {
      withContext(Dispatchers.Default) { snapshot = diskCache?.openSnapshot(PostImage.cacheKey(post.md5, Variant.Sample)) }
      file = snapshot?.data
      if (file == null) {
        fetched = false
        return@LaunchedEffect reportFailure(ImageFailure.Failed)
      }
      awaitCancellation()
    } finally {
      withContext(NonCancellable + Dispatchers.Default) { snapshot?.close() }
    }
  }
  file?.let { AnimatedImage(it, modifier, onShown) }
}

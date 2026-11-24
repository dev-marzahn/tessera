package dev.marzahn.sankaku.features.post

import dev.marzahn.sankaku.shell.RetryWhenBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.github.panpf.zoomimage.zoom.ScalesCalculator
import dev.marzahn.sankaku.platformIsTouch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.memory.MemoryCache
import androidx.compose.foundation.Image
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import androidx.compose.runtime.derivedStateOf
import dev.marzahn.sankaku.ui.components.LoadBar
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.compose.zoom.ScrollBarSpec
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.github.panpf.zoomimage.subsampling.ImageInfo
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.fromFile
import com.google.fonts.icons.open_in_new
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.data.url
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalWindowInfo
import coil3.size.Size as CoilSize
import dev.marzahn.sankaku.images.ImageFailure
import dev.marzahn.sankaku.images.PostImage
import dev.marzahn.sankaku.images.fittedRequestSize
import dev.marzahn.sankaku.images.failure
import dev.marzahn.sankaku.images.fittedTo
import dev.marzahn.sankaku.images.imageFailure
import dev.marzahn.sankaku.images.postImageRequest
import dev.marzahn.sankaku.ui.components.ImageFailed
import dev.marzahn.sankaku.ui.components.ImageRetryChip
import dev.marzahn.sankaku.openUrl
import dev.marzahn.sankaku.ui.Messages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

@Composable
fun PostImageView(
  post: PostDto,
  modifier: Modifier = Modifier,
  imageBox: Modifier = Modifier,
  sampleBox: Modifier? = null,
  showSample: Boolean = true,
  onTap: (() -> Unit)? = null,
  onLongPress: (() -> Unit)? = null,
  zoom: PostZoom? = null,
  playVideo: Boolean = true,
  playing: Boolean = showSample,
  onShown: () -> Unit = {},
  actions: List<CustomAccessibilityAction> = emptyList(),
) {
  val zoomable = showSample && !post.redirectToSignup && !isFlash(post) && !isVideo(post) && !isAnimated(post)
  val unplayable = isVideo(post) && !platformPlaysVideo(post)
  val tap by rememberUpdatedState(onTap)
  val videoKeys = LocalVideoKeys.current
  val currentKeys by rememberUpdatedState(videoKeys)
  val seeks = platformIsTouch && isVideo(post)
  val seek = videoKeys?.tapSeek?.takeIf { seeks }
  LaunchedEffect(seek) {
    if (seek == null) return@LaunchedEffect
    delay(SEEK_SHOWN_MS)
    videoKeys?.tapSeekShown(seek)
  }
  val longPress by rememberUpdatedState(onLongPress)
  val pressHandler: (() -> Unit)? = if (onLongPress == null) null else ({ longPress?.invoke() })
  val tapModifier = when {
    (onTap == null && onLongPress == null) || zoomable -> Modifier
    seeks -> Modifier.videoTaps({ currentKeys }, key =listOf(post.id, onLongPress != null), onTap = { tap?.invoke() }, onLongPress = pressHandler)
    else -> Modifier.pictureTaps(key = listOf(post.id, onLongPress != null), onTap = { tap?.invoke() }, onLongPress = pressHandler, onDoubleTap = null)
  }
  if (post.redirectToSignup) {
    Box(modifier.then(tapModifier), contentAlignment = Alignment.Center) { Text("Sign in to view this post.") }
    return
  }
  val context = LocalPlatformContext.current
  val imageLoader = koinInject<ImageLoader>()
  var sampleLoaded by remember(post.id) { mutableStateOf(false) }
  var videoShown by remember(post.id) { mutableStateOf(false) }
  val shown by rememberUpdatedState(onShown)
  val window = LocalWindowInfo.current.containerSize
  val requestSize = remember(post.id, window) { fittedRequestSize(aspectRatio(post), window.width, window.height) }
  var attempt by remember(post.id) { mutableIntStateOf(0) }
  var previewFailure by remember(post.id, attempt) { mutableStateOf<ImageFailure?>(null) }
  var previewShown by remember(post.id, attempt) { mutableStateOf(false) }
  var sampleFailure by remember(post.id, attempt) { mutableStateOf<ImageFailure?>(null) }
  var fullAttempt by remember(post.id) { mutableIntStateOf(0) }
  var fullLoading by remember(post.id) { mutableStateOf(false) }
  var fullFailure by remember(post.id, attempt, fullAttempt) { mutableStateOf<ImageFailure?>(null) }
  RetryWhenBack {
    if (previewFailure == ImageFailure.Unreachable || sampleFailure == ImageFailure.Unreachable) attempt++
    if (fullFailure == ImageFailure.Unreachable) fullAttempt++
  }
  val picture = Modifier.semantics {
    contentDescription = pictureDescription(post)
    if (actions.isNotEmpty()) customActions = actions
  }
  BoxWithConstraints(modifier.then(tapModifier).then(picture), contentAlignment = Alignment.Center) {
    val ratio = aspectRatio(post)
    val (width, height) = fittedBox(maxWidth, maxHeight, ratio)
    val covered = zoomable && sampleLoaded
    val inMemory = remember(post.md5, sampleLoaded) { decodedInMemory(imageLoader, post.md5) }
    key(attempt) {
      Box(Modifier.size(width, height).graphicsLayer { alpha = if (covered) 0f else 1f }.then(imageBox)) {
        if (inMemory != null) {
          Image(inMemory.asPainter(context), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        } else {
          AsyncImage(
            model = previewRequest(context, post, requestSize),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            onState = {
              previewShown = it is AsyncImagePainter.State.Success
              previewFailure = it.failure()
            },
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
    when {
      isFlash(post) -> UnplayableFallback(post, "Flash isn't supported")
      unplayable -> UnplayableFallback(post, "WebM videos don't play on macOS")
      isVideo(post) -> if (playVideo) {
        PostVideo(
          post,
          if (sampleBox != null) Modifier.size(width, height).then(sampleBox) else Modifier.fillMaxSize(),
          play = showSample && playing,
          shown = showSample,
          onFirstFrame = { videoShown = true },
        )
      }
      isAnimated(post) -> if (showSample) {
        AnimatedSample(
          post,
          Modifier.size(width, height).then(sampleBox ?: Modifier),
          onShown = { sampleLoaded = true },
          attempt = attempt,
          onFailed = { sampleFailure = it },
        )
      }
      showSample -> key(attempt) {
        ZoomableSample(
          post,
          requestSize,
          Modifier.fillMaxSize(),
          onTap,
          onLongPress,
          zoom,
          onLoaded = { sampleLoaded = true },
          onFailed = { sampleFailure = imageFailure(it) },
          fullAttempt = fullAttempt,
          onFullLoading = { fullLoading = it },
          onFullFailed = { fullFailure = it },
        )
      }
    }
    val sampleLoading = showSample && (zoomable || isAnimated(post)) && !sampleLoaded && sampleFailure == null
    Box(Modifier.size(width, height)) {
      LoadBar(sampleLoading, Modifier.align(Alignment.BottomCenter))
    }
    LoadBar(fullLoading, Modifier.align(Alignment.BottomCenter))
    val nothingShown = inMemory == null && !previewShown && !sampleLoaded
    val failure = when {
      sampleLoaded -> null
      sampleFailure != null -> (if (previewFailure?.retryable == true) previewFailure else sampleFailure).takeIf { nothingShown }
      isFlash(post) || unplayable -> previewFailure
      else -> null
    }
    val retry: () -> Unit = { attempt++ }
    if (failure != null) {
      ImageFailed(noImage = failure == ImageFailure.NoImage, onRetry = retry, modifier = Modifier.size(width, height))
    } else if (sampleFailure?.retryable == true && !sampleLoaded) {
      ImageRetryChip(onRetry = retry)
    } else if (fullFailure?.retryable == true) {
      ImageRetryChip(onRetry = { fullAttempt++ })
    }
    if (seeks) SeekRipple(seek, Modifier.size(width, height))
    val fullyShown = when {
      isFlash(post) || unplayable -> previewShown || inMemory != null
      isVideo(post) -> videoShown
      else -> sampleLoaded
    }
    LaunchedEffect(post.id, fullyShown) { if (fullyShown) shown() }
  }
}

@Stable
class PostZoom {
  var zoomed by mutableStateOf(false)
    internal set

  var level by mutableFloatStateOf(1f)
    internal set

  var settled by mutableStateOf(false)
    internal set

  internal var toMin: (suspend () -> Unit)? = null

  suspend fun zoomOut() {
    toMin?.invoke()
  }
}

fun decodedInMemory(imageLoader: ImageLoader, md5: String, previewToo: Boolean = true): coil3.Image? {
  val cache = imageLoader.memoryCache ?: return null
  return cache[MemoryCache.Key(PostImage.cacheKey(md5, Variant.Sample))]?.image
    ?: if (previewToo) cache[MemoryCache.Key(PostImage.cacheKey(md5, Variant.Preview))]?.image else null
}

internal fun fittedBox(maxWidth: Dp, maxHeight: Dp, ratio: Float): Pair<Dp, Dp> =
  if (maxWidth / maxHeight > ratio) maxHeight * ratio to maxHeight else maxWidth to maxWidth / ratio

internal fun sampleVariant(post: PostDto): Variant =
  if (post.sampleUrl != null || post.url(Variant.File) == null) Variant.Sample else Variant.File

internal fun firstShownRequests(context: PlatformContext, post: PostDto, windowWidth: Int, windowHeight: Int): List<ImageRequest> {
  if (post.redirectToSignup) return emptyList()
  val size = fittedRequestSize(aspectRatio(post), windowWidth, windowHeight)
  val preview = previewRequest(context, post, size)
  return if (isVideo(post) || isAnimated(post) || isFlash(post)) listOf(preview) else listOf(preview, sampleRequest(context, post, size))
}

internal fun keptSampleRequest(context: PlatformContext, post: PostDto): ImageRequest? {
  if (post.redirectToSignup || isVideo(post) || isAnimated(post) || isFlash(post)) return null
  return postImageRequest(context, PostImage.of(post, sampleVariant(post))) {
    memoryCachePolicy(CachePolicy.DISABLED)
    size(1, 1)
  }
}

internal fun previewRequest(context: PlatformContext, post: PostDto, requestSize: CoilSize?): ImageRequest =
  postImageRequest(context, PostImage.of(post, Variant.Preview)) { fittedTo(requestSize) }

internal fun sampleRequest(context: PlatformContext, post: PostDto, requestSize: CoilSize?): ImageRequest =
  postImageRequest(context, PostImage.of(post, sampleVariant(post))) {
    placeholderMemoryCacheKey(PostImage.cacheKey(post.md5, Variant.Preview))
    fittedTo(requestSize)
  }

@Composable
private fun ZoomableSample(
  post: PostDto,
  requestSize: CoilSize?,
  modifier: Modifier,
  onTap: (() -> Unit)?,
  onLongPress: (() -> Unit)?,
  zoom: PostZoom?,
  onLoaded: () -> Unit,
  onFailed: (Throwable) -> Unit,
  fullAttempt: Int,
  onFullLoading: (Boolean) -> Unit,
  onFullFailed: (ImageFailure?) -> Unit,
) {
  val context = LocalPlatformContext.current
  val imageLoader = koinInject<ImageLoader>()
  val zoomState = rememberCoilZoomState()
  var shown by remember(post.id) { mutableStateOf(false) }

  if (platformIsTouch) {
    LaunchedEffect(zoomState) {
      zoomState.zoomable.setScalesCalculator(ScalesCalculator.fixed(TOUCH_DOUBLE_TAP_SCALE))
      zoomState.zoomable.setRubberBandScale(false)
    }
  }
  val zoomed by remember(zoomState) {
    derivedStateOf { zoomState.zoomable.transform.scaleX > zoomState.zoomable.minScale * ZOOMED_FROM }
  }
  if (zoom != null) {
    DisposableEffect(zoom, zoomState) {
      val toMin: suspend () -> Unit = { zoomState.zoomable.scale(zoomState.zoomable.minScale, animated = true) }
      zoom.toMin = toMin
      onDispose {
        if (zoom.toMin === toMin) {
          zoom.toMin = null
          zoom.zoomed = false
          zoom.settled = false
          zoom.level = 1f
        }
      }
    }
    LaunchedEffect(zoom, zoomState) {
      snapshotFlow { zoomed }.collect { zoom.zoomed = it }
    }
    LaunchedEffect(zoom, zoomState) {
      snapshotFlow { zoom.zoomed && zoomState.zoomable.continuousTransformType == 0 }.collect { zoom.settled = it }
    }
    LaunchedEffect(zoom, zoomState) {
      snapshotFlow { zoomState.zoomable.transform.scaleX / zoomState.zoomable.minScale }.collect { zoom.level = it }
    }
  }

  val sampleVariant = sampleVariant(post)
  val sample = remember(post.id, requestSize) { sampleRequest(context, post, requestSize) }

  val reportFullLoading by rememberUpdatedState(onFullLoading)
  val reportFullFailure by rememberUpdatedState(onFullFailed)
  LaunchedEffect(post.id, fullAttempt) {
    val fileWidth = post.fileWidth ?: return@LaunchedEffect
    val fileHeight = post.fileHeight ?: return@LaunchedEffect
    if (sampleVariant == Variant.File || fileWidth <= (post.sampleWidth ?: fileWidth)) return@LaunchedEffect
    snapshotFlow {
      val zoomable = zoomState.zoomable
      zoomable.contentSize.width * zoomable.transform.scaleX > (post.sampleWidth ?: fileWidth)
    }.first { it }
    val full = postImageRequest(context, PostImage.of(post, Variant.File)) {
      memoryCachePolicy(CachePolicy.DISABLED)
      size(1, 1)
    }
    reportFullLoading(true)
    val result = try {
      imageLoader.execute(full)
    } finally {
      reportFullLoading(false)
    }
    if (result is ErrorResult) return@LaunchedEffect reportFullFailure(imageFailure(result.throwable))
    val path = imageLoader.diskCache?.openSnapshot(PostImage.cacheKey(post.md5, Variant.File))?.use { it.data }
      ?: return@LaunchedEffect reportFullFailure(ImageFailure.Failed)
    zoomState.setSubsamplingImage(
      ImageSource.fromFile(path),
      ImageInfo(fileWidth, fileHeight, post.fileType ?: "image/${post.fileExt ?: "jpeg"}"),
    )
  }

  CoilZoomAsyncImage(
    model = sample,
    contentDescription = null,
    imageLoader = imageLoader,
    zoomState = zoomState,
    scrollBar = if (zoomed) ScrollBarSpec.Default else null,
    alpha = if (shown) 1f else 0f,
    onState = {
      if (it is AsyncImagePainter.State.Success) {
        shown = true
        onLoaded()
      }
      if (it is AsyncImagePainter.State.Error) onFailed(it.result.throwable)
    },
    onTap = onTap?.let { tap -> { _: Offset -> tap() } },
    onLongPress = onLongPress?.let { press -> { _: Offset -> press() } },
    modifier = modifier,
  )
}

internal fun Modifier.pictureTaps(
  key: Any?,
  onTap: () -> Unit,
  onLongPress: (() -> Unit)?,
  onDoubleTap: ((Offset, Float) -> Unit)?,
  seeking: () -> Boolean = { false },
): Modifier = pointerInput(key) {
  var presses = 0
  var seekingGesture = false
  detectTapGestures(
    onPress = {
      if (presses++ == 0) seekingGesture = onDoubleTap != null && seeking()
      if (!tryAwaitRelease()) presses = 0
    },
    onDoubleTap = onDoubleTap?.let { double ->
      { offset: Offset ->
        presses = 0
        if (seekingGesture) double(offset, size.width.toFloat())
        double(offset, size.width.toFloat())
      }
    },
    onLongPress = onLongPress?.let { press ->
      { _: Offset ->
        presses = 0
        press()
      }
    },
    onTap = { offset ->
      presses = 0
      if (seekingGesture && onDoubleTap != null) onDoubleTap(offset, size.width.toFloat()) else onTap()
    },
  )
}

internal fun Modifier.videoTaps(keys: () -> VideoKeys?, key: Any?, onTap: () -> Unit, onLongPress: (() -> Unit)?): Modifier =
  pictureTaps(
    key = key,
    onTap = onTap,
    onLongPress = onLongPress,
    onDoubleTap = { offset, width -> keys()?.seekFromTap(SeekDirection.at(offset.x, width)) },
    seeking = { keys()?.tapSeek != null },
  )

private const val TOUCH_DOUBLE_TAP_SCALE = 2.5f

private const val ZOOMED_FROM = 1.01f

@Composable
private fun UnplayableFallback(post: PostDto, note: String, messages: Messages = koinInject()) {
  Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)) {
    Column(
      Modifier.padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(note, style = MaterialTheme.typography.titleSmall)
      Button(onClick = { messages.openUrl(postPageUrl(post)) }) {
        Icon(open_in_new, contentDescription = null)
        Text("Open in browser")
      }
    }
  }
}

fun aspectRatio(post: PostDto): Float {
  val w = post.fileWidth ?: post.previewWidth ?: return 1f
  val h = post.fileHeight ?: post.previewHeight ?: return 1f
  return if (w > 0 && h > 0) w.toFloat() / h else 1f
}

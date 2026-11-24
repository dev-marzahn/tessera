package dev.marzahn.sankaku.features.post

import androidx.compose.animation.AnimatedVisibility
import dev.marzahn.sankaku.ui.components.ButtonHint
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.collectLatest
import com.google.fonts.icons.info
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import com.google.fonts.icons.pause
import com.google.fonts.icons.play_arrow
import com.google.fonts.icons.volume_off
import com.google.fonts.icons.volume_up
import dev.marzahn.sankaku.api.dto.PostDto
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.components.AppLoadingIndicator
import kotlin.math.roundToInt
import dev.marzahn.sankaku.ui.components.KeyHint
import dev.marzahn.sankaku.ui.components.MediaSlider
import dev.marzahn.sankaku.ui.components.VideoFailed
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.effectsSpec
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import org.koin.compose.koinInject

fun isVideo(post: PostDto): Boolean = post.fileType?.startsWith("video/") == true

internal fun videoBadgeDuration(post: PostDto): String? = post.videoDuration?.takeIf { it > 0 }?.let(::videoTime)

internal fun videoSource(url: String): String = url.substringBefore('?')

internal fun videoUrl(post: PostDto): String? = post.sampleUrl ?: post.fileUrl

internal fun platformPlaysVideo(post: PostDto): Boolean = videoUrl(post)?.let { playsVideoFile(videoSource(it)) } ?: true

internal fun videoCacheKey(post: PostDto, source: String): String =
  if (post.md5.isNotEmpty()) "${post.md5}/${source.substringAfterLast('/')}" else source

fun isFlash(post: PostDto): Boolean = post.fileExt.equals("swf", ignoreCase = true) || post.fileType?.contains("flash") == true

@Stable
class VideoKeys(private val preferences: PlayerPreferences) {
  internal var player by mutableStateOf<VideoPlayback?>(null)

  var scrubbing by mutableStateOf(false)
    private set

  var draggedTo by mutableStateOf<Float?>(null)
    private set

  private var resumeAfterDrag = false

  var previewsDrag = false

  fun drag(to: Float) {
    val player = player ?: return
    if (previewsDrag) {
      draggedTo = to.coerceIn(0f, 1f)
      return
    }
    if (draggedTo == null) {
      resumeAfterDrag = player.playWhenReady
      player.pause()
      player.dragging = true
    }
    draggedTo = to.coerceIn(0f, 1f)
    player.seekTo(to)
  }

  fun release() {
    val to = draggedTo ?: return
    draggedTo = null
    val player = player ?: return
    if (previewsDrag) return player.seekTo(to)
    player.dragging = false
    player.seekTo(to)
    if (resumeAfterDrag) player.play()
  }

  fun onVolumeKey(key: Key) {
    if (player != null && key == Key.VolumeUp && preferences.muted) preferences.toggleMute()
  }

  fun seek(forward: Boolean): Boolean {
    val player = player ?: return false
    if (player.duration <= 0.0) return false
    val to = player.position * player.duration + if (forward) SEEK_SECONDS else -SEEK_SECONDS
    player.seekTo((to / player.duration).toFloat())
    return true
  }

  internal var tapSeek by mutableStateOf<SeekShown?>(null)
    private set

  internal fun seekFromTap(direction: SeekDirection) {
    if (seek(forward = direction == SeekDirection.Forward)) tapSeek = SeekShown(direction, (tapSeek?.id ?: 0) + 1)
  }

  internal fun tapSeekShown(shown: SeekShown) {
    if (tapSeek == shown) tapSeek = null
  }

  fun onKey(key: Key): Boolean {
    val player = player ?: return false
    return when {
      Shortcuts.PlayPause.matches(key) -> true.also { player.togglePlay() }
      Shortcuts.Mute.matches(key) -> true.also { preferences.toggleMute() }
      Shortcuts.Scrub.matches(key) -> true.also { scrubbing = !scrubbing }
      !scrubbing -> false
      Shortcuts.Previous.matches(key) -> true.also { seek(forward = false) }
      Shortcuts.Next.matches(key) -> true.also { seek(forward = true) }
      Shortcuts.Up.matches(key) -> true.also { preferences.changeVolume(preferences.playedVolume + VOLUME_STEP) }
      Shortcuts.Down.matches(key) -> true.also { preferences.changeVolume(preferences.playedVolume - VOLUME_STEP) }
      Shortcuts.Back.matches(key) -> true.also { scrubbing = false }
      else -> false
    }
  }

  fun stopScrubbing() {
    scrubbing = false
  }

  private var postId: String? = null

  internal var restored: VideoPlace? = null

  internal fun place(): VideoPlace? {
    val player = player
    val postId = postId
    if (player == null || postId == null || player.duration <= 0.0) return restored
    return VideoPlace(postId, player.position, paused = !player.playsOnReturn)
  }

  internal fun restoredPlace(postId: String): VideoPlace? = restored?.takeIf { it.postId == postId }

  internal fun register(player: VideoPlayback, postId: String? = null) {
    this.player?.dragging = false
    draggedTo = null
    tapSeek = null
    this.player = player
    if (restored?.postId != postId) restored = null
    this.postId = postId
  }

  internal fun unregister(player: VideoPlayback) {
    if (this.player !== player) return
    player.dragging = false
    this.player = null
    postId = null
    restored = null
    scrubbing = false
    draggedTo = null
    tapSeek = null
  }

  private companion object {
    const val VOLUME_STEP = 0.1f
  }
}

internal const val SEEK_SECONDS = 5.0

val LocalVideoKeys = staticCompositionLocalOf<VideoKeys?> { null }

private fun VideoPlayback.shownTime(keys: VideoKeys?): Double = (keys?.draggedTo ?: position) * duration

private fun VideoPlayback.togglePlay() = if (playWhenReady) pause() else play()

@Composable
private fun PlayPauseButton(player: VideoPlayback, keyLabel: String?) {
  val name = if (player.playWhenReady) "Pause" else "Play"
  val label = keyLabel?.let { "$name ($it)" } ?: name
  ButtonHint(label) {
    IconButton(onClick = player::togglePlay) {
      Icon(if (player.playWhenReady) pause else play_arrow, contentDescription = label)
    }
  }
}

internal const val VideoBufferingDescription = "Loading the video"

internal const val SeekTrackDescription = "Seek"

internal const val VolumeSliderDescription = "Volume"

internal fun videoTime(seconds: Double): String {
  val total = seconds.coerceAtLeast(0.0).toInt()
  val (h, m, s) = Triple(total / 3600, total / 60 % 60, total % 60)
  fun two(n: Int) = n.toString().padStart(2, '0')
  return if (h > 0) "$h:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
}

@Composable
fun PostVideo(
  post: PostDto,
  modifier: Modifier = Modifier,
  play: Boolean = true,
  shown: Boolean = play,
  preferences: PlayerPreferences = koinInject(),
  onFirstFrame: () -> Unit = {},
  bookPage: Boolean = false,
  player: VideoPlayback = rememberVideoPlayback(),
) {
  val url = videoUrl(post) ?: return
  val volume = if (bookPage) 0f else preferences.playedVolume
  val firstFrame by rememberUpdatedState(onFirstFrame)
  val source = videoSource(url)
  LaunchedEffect(player, source) {
    snapshotFlow { player.firstFrameShown }.first { it }
    firstFrame()
  }
  val keys = LocalVideoKeys.current
  val latestUrl by rememberUpdatedState(url)
  val playing by rememberUpdatedState(play)
  val restored = remember(player, source) { keys?.restoredPlace(post.id) }
  var keepPaused by remember(player, source) { mutableStateOf(restored?.paused == true) }
  var attempt by remember(player, source) { mutableIntStateOf(0) }
  var resumeAt by remember(player, source) { mutableFloatStateOf(restored?.at ?: 0f) }
  var opened by remember(player, source) { mutableStateOf("") }
  LaunchedEffect(player, source, attempt) {
    player.volume = volume
    val cacheKey = videoCacheKey(post, source)
    opened = latestUrl
    player.open(opened, cacheKey, playing && !keepPaused)
    var pendingSeek: Float? = resumeAt.takeIf { it > 0f }
    val resume = pendingSeek?.let { to ->
      launch {
        snapshotFlow { player.duration > 0.0 || player.failed }.first { it }
        if (!player.failed) {
          player.seekTo(to)
          pendingSeek = null
        }
      }
    }
    snapshotFlow { player.failed && latestUrl != opened }.collect { failed ->
      if (!failed) return@collect
      resume?.cancel()
      val at = pendingSeek ?: player.position
      pendingSeek = at
      opened = latestUrl
      player.open(opened, cacheKey, playing && !keepPaused)
      snapshotFlow { player.duration > 0.0 || player.failed }.first { it }
      if (!player.failed) {
        player.seekTo(at)
        pendingSeek = null
      }
    }
  }
  var wasPlayed by remember(player, source) { mutableStateOf(false) }
  LaunchedEffect(player, play) {
    if (play) wasPlayed = true else if (wasPlayed) keepPaused = false
    if (play && !keepPaused) player.play() else player.pause()
  }
  LaunchedEffect(player, source) {
    snapshotFlow { player.playWhenReady }.first { it }
    keepPaused = false
  }
  LaunchedEffect(volume) { player.volume = volume }
  DisposableEffect(keys, player, play) {
    if (play) keys?.register(player, post.id)
    onDispose { keys?.unregister(player) }
  }
  val hover = remember { MutableInteractionSource() }
  val hovered by hover.collectIsHoveredAsState()
  val scrubbing = keys?.scrubbing == true
  Box(modifier.hoverable(hover)) {
    if (shown && player.hasMedia) VideoPlaybackSurface(player, Modifier.fillMaxSize())
    val failed = player.failed && latestUrl == opened
    if (play && failed) {
      VideoFailed(
        onRetry = {
          if (player.position > 0f) resumeAt = player.position
          attempt++
        },
        modifier = Modifier.align(Alignment.Center),
      )
    }
    if (play && !failed && player.buffering) {
      Box(
        Modifier
          .align(Alignment.Center)
          .clip(CircleShape)
          .background(VideoScrim)
          .padding(8.dp)
          .semantics { contentDescription = VideoBufferingDescription },
      ) {
        AppLoadingIndicator(color = Color.White)
      }
    }
    AnimatedVisibility(
      visible = play && !bookPage && !platformIsTouch && !failed && (hovered || scrubbing || (player.hasMedia && !player.playWhenReady)),
      enter = fadeIn(effectsSpec()),
      exit = fadeOut(effectsSpec()),
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
    ) {
      VideoControls(player, keys, preferences, scrubbing)
    }
  }
}

@Immutable
internal data class VideoPlace(val postId: String, val at: Float, val paused: Boolean)

internal fun videoPlaceSaver(keys: VideoKeys): Saver<VideoKeys, Any> = listSaver(
  save = { it.place()?.let { place -> listOf(place.postId, place.at, place.paused) }.orEmpty() },
  restore = { saved ->
    keys.also {
      if (saved.size == 3) it.restored = VideoPlace(saved[0] as String, saved[1] as Float, saved[2] as Boolean)
    }
  },
)

internal const val VIEWER_VIDEO_KEY = "viewer-video"

@Composable
private fun VideoTrack(player: VideoPlayback, keys: VideoKeys?, modifier: Modifier, colors: SliderColors = SliderDefaults.colors()) {
  MediaSlider(
    value = keys?.draggedTo ?: player.position,
    onValueChange = { if (keys != null) keys.drag(it) else player.seekTo(it) },
    onValueChangeFinished = { keys?.release() },
    description = SeekTrackDescription,
    state = "${videoTime(player.shownTime(keys))} of ${videoTime(player.duration)}",
    colors = colors,
    modifier = modifier,
  )
}

@Composable
private fun VideoControls(player: VideoPlayback, keys: VideoKeys?, preferences: PlayerPreferences, scrubbing: Boolean) {
  Surface(
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
    shadowElevation = 2.dp,
  ) {
    Row(
      Modifier.padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      PlayPauseButton(player, Shortcuts.PlayPause.label)
      Text("${videoTime(player.shownTime(keys))} / ${videoTime(player.duration)}", style = MaterialTheme.typography.labelMedium)
      VideoTrack(player, keys, Modifier.weight(1f))
      if (scrubbing) {
        KeyHint("A D", "seek")
        KeyHint("W S", "volume")
        KeyHint(Shortcuts.Back.label, "done")
      } else {
        KeyHint(Shortcuts.Scrub.label, "keys")
      }
      val muteLabel = "${if (preferences.muted) "Unmute" else "Mute"} (${Shortcuts.Mute.label})"
      ButtonHint(muteLabel) {
        IconButton(onClick = preferences::toggleMute) {
          Icon(if (preferences.playedVolume == 0f) volume_off else volume_up, contentDescription = muteLabel)
        }
      }
      MediaSlider(
        value = preferences.playedVolume,
        onValueChange = preferences::changeVolume,
        description = VolumeSliderDescription,
        state = "${(preferences.playedVolume * 100).roundToInt()}%",
        modifier = Modifier.width(96.dp),
      )
    }
  }
}

@Composable
fun VideoSeekRow(
  keys: VideoKeys,
  modifier: Modifier = Modifier,
  preferences: PlayerPreferences = koinInject(),
  flush: Boolean = false,
  onDetails: (() -> Unit)? = null,
  onTrackPlaced: (LayoutCoordinates) -> Unit = {},
) {
  val player = keys.player ?: return
  SankakuTheme(isDark = true) {
    Surface(
      shape = if (flush) RectangleShape else CircleShape,
      color = VideoScrim,
      contentColor = Color.White,
      modifier = modifier.height(VIDEO_SEEK_ROW_HEIGHT),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PlayPauseButton(player, keyLabel = null)
        Text(videoTime(player.shownTime(keys)), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
        VideoTrack(
          player,
          keys,
          colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = UNPLAYED_ALPHA)),
          modifier = Modifier.weight(1f).onGloballyPositioned(onTrackPlaced),
        )
        Text(videoTime(player.duration), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
        IconButton(onClick = preferences::toggleMute) {
          Icon(
            if (preferences.playedVolume == 0f) volume_off else volume_up,
            contentDescription = if (preferences.muted) "Unmute" else "Mute",
          )
        }
        if (onDetails != null) {
          IconButton(onClick = onDetails) { Icon(info, contentDescription = VideoDetailsDescription) }
        }
      }
    }
  }
}

internal const val VideoDetailsDescription = "Details"

@Composable
fun VideoScrubPreview(keys: VideoKeys, post: PostDto, shown: Boolean, modifier: Modifier = Modifier) {
  val url = videoUrl(post) ?: return
  val source = videoSource(url)
  val player = rememberVideoPlayback()
  LaunchedEffect(player, source) {
    player.volume = 0f
    player.open(url, videoCacheKey(post, source), play = false)
    player.dragging = true
  }
  LaunchedEffect(player) {
    snapshotFlow { keys.draggedTo }.collectLatest { at ->
      if (at == null) return@collectLatest
      snapshotFlow { player.duration > 0.0 }.first { it }
      player.seekTo(at)
    }
  }
  val ratio = aspectRatio(post)
  val frame = if (ratio >= PREVIEW_WIDTH / PREVIEW_HEIGHT) {
    Modifier.width(PREVIEW_WIDTH).aspectRatio(ratio)
  } else {
    Modifier.height(PREVIEW_HEIGHT).aspectRatio(ratio)
  }
  val at = keys.draggedTo ?: player.position
  val duration = keys.player?.duration ?: player.duration
  val context = LocalPlatformContext.current
  val imageLoader = koinInject<ImageLoader>()
  val placeholder = remember(post.md5) { decodedInMemory(imageLoader, post.md5) }
  val shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f)
  Column(
    modifier.graphicsLayer { alpha = if (shown) 1f else 0f },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
      frame
        .clip(RoundedCornerShape(8.dp))
        .background(Color.Black)
        .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
    ) {
      if (placeholder != null && !player.firstFrameShown) {
        Image(placeholder.asPainter(context), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      }
      if (player.hasMedia) VideoPlaybackSurface(player, Modifier.fillMaxSize().padding(2.dp))
    }
    Text(
      videoTime(at * duration),
      color = Color.White,
      style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace, shadow = shadow),
    )
  }
}

private val PREVIEW_WIDTH = 160.dp
private val PREVIEW_HEIGHT = 120.dp

val VIDEO_SEEK_ROW_HEIGHT = 48.dp

@Composable
fun VideoProgressLine(keys: VideoKeys, modifier: Modifier = Modifier) {
  val player = keys.player ?: return
  SankakuTheme(isDark = true) {
    val played = MaterialTheme.colorScheme.primary
    Box(
      modifier
        .fillMaxWidth()
        .height(2.dp)
        .drawBehind {
          drawRect(Color.White.copy(alpha = UNPLAYED_ALPHA))
          drawRect(played, size = size.copy(width = size.width * player.position.coerceIn(0f, 1f)))
        },
    )
  }
}

internal enum class SeekDirection(val glyph: String, val sign: String, val alignment: Alignment, val shape: Shape) {
  Back("«", "−", Alignment.CenterStart, GenericShape { size, _ -> addOval(Rect(-size.width, 0f, size.width, size.height)) }),
  Forward("»", "+", Alignment.CenterEnd, GenericShape { size, _ -> addOval(Rect(0f, 0f, size.width * 2, size.height)) }),
  ;

  val label: String get() = "$sign${SEEK_SECONDS.toInt()} s"

  companion object {
    fun at(x: Float, width: Float): SeekDirection = if (x >= width / 2f) Forward else Back
  }
}

@Immutable
internal data class SeekShown(val direction: SeekDirection, val id: Int)

@Composable
internal fun SeekRipple(seek: SeekShown?, modifier: Modifier = Modifier) {
  val last = remember { mutableStateOf<SeekShown?>(null) }
  SideEffect { if (seek != null) last.value = seek }
  val shown = (seek ?: last.value)?.direction ?: return
  Box(modifier) {
    AnimatedVisibility(
      visible = seek != null,
      enter = fadeIn(effectsSpec()),
      exit = fadeOut(effectsSpec()),
      modifier = Modifier.align(shown.alignment).fillMaxHeight().fillMaxWidth(RIPPLE_SHARE),
    ) {
      Column(
        Modifier.fillMaxSize().clip(shown.shape).background(Color.White.copy(alpha = 0.18f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(shown.glyph, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(shown.label, color = Color.White, style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

private val VideoScrim = Color.Black.copy(alpha = 0.55f)

private const val UNPLAYED_ALPHA = 0.35f

internal const val SEEK_SHOWN_MS = 700L

private const val RIPPLE_SHARE = 0.27f

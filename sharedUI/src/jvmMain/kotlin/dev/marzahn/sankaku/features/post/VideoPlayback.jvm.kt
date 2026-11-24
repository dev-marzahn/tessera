package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

@Stable
internal class LibraryVideoPlayback(val state: VideoPlayerState) : VideoPlayback {
  private var seekTarget by mutableStateOf<Float?>(null)

  private val reported get() = state.sliderPos / SLIDER_RANGE

  override val hasMedia get() = state.hasMedia
  override val isPlaying get() = state.isPlaying

  override var playWhenReady by mutableStateOf(false)
    private set
  override val buffering get() = playWhenReady && state.isLoading && state.error == null
  override val failed get() = state.error != null
  override val firstFrameShown get() = state.hasMedia && state.isPlaying
  override val duration get() = state.duration
  override val position get() = seekTarget ?: reported
  override var volume: Float
    get() = state.volume
    set(value) {
      state.volume = value
    }
  override var dragging = false

  override fun open(url: String, cacheKey: String, play: Boolean) {
    seekTarget = null
    state.loop = true
    state.clearError()
    playWhenReady = play
    state.openUri(url, if (play) InitialPlayerState.PLAY else InitialPlayerState.PAUSE)
  }

  override fun play() {
    playWhenReady = true
    state.play()
  }

  override fun pause() {
    playWhenReady = false
    state.pause()
  }

  override fun seekTo(fraction: Float) {
    val to = fraction.coerceIn(0f, 1f)
    state.seekTo(to * SLIDER_RANGE)
    seekTarget = to
  }

  suspend fun settleSeeks() {
    snapshotFlow { seekTarget }.collectLatest { to ->
      if (to == null) return@collectLatest
      val settled = withTimeoutOrNull(SEEK_SETTLE_MS) { awaitLanded(to) } != null
      if (!settled) snapshotFlow { state.isPlaying }.first { it }
      if (settled) withTimeoutOrNull(CATCH_UP_MS) { snapshotFlow { reported >= to || !state.isPlaying }.first { it } }
      seekTarget = null
    }
  }

  private suspend fun awaitLanded(to: Float) {
    while (true) {
      snapshotFlow { landed(to) }.first { it }
      delay(SEEK_HOLD_MS)
      if (landed(to)) return
    }
  }

  private fun landed(to: Float) = state.isPlaying && abs(reported - to) * state.duration <= SEEK_LANDED_SECONDS

  internal companion object {
    const val SLIDER_RANGE = 1000f

    const val SEEK_LANDED_SECONDS = 2.0

    const val SEEK_HOLD_MS = 400L

    const val SEEK_SETTLE_MS = 1_500L

    const val CATCH_UP_MS = 2_500L
  }
}

@Composable
internal actual fun rememberVideoPlayback(): VideoPlayback {
  val state = rememberVideoPlayerState()
  val playback = remember(state) { LibraryVideoPlayback(state) }
  LaunchedEffect(playback) { playback.settleSeeks() }
  return playback
}

@Composable
internal actual fun VideoPlaybackSurface(playback: VideoPlayback, modifier: Modifier) {
  VideoPlayerSurface((playback as LibraryVideoPlayback).state, modifier)
}

actual suspend fun clearVideoCache() {}

actual suspend fun videoCacheBytes(): Long = 0

actual val platformKeepsVideos: Boolean = false

actual suspend fun resizeVideoCache(bytes: Long) {}

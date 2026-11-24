package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

@Stable
interface VideoPlayback {
  val hasMedia: Boolean

  val isPlaying: Boolean

  val playWhenReady: Boolean get() = isPlaying

  val playsOnReturn: Boolean get() = playWhenReady

  val buffering: Boolean get() = false

  val failed: Boolean

  val firstFrameShown: Boolean

  val duration: Double

  val position: Float

  var volume: Float

  fun open(url: String, cacheKey: String, play: Boolean)

  fun play()

  fun pause()

  var dragging: Boolean

  fun seekTo(fraction: Float)
}

@Composable
internal expect fun rememberVideoPlayback(): VideoPlayback

internal val LocalVideoPlayback = staticCompositionLocalOf<@Composable () -> VideoPlayback> { { rememberVideoPlayback() } }

@Composable
internal expect fun VideoPlaybackSurface(playback: VideoPlayback, modifier: Modifier = Modifier)

expect suspend fun clearVideoCache()

expect suspend fun videoCacheBytes(): Long

expect val platformKeepsVideos: Boolean

expect suspend fun resizeVideoCache(bytes: Long)

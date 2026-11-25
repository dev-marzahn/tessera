package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class Seek(val to: Float, val dragging: Boolean)

internal class FakeVideoPlayback(
  playing: Boolean = false,
  position: Float = 0f,
  override val hasMedia: Boolean = false,
  override val firstFrameShown: Boolean = false,
  override val duration: Double = 10.0,
) : VideoPlayback {
  override var isPlaying by mutableStateOf(playing)
    private set
  override var position by mutableStateOf(position)
    private set
  override val failed = false
  override var volume = 1f
  override var dragging = false
  var opened = 0
    private set
  var released = false
    private set
  val seeks = mutableListOf<Seek>()

  override fun open(url: String, cacheKey: String, play: Boolean) {
    opened++
    isPlaying = play
  }

  override fun play() {
    isPlaying = true
  }

  override fun pause() {
    isPlaying = false
  }

  override fun seekTo(fraction: Float) {
    position = fraction.coerceIn(0f, 1f)
    seeks += Seek(position, dragging)
  }

  override fun release() {
    released = true
  }
}

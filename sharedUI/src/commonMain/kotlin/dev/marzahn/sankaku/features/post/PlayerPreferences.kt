package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.marzahn.sankaku.data.TextFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SavedPlayer(val volume: Float = 1f, val muted: Boolean = false, val startMuted: Boolean = false)

class PlayerPreferences(private val file: TextFile) {
  private val saved = file.read()?.let { runCatching { json.decodeFromString(SavedPlayer.serializer(), it) }.getOrNull() }
    ?: SavedPlayer()

  var volume by mutableFloatStateOf(saved.volume.coerceIn(0f, 1f))
    private set

  private var ownMute = saved.muted

  var muted by mutableStateOf(saved.muted || saved.startMuted)
    private set

  var startMuted by mutableStateOf(saved.startMuted)
    private set

  val playedVolume: Float get() = if (muted) 0f else volume

  fun changeVolume(to: Float) {
    volume = to.coerceIn(0f, 1f)
    muted = false
    ownMute = false
    save()
  }

  fun toggleMute() {
    muted = !muted
    ownMute = muted
    save()
  }

  fun changeStartMuted(to: Boolean) {
    startMuted = to
    save()
  }

  private fun save() = file.write(json.encodeToString(SavedPlayer.serializer(), SavedPlayer(volume, ownMute, startMuted)))

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
  }
}

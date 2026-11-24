package dev.marzahn.sankaku.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SavedFirstLaunch(val accepted: Boolean = false)

class FirstLaunch(private val file: TextFile) {
  private val _accepted = MutableStateFlow(
    file.read()?.let { runCatching { json.decodeFromString(SavedFirstLaunch.serializer(), it) }.getOrNull() }?.accepted
      ?: false,
  )
  val accepted: StateFlow<Boolean> = _accepted.asStateFlow()

  fun accept() {
    _accepted.value = true
    runCatching { file.write(json.encodeToString(SavedFirstLaunch.serializer(), SavedFirstLaunch(accepted = true))) }
  }

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
  }
}

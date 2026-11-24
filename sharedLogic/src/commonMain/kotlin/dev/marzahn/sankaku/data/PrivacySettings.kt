package dev.marzahn.sankaku.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class StartOn {
  Queue,

  Browse,
}

@Serializable
private data class SavedPrivacy(
  val hideInRecents: Boolean = true,
  val gOnly: Boolean = false,
  val startOn: StartOn = StartOn.Queue,
  val alwaysStartGOnly: Boolean = false,
)

class PrivacySettings(private val file: TextFile, val gOnlyForced: Boolean = false) {
  private val saved = file.read()?.let { runCatching { json.decodeFromString(SavedPrivacy.serializer(), it) }.getOrNull() }
    ?: SavedPrivacy()

  private val _hideInRecents = MutableStateFlow(saved.hideInRecents)
  val hideInRecents: StateFlow<Boolean> = _hideInRecents.asStateFlow()

  private val _alwaysStartGOnly = MutableStateFlow(saved.alwaysStartGOnly)
  val alwaysStartGOnly: StateFlow<Boolean> = _alwaysStartGOnly.asStateFlow()

  private val _startOn = MutableStateFlow(saved.startOn)
  val startOn: StateFlow<StartOn> = _startOn.asStateFlow()

  private val _gOnlySwitch = MutableStateFlow(saved.gOnly || saved.alwaysStartGOnly)

  val gOnlySwitch: StateFlow<Boolean> = _gOnlySwitch.asStateFlow()

  private val _gOnly = MutableStateFlow(_gOnlySwitch.value || gOnlyForced)

  val gOnly: StateFlow<Boolean> = _gOnly.asStateFlow()

  init {
    if (_gOnlySwitch.value != saved.gOnly) save()
  }

  fun setHideInRecents(on: Boolean) {
    _hideInRecents.value = on
    save()
  }

  fun setGOnly(on: Boolean) {
    _gOnlySwitch.value = on
    _gOnly.value = on || gOnlyForced
    save()
  }

  fun setStartOn(start: StartOn) {
    _startOn.value = start
    save()
  }

  fun setAlwaysStartGOnly(on: Boolean) {
    _alwaysStartGOnly.value = on
    save()
  }

  private fun save() {
    runCatching {
      val state = SavedPrivacy(_hideInRecents.value, _gOnlySwitch.value, _startOn.value, _alwaysStartGOnly.value)
      file.write(json.encodeToString(SavedPrivacy.serializer(), state))
    }
  }

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
  }
}

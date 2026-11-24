package dev.marzahn.sankaku.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SavedAccessibility(val showQueueButtons: Boolean = false, val leftHanded: Boolean = false)

class AccessibilitySettings(private val file: TextFile) {
  private val saved = file.read()?.let { runCatching { json.decodeFromString(SavedAccessibility.serializer(), it) }.getOrNull() }
    ?: SavedAccessibility()

  private val _showQueueButtons = MutableStateFlow(saved.showQueueButtons)
  val showQueueButtons: StateFlow<Boolean> = _showQueueButtons.asStateFlow()

  private val _leftHanded = MutableStateFlow(saved.leftHanded)
  val leftHanded: StateFlow<Boolean> = _leftHanded.asStateFlow()

  fun setShowQueueButtons(on: Boolean) {
    _showQueueButtons.value = on
    save()
  }

  fun setLeftHanded(on: Boolean) {
    _leftHanded.value = on
    save()
  }

  private fun save() {
    val saved = SavedAccessibility(showQueueButtons = _showQueueButtons.value, leftHanded = _leftHanded.value)
    runCatching { file.write(json.encodeToString(SavedAccessibility.serializer(), saved)) }
  }

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
  }
}

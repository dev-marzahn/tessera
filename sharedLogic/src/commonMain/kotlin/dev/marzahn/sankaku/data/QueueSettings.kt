package dev.marzahn.sankaku.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SavedQueue(val forYouInQueue: Boolean = false, val switcherList: Boolean = false)

class QueueSettings(private val file: TextFile) {
  private val saved = file.read()?.let { runCatching { json.decodeFromString(SavedQueue.serializer(), it) }.getOrNull() }
    ?: SavedQueue()

  private val _forYouInQueue = MutableStateFlow(saved.forYouInQueue)
  val forYouInQueue: StateFlow<Boolean> = _forYouInQueue.asStateFlow()

  private val _switcherList = MutableStateFlow(saved.switcherList)
  val switcherList: StateFlow<Boolean> = _switcherList.asStateFlow()

  fun setForYouInQueue(on: Boolean) {
    _forYouInQueue.value = on
    save()
  }

  fun setSwitcherList(list: Boolean) {
    _switcherList.value = list
    save()
  }

  private fun save() {
    val saved = SavedQueue(_forYouInQueue.value, _switcherList.value)
    runCatching { file.write(json.encodeToString(SavedQueue.serializer(), saved)) }
  }

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
  }
}

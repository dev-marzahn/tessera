package dev.marzahn.sankaku.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SavedCache(val imageBytes: Long? = null, val videoBytes: Long? = null)

data class CacheSizes(val presets: List<Long>, val default: Long) {
  init {
    require(presets.isNotEmpty() && presets == presets.sorted().distinct()) { "presets must be ascending" }
    require(default in presets) { "the default must be one of the presets" }
  }

  fun snap(bytes: Long): Long = presets.lastOrNull { it <= bytes } ?: presets.first()

  companion object {
    private const val MB = 1024L * 1024
    private const val GB = MB * 1024

    val Images = CacheSizes(listOf(512 * MB, 1 * GB, 2 * GB, 4 * GB, 8 * GB, 16 * GB), default = 2 * GB)
    val Videos = CacheSizes(listOf(256 * MB, 512 * MB, 1 * GB, 2 * GB, 4 * GB, 8 * GB), default = 1 * GB)
  }
}

class CacheSettings(private val file: TextFile) {
  private val saved = file.read()?.let { runCatching { json.decodeFromString(SavedCache.serializer(), it) }.getOrNull() }
    ?: SavedCache()

  private var chosenImage = saved.imageBytes?.let(CacheSizes.Images::snap)
  private var chosenVideo = saved.videoBytes?.let(CacheSizes.Videos::snap)

  private val _imageBytes = MutableStateFlow(chosenImage ?: CacheSizes.Images.default)
  val imageBytes: StateFlow<Long> = _imageBytes.asStateFlow()

  private val _videoBytes = MutableStateFlow(chosenVideo ?: CacheSizes.Videos.default)
  val videoBytes: StateFlow<Long> = _videoBytes.asStateFlow()

  fun setImageBytes(bytes: Long) {
    val snapped = CacheSizes.Images.snap(bytes)
    chosenImage = snapped
    _imageBytes.value = snapped
    save()
  }

  fun setVideoBytes(bytes: Long) {
    val snapped = CacheSizes.Videos.snap(bytes)
    chosenVideo = snapped
    _videoBytes.value = snapped
    save()
  }

  private fun save() {
    val saved = SavedCache(imageBytes = chosenImage, videoBytes = chosenVideo)
    runCatching { file.write(json.encodeToString(SavedCache.serializer(), saved)) }
  }

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
  }
}

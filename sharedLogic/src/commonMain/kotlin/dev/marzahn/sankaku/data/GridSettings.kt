package dev.marzahn.sankaku.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SavedGrid(val cellWidth: Int? = null)

data class PostSizes(val min: Int, val max: Int, val step: Int, val default: Int) {
  init {
    require(step > 0 && min < max && (max - min) % step == 0) { "max - min must be a whole number of steps" }
    require(default in min..max && (default - min) % step == 0) { "the default must be one of the sizes" }
  }

  fun snap(dp: Int): Int {
    val clamped = dp.coerceIn(min, max)
    val steps = (clamped - min + step / 2) / step
    return min + steps * step
  }

  companion object {
    val Desktop = PostSizes(min = 160, max = 560, step = 20, default = 220)
    val Phone = PostSizes(min = 80, max = 200, step = 10, default = 110)
  }
}

class GridSettings(private val file: TextFile, val sizes: PostSizes = PostSizes.Desktop) {
  private val saved = file.read()?.let { runCatching { json.decodeFromString(SavedGrid.serializer(), it) }.getOrNull() }
    ?: SavedGrid()

  private val _cellWidth = MutableStateFlow(saved.cellWidth?.let(sizes::snap))
  val cellWidth: StateFlow<Int?> = _cellWidth.asStateFlow()

  fun setCellWidth(dp: Int) {
    val snapped = sizes.snap(dp)
    if (_cellWidth.value == snapped) return
    _cellWidth.value = snapped
    runCatching { file.write(json.encodeToString(SavedGrid.serializer(), SavedGrid(snapped))) }
  }

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
  }
}

package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.TextFile
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.queue.SessionId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

@Serializable
data class SavedTabs(
  val activeIndex: Int,
  val tabs: List<SavedTab>,
  val version: Int? = null,
  val session: String? = null,
  val faded: List<SavedFaded> = emptyList(),
  val returns: Map<String, String> = emptyMap(),
  val jumps: List<SavedJump> = emptyList(),
  val closed: List<SavedClosed> = emptyList(),
) {
  companion object {
    const val VERSION = 2
  }
}

@Serializable
data class SavedClosed(
  val at: Long,
  val members: List<SavedClosedMember>,
  val active: Int? = null,
  val cleared: Boolean? = null,
  val keptStay: Int? = null,
)

@Serializable
data class SavedClosedMember(
  val index: Int,
  val routes: List<Route>,
  val id: String? = null,
  val block: String? = null,
  val addedIn: String? = null,
  val place: GridPlace? = null,
  val keptAt: Long? = null,
  val closedSource: List<String>? = null,
  val title: String? = null,
  val gridTouched: Boolean? = null,
  val mosaic: List<QueueThumb>? = null,
  val posts: Int? = null,
  val openedFrom: String? = null,
  val refines: List<String>? = null,
  val preview: SourcePreview? = null,
  val foundOn: FoundOn? = null,
  val wayBack: String? = null,
  val later: Boolean? = null,
)

@Serializable
data class SavedJump(val to: String, val from: String, val moved: Boolean = false)

@Serializable
data class SavedTab(
  val id: String,
  val routes: List<Route>,
  val block: String? = null,
  val place: GridPlace? = null,
  val kind: QueueKind? = null,
  val touched: Long? = null,
  val addedIn: String? = null,
  val keptAt: Long? = null,
  val closedSource: List<String>? = null,
  val title: String? = null,
  val mosaic: List<QueueThumb>? = null,
  val posts: Int? = null,
  val gridTouched: Boolean? = null,
  val openedFrom: String? = null,
  val refines: List<String>? = null,
  val preview: SourcePreview? = null,
  val foundOn: FoundOn? = null,
  val later: Boolean? = null,
)

@Serializable
data class SavedFaded(val at: Long, val tabs: List<SavedTab>)

interface TabStateStore {
  fun load(): SavedTabs?

  fun save(tabs: SavedTabs)

  fun clear()

  fun clearBackups() = Unit

  object None : TabStateStore {
    override fun load(): SavedTabs? = null

    override fun save(tabs: SavedTabs) = Unit

    override fun clear() = Unit
  }
}

class FileTabStateStore(
  private val file: TextFile,
  private val backup: TextFile = InMemoryTextFile(),
  private val broken: TextFile = InMemoryTextFile(),
  private val clock: Clock = Clock.System,
) : TabStateStore {
  private var copy: Pair<TextFile, String>? = null

  override fun load(): SavedTabs? {
    val text = file.read() ?: return null
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
    if (root != null && root.isEmpty()) return null
    val saved = root?.let { runCatching { read(it, text) }.getOrNull() }
    if (saved == null) moveAside(text)
    return saved
  }

  private fun read(root: JsonObject, text: String): SavedTabs? = when (root["version"]?.jsonPrimitive?.int) {
    null -> migrate(json.decodeFromString(SavedTabs.serializer(), text), clock.now().epochSeconds).also { copy = backup to text }
    SavedTabs.VERSION -> json.decodeFromString(SavedTabs.serializer(), text)
    else -> null
  }

  override fun save(tabs: SavedTabs) = write(json.encodeToString(SavedTabs.serializer(), tabs.copy(version = SavedTabs.VERSION)))

  override fun clear() = write("{}")

  private fun write(text: String) {
    copy?.let { (to, copied) -> to.write(copied) }
    copy = null
    file.write(text)
  }

  override fun clearBackups() {
    copy = null
    backup.delete()
    broken.delete()
  }

  private fun moveAside(text: String) {
    copy = broken to text
    runCatching { broken.write(text) }.onSuccess { copy = null }
  }

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    fun migrate(v1: SavedTabs, now: Long): SavedTabs = v1.copy(
      version = SavedTabs.VERSION,
      tabs = v1.tabs.map { tab ->
        val kind = queueKind(tab.routes.lastOrNull())
        tab.copy(
          kind = kind,
          touched = now,
          addedIn = SessionId.EARLIER.value,
          keptAt = now.takeIf { kind == QueueKind.KeptPost },
        )
      },
    )
  }
}

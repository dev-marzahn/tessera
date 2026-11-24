package dev.marzahn.sankaku.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.attribute.PosixFilePermissions

@Serializable
data class SavedCursor(
  val kind: String,
  val savedAt: Long,
  val tags: String,
  val flag: String?,
  val limit: Int,
  val page: Int,
  val cursor: String,
  val digest: String,
  val count: Int,
)

@Serializable
data class HeadSnapshot(
  val savedAt: Long,
  val newest: Long,
  val oldest: Long,
  val seen: List<String>,
  val follows: List<String>? = null,
)

@Serializable
data class LedgerData(val cursors: List<SavedCursor> = emptyList(), val head: HeadSnapshot? = null)

class FeedLedger(private val file: File) {
  private val json = Json { ignoreUnknownKeys = true }
  var data: LedgerData = load()
    private set

  private fun load(): LedgerData {
    if (!file.exists()) return LedgerData()
    return runCatching { json.decodeFromString<LedgerData>(file.readText()) }.getOrElse {
      runCatching { file.copyTo(File(file.path + ".unreadable"), overwrite = true) }
      LedgerData()
    }
  }

  fun due(kind: String, now: Long): Boolean =
    data.cursors.none { it.kind == kind && now - it.savedAt < MIN_GAP_SECONDS }

  fun add(entry: SavedCursor) {
    val young = (data.cursors.filter { it.kind == entry.kind } + entry)
      .filter { entry.savedAt - it.savedAt < MAX_AGE_SECONDS }
    val sameKind = if (young.size <= MAX_PER_KIND) young else listOf(young.first()) + young.takeLast(MAX_PER_KIND - 1)
    data = data.copy(cursors = data.cursors.filter { it.kind != entry.kind } + sameKind)
  }

  fun setHead(head: HeadSnapshot) {
    data = data.copy(head = head)
  }

  fun save() {
    file.parentFile?.mkdirs()
    if (!file.exists()) {
      runCatching {
        java.nio.file.Files.createFile(file.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
      }
    }
    file.writeText(json.encodeToString(LedgerData.serializer(), data) + "\n")
  }

  companion object {
    const val MIN_GAP_SECONDS = 90 * 60L
    const val MAX_AGE_SECONDS = 30 * 86400L
    private const val MAX_PER_KIND = 8

    fun default(env: Map<String, String>): FeedLedger =
      FeedLedger(File(env["SANKAKU_FEED_LEDGER"] ?: "${System.getProperty("user.home")}/.sankaku-cli/feed-ledger.json"))
  }
}

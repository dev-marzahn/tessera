package dev.marzahn.sankaku.features.settings

import dev.marzahn.sankaku.platformAppDataText
import dev.marzahn.sankaku.platformShareFile
import dev.marzahn.sankaku.ui.Messages
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val QueueExportName = "tessera-queue.json"

internal const val ExportQueueHelp = "Your queue and the posts you've seen, as one file to send. It holds your searches and post ids."

internal fun queueExport(tabs: String?, seen: String?, exportedAt: Instant): String =
  buildJsonObject {
    put("version", 1)
    put("exportedAt", exportedAt.toString())
    put("tabs", tabs.asJson())
    put("seen", seen.asJson())
  }.toString()

private fun String?.asJson(): JsonElement =
  if (this == null) JsonNull else runCatching { Json.parseToJsonElement(this) }.getOrElse { JsonPrimitive(this) }

internal const val ExportQueueFailed = "Couldn't export the queue"

internal suspend fun shareQueue(
  messages: Messages,
  read: (String) -> String? = ::platformAppDataText,
  share: (String, String, String) -> Unit = ::platformShareFile,
  now: () -> Instant = { Clock.System.now() },
) {
  try {
    withContext(Dispatchers.IO) {
      share(QueueExportName, "application/json", queueExport(read("tabs.json"), read("seen.json"), now()))
    }
  } catch (e: CancellationException) {
    throw e
  } catch (_: Exception) {
    messages.showError(ExportQueueFailed)
  }
}

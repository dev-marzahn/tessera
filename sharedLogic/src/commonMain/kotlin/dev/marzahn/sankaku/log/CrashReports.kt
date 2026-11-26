package dev.marzahn.sankaku.log

import dev.marzahn.sankaku.APP_VERSION
import dev.marzahn.sankaku.data.TextFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Instant

class CrashReports(
  private val file: TextFile,
  private val clock: Clock = Clock.System,
  private val version: String = APP_VERSION,
) {
  private val previous = runCatching { file.read() }.getOrNull()?.takeIf { it.isNotBlank() }

  private val _pending = MutableStateFlow(previous)

  val pending: StateFlow<String?> = _pending.asStateFlow()

  fun record(error: Throwable) {
    runCatching { file.write(report(error)) }
  }

  fun deleteFile() {
    runCatching { if (previous != null && file.read() == previous) file.delete() }
  }

  fun clear() {
    _pending.value = null
    deleteFile()
  }

  private fun report(error: Throwable): String = buildString {
    append("Crashed: ${Instant.fromEpochSeconds(clock.now().epochSeconds)}, Tessera $version\n")
    appendThrowable(error, prefix = "", seen = mutableListOf())
  }

  private fun StringBuilder.appendThrowable(error: Throwable, prefix: String, seen: MutableList<Throwable>) {
    if (seen.any { it === error }) {
      append("\t[CIRCULAR REFERENCE: ${error.typeName}]\n")
      return
    }
    seen += error
    append(prefix).append(error.typeName).append('\n')
    val frames = error.frames()
    frames.take(MAX_FRAMES).forEach { append("\tat ").append(it).append('\n') }
    if (frames.size > MAX_FRAMES) append("\t... ${frames.size - MAX_FRAMES} more frames\n")
    error.suppressedExceptions.forEach { appendThrowable(it, "Suppressed: ", seen) }
    error.cause?.let { appendThrowable(it, "Caused by: ", seen) }
  }

  companion object {
    const val MAX_FRAMES = 100
  }
}

internal expect val Throwable.typeName: String

internal expect fun Throwable.frames(): List<String>

expect fun installUncaughtHandler(closesApp: () -> Boolean = { true }, recordTo: () -> CrashReports)

fun CrashReports.installUncaughtHandler(closesApp: () -> Boolean = { true }) = installUncaughtHandler(closesApp) { this }

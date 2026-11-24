package dev.marzahn.sankaku.log

import dev.marzahn.sankaku.PUBLIC_REPO_URL
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

data class AppEnvironment(val version: String, val platform: String, val os: String, val device: String)

class Diagnostics(private val log: RequestLog, val environment: AppEnvironment) {
  val entries: StateFlow<List<RequestLogEntry>> get() = log.entries

  fun copyText(): String {
    val entries = log.entries.value
    if (entries.isEmpty()) return "$header\n\nRequests: none yet"
    val lines = entries.asReversed().map { entry ->
      listOf(utcSeconds(entry.time), entry.method, entry.host, entry.pathTemplate, entry.outcome, "${entry.durationMs} ms")
        .joinToString("  ")
    }
    return "$header\n\nRequests, newest first (${entries.size}):\n" + lines.joinToString("\n")
  }

  fun crashText(crash: String): String = "$header\n\n$crash"

  private val header: String get() = listOf(
    "Tessera ${environment.version}",
    "Platform: ${environment.platform}",
    "OS: ${environment.os}",
    "Device: ${environment.device}",
  ).joinToString("\n")

  fun bugReportUrl(): String = URLBuilder(Url("$PUBLIC_REPO_URL/issues/new")).apply {
    parameters.append("template", BUG_FORM)
    parameters.append("version", environment.version)
    if (environment.platform in FORM_PLATFORMS) parameters.append("platform", environment.platform)
    parameters.append("os", environment.os)
    parameters.append("device", environment.device)
  }.buildString()

  private companion object {
    const val BUG_FORM = "bug.yml"

    val FORM_PLATFORMS = setOf("Android", "macOS")

    fun utcSeconds(time: Instant): String = Instant.fromEpochSeconds(time.epochSeconds).toString()
  }
}

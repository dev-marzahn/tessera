package dev.marzahn.sankaku.update

import dev.marzahn.sankaku.APP_VERSION
import dev.marzahn.sankaku.PUBLIC_REPO
import dev.marzahn.sankaku.PUBLIC_REPO_URL
import dev.marzahn.sankaku.data.TextFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

const val GITHUB_API_BASE = "https://api.github.com"

const val LATEST_RELEASE_URL = "$GITHUB_API_BASE/repos/$PUBLIC_REPO/releases/latest"

val UPDATE_CHECK_INTERVAL = 1.days

data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
  override fun compareTo(other: ReleaseVersion): Int =
    compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

  override fun toString(): String = "$major.$minor.$patch"

  companion object {
    private val pattern = Regex("""v?(\d{1,9})\.(\d{1,9})\.(\d{1,9})""")

    fun parse(text: String): ReleaseVersion? = pattern.matchEntire(text.trim())?.destructured?.let { (a, b, c) ->
      ReleaseVersion(a.toInt(), b.toInt(), c.toInt())
    }
  }
}

data class AvailableUpdate(val version: ReleaseVersion, val url: String)

fun createGitHubClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
  expectSuccess = false
  install(HttpTimeout) {
    connectTimeoutMillis = 10_000
    socketTimeoutMillis = 30_000
    requestTimeoutMillis = 30_000
  }
  defaultRequest {
    header(HttpHeaders.UserAgent, "Tessera/$APP_VERSION")
    header(HttpHeaders.Accept, "application/vnd.github+json")
    header("X-GitHub-Api-Version", "2022-11-28")
  }
}

@Serializable
private data class LatestRelease(
  @SerialName("tag_name") val tagName: String,
  @SerialName("html_url") val htmlUrl: String? = null,
)

@Serializable
private data class SavedUpdates(
  val enabled: Boolean = true,
  val lastCheckEpochMs: Long? = null,
  val latestVersion: String? = null,
  val latestUrl: String? = null,
)

class UpdateCheck(
  private val file: TextFile,
  private val client: HttpClient,
  private val clock: Clock,
  private val current: ReleaseVersion? = ReleaseVersion.parse(APP_VERSION),
) {
  private val saved = MutableStateFlow(
    file.read()?.let { runCatching { json.decodeFromString(SavedUpdates.serializer(), it) }.getOrNull() } ?: SavedUpdates(),
  )

  private val _enabled = MutableStateFlow(saved.value.enabled)
  val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

  private val _available = MutableStateFlow(availableIn(saved.value))

  val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

  fun setEnabled(on: Boolean) = change { it.copy(enabled = on) }

  fun start(scope: CoroutineScope, ready: Flow<Boolean>) {
    scope.launch {
      combine(ready, _enabled) { isReady, on -> isReady && on }
        .distinctUntilChanged()
        .filter { it }
        .collect { checkIfDue() }
    }
  }

  suspend fun checkIfDue() {
    if (!_enabled.value) return
    val now = clock.now()
    val last = saved.value.lastCheckEpochMs?.let(Instant::fromEpochMilliseconds)
    if (last != null && last <= now && now - last < UPDATE_CHECK_INTERVAL) return
    val release = try {
      fetchLatest() ?: return
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      return
    }
    change {
      it.copy(
        lastCheckEpochMs = now.toEpochMilliseconds(),
        latestVersion = release.update?.version?.toString(),
        latestUrl = release.update?.url,
      )
    }
  }

  private class Answer(val update: AvailableUpdate?)

  private suspend fun fetchLatest(): Answer? {
    val response = client.get(LATEST_RELEASE_URL)
    return when (response.status.value) {
      200 -> {
        val body = json.decodeFromString(LatestRelease.serializer(), response.bodyAsText())
        Answer(ReleaseVersion.parse(body.tagName)?.let { AvailableUpdate(it, releasePageUrl(body.htmlUrl)) })
      }
      404 -> Answer(null)
      else -> null
    }
  }

  private fun change(transform: (SavedUpdates) -> SavedUpdates) {
    saved.update(transform)
    do {
      val shown = saved.value
      _enabled.value = shown.enabled
      _available.value = availableIn(shown)
      runCatching { file.write(json.encodeToString(SavedUpdates.serializer(), shown)) }
    } while (saved.value !== shown)
  }

  private fun availableIn(state: SavedUpdates): AvailableUpdate? {
    if (!state.enabled || current == null) return null
    val version = state.latestVersion?.let(ReleaseVersion::parse)?.takeIf { it > current } ?: return null
    return AvailableUpdate(version, releasePageUrl(state.latestUrl))
  }

  private companion object {
    val json = Json { ignoreUnknownKeys = true }

    fun releasePageUrl(htmlUrl: String?): String =
      htmlUrl?.takeIf { it.startsWith("$PUBLIC_REPO_URL/releases/") } ?: "$PUBLIC_REPO_URL/releases/latest"
  }
}

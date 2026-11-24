package dev.marzahn.sankaku.api

import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.log.RequestLog
import dev.marzahn.sankaku.net.ConnectivityState
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.replaceResponse
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.TimeSource

class ImageRefusedException(val host: String) : Exception("Image refused by $host")

internal val SankakuJson = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
}

internal const val CONNECT_TIMEOUT_MS = 10_000L
private const val SOCKET_TIMEOUT_MS = 30_000L

internal const val IMAGE_CONNECT_TIMEOUT_MS = 2_500L

internal const val IMAGE_CONNECT_RETRIES = 4

internal const val IMAGE_RETRIES = 2

private fun HttpClientConfig<*>.baseConfig(
  log: RequestLog,
  limiter: RateLimiter,
  connectivity: ConnectivityState,
  fingerprint: BrowserFingerprint,
) {
  expectSuccess = false
  install(HttpTimeout) {
    connectTimeoutMillis = CONNECT_TIMEOUT_MS
    socketTimeoutMillis = SOCKET_TIMEOUT_MS
  }
  install(ContentNegotiation) { json(SankakuJson) }
  install(fingerprintPlugin(fingerprint))
  install(rateLimiterPlugin(limiter))
  install(responseHook(log, connectivity))
}

private fun fingerprintPlugin(fingerprint: BrowserFingerprint): ClientPlugin<Unit> = createClientPlugin("Fingerprint") {
  onRequest { request, _ ->
    val about = request.attributes.getOrNull(AboutId)
    val claimed = fingerprint.forNumericIds?.takeIf { aboutNumericId(request.url.build(), about) } ?: fingerprint
    request.headers[HttpHeaders.UserAgent] = claimed.userAgent
    claimed.clientHints.forEach { (name, value) -> request.headers[name] = value }
  }
}

private fun responseHook(log: RequestLog, connectivity: ConnectivityState): ClientPlugin<Unit> =
  createClientPlugin("ResponseHook") {
    on(Send) { request ->
      val host = request.url.host
      val method = request.method.value
      val path = request.url.encodedPath
      val start = TimeSource.Monotonic.markNow()
      try {
        val call = proceed(request)
        val status = call.response.status.value
        log.record(method, host, path, status, start.elapsedNow().inWholeMilliseconds, null)
        connectivity.setOffline(false)
        if (host == API_HOST) {
          val isJson = call.response.contentType()?.match(ContentType.Application.Json) == true
          when {
            status == 403 || (!isJson && status != 204) -> connectivity.setRejected(true)
            isJson && status in 200..299 -> connectivity.setRejected(false)
          }
        }
        call
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        log.record(method, host, path, null, start.elapsedNow().inWholeMilliseconds, e::class.simpleName)
        if (e is IOException) connectivity.setOffline(true)
        throw e
      }
    }
  }

private val ImageRefusal: ClientPlugin<Unit> = createClientPlugin("ImageRefusal") {
  on(Send) { request ->
    val host = request.url.host
    val call = proceed(request)
    if (host in IMAGE_HOSTS) {
      val location = call.response.headers[HttpHeaders.Location]
      val target = if (call.response.status.value in 300..399 && location != null) {
        runCatching { Url(location) }.getOrNull()
      } else {
        call.request.url
      }
      if (target == null || target.parameters["e"] == null) throw ImageRefusedException(host)
    }
    call
  }
}

private val ImageConnectTimeout: ClientPlugin<Unit> = createClientPlugin("ImageConnectTimeout") {
  onRequest { request, _ ->
    if (request.url.host in IMAGE_HOSTS) request.timeout { connectTimeoutMillis = IMAGE_CONNECT_TIMEOUT_MS }
  }
}

private val ApiHostOnlyAuth: ClientPlugin<Unit> = createClientPlugin("ApiHostOnlyAuth") {
  onRequest { request, _ ->
    if (request.url.host != API_HOST) request.attributes.put(AuthCircuitBreaker, Unit)
  }
}

private val ApiUnauthorizedHeader: ClientPlugin<Unit> = createClientPlugin("ApiUnauthorizedHeader") {
  on(Send) { request ->
    val call = proceed(request)
    val headers = call.response.headers
    if (request.url.host != API_HOST || call.response.status != HttpStatusCode.Unauthorized ||
      HttpHeaders.WWWAuthenticate !in headers
    ) {
      return@on call
    }
    val body = call.response.readRawBytes()
    call.replaceResponse(
      headers = Headers.build {
        headers.forEach { name, values -> if (!name.equals(HttpHeaders.WWWAuthenticate, ignoreCase = true)) appendAll(name, values) }
      },
      content = { ByteReadChannel(body) },
    )
  }
}

fun createAuthClient(
  engine: HttpClientEngine,
  log: RequestLog,
  limiter: RateLimiter,
  connectivity: ConnectivityState,
  fingerprint: BrowserFingerprint = BrowserFingerprint.Current,
): HttpClient = HttpClient(engine) { baseConfig(log, limiter, connectivity, fingerprint) }

fun createMainClient(
  engine: HttpClientEngine,
  log: RequestLog,
  limiter: RateLimiter,
  connectivity: ConnectivityState,
  authSession: AuthSession,
  fingerprint: BrowserFingerprint = BrowserFingerprint.Current,
): HttpClient {
  val client = HttpClient(engine) {
    baseConfig(log, limiter, connectivity, fingerprint)
    install(ImageRefusal)
    install(ImageConnectTimeout)
    install(HttpRequestRetry) {
      noRetry()
      retryOnExceptionIf(maxRetries = IMAGE_CONNECT_RETRIES) { request, cause ->
        request.url.host in IMAGE_HOSTS && cause is IOException && (cause is ConnectTimeoutException || retryCount <= IMAGE_RETRIES)
      }
      exponentialDelay(baseDelayMs = 250, maxDelayMs = 2_000, randomizationMs = 250)
    }
    install(ApiHostOnlyAuth)
    install(Auth) {
      bearer {
        loadTokens { authSession.currentBearerTokens() }
        refreshTokens {
          if (response.call.request.url.host != API_HOST) null else authSession.refresh(oldTokens)
        }
        sendWithoutRequest { it.url.host == API_HOST }
      }
    }
    install(ApiUnauthorizedHeader)
  }
  authSession.onTokensChanged = { client.authProvider<BearerAuthProvider>()?.clearToken() }
  return client
}

data class RawResponse(
  val status: Int,
  val json: JsonElement?,
  val hint: String,
  val finalUrl: String,
  val headerNames: Set<String>,
  val headers: Map<String, String> = emptyMap(),
)

suspend fun HttpClient.probe(
  method: HttpMethod,
  base: String,
  path: String,
  headerSet: HeaderSet = HeaderSet.NONE,
  extraHeaders: Map<String, String> = emptyMap(),
  bearer: String? = null,
  query: Map<String, String> = emptyMap(),
  body: JsonObject? = null,
): RawResponse {
  val response = request(if (path.startsWith("http")) path else base + path) {
    this.method = method
    query.forEach { (key, value) -> parameter(key, value) }
    headerSet.headers.forEach { (key, value) -> header(key, value) }
    extraHeaders.forEach { (key, value) -> header(key, value) }
    bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    body?.let {
      contentType(ContentType.Application.Json)
      setBody(it)
    }
  }
  val json = response.jsonOrNull()
  return RawResponse(
    status = response.status.value,
    json = json,
    hint = if (response.status.value >= 400) errorHint(json) else "",
    finalUrl = response.call.request.url.toString(),
    headerNames = response.headers.names().map { it.lowercase() }.toSet(),
    headers = response.headers.names().associate { it.lowercase() to response.headers[it].orEmpty() },
  )
}

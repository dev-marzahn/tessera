package dev.marzahn.sankaku.api

import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
  class Network(cause: Throwable) : ApiError("Can't reach Sankaku", cause) {
    val timedOut: Boolean get() = cause is HttpRequestTimeoutException || cause is SocketTimeoutException
  }

  class Unauthorized : ApiError("Unauthorized")

  class RateLimited(val retryAfterSeconds: Long) : ApiError("Rate-limited, retry in $retryAfterSeconds s")

  class Rejected(val status: Int) : ApiError("Sankaku rejected this client ($status)")

  class TagLimit(val limit: Int?) : ApiError("Search tag limit${limit?.let { " ($it)" } ?: ""}")

  class SearchTimeout : ApiError("Sankaku took too long to search")

  class Http(val status: Int, val hint: String) : ApiError("HTTP $status${if (hint.isEmpty()) "" else ": $hint"}")

  class Decoding(cause: Throwable) : ApiError("Unexpected response", cause)
}

fun errorHint(json: JsonElement?): String {
  val obj = json as? JsonObject ?: return ""
  return listOf("code", "error", "message", "errors")
    .mapNotNull { key -> obj[key]?.let { "$key=${it.toString().take(120)}" } }
    .joinToString(", ")
}

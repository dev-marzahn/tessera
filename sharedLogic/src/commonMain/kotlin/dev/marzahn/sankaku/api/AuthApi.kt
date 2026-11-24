package dev.marzahn.sankaku.api

import dev.marzahn.sankaku.api.dto.TokenResponse
import dev.marzahn.sankaku.api.dto.UserSummary
import dev.marzahn.sankaku.auth.StoredTokens
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class TokenPair(
  val accessToken: String,
  val refreshToken: String,
  val accessExpiresAt: Instant,
  val refreshExpiresAt: Instant,
  val user: UserSummary,
) {
  fun toStored() = StoredTokens(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt, user)
}

sealed interface LoginResult {
  data class Token(val loginToken: String) : LoginResult

  data object WrongCredentials : LoginResult

  data object TwoFactorRequired : LoginResult
}

sealed interface RefreshResult {
  data class Ok(val pair: TokenPair) : RefreshResult

  data object Rejected : RefreshResult

  data class Transient(val error: Throwable) : RefreshResult
}

class AuthApi(private val client: HttpClient, private val clock: Clock = Clock.System) {
  suspend fun passwordLogin(login: String, password: String): LoginResult {
    val body = buildJsonObject {
      put("login", login)
      putJsonObject("mfaParams") { put("login", login) }
      put("password", password)
    }
    val response = client.sendApiRequest(HttpMethod.Post, LOGIN_BASE, "/auth/token", HeaderSet.NONE, emptyMap(), body)
    if (!response.status.isSuccess()) {
      if (isLoginRejection(response.status, response.jsonOrNull())) return LoginResult.WrongCredentials
      if (response.status == HttpStatusCode.TooManyRequests) throw rateLimited(response)
      throw ApiError.Http(response.status.value, hint = "")
    }
    val json = response.jsonOrNull() as? JsonObject
      ?: throw ApiError.Decoding(IllegalStateException("login response isn't a JSON object"))
    if (json.isUnsuccessful()) return LoginResult.WrongCredentials
    json.string("access_token")?.let { return LoginResult.Token(it) }
    if (json.keys.any { it.startsWith("mfa") }) return LoginResult.TwoFactorRequired
    throw ApiError.Decoding(IllegalStateException("login response has no access_token"))
  }

  suspend fun exchange(loginToken: String): TokenPair {
    val body = buildJsonObject {
      put("access_token", loginToken)
      put("client_id", CLIENT_ID)
      put("url", CLIENT_URL)
    }
    val response = client.apiRequest<TokenResponse>(HttpMethod.Post, API_BASE, "/sso/token-exchange", HeaderSet.NONE, body = body)
    return response.toPair() ?: throw ApiError.Decoding(IllegalStateException("exchange returned no token pair"))
  }

  suspend fun refresh(refreshToken: String): RefreshResult {
    val body = buildJsonObject { put("refresh_token", refreshToken) }
    val response = try {
      client.sendApiRequest(HttpMethod.Post, API_BASE, "/auth/token", HeaderSet.NONE, emptyMap(), body)
    } catch (e: ApiError) {
      return RefreshResult.Transient(e)
    }
    if (isRefreshRejection(response.status)) return RefreshResult.Rejected
    if (!response.status.isSuccess() || !response.isJson()) {
      return RefreshResult.Transient(ApiError.Http(response.status.value, errorHint(response.jsonOrNull())))
    }
    val pair = try {
      response.body<TokenResponse>().toPair()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      return RefreshResult.Transient(ApiError.Decoding(e))
    }
    return pair?.let { RefreshResult.Ok(it) }
      ?: RefreshResult.Transient(ApiError.Decoding(IllegalStateException("refresh returned no token pair")))
  }

  private fun TokenResponse.toPair(): TokenPair? {
    val now = clock.now()
    return TokenPair(
      accessToken = accessToken ?: return null,
      refreshToken = refreshToken ?: return null,
      accessExpiresAt = now + (accessTokenTtl ?: return null).seconds,
      refreshExpiresAt = now + (refreshTokenTtl ?: return null).seconds,
      user = currentUser ?: return null,
    )
  }

  companion object {
    internal fun isRefreshRejection(status: HttpStatusCode): Boolean =
      status == HttpStatusCode.Unauthorized

    internal fun isLoginRejection(status: HttpStatusCode, json: JsonElement?): Boolean =
      status == HttpStatusCode.Forbidden && json.isUnsuccessful()

    private fun JsonElement?.isUnsuccessful(): Boolean =
      ((this as? JsonObject)?.get("success") as? JsonPrimitive)?.booleanOrNull == false
  }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

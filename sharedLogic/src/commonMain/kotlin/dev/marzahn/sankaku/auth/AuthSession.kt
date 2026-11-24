package dev.marzahn.sankaku.auth

import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.AuthApi
import dev.marzahn.sankaku.api.LoginResult
import dev.marzahn.sankaku.api.RefreshResult
import dev.marzahn.sankaku.auth.AuthState.SignedOut.Reason
import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

sealed interface SignInResult {
  data object Ok : SignInResult

  data object WrongCredentials : SignInResult

  data object TwoFactorUnsupported : SignInResult

  data class Failed(val error: ApiError) : SignInResult
}

class AuthSession(
  private val authApi: AuthApi,
  private val tokenStore: TokenStore,
  private val clock: Clock = Clock.System,
  private val expireAccessTokenOnRestore: Boolean = false,
  private val expireSessionOnRestore: Boolean = false,
) {
  private val _state = MutableStateFlow<AuthState>(AuthState.Restoring)
  private val _isSignedIn = MutableStateFlow(false)
  private val mutex = Mutex()

  val state: StateFlow<AuthState> = _state.asStateFlow()
  val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

  var onTokensChanged: () -> Unit = {}

  suspend fun restore() {
    val stored = tokenStore.load()
    when {
      stored == null -> setState(AuthState.SignedOut(Reason.None))
      expireSessionOnRestore || stored.refreshExpiresAt <= clock.now() -> {
        tokenStore.clear()
        setState(AuthState.SignedOut(Reason.Expired))
      }
      else -> {
        if (expireAccessTokenOnRestore) tokenStore.save(stored.copy(accessToken = DEBUG_EXPIRED_ACCESS_TOKEN))
        setState(AuthState.SignedIn(stored.user))
      }
    }
  }

  suspend fun signIn(login: String, password: String): SignInResult {
    val pair = try {
      when (val result = authApi.passwordLogin(login, password)) {
        LoginResult.WrongCredentials -> {
          setState(AuthState.SignedOut(Reason.WrongCredentials))
          return SignInResult.WrongCredentials
        }
        LoginResult.TwoFactorRequired -> {
          setState(AuthState.SignedOut(Reason.TwoFactorUnsupported))
          return SignInResult.TwoFactorUnsupported
        }
        is LoginResult.Token -> authApi.exchange(result.loginToken)
      }
    } catch (e: ApiError) {
      return SignInResult.Failed(e)
    }
    mutex.withLock {
      tokenStore.save(pair.toStored())
      onTokensChanged()
      setState(AuthState.SignedIn(pair.user))
    }
    return SignInResult.Ok
  }

  suspend fun signOut() {
    mutex.withLock { signOutLocked(Reason.None) }
  }

  suspend fun currentBearerTokens(): BearerTokens? = tokenStore.load()?.bearer()

  suspend fun refresh(seen: BearerTokens?): BearerTokens? = mutex.withLock {
    val stored = tokenStore.load() ?: return null
    if (seen == null || stored.accessToken != seen.accessToken || stored.refreshToken != seen.refreshToken) {
      return stored.bearer()
    }
    when (val result = authApi.refresh(stored.refreshToken)) {
      is RefreshResult.Ok -> {
        tokenStore.save(result.pair.toStored())
        if (_state.value != AuthState.SignedIn(result.pair.user)) setState(AuthState.SignedIn(result.pair.user))
        result.pair.toStored().bearer()
      }
      RefreshResult.Rejected -> {
        signOutLocked(Reason.Expired)
        null
      }
      is RefreshResult.Transient -> null
    }
  }

  private suspend fun signOutLocked(reason: Reason) {
    tokenStore.clear()
    onTokensChanged()
    setState(AuthState.SignedOut(reason))
  }

  private fun setState(state: AuthState) {
    _state.value = state
    _isSignedIn.value = state is AuthState.SignedIn
  }

  private fun StoredTokens.bearer() = BearerTokens(accessToken, refreshToken)

  companion object {
    internal const val DEBUG_EXPIRED_ACCESS_TOKEN = "expired-by-debug-flag"
  }
}

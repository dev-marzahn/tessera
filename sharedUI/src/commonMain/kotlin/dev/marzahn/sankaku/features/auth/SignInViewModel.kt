package dev.marzahn.sankaku.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.advice
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.SignInResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignInController {
  private val _isOpen = MutableStateFlow(false)
  val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

  private val _reason = MutableStateFlow<String?>(null)

  val reason: StateFlow<String?> = _reason.asStateFlow()

  private var pending: (() -> Unit)? = null

  fun open(reason: String? = null, then: (() -> Unit)? = null) {
    _reason.value = reason
    pending = then
    _isOpen.value = true
  }

  fun close() {
    pending = null
    _isOpen.value = false
    _reason.value = null
  }

  fun succeeded() {
    val action = pending
    close()
    action?.invoke()
  }
}

data class SignInUiState(
  val submitting: Boolean = false,
  val error: String? = null,
  val errorDetail: String? = null,
  val fieldsInError: Boolean = false,
  val rejections: Int = 0,
)

class SignInViewModel(private val session: AuthSession, private val controller: SignInController) : ViewModel() {
  private val _state = MutableStateFlow(SignInUiState())
  val state: StateFlow<SignInUiState> = _state.asStateFlow()
  private var signInJob: Job? = null

  fun submit(login: String, password: String) {
    if (_state.value.submitting || login.isBlank() || password.isEmpty()) return
    _state.update { it.copy(submitting = true, error = null, fieldsInError = false) }
    signInJob = viewModelScope.launch {
      val result = session.signIn(login.trim(), password)
      val rejected = result == SignInResult.WrongCredentials
      _state.update {
        SignInUiState(
          error = messageFor(result),
          errorDetail = detailFor(result),
          fieldsInError = rejected,
          rejections = if (rejected) it.rejections + 1 else 0,
        )
      }
      if (result == SignInResult.Ok) controller.succeeded()
    }
  }

  fun cancel() {
    signInJob?.cancel()
    signInJob = null
    _state.value = SignInUiState()
    controller.close()
  }

  companion object {
    private const val UNAVAILABLE = "Sankaku couldn't sign you in right now. Try again later."

    fun messageFor(result: SignInResult): String? = when (result) {
      SignInResult.Ok -> null
      SignInResult.WrongCredentials -> "Wrong username or password."
      SignInResult.TwoFactorUnsupported ->
        "This account uses two-factor sign-in, which the app doesn't support yet. Browse signed out, or turn " +
          "two-factor sign-in off in your account settings on sankakucomplex.com."
      is SignInResult.Failed -> when (val e = result.error) {
        is ApiError.Network ->
          if (e.timedOut) UNAVAILABLE else "Can't reach Sankaku. Check your connection and try again."
        is ApiError.RateLimited -> "Too many attempts. Try again in ${e.retryAfterSeconds} s."
        is ApiError.Http, is ApiError.Unauthorized -> UNAVAILABLE
        else -> e.advice().sentence
      }
    }

    fun detailFor(result: SignInResult): String? = when (val e = (result as? SignInResult.Failed)?.error) {
      is ApiError.Http -> "HTTP ${e.status}"
      is ApiError.Unauthorized -> "HTTP 401"
      is ApiError.Network -> if (e.timedOut) "Timed out" else null
      else -> null
    }
  }
}

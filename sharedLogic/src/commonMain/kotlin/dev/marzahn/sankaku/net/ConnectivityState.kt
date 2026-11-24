package dev.marzahn.sankaku.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile

class ConnectivityState {
  private val _offline = MutableStateFlow(false)
  private val _rejected = MutableStateFlow(false)
  private val _retryRequests = MutableStateFlow(0)
  private val _unreachable = MutableStateFlow(false)

  val offline: StateFlow<Boolean> = _offline.asStateFlow()

  val rejected: StateFlow<Boolean> = _rejected.asStateFlow()

  val retryRequests: StateFlow<Int> = _retryRequests.asStateFlow()

  val unreachable: StateFlow<Boolean> = _unreachable.asStateFlow()

  @Volatile
  var connectionFailedSinceRetry: Boolean = false
    private set

  fun networkAvailable() {
    if (_unreachable.value) setOffline(false)
    if (connectionFailedSinceRetry) requestRetry()
  }

  fun requestRetry() {
    connectionFailedSinceRetry = false
    _retryRequests.update { it + 1 }
  }

  internal fun setOffline(value: Boolean) {
    if (value) {
      connectionFailedSinceRetry = true
      _unreachable.value = true
      _offline.value = true
    } else {
      _offline.value = false
      _unreachable.value = false
    }
  }

  internal fun setRejected(value: Boolean) {
    _rejected.value = value
  }
}

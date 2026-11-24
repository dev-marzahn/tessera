package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.net.ConnectivityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OutboxSender(
  private val outbox: Outbox,
  private val posts: PostsRepository,
  private val follows: TagFollows,
  private val session: AuthSession,
  private val connectivity: ConnectivityState,
  private val scope: CoroutineScope,
) {
  data class Refusal(val change: PendingChange, val error: ApiError)

  private val _refusals = MutableSharedFlow<Refusal>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val refusals: SharedFlow<Refusal> = _refusals.asSharedFlow()

  private val sending = Mutex()

  fun start() {
    scope.launch {
      session.state.collect { state ->
        if (state is AuthState.Restoring) return@collect
        val user = (state as? AuthState.SignedIn)?.user?.id
        val owner = outbox.owner
        if (owner != null && owner != user) {
          outbox.clear()
          follows.clear()
        }
      }
    }
    scope.launch {
      merge(connectivity.offline.filter { !it }.map { }, connectivity.retryRequests.drop(1).map { }).collect { send() }
    }
  }

  internal suspend fun idle() = sending.withLock {}

  suspend fun send() = sending.withLock {
    if (outbox.changes.value.isEmpty()) return@withLock
    posts.awaitCurrentAccount()
    val user = (session.state.value as? AuthState.SignedIn)?.user?.id ?: return@withLock
    for (change in outbox.changes.value) {
      if (outbox.owner != user) return@withLock
      val error = when (change) {
        is PendingChange.Favourite -> posts.send(change)
        is PendingChange.Vote -> posts.send(change)
        is PendingChange.Follow -> follows.send(change)
      } ?: continue
      if (error.waits()) return@withLock
      _refusals.tryEmit(Refusal(change, error))
    }
  }
}

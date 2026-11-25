package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.snapshots.Snapshot
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

internal fun <T> settledSnapshotFlow(settle: Duration, block: () -> T): Flow<T> = flow {
  ReadWatch().use { watch ->
    delay(settle)
    while (true) {
      emit(watch.read(block))
      watch.awaitChange()
      watch.awaitQuiet(settle)
    }
  }
}

private class ReadWatch : AutoCloseable {
  private val applied = Channel<Set<Any>>(Channel.UNLIMITED)

  @Volatile
  private var reads: Set<Any> = emptySet()

  @Volatile
  private var reading = false

  private val observer = Snapshot.registerApplyObserver { changed, _ ->
    if (reading || touchesReads(changed)) applied.trySend(changed.toSet())
  }

  private fun touchesReads(changed: Set<Any>) = changed.any { it in reads }

  fun <T> read(block: () -> T): T {
    val read = presizedFor(reads)
    reading = true
    try {
      val snapshot = Snapshot.takeSnapshot(readObserver = { read.add(it) })
      try {
        return snapshot.enter(block)
      } finally {
        snapshot.dispose()
      }
    } finally {
      reads = read
      reading = false
    }
  }

  suspend fun awaitChange() {
    do {
      val changed = applied.receive()
    } while (!touchesReads(changed))
  }

  suspend fun awaitQuiet(settle: Duration) {
    do {
      val changedAgain = withTimeoutOrNull(settle) { awaitChange() } != null
    } while (changedAgain)
  }

  override fun close() {
    observer.dispose()
  }
}

private fun presizedFor(previous: Set<Any>) = HashSet<Any>(previous.size * 4 / 3 + 16)

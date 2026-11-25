package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal fun <T> settledSnapshotFlow(settle: Duration, snapshotThread: CoroutineContext, block: () -> T): Flow<T> = flow {
  val watch = ReadWatch(snapshotThread)
  var observer: ObserverHandle? = null
  try {
    withContext(snapshotThread) { observer = Snapshot.registerApplyObserver { changed, _ -> watch.onApplied(changed) } }
    delay(settle)
    while (true) {
      emit(watch.read(block))
      watch.awaitChange()
      watch.awaitQuiet(settle)
    }
  } finally {
    observer?.dispose()
  }
}

private class ReadWatch(private val snapshotThread: CoroutineContext) {
  private val applied = Channel<Set<Any>>(Channel.UNLIMITED)

  @Volatile
  private var reads: Set<Any> = emptySet()

  @Volatile
  private var reading = false

  fun onApplied(changed: Set<Any>) {
    if (reading || touchesReads(changed)) applied.trySend(changed.toSet())
  }

  private fun touchesReads(changed: Set<Any>) = changed.any { it in reads }

  suspend fun <T> read(block: () -> T): T {
    val read = presizedFor(reads)
    reading = true
    var base: Snapshot? = null
    try {
      withContext(snapshotThread) { base = Snapshot.global { Snapshot.takeSnapshot() } }
      return readIn(checkNotNull(base), read, block)
    } finally {
      base?.dispose()
      reads = read
      reading = false
    }
  }

  private fun <T> readIn(base: Snapshot, read: MutableSet<Any>, block: () -> T): T {
    val snapshot = base.takeNestedSnapshot(readObserver = { read.add(it) })
    try {
      return snapshot.enter(block)
    } finally {
      snapshot.dispose()
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
}

private fun presizedFor(previous: Set<Any>) = HashSet<Any>(previous.size * 4 / 3 + 16)

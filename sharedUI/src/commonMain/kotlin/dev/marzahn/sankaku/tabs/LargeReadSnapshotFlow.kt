package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.concurrent.Volatile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal fun <T> largeReadSnapshotFlow(block: () -> T): Flow<T> = flow {
  val applied = Channel<Set<Any>>(Channel.UNLIMITED)
  val watch = Watch()
  val observer = Snapshot.registerApplyObserver { changed, _ ->
    if (watch.reading || watch.reads.let { reads -> changed.any { it in reads } }) applied.trySend(changed.toSet())
  }
  try {
    while (true) {
      val reads = HashSet<Any>()
      watch.reading = true
      val value = try {
        val snapshot = Snapshot.takeSnapshot(readObserver = { reads.add(it) })
        try {
          snapshot.enter(block)
        } finally {
          snapshot.dispose()
        }
      } finally {
        watch.reads = reads
        watch.reading = false
      }
      emit(value)
      while (applied.receive().none { it in reads }) Unit
    }
  } finally {
    observer.dispose()
  }
}

private class Watch {
  @Volatile
  var reads: Set<Any> = emptySet()

  @Volatile
  var reading = false
}

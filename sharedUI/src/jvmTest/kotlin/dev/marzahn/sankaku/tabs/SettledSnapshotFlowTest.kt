package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class SettledSnapshotFlowTest {
  @Test
  fun itFollowsWhatTheBlockReadAndNothingElse() = runTest {
    val read = mutableIntStateOf(1)
    val other = mutableIntStateOf(0)
    val values = mutableListOf<Int>()
    backgroundScope.launch {
      onTheTestThread(settle = Duration.ZERO) { read.intValue }.collect { values += it }
    }
    runCurrent()
    assertEquals(listOf(1), values)

    change { other.intValue = 5 }
    assertEquals(listOf(1), values)

    change { read.intValue = 2 }
    assertEquals(listOf(1, 2), values)
  }

  @Test
  fun aStateReadOnlyAfterAChangeIsFollowed() = runTest {
    val gate = mutableIntStateOf(0)
    val late = mutableIntStateOf(10)
    val values = mutableListOf<Int>()
    backgroundScope.launch {
      onTheTestThread(settle = Duration.ZERO) { if (gate.intValue == 0) -1 else late.intValue }.collect { values += it }
    }
    runCurrent()

    change { gate.intValue = 1 }
    change { late.intValue = 11 }

    assertEquals(listOf(-1, 10, 11), values)
  }

  @Test
  fun aChangeAppliedWhileTheBlockRunsIsNotLost() = runTest {
    val read = mutableIntStateOf(1)
    val values = mutableListOf<Int>()
    backgroundScope.launch {
      onTheTestThread(settle = Duration.ZERO, block = readThenChangeOnce(read)).collect { values += it }
    }
    runCurrent()

    assertEquals(listOf(1, 2), values)
  }

  @Test
  fun aChangeAppliedWhileTheBlockRunsIsReadOnceItSettles() = runTest {
    val read = mutableIntStateOf(1)
    val values = mutableListOf<Int>()
    backgroundScope.launch {
      onTheTestThread(settle = SETTLE, block = readThenChangeOnce(read)).collect { values += it }
    }
    advanceTimeBy(SETTLE + 1.milliseconds)
    assertEquals(listOf(1), values)

    advanceTimeBy(SETTLE)

    assertEquals(listOf(1, 2), values)
  }

  @Test
  fun theFirstReadWaitsForTheSettle() = runTest {
    val read = mutableIntStateOf(1)
    val values = mutableListOf<Int>()
    backgroundScope.launch {
      onTheTestThread(settle = SETTLE) { read.intValue }.collect { values += it }
    }

    advanceTimeBy(SETTLE - 1.milliseconds)
    assertEquals(emptyList(), values)

    advanceTimeBy(2.milliseconds)
    assertEquals(listOf(1), values)
  }

  @Test
  fun aBurstOfChangesIsReadOnceItSettles() = runTest {
    val read = mutableIntStateOf(0)
    var blockRuns = 0
    val values = mutableListOf<Int>()
    backgroundScope.launch {
      onTheTestThread(settle = SETTLE) {
        blockRuns++
        read.intValue
      }.collect { values += it }
    }
    advanceTimeBy(SETTLE + 1.milliseconds)

    repeat(5) {
      change { read.intValue = it + 1 }
      advanceTimeBy(SETTLE / 2)
    }
    assertEquals(1, blockRuns)

    advanceTimeBy(SETTLE / 2 + 1.milliseconds)
    assertEquals(2, blockRuns)
    assertEquals(listOf(0, 5), values)
  }

  @Test
  fun aChangeToAStateTheBlockDidNotReadDoesNotHoldTheSettle() = runTest {
    val read = mutableIntStateOf(0)
    val other = mutableIntStateOf(0)
    val values = mutableListOf<Int>()
    backgroundScope.launch {
      onTheTestThread(settle = SETTLE) { read.intValue }.collect { values += it }
    }
    advanceTimeBy(SETTLE + 1.milliseconds)

    change { read.intValue = 1 }
    advanceTimeBy(SETTLE / 2)
    change { other.intValue = 1 }
    advanceTimeBy(SETTLE / 2 + 1.milliseconds)

    assertEquals(listOf(0, 1), values)
  }

  @Test
  fun aWriteAnotherThreadHasNotAppliedIsNeverAppliedOnTheReader() = withSnapshotThread { snapshots ->
    val read = mutableIntStateOf(0)
    val derived = derivedStateOf { read.intValue }
    val unrelated = mutableIntStateOf(0)
    val appliedOn = Collections.synchronizedSet(HashSet<Thread>())
    val observer = Snapshot.registerApplyObserver { changed, _ -> if (unrelated in changed) appliedOn += Thread.currentThread() }
    val reader = ReaderWithAPendingWrite { onAnotherThread { unrelated.intValue++ } }
    try {
      withContext(reader) { settledSnapshotFlow(settle = Duration.ZERO, snapshots) { derived.value }.first() }

      assertFalse(reader.thread in appliedOn, "no apply observer ran on the reader's thread")
    } finally {
      observer.dispose()
      reader.close()
      Snapshot.sendApplyNotifications()
    }
  }

  @Test
  fun aWriteMadeDuringAnApplyNotificationIsRead() = withReaderAndSnapshotThreads { reader, snapshots ->
    val read = mutableIntStateOf(0)
    val firstRead = CountDownLatch(1)
    val secondWritten = CountDownLatch(1)
    val finalRead = launch(reader) {
      settledSnapshotFlow(settle = Duration.ZERO, snapshots) {
        read.intValue.also { firstRead.countDown() }
      }.first { it == 2 }
    }
    assertTrue(firstRead.await(5, TimeUnit.SECONDS), "the first read ran")
    val secondWriteFinished = AtomicBoolean(false)
    val observer = Snapshot.registerApplyObserver { changed, _ ->
      if (read in changed && read.intValue == 1) {
        onAnotherThreadAsync(secondWritten) { change(read, 2) }
        secondWriteFinished.set(secondWritten.await(5, TimeUnit.SECONDS))
      }
    }
    try {
      onAnotherThread { change(read, 1) }

      withTimeout(5_000) { finalRead.join() }
      assertTrue(secondWriteFinished.get(), "the second write finished during the first apply notification")
    } finally {
      observer.dispose()
    }
  }

  private fun <T> onTheTestThread(settle: Duration, block: () -> T): Flow<T> =
    settledSnapshotFlow(settle, snapshotThread = EmptyCoroutineContext, block)

  private fun TestScope.change(write: () -> Unit) {
    write()
    Snapshot.sendApplyNotifications()
    runCurrent()
  }

  private fun change(state: MutableIntState, value: Int) {
    state.intValue = value
    Snapshot.sendApplyNotifications()
  }

  private fun withSnapshotThread(test: suspend CoroutineScope.(snapshots: CoroutineDispatcher) -> Unit) = runBlocking {
    val snapshots = newSingleThreadContext("snapshots")
    try {
      test(snapshots)
    } finally {
      snapshots.close()
    }
  }

  private fun withReaderAndSnapshotThreads(
    test: suspend CoroutineScope.(reader: CoroutineDispatcher, snapshots: CoroutineDispatcher) -> Unit,
  ) = withSnapshotThread { snapshots ->
    val reader = newSingleThreadContext("reader")
    try {
      test(reader, snapshots)
    } finally {
      reader.close()
    }
  }

  private class ReaderWithAPendingWrite(private val writeElsewhere: () -> Unit) : CoroutineDispatcher(), AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()

    val thread: Thread = executor.submit<Thread> { Thread.currentThread() }.get()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      executor.execute {
        writeElsewhere()
        block.run()
      }
    }

    override fun close() = executor.shutdown()
  }

  private fun onAnotherThread(work: () -> Unit) {
    val thread = Thread(work)
    thread.start()
    thread.join()
  }

  private fun onAnotherThreadAsync(done: CountDownLatch, work: () -> Unit) {
    Thread {
      work()
      done.countDown()
    }.start()
  }

  private fun readThenChangeOnce(state: MutableIntState): () -> Int {
    var changed = false
    return {
      val value = state.intValue
      if (!changed) {
        changed = true
        onAnotherThread { change(state, value + 1) }
      }
      value
    }
  }

  private companion object {
    val SETTLE = 1000.milliseconds
  }
}

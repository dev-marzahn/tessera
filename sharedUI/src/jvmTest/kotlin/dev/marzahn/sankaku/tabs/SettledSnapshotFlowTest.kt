package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SettledSnapshotFlowTest {
  @Test
  fun itFollowsWhatTheBlockReadAndNothingElse() = runTest {
    val read = mutableIntStateOf(1)
    val other = mutableIntStateOf(0)
    val values = mutableListOf<Int>()
    backgroundScope.launch {
      settledSnapshotFlow(settle = Duration.ZERO) { read.intValue }.collect { values += it }
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
      settledSnapshotFlow(settle = Duration.ZERO) { if (gate.intValue == 0) -1 else late.intValue }.collect { values += it }
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
      settledSnapshotFlow(settle = Duration.ZERO, block = readThenChangeOnce(read)).collect { values += it }
    }
    runCurrent()

    assertEquals(listOf(1, 2), values)
  }

  @Test
  fun aChangeAppliedWhileTheBlockRunsIsReadOnceItSettles() = runTest {
    val read = mutableIntStateOf(1)
    val values = mutableListOf<Int>()
    backgroundScope.launch {
      settledSnapshotFlow(settle = SETTLE, block = readThenChangeOnce(read)).collect { values += it }
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
      settledSnapshotFlow(settle = SETTLE) { read.intValue }.collect { values += it }
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
      settledSnapshotFlow(settle = SETTLE) {
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
      settledSnapshotFlow(settle = SETTLE) { read.intValue }.collect { values += it }
    }
    advanceTimeBy(SETTLE + 1.milliseconds)

    change { read.intValue = 1 }
    advanceTimeBy(SETTLE / 2)
    change { other.intValue = 1 }
    advanceTimeBy(SETTLE / 2 + 1.milliseconds)

    assertEquals(listOf(0, 1), values)
  }

  private fun TestScope.change(write: () -> Unit) {
    write()
    Snapshot.sendApplyNotifications()
    runCurrent()
  }

  private fun readThenChangeOnce(state: MutableIntState): () -> Int {
    var changed = false
    return {
      val value = state.intValue
      if (!changed) {
        changed = true
        changeFromAnotherThread(state, value + 1)
      }
      value
    }
  }

  private fun changeFromAnotherThread(state: MutableIntState, value: Int) {
    val writer = Thread {
      state.intValue = value
      Snapshot.sendApplyNotifications()
    }
    writer.start()
    writer.join()
  }

  private companion object {
    val SETTLE = 1000.milliseconds
  }
}

package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LargeReadSnapshotFlowTest {
  @Test
  fun itFollowsWhatTheBlockReadAndNothingElse() = runTest {
    val read = mutableIntStateOf(1)
    val other = mutableIntStateOf(0)
    val values = mutableListOf<Int>()
    backgroundScope.launch { largeReadSnapshotFlow { read.intValue }.collect { values += it } }
    runCurrent()
    assertEquals(listOf(1), values)

    other.intValue = 5
    Snapshot.sendApplyNotifications()
    runCurrent()
    assertEquals(listOf(1), values)

    read.intValue = 2
    Snapshot.sendApplyNotifications()
    runCurrent()
    assertEquals(listOf(1, 2), values)
  }

  @Test
  fun aStateReadOnlyAfterAChangeIsFollowed() = runTest {
    val gate = mutableIntStateOf(0)
    val late = mutableIntStateOf(10)
    val values = mutableListOf<Int>()
    backgroundScope.launch { largeReadSnapshotFlow { if (gate.intValue == 0) -1 else late.intValue }.collect { values += it } }
    runCurrent()

    gate.intValue = 1
    Snapshot.sendApplyNotifications()
    runCurrent()
    late.intValue = 11
    Snapshot.sendApplyNotifications()
    runCurrent()

    assertEquals(listOf(-1, 10, 11), values)
  }

  @Test
  fun aChangeAppliedWhileTheBlockRunsIsNotLost() = runTest {
    val read = mutableIntStateOf(1)
    val values = mutableListOf<Int>()
    var first = true
    backgroundScope.launch {
      largeReadSnapshotFlow {
        val value = read.intValue
        if (first) {
          first = false
          Thread { read.intValue = 2; Snapshot.sendApplyNotifications() }.apply { start(); join() }
        }
        value
      }.collect { values += it }
    }
    runCurrent()

    assertEquals(listOf(1, 2), values)
  }
}

package dev.marzahn.sankaku.ui

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class UserGesturesTest {
  private val gestures = UserGestures()

  @Test
  fun aGestureBegunBeforeDoesntEndItAndTheNextOneDoes() = runTest {
    gestures.press()
    val ended = launch(start = CoroutineStart.UNDISPATCHED) { gestures.awaitNext() }
    gestures.release()
    Snapshot.sendApplyNotifications()
    runCurrent()
    assertTrue(ended.isActive)

    gestures.press()
    Snapshot.sendApplyNotifications()
    runCurrent()
    assertTrue(ended.isActive)
    gestures.release()
    Snapshot.sendApplyNotifications()
    runCurrent()

    assertTrue(ended.isCompleted)
  }
}

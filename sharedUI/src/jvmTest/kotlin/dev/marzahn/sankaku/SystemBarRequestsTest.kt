package dev.marzahn.sankaku

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemBarRequestsTest {

  private data class Look(val name: String, val lightIcons: Boolean, val appLight: Boolean = true)

  private class FakeWindow {
    val log = mutableListOf<String>()
    var lightIcons: Boolean? = null
    val requests = SystemBarRequests<Look>(
      apply = { log += "apply ${it.name}"; lightIcons = it.lightIcons },
      restore = { log += "restore app"; lightIcons = !it.appLight },
    )
  }

  @Test
  fun aNewRequestThenTheOldOneReleasedLeavesTheNewOneApplied() {
    val window = FakeWindow()
    window.requests.request("old", Look("old", lightIcons = true))
    window.requests.request("new", Look("new", lightIcons = true))
    window.requests.release("old")
    assertEquals(listOf("apply old", "apply new"), window.log)
    assertEquals(true, window.lightIcons)
  }

  @Test
  fun anOlderRequestThatChangesStaysUnderTheNewest() {
    val window = FakeWindow()
    window.requests.request("old", Look("old", lightIcons = true))
    window.requests.request("new", Look("new", lightIcons = true))
    window.requests.request("old", Look("old leaving", lightIcons = false))
    assertEquals(listOf("apply old", "apply new"), window.log)
  }

  @Test
  fun releasingTheNewestAppliesTheOneUnderIt() {
    val window = FakeWindow()
    window.requests.request("old", Look("old", lightIcons = true))
    window.requests.request("new", Look("new", lightIcons = false))
    window.requests.release("new")
    assertEquals(listOf("apply old", "apply new", "apply old"), window.log)
    assertEquals(true, window.lightIcons)
  }

  @Test
  fun releasingTheLastRestoresTheAppsOwnIconsInBothThemes() {
    for (appLight in listOf(true, false)) {
      val window = FakeWindow()
      window.requests.request("viewer", Look("viewer", lightIcons = true, appLight = appLight))
      window.requests.release("viewer")
      assertTrue(window.requests.isEmpty)
      assertEquals(listOf("apply viewer", "restore app"), window.log)
      assertEquals(!appLight, window.lightIcons)
    }
  }

  @Test
  fun anUnchangedRequestIsntAppliedAgain() {
    val window = FakeWindow()
    repeat(3) { window.requests.request("viewer", Look("viewer", lightIcons = true)) }
    assertEquals(listOf("apply viewer"), window.log)
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun aViewerLeavingAfterTheNextOneComposedDoesntUndoIt() = runComposeUiTest {
    val window = FakeWindow()
    val viewers = mutableStateListOf("post 1")
    setContent {
      for (viewer in viewers) key(viewer) { SystemBarRequest(window.requests, Look(viewer, lightIcons = true)) }
    }
    waitForIdle()
    viewers += "post 2"
    waitForIdle()
    viewers -= "post 1"
    waitForIdle()
    assertEquals(listOf("apply post 1", "apply post 2"), window.log)
    assertEquals(true, window.lightIcons)

    viewers.clear()
    viewers += "post 3"
    waitForIdle()
    assertEquals(true, window.lightIcons)
    assertEquals("apply post 3", window.log.last())

    viewers.clear()
    waitForIdle()
    assertEquals("restore app", window.log.last())
    assertEquals(false, window.lightIcons)
  }
}

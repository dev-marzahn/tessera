package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SheetStopRestoredTest {
  private val stops = SheetStops(half = 800f, full = 1600f)
  private var sheet: ViewerSheetState? = null

  @Composable
  private fun Viewer(postId: String) {
    val onScreen = remember { Any() }
    key(onScreen) {
      val scope = rememberCoroutineScope()
      sheet = rememberViewerSheetState(scope, snap(), flingThreshold = 1200f, postId = postId)
    }
  }

  @Test
  fun aViewerBackFromAnotherItemHasItsSheetAtTheStopItWasLeftAt() = runComposeUiTest {
    var itemShown by mutableStateOf(true)
    setContent {
      val holder = rememberSaveableStateHolder()
      if (itemShown) holder.SaveableStateProvider("item") { Viewer("1") }
    }
    runOnIdle {
      sheet!!.update(stops)
      sheet!!.animateTo(stops.full)
    }
    runOnIdle { assertEquals(SheetStop.Full, sheet!!.stop) }

    runOnIdle { itemShown = false }
    runOnIdle { itemShown = true }
    runOnIdle { sheet!!.update(stops) }

    runOnIdle {
      assertEquals(SheetStop.Full, sheet!!.stop)
      assertEquals(stops.full, sheet!!.lift)
    }
  }

  @Test
  fun aViewerOpenedAgainOnTheSameItemStartsAtPeek() = runComposeUiTest {
    var viewerShown by mutableStateOf(true)
    setContent {
      val holder = rememberSaveableStateHolder()
      holder.SaveableStateProvider("item") { if (viewerShown) Viewer("1") }
    }
    runOnIdle {
      sheet!!.update(stops)
      sheet!!.animateTo(stops.half)
    }
    runOnIdle { assertEquals(SheetStop.Half, sheet!!.stop) }

    runOnIdle { viewerShown = false }
    runOnIdle { viewerShown = true }
    runOnIdle { sheet!!.update(stops) }

    runOnIdle {
      assertEquals(SheetStop.Peek, sheet!!.stop)
      assertEquals(0f, sheet!!.lift)
    }
  }

  @Test
  fun aStopWhoseViewerDidntComeBackDoesntReachAnotherPost() = runComposeUiTest {
    var itemShown by mutableStateOf(true)
    var viewerOn by mutableStateOf<String?>("1")
    setContent {
      val holder = rememberSaveableStateHolder()
      if (itemShown) holder.SaveableStateProvider("item") { viewerOn?.let { Viewer(it) } }
    }
    runOnIdle {
      sheet!!.update(stops)
      sheet!!.animateTo(stops.full)
    }
    runOnIdle { assertEquals(SheetStop.Full, sheet!!.stop) }

    runOnIdle { itemShown = false }
    runOnIdle {
      viewerOn = null
      itemShown = true
    }
    runOnIdle { viewerOn = "2" }
    runOnIdle { sheet!!.update(stops) }

    runOnIdle { assertEquals(SheetStop.Peek, sheet!!.stop) }
  }

  @Test
  fun anotherItemsViewerStartsAtPeek() = runComposeUiTest {
    var item by mutableStateOf("first")
    setContent {
      val holder = rememberSaveableStateHolder()
      holder.SaveableStateProvider(item) { Viewer("1") }
    }
    runOnIdle {
      sheet!!.update(stops)
      sheet!!.animateTo(stops.half)
    }
    runOnIdle { assertEquals(SheetStop.Half, sheet!!.stop) }

    runOnIdle { item = "second" }
    runOnIdle { sheet!!.update(stops) }

    runOnIdle {
      assertEquals(SheetStop.Peek, sheet!!.stop)
      assertEquals(0f, sheet!!.lift)
    }
  }
}

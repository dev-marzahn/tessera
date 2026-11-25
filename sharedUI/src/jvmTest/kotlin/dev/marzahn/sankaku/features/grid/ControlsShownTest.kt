package dev.marzahn.sankaku.features.grid

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlsShownTest {
  private fun shown(progress: Float = 0.5f, sheetDragged: Boolean = false, closing: Boolean = false, leaving: Boolean = false) =
    controlsShown(progress, sheetDragged, closing, leaving)

  @Test
  fun anOpenAtPeekShowsTheControlsOnlyNearItsEnd() {
    assertFalse(shown(progress = 0.5f))
    assertTrue(shown(progress = 0.95f))
  }

  @Test
  fun aSheetDraggedDuringTheOpenShowsTheControlsAtOnce() {
    assertTrue(shown(progress = 0.2f, sheetDragged = true))
  }

  @Test
  fun closingHidesThemOnceTheProgressFallsWhateverTheSheetDid() {
    assertFalse(shown(progress = 0.5f, sheetDragged = true, closing = true))
    assertTrue(shown(progress = 0.95f, closing = true), "a close only just started still shows them")
  }

  @Test
  fun leavingHidesThemAtOnce() {
    assertFalse(shown(progress = 1f, leaving = true, closing = true))
    assertFalse(shown(progress = 1f, sheetDragged = true, leaving = true, closing = true))
  }
}

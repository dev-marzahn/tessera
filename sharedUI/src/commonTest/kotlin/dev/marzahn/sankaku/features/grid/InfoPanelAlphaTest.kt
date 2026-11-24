package dev.marzahn.sankaku.features.grid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InfoPanelAlphaTest {
  @Test
  fun aPanelAFewPixelsWideIsAlmostInvisible() {
    assertTrue(infoPanelAlpha(0.003f) < 0.05f)
  }

  @Test
  fun theSplitPanelIsOpaque() {
    assertEquals(1f, infoPanelAlpha(0.1f))
    assertEquals(1f, infoPanelAlpha(1f))
    assertEquals(1f, infoPanelAlpha(1.04f))
  }
}

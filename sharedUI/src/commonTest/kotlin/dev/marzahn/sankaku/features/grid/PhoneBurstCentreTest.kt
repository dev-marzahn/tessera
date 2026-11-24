package dev.marzahn.sankaku.features.grid

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneBurstCentreTest {
  @Test
  fun atPeekItsThePicturesCentreClearOfTheInset() {
    assertEquals(1010f, phoneBurstCentre(pictureTop = 200f, sheetTop = 2000f, inset = 180f, minCentre = 300f))
  }

  @Test
  fun itRisesWithTheSheet() {
    assertEquals(610f, phoneBurstCentre(pictureTop = 200f, sheetTop = 1200f, inset = 180f, minCentre = 300f))
  }

  @Test
  fun nearFullItStaysWholeBelowTheStatusBar() {
    assertEquals(300f, phoneBurstCentre(pictureTop = 200f, sheetTop = 0f, inset = 180f, minCentre = 300f))
  }
}

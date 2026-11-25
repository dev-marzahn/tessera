package dev.marzahn.sankaku.features.grid

import kotlin.test.Test
import kotlin.test.assertEquals

class BarSpotTakerTest {
  @Test
  fun theFlyingBarHoldsItsSpotUntilItDocks() {
    assertEquals(BarSpotTaker.Bar, barSpotTaker(closing = false, barDocked = false, barDragging = false))
    assertEquals(BarSpotTaker.Stats, barSpotTaker(closing = false, barDocked = true, barDragging = false))
  }

  @Test
  fun aDragThatStartedOnTheBarKeepsItThroughTheDock() {
    assertEquals(BarSpotTaker.Bar, barSpotTaker(closing = false, barDocked = true, barDragging = true))
  }

  @Test
  fun closingGivesTheSpotToNobody() {
    assertEquals(BarSpotTaker.Nobody, barSpotTaker(closing = true, barDocked = false, barDragging = true))
    assertEquals(BarSpotTaker.Nobody, barSpotTaker(closing = true, barDocked = true, barDragging = false))
  }
}

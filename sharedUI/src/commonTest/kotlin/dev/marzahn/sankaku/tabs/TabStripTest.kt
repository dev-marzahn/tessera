package dev.marzahn.sankaku.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabStripTest {
  @Test
  fun aTabCutByAFewPixelsAtTheEdgeStillCountsAsShown() {
    assertFalse(mostlyHidden(0..99, scrolled = 4, viewport = 300))
    assertFalse(mostlyHidden(204..303, scrolled = 0, viewport = 300))
  }

  @Test
  fun hiddenTabsCountOnTheSideTheyAreOn() {
    val spans = (0 until 5).map { it * 100 until it * 100 + 100 }
    assertEquals(2 to 0, hiddenCounts(spans, scrolled = 250, viewport = 250))
    assertEquals(0 to 2, hiddenCounts(spans, scrolled = 0, viewport = 250))
  }

  @Test
  fun twinsInARowAreNumberedByTheirPlaceInTheRun() {
    assertEquals(
      listOf("", " · 2", " · 3", "", "", "", " · 2"),
      twinSuffixes(listOf("Xingtong", "Xingtong", "Xingtong", "Rosehip", "Xingtong", "Rosehip", "Rosehip")),
    )
    assertEquals(emptyList(), twinSuffixes(emptyList()))
  }

  @Test
  fun aTabMostlyOrFullyOutOfViewCounts() {
    assertTrue(mostlyHidden(0..99, scrolled = 60, viewport = 300))
    assertTrue(mostlyHidden(400..499, scrolled = 0, viewport = 300))
  }
}

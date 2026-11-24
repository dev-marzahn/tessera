package dev.marzahn.sankaku

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeOnlyTest {
  @Test
  fun theLaunchExtraOrThePropertyTurnsItOn() {
    assertTrue(safeOnlyRequested(extra = true, property = null))
    assertTrue(safeOnlyRequested(extra = false, property = "true\n"))
    assertTrue(safeOnlyRequested(extra = false, property = "TRUE"))
    assertTrue(safeOnlyRequested(extra = true, property = "false"))
  }

  @Test
  fun neitherLeavesItOff() {
    assertFalse(safeOnlyRequested(extra = false, property = null))
    assertFalse(safeOnlyRequested(extra = false, property = "\n"))
    assertFalse(safeOnlyRequested(extra = false, property = "false"))
  }
}

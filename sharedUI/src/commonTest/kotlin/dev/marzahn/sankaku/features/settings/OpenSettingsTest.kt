package dev.marzahn.sankaku.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenSettingsTest {
  @Test
  fun theSectionsAreInTheSpecsOrderAndTheSlotsShowOnceFilled() {
    assertEquals(
      listOf("Account", "Privacy", "Queue", "Grid", "Videos", "Accessibility", "Storage", "Updates", "Diagnostics", "About"),
      shownSections(touch = true).map { it.title },
    )
    assertEquals(
      listOf("Account", "Privacy", "Queue", "Grid", "Videos", "Keyboard", "Storage", "Updates", "Diagnostics", "About"),
      shownSections(touch = false).map { it.title },
    )
    assertEquals(
      listOf("Account", "Privacy", "Queue", "Grid", "Videos", "Keyboard", "Storage", "Updates", "Diagnostics", "About"),
      shownSections(touch = false, updates = true, diagnostics = true, about = true).map { it.title },
    )
  }
}

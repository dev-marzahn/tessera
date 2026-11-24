package dev.marzahn.sankaku.features.grid

import dev.marzahn.sankaku.api.dto.TagSuggestion
import kotlin.test.Test
import kotlin.test.assertEquals

class OfferedSuggestionsTest {
  private val found = listOf(
    TagSuggestion("scenery", rating = "s"),
    TagSuggestion("imagination_scenery", rating = "e"),
    TagSuggestion("scenery_censor", rating = "q"),
    TagSuggestion("unrated_scenery"),
    TagSuggestion("-scenery", rating = "s"),
  )

  @Test
  fun gOnlyLeavesOutR15AndR18Tags() {
    assertEquals(listOf("scenery", "unrated_scenery", "-scenery"), offeredSuggestions(found, gOnly = true).map { it.tagName })
  }

  @Test
  fun withGOnlyOffEverythingIsOffered() {
    assertEquals(found, offeredSuggestions(found, gOnly = false))
  }
}

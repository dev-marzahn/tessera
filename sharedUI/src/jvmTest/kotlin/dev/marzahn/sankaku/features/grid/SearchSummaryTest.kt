package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SearchSummaryTest {
  @Test
  fun aLineTooLongForTheBarShowsItsContentTagsAndCountsTheFiltersBeforeThem() = runComposeUiTest {
    setContent {
      SearchSummary(
        tags = listOf("order:popularity", "threshold:3", "feed:following", "some_long_content_tag", "last_tag"),
        ratings = emptySet(),
        known = emptyMap(),
        onOpen = {},
        modifier = Modifier.width(360.dp),
      )
    }

    onNodeWithText("last_tag", substring = true, useUnmergedTree = true).assertIsDisplayed()
    onNodeWithText("Popularity", useUnmergedTree = true).assertIsNotDisplayed()
    onNodeWithText("+", substring = true, useUnmergedTree = true).assertIsDisplayed()
  }
}

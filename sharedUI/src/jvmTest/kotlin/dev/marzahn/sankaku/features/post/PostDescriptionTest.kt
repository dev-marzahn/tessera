package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.tabs.DescribeTab
import dev.marzahn.sankaku.tabs.LocalTabEntry
import dev.marzahn.sankaku.tabs.TabEntry
import dev.marzahn.sankaku.tabs.TabId
import dev.marzahn.sankaku.tabs.TabState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PostDescriptionTest {
  private val route = Route.Post("1", md5 = null, originTags = emptyList(), label = "1", rating = "s")
  private val tab = TabState(TabId("t"), listOf(route))

  @Test
  fun aPostLeftBeforeItLoadedShowsItsPictureOnceItLoads() = runComposeUiTest {
    val state = MutableStateFlow<PostUiState>(PostUiState.Loading)
    val description = postDescription(route, state)
    var shown by mutableStateOf(true)
    setContent {
      if (shown) {
        CompositionLocalProvider(LocalTabEntry provides TabEntry(tab, "post")) { DescribeTab(route) { description } }
      }
    }
    waitForIdle()

    runOnIdle { shown = false }
    waitForIdle()
    state.value = PostUiState.Loaded(PostDto(id = "1", md5 = "loaded", rating = "s", status = "active"))

    assertEquals(listOf("loaded"), tab.topContents()?.preview?.map { it.md5 })
  }
}

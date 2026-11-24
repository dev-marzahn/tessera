package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.Route
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ClosePeekTest {
  private val tabs = TabsController()

  private fun post(id: String) = Route.Post(id, "m$id", listOf("artist_x"), label = "Post $id", rating = "s")

  private fun keptAB(cat: Boolean = true) {
    tabs.open(Route.Grid(listOf("artist_x")), background = false)
    listOf("A", "B").forEach { tabs.openInBlock(listOf(post(it))) }
    if (cat) tabs.open(Route.Grid(listOf("cat")), background = true)
  }

  private fun show(title: String) = tabs.activate(tabs.tabs.value.single { it.title == title }.id)

  private fun peekOver(assertions: ComposeUiTest.() -> Unit) = runComposeUiTest {
    setContent {
      val activeId by tabs.active.collectAsState()
      val tabList by tabs.tabs.collectAsState()
      val active = tabList.first { it.id == activeId }
      Box(Modifier.size(400.dp)) {
        QueueSwipeHost(active, tabs = tabs, upNext = noUpNext(tabs), undo = ClosedTabUndo(tabs), preload = { _, _ -> emptyMap() }, content = {
          LocalQueueClosePeek.current?.let { peek ->
            peek.behind()
            peek.Line()
          }
        })
      }
    }
    assertions()
  }

  @Test
  fun onABlocksLastPostThePeekLeavesItForTheNextSource() {
    keptAB()
    show("Post B")
    peekOver {
      onNodeWithText("Next: Cat", useUnmergedTree = true).assertExists()
      onNodeWithText("Leaving Artist X", useUnmergedTree = true).assertExists()
      onNodeWithText("Cat", useUnmergedTree = true).assertExists()
    }
  }

  @Test
  fun withinTheBlockThePeekIsTheNextKeptPostWithoutLeaving() {
    keptAB()
    show("Post A")
    peekOver {
      onNodeWithText("Next: Post B", useUnmergedTree = true).assertExists()
      onNodeWithText("Leaving", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }
  }

  @Test
  fun onTheLastItemThePeekIsEndOfTheQueue() {
    keptAB(cat = false)
    show("Post B")
    peekOver {
      onNodeWithText("Next: $END_PANE", useUnmergedTree = true).assertExists()
      onNodeWithText("Leaving Artist X", useUnmergedTree = true).assertExists()
    }
  }
}

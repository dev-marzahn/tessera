package dev.marzahn.sankaku.features.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.tabs.TabState
import dev.marzahn.sankaku.tabs.TabsController

@Composable
fun seenMark(
  route: Route.Grid,
  tab: TabState?,
  tabs: TabsController,
  seen: SeenStore,
  keptCopyOf: (PostDto) -> TabState? = { null },
): (PostDto) -> Boolean {
  val revision = seen.revision.collectAsState()
  val all = tabs.tabs.collectAsState()
  val grid = tab?.takeIf { it.top == route }
  val keptState = remember(tabs, grid) {
    derivedStateOf {
      all.value
      grid?.let(tabs::keptFrom).orEmpty()
    }
  }
  val keptHere = keptState.value
  fun seenHere(postId: String) = keptHere[postId]?.any { it.seenSinceKept(seen) } == true
  return { post ->
    revision.value
    seenHere(post.id) || (keptCopyOf(post)?.top as? Route.Post)?.let { seenHere(it.id) } == true
  }
}

package dev.marzahn.sankaku.features.grid

import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.PostTitle
import dev.marzahn.sankaku.existingPosition
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.tabs.ClosedTab
import dev.marzahn.sankaku.tabs.TabState
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.ui.components.KEPT_AGAIN
import dev.marzahn.sankaku.ui.components.alreadySpoken

internal const val KEEP_FOR_LATER = "Keep for later"

internal data class Kept(val position: Int, val already: Boolean, val again: Boolean = false)

internal fun keepPost(
  post: PostDto,
  searchTags: List<String>,
  navigator: Navigator,
  tabs: TabsController,
  seen: SeenStore,
  keptCopyOf: (PostDto) -> TabState? = { null },
): Kept {
  if (tabs.indexOfPost(post.id) == null) {
    val copyId = (keptCopyOf(post)?.top as? Route.Post)?.id
    val index = copyId?.let(tabs::indexOfPost)
    if (copyId != null && index != null) {
      val keptSeen = tabs.keptPostSeen(copyId, seen)
      tabs.keepAgain(tabs.tabs.value[index].id)
      return Kept(existingPosition(tabs.tabs.value, index), already = true, again = keptSeen)
    }
  }
  val keptSeen = tabs.keptPostSeen(post.id, seen)
  val route = Route.Post(post.id, post.md5, searchTags, PostTitle.of(post), post.rating)
  navigator.openInNewTab(route)?.let { return Kept(it, already = true, again = keptSeen) }
  val index = checkNotNull(tabs.indexOfLone(route)) { "a kept post isn't in the queue" }
  return Kept(existingPosition(tabs.tabs.value, index), already = false)
}

internal const val REMOVE_FROM_QUEUE = "Remove from queue"

internal fun keptItemOf(post: PostDto, tabs: List<TabState>, keptCopyOf: (PostDto) -> TabState? = { null }): TabState? {
  fun lone(id: String) = tabs.firstOrNull {
    it.kind == QueueKind.KeptPost && it.backStack.size == 1 && (it.top as? Route.Post)?.id == id
  }
  return lone(post.id) ?: (keptCopyOf(post)?.top as? Route.Post)?.id?.let(::lone)
}

internal fun unkeepPost(post: PostDto, tabs: TabsController, keptCopyOf: (PostDto) -> TabState? = { null }): ClosedTab? =
  keptItemOf(post, tabs.tabs.value, keptCopyOf)?.let { tabs.close(it.id) }
internal fun keptMessage(kept: Kept, touch: Boolean): String = when {
  kept.again -> KEPT_AGAIN
  kept.already -> alreadySpoken(kept.position, touch)
  touch -> "Kept · item ${kept.position}"
  else -> "Kept in tab ${kept.position}"
}

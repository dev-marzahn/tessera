package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.shell.MenuBadge

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.closeRow(title: String) {
  onNodeWithText(title).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
    .first { it.label == "Close" }.action()
  waitForIdle()
}

@Composable
internal fun PhoneQueuePage(
  controller: TabsController,
  onDismiss: () -> Unit = {},
  onMenu: (() -> Unit)? = null,
  menuBadge: MenuBadge = MenuBadge.None,
  posts: TabPosts = TabPosts.None,
  privacy: PrivacySettings = remember { PrivacySettings(InMemoryTextFile()) },
  seen: SeenStore? = null,
  undo: ClosedTabUndo = remember(controller) { ClosedTabUndo(controller) },
  clearViewed: ClearViewed = remember(controller, seen, undo) {
    ClearViewed(controller, seen ?: SeenStore(InMemoryTextFile()), ClearedUndo { undo.opened(it, Closing.Cleared) })
  },
  interacted: () -> Boolean = { false },
  onInteract: () -> Unit = {},
  onListTop: (Dp) -> Unit = {},
  leaving: Boolean = false,
  previews: SourcePreviews? = null,
  today: TodayStore? = null,
  onSort: (() -> Unit)? = null,
  folding: Boolean = true,
  chipMemory: QueueStart? = null,
  following: FollowingNewsSource? = null,
  catchUp: FeedCatchUp? = null,
) = QueuePage(
  onDismiss = onDismiss,
  onMenu = onMenu,
  menuBadge = menuBadge,
  controller = controller,
  posts = posts,
  privacy = privacy,
  seen = seen,
  clearViewed = clearViewed,
  undo = undo,
  interacted = interacted,
  onInteract = onInteract,
  onListTop = onListTop,
  leaving = leaving,
  previews = previews,
  today = today,
  onSort = onSort,
  folding = folding,
  chipMemory = chipMemory,
  following = following,
  catchUp = catchUp,
)

package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages

class ClearViewed(
  private val controller: TabsController,
  private val seen: SeenStore,
  private val messages: Messages,
) {
  data class Status(
    val count: Int,
    val suggested: Boolean,
  )

  fun status() = Status(controller.viewedKeptPosts(seen).size, controller.everyKeptPostViewed(seen))

  operator fun invoke(): Int {
    val cleared = controller.clearViewed(seen) ?: return 0
    messages.show(
      Message("Cleared ${cleared.size}", actionLabel = "Undo", short = true) {
        controller.reopen(cleared)
      },
    )
    return cleared.size
  }
}

internal fun clearViewedLabel(count: Int, touch: Boolean = platformIsTouch) =
  "Clear viewed, $count ${if (touch && count == 1) "kept post" else "kept posts"}"

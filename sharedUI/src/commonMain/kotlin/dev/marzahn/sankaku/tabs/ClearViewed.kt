package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages

class ClearViewed(
  private val controller: TabsController,
  private val seen: SeenStore,
  private val undo: ClearedUndo,
) {
  data class Status(
    val count: Int,
    val suggested: Boolean,
  )

  fun status() = Status(controller.viewedKeptPosts(seen).size, controller.everyKeptPostViewed(seen))

  operator fun invoke(): Int {
    val cleared = controller.clearViewed(seen) ?: return 0
    undo.offer(cleared)
    return cleared.size
  }
}

fun interface ClearedUndo {
  fun offer(cleared: ClosedTab)
}

class ClearedUndoOnTopLine(private val undo: ClosedTabUndo, private val messages: Messages) : ClearedUndo {
  override fun offer(cleared: ClosedTab) =
    messages.show(Message("Cleared ${cleared.size}", actionLabel = "Undo", short = true) { undo.undo(cleared, Closing.Cleared) })
}

internal fun clearViewedLabel(count: Int) = "Clear viewed, ${keptPosts(count)}"

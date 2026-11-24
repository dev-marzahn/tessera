package dev.marzahn.sankaku.features.post

import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.existingPosition
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.tabs.sourceTitle
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages

internal const val OPEN_NOW = "Open now"

internal fun openedForLaterText(name: String, position: Int, from: String?): String =
  listOfNotNull(name, "later, item $position", from?.let { "from $it" }).joinToString(" · ")

internal fun openTagForLater(tag: TagDto, route: Route.Grid, navigator: Navigator, tabs: TabsController, messages: Messages) {
  if (navigator.openInNewTab(route) != null) return
  val all = tabs.tabs.value
  val opened = tabs.exactSourceFor(route) ?: return
  val position = existingPosition(all, all.indexOf(opened))
  val from = opened.foundOn?.let { sourceTitle(it.search, tabs.tagNames) }
  messages.show(Message(openedForLaterText(tag.displayName, position, from), actionLabel = OPEN_NOW) { tabs.search(route) })
}

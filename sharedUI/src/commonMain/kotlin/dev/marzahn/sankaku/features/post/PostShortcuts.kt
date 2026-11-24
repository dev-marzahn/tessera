package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.ui.components.OncePerPress
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.isCommand

fun Modifier.postShortcuts(
  post: () -> PostDto?,
  actions: PostActions,
  onBack: () -> Unit,
  enabled: () -> Boolean = { true },
): Modifier = composed {
  val presses = remember { OncePerPress() }
  onFocusChanged { if (!it.hasFocus) presses.letGo() }.onKeyEvent { event ->
    val newPress = presses.isNewPress(event)
    if (!enabled() || event.type != KeyEventType.KeyDown) return@onKeyEvent false
    if (event.isCommand) return@onKeyEvent false
    when (event.key) {
      Shortcuts.Favourite.key -> true.also { if (newPress) post()?.let(actions::toggleFavourite) }
      Shortcuts.Back.key -> true.also { onBack() }
      else -> Shortcuts.voteFor(event.key)?.let { score ->
        if (newPress) post()?.let { actions.vote(it, if (it.userVote == score) null else score) }
        true
      } ?: false
    }
  }
}

package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import org.koin.mp.KoinPlatform
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.api.dto.TagType
import dev.marzahn.sankaku.features.post.FollowOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FollowInstead(
  private val tabs: TabsController,
  private val scope: CoroutineScope,
  private val account: () -> String?,
  private val lookUp: suspend (String) -> TagDto?,
  private val isFollowing: (TagDto) -> Boolean,
  private val follow: suspend (TagDto) -> FollowOutcome,
  private val unfollow: (TagDto) -> Unit,
  private val knownType: (String) -> TagType? = { null },
) {
  private val tags = mutableStateMapOf<Pair<String, String>, TagDto>()

  private val following = mutableStateSetOf<TabId>()

  fun offer(tab: TabState): TagDto? {
    val account = account() ?: return null
    val key = keyOf(tab) ?: return null
    val tag = tags[account to key] ?: return null
    return tag.takeIf { it.category == TagType.Artist && it.id != null && !isFollowing(it) }
  }

  suspend fun load(tab: TabState) {
    val account = account() ?: return
    val key = keyOf(tab) ?: return
    if ((account to key) in tags) return
    knownType(key)?.let { if (it != TagType.Artist) return }
    val tag = lookUp(key) ?: return
    if (account() == account) tags[account to key] = tag
  }

  fun followAndClose(tab: TabState, close: (undo: () -> Unit) -> Unit): Job? {
    val tag = offer(tab) ?: return null
    if (!following.add(tab.id)) return null
    return scope.launch {
      try {
        val outcome = follow(tag)
        if (outcome == FollowOutcome.Refused) return@launch
        if (tabs.tabs.value.none { it.id == tab.id }) return@launch
        close(if (outcome == FollowOutcome.Followed) ({ unfollow(tag) }) else ({}))
      } finally {
        following.remove(tab.id)
      }
    }
  }

  private fun keyOf(tab: TabState): String? = (tab.top as? Route.Grid)?.let { oneTag(it.tags) }?.lowercase()
}

@Composable
internal fun followInsteadOrNull(): FollowInstead? = remember { KoinPlatform.getKoinOrNull()?.getOrNull<FollowInstead>() }

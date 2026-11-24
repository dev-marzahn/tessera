package dev.marzahn.sankaku.features.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import coil3.Image
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.HashedPost
import dev.marzahn.sankaku.data.PreviewHash
import dev.marzahn.sankaku.data.isReupload
import dev.marzahn.sankaku.images.previewHashOf
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.tabs.TabState
import dev.marzahn.sankaku.tabs.TabsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PreviewHashes(
  private val seen: SeenStore,
  private val tabs: TabsController,
  private val scope: CoroutineScope,
  private val hash: (Image) -> PreviewHash? = ::previewHashOf,
) {
  private val hashes = object : LinkedHashMap<String, MutableState<HashedPost?>>(64) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableState<HashedPost?>>) = size > LIMIT
  }

  fun of(postId: String): State<HashedPost?> = stateOf(postId)

  private fun stateOf(postId: String): MutableState<HashedPost?> = synchronized(hashes) {
    hashes.getOrPut(postId) { mutableStateOf(null) }
  }

  private fun held(postId: String): HashedPost? = synchronized(hashes) { hashes[postId] }?.value

  fun offer(post: PostDto, image: Image) {
    val state = stateOf(post.id)
    if (state.value != null || synchronized(hashless) { post.id in hashless }) return
    scope.launch(Dispatchers.Default) {
      if (state.value != null) return@launch
      val hashed = runCatching { hash(image) }.getOrNull()
      if (hashed == null) {
        synchronized(hashless) {
          if (hashless.size >= LIMIT) hashless.clear()
          hashless += post.id
        }
        return@launch
      }
      put(HashedPost(post.id, hashed, post.parentId, post.createdAt))
    }
  }

  private val hashless = HashSet<String>()

  fun put(post: HashedPost) {
    stateOf(post.id).value = post
    if (tabs.indexOfPost(post.id) != null) seen.recordPicture(post)
  }

  fun recordKept(postId: String) {
    held(postId)?.let(seen::recordPicture)
  }

  fun keptCopyOf(post: HashedPost, kept: List<KeptHash>): TabState? = kept.firstOrNull { isReupload(post, it.hash) }?.tab

  fun keptHashes(items: List<TabState>): List<KeptHash> = items.mapNotNull { tab ->
    val id = (tab.top as? Route.Post)?.id ?: return@mapNotNull null
    (held(id) ?: seen.picture(id))?.let { KeptHash(tab, it) }
  }

  data class KeptHash(val tab: TabState, val hash: HashedPost)

  companion object {
    const val LIMIT: Int = 5_000
  }
}

@Composable
fun keptCopyMark(hashes: PreviewHashes, tabs: TabsController, seen: SeenStore): (PostDto) -> TabState? {
  val all = tabs.tabs.collectAsState()
  val revision = seen.revision.collectAsState()
  val keptState = remember(hashes, all, revision) {
    derivedStateOf {
      revision.value
      hashes.keptHashes(all.value)
    }
  }
  val kept = keptState.value
  return { post -> hashes.of(post.id).value?.let { hashes.keptCopyOf(it, kept) } }
}

package dev.marzahn.sankaku.queue

class Viewing(private val seen: SeenStore) {
  private var current: Shown? = null

  private val loaded = ArrayDeque<String>()

  fun current(postId: String?, md5: String?) {
    if (current?.id == postId) return
    leave()
    current = postId?.let { Shown(it, md5) }
  }

  fun loaded(postId: String) {
    if (postId in loaded) return
    loaded.addLast(postId)
    while (loaded.size > LOADED_KEPT) loaded.removeFirst()
  }

  fun keeping(postId: String) {
    current?.takeIf { it.id == postId }?.let { current = it.copy(keptWhileShown = true) }
  }

  fun leave() {
    val post = current ?: return
    current = null
    if (!post.keptWhileShown && post.id in loaded) seen.record(post.id, post.md5)
  }

  private data class Shown(val id: String, val md5: String?, val keptWhileShown: Boolean = false)

  private companion object {
    const val LOADED_KEPT = 8
  }
}

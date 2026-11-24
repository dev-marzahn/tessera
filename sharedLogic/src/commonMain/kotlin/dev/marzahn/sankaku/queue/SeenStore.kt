package dev.marzahn.sankaku.queue

import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.data.HashedPost
import dev.marzahn.sankaku.data.PreviewHash
import dev.marzahn.sankaku.data.TextFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class SeenPost(val id: String, val md5: String?, val at: Instant)

class SeenStore(
  private val file: TextFile,
  private val clock: Clock = Clock.System,
  private val scope: CoroutineScope? = null,
  private val limit: Int = LIMIT,
  private val onClear: () -> Unit = {},
) {
  private val state: MutableStateFlow<Snapshot>

  private val flushes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    val saved = file.read()?.let { text -> runCatching { json.decodeFromString(SeenFile.serializer(), text) }.getOrNull() }
    val posts = LinkedHashMap<String, SeenPost>()
    saved?.posts.orEmpty().sortedBy { it.at }.forEach { entry ->
      posts.remove(entry.id)
      posts[entry.id] = SeenPost(entry.id, entry.md5, Instant.fromEpochSeconds(entry.at))
    }
    val pictures = LinkedHashMap<String, HashedPost>()
    saved?.pictures.orEmpty().forEach { entry ->
      val hash = PreviewHash.decode(entry.hash) ?: return@forEach
      pictures.remove(entry.id)
      pictures[entry.id] = HashedPost(entry.id, hash, entry.parentId, entry.created?.let(Instant::fromEpochSeconds))
    }
    state = MutableStateFlow(Snapshot(posts.bounded(), saved?.pruned?.let(Instant::fromEpochSeconds), pictures.boundedPictures()))
    if (prune()) write(state.value)
    startSaving(state.value)
  }

  private val _revision = MutableStateFlow(0)

  val revision: StateFlow<Int> get() = _revision

  val size: Int get() = state.value.posts.size

  val posts: List<SeenPost> get() = state.value.posts.values.toList()

  fun record(postId: String, md5: String?) {
    val now = clock.now()
    change { current ->
      val posts = LinkedHashMap(current.posts)
      val known = posts.remove(postId)
      posts[postId] = SeenPost(postId, md5 ?: known?.md5, now)
      Snapshot(posts.bounded(), current.pruned, current.pictures)
    }
  }

  fun recordPicture(post: HashedPost) {
    if (state.value.pictures[post.id] == post) return
    change { current ->
      val pictures = LinkedHashMap(current.pictures)
      pictures.remove(post.id)
      pictures[post.id] = post
      Snapshot(current.posts, current.pruned, pictures.boundedPictures())
    }
  }

  fun picture(postId: String): HashedPost? = state.value.pictures[postId]

  fun isSeen(postId: String, md5: String? = null): Boolean = lastSeen(postId, md5) != null

  fun lastSeen(postId: String, md5: String? = null): Instant? {
    val snapshot = state.value
    val byId = snapshot.posts[postId]?.at
    val byMd5 = md5?.let { snapshot.byMd5[it.lowercase()] }
    return listOfNotNull(byId, byMd5).maxOrNull()
  }

  fun seenSince(postId: String, md5: String?, keptAt: Instant): Boolean =
    lastSeen(postId, md5)?.let { it > keptAt } == true

  fun countSeenSince(from: Instant): Int =
    state.value.posts.values.count { it.at >= from && !it.id.startsWith(BOOK_PREFIX) }

  private fun prune(): Boolean {
    val now = clock.now()
    val current = state.value
    if (current.pruned != null && now - current.pruned < PRUNE_EVERY) return false
    val cutoff = now - KEPT_FOR
    state.value = Snapshot(LinkedHashMap(current.posts.filterValues { it.at >= cutoff }), pruned = now, current.pictures)
    return true
  }

  fun clear() {
    state.update { Snapshot(LinkedHashMap(), it.pruned, LinkedHashMap()) }
    _revision.update { it + 1 }
    if (scope == null) write(state.value) else flushes.tryEmit(Unit)
    runCatching(onClear)
  }

  fun clearOnSignOut(session: AuthSession, scope: CoroutineScope) {
    scope.launch {
      var signedIn: Boolean? = null
      session.state.collect { state ->
        if (state is AuthState.Restoring) return@collect
        val now = state is AuthState.SignedIn
        val expired = state is AuthState.SignedOut && state.reason == AuthState.SignedOut.Reason.Expired
        if ((signedIn == true && !now) || (signedIn == null && expired)) clear()
        signedIn = now
      }
    }
  }

  private fun change(next: (Snapshot) -> Snapshot) {
    state.update(next)
    _revision.update { it + 1 }
    if (scope == null) write(state.value)
  }

  @OptIn(FlowPreview::class)
  private fun startSaving(initial: Snapshot) {
    val scope = scope ?: return
    scope.launch(Dispatchers.Default) {
      merge(state.filter { it !== initial }.debounce(SAVE_DELAY), flushes.map { state.value }).collect(::write)
    }
  }

  private fun write(snapshot: Snapshot) {
    val entries = snapshot.posts.values.map { SeenEntry(it.id, it.md5, it.at.epochSeconds) }
    val pictures = snapshot.pictures.values.map { PictureEntry(it.id, it.hash.encode(), it.parentId, it.created?.epochSeconds) }
    val saved = SeenFile(VERSION, snapshot.pruned?.epochSeconds, entries, pictures)
    runCatching { file.write(json.encodeToString(SeenFile.serializer(), saved)) }
  }

  private fun LinkedHashMap<String, HashedPost>.boundedPictures(): LinkedHashMap<String, HashedPost> {
    val excess = size - PICTURES_LIMIT
    if (excess > 0) keys.take(excess).forEach(::remove)
    return this
  }

  private fun LinkedHashMap<String, SeenPost>.bounded(): LinkedHashMap<String, SeenPost> {
    val excess = size - limit
    if (excess <= 0) return this
    val keys = keys.take(excess)
    keys.forEach(::remove)
    return this
  }

  private class Snapshot(
    val posts: LinkedHashMap<String, SeenPost>,
    val pruned: Instant?,
    val pictures: LinkedHashMap<String, HashedPost> = LinkedHashMap(),
  ) {
    val byMd5: Map<String, Instant> by lazy {
      buildMap { posts.values.forEach { post -> post.md5?.lowercase()?.let { put(it, maxOf(get(it) ?: post.at, post.at)) } } }
    }
  }

  companion object {
    const val LIMIT: Int = 50_000

    val KEPT_FOR: Duration = 90.days

    val PRUNE_EVERY: Duration = 1.days

    val SAVE_DELAY: Duration = 2.seconds

    const val PICTURES_LIMIT: Int = 5_000

    private const val VERSION = 1

    private const val BOOK_PREFIX = "book:"

    private val json = Json { ignoreUnknownKeys = true }
  }
}

@Serializable
private data class SeenFile(
  val version: Int,
  val pruned: Long? = null,
  val posts: List<SeenEntry> = emptyList(),
  val pictures: List<PictureEntry> = emptyList(),
)

@Serializable
private data class PictureEntry(
  @SerialName("i") val id: String,
  @SerialName("h") val hash: String,
  @SerialName("p") val parentId: String? = null,
  @SerialName("c") val created: Long? = null,
)

@Serializable
private data class SeenEntry(@SerialName("i") val id: String, @SerialName("m") val md5: String? = null, @SerialName("t") val at: Long)

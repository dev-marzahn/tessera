package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.TagDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlin.concurrent.Volatile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface PendingChange {
  @Serializable
  @SerialName("favourite")
  data class Favourite(
    val postId: String,
    val favourited: Boolean,
    val favCount: Int,
    val wasFavourited: Boolean,
    val wasFavCount: Int,
  ) : PendingChange

  @Serializable
  @SerialName("vote")
  data class Vote(val postId: String, val vote: VoteState, val was: VoteState) : PendingChange

  @Serializable
  @SerialName("follow")
  data class Follow(val tagId: String, val name: String, val tagName: String?, val following: Boolean) : PendingChange {
    val tag: TagDto get() = TagDto(name = name, tagName = tagName, id = tagId, isFollowing = !following)
  }
}

class Outbox(private val file: TextFile, private val currentUser: () -> String?) {
  private val lock = Mutex()
  private val _changes: MutableStateFlow<List<PendingChange>>

  @Volatile
  var owner: String?
    private set

  init {
    val saved = file.read()?.let { text -> runCatching { json.decodeFromString(OutboxFile.serializer(), text) }.getOrNull() }
      ?.takeIf { it.version == VERSION && it.owner != null }
    owner = saved?.owner
    _changes = MutableStateFlow(saved?.changes.orEmpty())
  }

  val changes: StateFlow<List<PendingChange>> = _changes.asStateFlow()

  suspend fun add(change: PendingChange): Boolean = lock.withLock {
    val user = currentUser() ?: return@withLock false
    val held = if (owner == user) _changes.value else emptyList()
    val earlier = held.firstOrNull { it.sameTarget(change) }
    val merged = if (earlier == null) change else change.after(earlier)
    set(user, held.filterNot { it === earlier } + listOfNotNull(merged))
    true
  }

  fun holds(kind: Kind, id: String): Boolean =
    owner != null && owner == currentUser() && _changes.value.any { it.kind == kind && it.id == id }

  suspend fun remove(change: PendingChange) = lock.withLock {
    val held = _changes.value
    if (change in held) set(owner, held - change)
  }

  suspend fun settled(kind: Kind, id: String) = lock.withLock {
    val held = _changes.value
    val left = held.filterNot { it.kind == kind && it.id == id }
    if (left.size != held.size) set(owner, left)
  }

  suspend fun clear() = lock.withLock {
    if (owner != null || _changes.value.isNotEmpty()) set(null, emptyList())
  }

  private fun set(user: String?, changes: List<PendingChange>) {
    _changes.value = changes
    owner = user.takeIf { changes.isNotEmpty() }
    if (changes.isEmpty()) {
      runCatching { file.delete() }
    } else {
      runCatching { file.write(json.encodeToString(OutboxFile.serializer(), OutboxFile(VERSION, user, changes))) }
    }
  }

  enum class Kind { Favourite, Vote, Follow }

  @Serializable
  private data class OutboxFile(val version: Int, val owner: String?, val changes: List<PendingChange>)

  private companion object {
    const val VERSION = 1
    val json = Json { ignoreUnknownKeys = true }
  }
}

internal val PendingChange.kind: Outbox.Kind
  get() = when (this) {
    is PendingChange.Favourite -> Outbox.Kind.Favourite
    is PendingChange.Vote -> Outbox.Kind.Vote
    is PendingChange.Follow -> Outbox.Kind.Follow
  }

internal val PendingChange.id: String
  get() = when (this) {
    is PendingChange.Favourite -> postId
    is PendingChange.Vote -> postId
    is PendingChange.Follow -> tagId
  }

private fun PendingChange.sameTarget(other: PendingChange) = kind == other.kind && id == other.id

private fun PendingChange.after(earlier: PendingChange): PendingChange? = when (this) {
  is PendingChange.Favourite -> (earlier as PendingChange.Favourite)
    .let { copy(wasFavourited = it.wasFavourited, wasFavCount = it.wasFavCount) }
    .takeUnless { it.favourited == it.wasFavourited }
  is PendingChange.Vote -> copy(was = (earlier as PendingChange.Vote).was).takeUnless { it.vote.userVote == it.was.userVote }
  is PendingChange.Follow -> takeIf { following == (earlier as PendingChange.Follow).following }
}

internal fun ApiError.waits(): Boolean = when (this) {
  is ApiError.Network, is ApiError.RateLimited, is ApiError.Rejected, is ApiError.Unauthorized -> true
  is ApiError.Http -> status >= 500 || status == 408
  is ApiError.Decoding, is ApiError.SearchTimeout, is ApiError.TagLimit -> false
}

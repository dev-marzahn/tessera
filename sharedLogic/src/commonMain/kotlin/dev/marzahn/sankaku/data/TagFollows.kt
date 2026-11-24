package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.TagsApi
import dev.marzahn.sankaku.api.dto.TagDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TagFollows(private val api: TagsApi, private val outbox: Outbox = Outbox(InMemoryTextFile()) { null }) {
  private val _overrides = MutableStateFlow(pending())

  val overrides: StateFlow<Map<String, Boolean>> = _overrides.asStateFlow()

  private val _changed = MutableStateFlow<Map<String, TagDto>>(emptyMap())

  val changed: StateFlow<Map<String, TagDto>> = _changed.asStateFlow()

  private val lock = Mutex()

  fun withChanges(
    listed: List<TagDto>,
    changed: Map<String, TagDto> = _changed.value,
    overrides: Map<String, Boolean> = _overrides.value,
    keep: Set<String> = emptySet(),
  ): List<TagDto> {
    val ids = listed.mapNotNullTo(mutableSetOf()) { it.id }
    val added = changed.values.reversed().filter { it.id !in ids && (overrides[it.id] == true || it.id in keep) }
    return added + listed
  }

  fun isFollowing(tag: TagDto, overrides: Map<String, Boolean> = _overrides.value): Boolean =
    tag.id?.let { overrides[it] } ?: tag.isFollowing

  suspend fun toggle(tag: TagDto): Result<Unit> {
    val id = tag.id ?: return Result.failure(ApiError.Http(0, "tag has no id"))
    val was = isFollowing(tag)
    _overrides.update { it + (id to !was) }
    _changed.update { it - id + (id to tag) }
    val error = lock.withLock {
      try {
        if (was) api.unfollow(id, tag.searchName) else api.follow(id, tag.searchName)
        outbox.settled(Outbox.Kind.Follow, id)
        null
      } catch (e: ApiError) {
        e.takeUnless { it is ApiError.Network && outbox.add(PendingChange.Follow(id, tag.name, tag.tagName, following = !was)) }
      }
    }
    if (error == null) return Result.success(Unit)
    _overrides.update { it + (id to was) }
    return Result.failure(error)
  }

  suspend fun send(change: PendingChange.Follow): ApiError? = lock.withLock {
    if (change !in outbox.changes.value) return@withLock null
    val error = try {
      if (change.following) api.follow(change.tagId, change.tag.searchName) else api.unfollow(change.tagId, change.tag.searchName)
      null
    } catch (e: ApiError) {
      e
    }
    if (error?.waits() == true) return@withLock error
    outbox.remove(change)
    if (error != null) _overrides.update { if (it[change.tagId] == change.following) it + (change.tagId to !change.following) else it }
    error
  }

  fun clear() {
    _overrides.value = pending()
    _changed.value = emptyMap()
  }

  private fun pending(): Map<String, Boolean> =
    outbox.changes.value.filterIsInstance<PendingChange.Follow>().associate { it.tagId to it.following }
}

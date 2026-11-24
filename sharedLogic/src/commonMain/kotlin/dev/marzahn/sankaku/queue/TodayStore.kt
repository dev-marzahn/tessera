package dev.marzahn.sankaku.queue

import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.data.TextFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class TodayCounts(val found: Int, val done: Int, val liked: Int, val dropped: Int = 0) {
  companion object {
    val NONE = TodayCounts(0, 0, 0)
  }
}

class TodayStore(
  private val file: TextFile,
  private val clock: Clock = Clock.System,
  private val zone: () -> TimeZone = TimeZone::currentSystemDefault,
  private val scope: CoroutineScope? = null,
) {
  private val state = MutableStateFlow(load())

  private val flushes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val changes: StateFlow<Any?> = state.asStateFlow()

  init {
    startSaving()
  }

  fun counts(): TodayCounts =
    today()?.let { TodayCounts(it.found.size, it.done.size, it.liked.size, it.dropped.size) } ?: TodayCounts.NONE

  fun startOfToday(): Instant = date().atStartOfDayIn(zone())

  fun added(key: SourceKey) {
    if (key.isOnlyFavourites) return
    change { day -> if (key.value in day.done) day.copy(done = day.done - key.value) else day.copy(found = day.found + key.value) }
  }

  fun replaced(was: SourceKey?, key: SourceKey) {
    if (was == key) return
    if (was != null) change { it.copy(found = it.found - was.value) }
    added(key)
  }

  fun closed(key: SourceKey) {
    if (key.isOnlyFavourites) return
    change { it.copy(done = it.done + key.value) }
  }

  fun keptLeft(postId: String) = change { it.copy(dropped = it.dropped + postId) }

  fun keptBack(postId: String) = change { if (postId in it.dropped) it.copy(dropped = it.dropped - postId) else it }

  fun liked(postId: String, on: Boolean) = change { if (on) it.copy(liked = it.liked + postId) else it.copy(liked = it.liked - postId) }

  fun isLiked(postId: String): Boolean = today()?.liked?.contains(postId) == true

  fun likedPosts(): Set<String> = today()?.liked.orEmpty()

  fun clear() {
    state.value = null
    if (scope == null) write(null) else flushes.tryEmit(Unit)
  }

  fun clearLikedOnSignOut(session: AuthSession, on: CoroutineScope) {
    on.launch {
      var signedIn: Boolean? = null
      session.state.collect { auth ->
        if (auth is AuthState.Restoring) return@collect
        val now = auth is AuthState.SignedIn
        if (signedIn == true && !now) change { it.copy(liked = emptySet()) }
        signedIn = now
      }
    }
  }

  private fun date(): LocalDate = clock.now().toLocalDateTime(zone()).date

  private fun today(): TodayFile? = state.value?.takeIf { it.day == date().toString() }

  private fun change(next: (TodayFile) -> TodayFile) {
    val day = date().toString()
    var changed: TodayFile? = null
    state.update { saved ->
      val current = saved?.takeIf { it.day == day } ?: TodayFile(VERSION, day)
      val new = next(current)
      changed = new.takeIf { it != saved }
      new
    }
    if (scope == null) changed?.let(::write)
  }

  private fun load(): TodayFile? =
    file.read()?.let { text -> runCatching { json.decodeFromString(TodayFile.serializer(), text) }.getOrNull() }
      ?.takeIf { it.version == VERSION }

  @OptIn(FlowPreview::class)
  private fun startSaving() {
    val scope = scope ?: return
    scope.launch(Dispatchers.Default) {
      merge(state.drop(1).debounce(SAVE_DELAY), flushes.map { state.value }).collect(::write)
    }
  }

  private fun write(day: TodayFile?) {
    runCatching { if (day == null) file.delete() else file.write(json.encodeToString(TodayFile.serializer(), day)) }
  }

  private companion object {
    const val VERSION = 1
    val SAVE_DELAY = 1.seconds
    val json = Json { ignoreUnknownKeys = true }
  }
}

@Serializable
private data class TodayFile(
  val version: Int,
  val day: String,
  val found: Set<String> = emptySet(),
  val done: Set<String> = emptySet(),
  val liked: Set<String> = emptySet(),
  val dropped: Set<String> = emptySet(),
) {
  override fun toString() =
    "TodayFile(day=$day, found=${found.size}, done=${done.size}, liked=${liked.size}, dropped=${dropped.size})"
}

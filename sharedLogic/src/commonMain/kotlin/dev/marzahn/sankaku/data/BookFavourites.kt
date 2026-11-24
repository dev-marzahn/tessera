package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.BooksApi
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BookFavourite(val on: Boolean, val count: Int)

fun BookDto.favourite(overrides: Map<String, BookFavourite>): BookFavourite =
  overrides[id] ?: BookFavourite(isFavorited, favCount)


class BookFavourites(private val api: BooksApi, private val session: AuthSession, scope: CoroutineScope) {
  private data class Key(val user: String, val bookId: String)

  private data class Entry(
    val shown: BookFavourite,
    val confirmed: BookFavourite,
    val generation: Int,
    val settledAt: Int? = null,
  )

  private val entries = MutableStateFlow<Map<Key, Entry>>(emptyMap())

  private val state = Mutex()
  private var generation = 0
  private val calls = mutableMapOf<String, Mutex>()

  val overrides: StateFlow<Map<String, BookFavourite>> = combine(entries, session.state) { all, state -> mine(all, state) }
    .stateIn(scope, SharingStarted.Eagerly, emptyMap())

  private fun mine(all: Map<Key, Entry>, state: AuthState): Map<String, BookFavourite> {
    val user = userOf(state) ?: return emptyMap()
    return all.entries.filter { it.key.user == user }.associate { it.key.bookId to it.value.shown }
  }

  fun current(book: BookDto): BookFavourite =
    userOf(session.state.value)?.let { entries.value[Key(it, book.id)]?.shown } ?: book.loaded()

  suspend fun toggle(book: BookDto, onShown: (on: Boolean) -> Unit = {}): Result<BookFavourite> {
    val user = userOf(session.state.value) ?: return Result.failure(ApiError.Unauthorized())
    val key = Key(user, book.id)
    val (target, mine) = state.withLock {
      val entry = entries.value[key]
      val before = entry?.shown ?: book.loaded()
      val target = BookFavourite(!before.on, (before.count + if (before.on) -1 else 1).coerceAtLeast(0))
      val made = ++generation
      entries.update { it + (key to Entry(target, entry?.confirmed ?: book.loaded(), made)) }
      onShown(target.on)
      target to made
    }
    val call = state.withLock { calls.getOrPut(book.id) { Mutex() } }
    return try {
      val result = call.withLock { api.favourite(book.id, target.on) }
      val done = target.copy(count = result.favCount ?: target.count)
      settle(key) { entry, now -> if (entry.generation == mine) Entry(done, done, mine, now) else entry.copy(confirmed = done) }
      Result.success(done)
    } catch (e: ApiError) {
      settle(key) { entry, now ->
        if (entry.generation == mine) {
          if (entry.confirmed.on != entry.shown.on) onShown(entry.confirmed.on)
          entry.copy(shown = entry.confirmed, settledAt = now)
        } else {
          entry
        }
      }
      Result.failure(e)
    }
  }

  suspend fun <T> fresh(load: suspend () -> T, books: (T) -> List<BookDto>): T {
    val user = userOf(session.state.value)
    val sentAt = state.withLock { ++generation }
    val answer = load()
    if (user != null) {
      val ids = books(answer).mapTo(HashSet()) { it.id }
      state.withLock {
        entries.update { all ->
          all.filterNot { (key, entry) -> key.user == user && key.bookId in ids && (entry.settledAt ?: Int.MAX_VALUE) < sentAt }
        }
      }
    }
    return answer
  }

  suspend fun favouriteUnlessAlready(book: BookDto, onShown: (on: Boolean) -> Unit = {}): Result<BookFavourite> {
    val fresh = try {
      api.pages(book.id)
    } catch (_: ApiError) {
      book
    }
    val now = current(fresh)
    return if (now.on) Result.success(now) else toggle(fresh, onShown)
  }

  private suspend fun settle(key: Key, change: (Entry, now: Int) -> Entry) = state.withLock {
    val now = ++generation
    entries.update { all -> all[key]?.let { all + (key to change(it, now)) } ?: all }
  }

  private fun BookDto.loaded() = BookFavourite(isFavorited, favCount)

  private fun userOf(state: AuthState) = (state as? AuthState.SignedIn)?.user?.id
}

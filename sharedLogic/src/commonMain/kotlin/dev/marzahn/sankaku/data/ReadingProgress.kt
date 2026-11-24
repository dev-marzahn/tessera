package dev.marzahn.sankaku.data

import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.BooksApi
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.net.ConnectivityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class BookPlace(val sequence: Int, val page: Int, val pages: Int) {
  val finished: Boolean get() = page >= pages
}

data class SavedReading(val sequence: Int, val page: Int, val pages: Int, val finished: Boolean) {
  val fraction: Float get() = if (finished) 1f else (page.toFloat() / pages.coerceAtLeast(1)).coerceIn(0f, 1f)
}

class ReadingProgress(
  private val api: BooksApi,
  private val session: AuthSession,
  private val connectivity: ConnectivityState,
  private val file: TextFile,
  private val scope: CoroutineScope,
) {
  private val saved = load()

  private val device = MutableStateFlow(saved.books.associate { it.id to it.place() })

  private val finished = MutableStateFlow(saved.read.associate { it.id to it.sequence })

  private data class Unsent(val place: BookPlace, val account: String)

  private val unsent = MutableStateFlow(saved.unsent.associate { it.id to Unsent(it.place(), it.account) })

  private val unreachable = MutableStateFlow<Set<String>>(emptySet())

  private val sent = MutableStateFlow(unsent.value.mapValues { it.value.place })

  private val _revision = MutableStateFlow(0)

  val revision: StateFlow<Int> = _revision.asStateFlow()

  private val _landed = MutableStateFlow(0)

  val landed: StateFlow<Int> = _landed.asStateFlow()

  private class Send(val bookId: String, val place: BookPlace, val account: String)

  private val outbox = Channel<Send>(Channel.UNLIMITED)
  private val writes = Channel<Unit>(Channel.CONFLATED)

  private val fileLock = Mutex()

  init {
    scope.launch {
      for (send in outbox) {
        if (account() != send.account) continue
        val couldNotReach = try {
          api.saveReading(send.bookId, send.place.sequence)
          _landed.update { it + 1 }
          false
        } catch (e: ApiError) {
          e is ApiError.Network
        }
        val mine = Unsent(send.place, send.account)
        if (couldNotReach) {
          if (unsent.value[send.bookId] == mine) unreachable.update { it + send.bookId }
        } else if (unsent.getAndUpdate { if (it[send.bookId] == mine) it - send.bookId else it }[send.bookId] == mine) {
          writes.trySend(Unit)
        }
      }
    }
    scope.launch {
      connectivity.retryRequests.drop(1).collect {
        val ids = unreachable.getAndUpdate { emptySet() }
        sendUnsent { bookId, _ -> bookId in ids }
      }
    }
    scope.launch {
      for (write in writes) fileLock.withLock { write() }
    }
    scope.launch {
      var last: String? = null
      var known = false
      session.state.collect { state ->
        if (state is AuthState.Restoring) return@collect
        val now = (state as? AuthState.SignedIn)?.user?.id
        if (known && last != now) {
          sent.value = emptyMap()
          finished.value = emptyMap()
          unsent.value = emptyMap()
          unreachable.value = emptySet()
          changed()
          writes.trySend(Unit)
        }
        if (!known) {
          val foreign = unsent.value.filterValues { it.account != now }.keys
          if (foreign.isNotEmpty()) {
            unsent.update { it - foreign }
            sent.update { it - foreign }
            changed()
            writes.trySend(Unit)
          }
          val earlier = saved.unsent.associate { it.id to Unsent(it.place(), it.account) }
          sendUnsent { bookId, entry -> earlier[bookId] == entry }
        }
        last = now
        known = true
      }
    }
  }

  private fun sendUnsent(which: (bookId: String, entry: Unsent) -> Boolean) {
    val account = account() ?: return
    unsent.value.forEach { (bookId, entry) ->
      if (entry.account == account && which(bookId, entry)) outbox.trySend(Send(bookId, entry.place, account))
    }
  }

  private fun account(): String? = (session.state.value as? AuthState.SignedIn)?.user?.id

  fun savedOf(book: BookDto, signedIn: Boolean = session.isSignedIn.value): SavedReading? {
    val place = if (signedIn) sent.value[book.id] else device.value[book.id]
    if (place != null) return SavedReading(place.sequence, place.page.coerceIn(1, place.pages.coerceAtLeast(1)), place.pages.coerceAtLeast(1), place.finished)
    if (!signedIn) return null
    val reading = book.reading ?: return null
    val current = reading.currentPage ?: reading.sequence?.plus(1) ?: return null
    val sequence = (reading.sequence ?: (current - 1)).coerceAtLeast(0)
    val pages = book.postCount.coerceAtLeast(current).coerceAtLeast(1)
    val read = current >= pages || (reading.percent ?: 0.0) >= 100.0 || finished.value[book.id] == sequence
    return SavedReading(sequence, current.coerceIn(1, pages), pages, read)
  }

  fun save(bookId: String, place: BookPlace) {
    val account = account()
    if (account != null) {
      sent.update { it + (bookId to place) }
      unsent.update { it.latest(bookId, Unsent(place, account)) }
      unreachable.update { it - bookId }
      finished.update { read -> read.latest(bookId, place.sequence.takeIf { place.finished }) }
      outbox.trySend(Send(bookId, place, account))
    } else {
      device.update { it.latest(bookId, place) }
    }
    changed()
    writes.trySend(Unit)
  }

  suspend fun clear() {
    fileLock.withLock {
      device.value = emptyMap()
      finished.value = emptyMap()
      unsent.value = emptyMap()
      unreachable.value = emptySet()
      sent.value = emptyMap()
      runCatching { file.delete() }
    }
    changed()
  }

  private fun changed() = _revision.update { it + 1 }

  private fun load(): ReadingsFile =
    file.read()?.let { text -> runCatching { json.decodeFromString(ReadingsFile.serializer(), text) }.getOrNull() } ?: ReadingsFile(VERSION)

  private fun write() {
    val books = device.value.map { (id, p) -> ReadingEntry.of(id, p) }
    val read = finished.value.map { (id, sequence) -> ReadEntry(id, sequence) }
    val unsent = unsent.value.map { (id, u) -> UnsentEntry(id, u.place.sequence, u.place.page, u.place.pages, u.account) }
    runCatching {
      if (books.isEmpty() && read.isEmpty() && unsent.isEmpty()) {
        file.delete()
      } else {
        file.write(json.encodeToString(ReadingsFile.serializer(), ReadingsFile(VERSION, books, read, unsent)))
      }
    }
  }

  private fun <V : Any> Map<String, V>.latest(bookId: String, value: V?): Map<String, V> {
    val map = LinkedHashMap(this).apply { remove(bookId); if (value != null) put(bookId, value) }
    val excess = map.size - LIMIT
    if (excess > 0) map.keys.take(excess).forEach(map::remove)
    return map
  }

  companion object {
    const val LIMIT = 1_000

    private const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }
  }
}

@Serializable
private data class ReadingsFile(
  val version: Int,
  val books: List<ReadingEntry> = emptyList(),
  val read: List<ReadEntry> = emptyList(),
  val unsent: List<UnsentEntry> = emptyList(),
)

@Serializable
private data class UnsentEntry(
  @SerialName("i") val id: String,
  @SerialName("s") val sequence: Int,
  @SerialName("p") val page: Int,
  @SerialName("n") val pages: Int,
  @SerialName("a") val account: String,
) {
  fun place() = BookPlace(sequence, page, pages)
}

@Serializable
private data class ReadingEntry(
  @SerialName("i") val id: String,
  @SerialName("s") val sequence: Int,
  @SerialName("p") val page: Int,
  @SerialName("n") val pages: Int,
) {
  fun place() = BookPlace(sequence, page, pages)

  companion object {
    fun of(id: String, place: BookPlace) = ReadingEntry(id, place.sequence, place.page, place.pages)
  }
}

@Serializable
private data class ReadEntry(@SerialName("i") val id: String, @SerialName("s") val sequence: Int)

package dev.marzahn.sankaku.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.PostsApi
import dev.marzahn.sankaku.api.dto.Page
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.queue.TodayStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PostsRepository(
  private val api: PostsApi,
  private val store: PostStateStore,
  private val authSession: AuthSession,
  private val scope: CoroutineScope,
  private val clock: Clock = Clock.System,
  private val outbox: Outbox = Outbox(InMemoryTextFile()) { null },
  private val today: TodayStore? = null,
) {
  private val lock = Mutex()
  private val favouriteLocks = mutableMapOf<String, Mutex>()
  private val voteLocks = mutableMapOf<String, Mutex>()
  private val refetches = mutableMapOf<String, Deferred<PostDto>>()

  private val fullPosts = LinkedHashMap<String, PostDto>()
  private val fullFetches = mutableMapOf<String, Deferred<PostDto>>()
  private val pages = LinkedHashMap<PageKey, CachedPage>()
  private val pageFetches = mutableMapOf<PageKey, PageFetch>()
  private val latestPosts = LinkedHashMap<String, PostDto>()
  private val strips = LinkedHashMap<StripKey, List<PostDto>>()
  private val stripFetches = mutableMapOf<StripKey, Deferred<List<PostDto>>>()

  private var account = 0

  private fun userOf(state: AuthState) = (state as? AuthState.SignedIn)?.user?.id

  private data class CachesOwner(val user: String?)

  private val cachesOwner = MutableStateFlow<CachesOwner?>(null)

  init {
    scope.launch {
      authSession.state
        .map(::userOf)
        .distinctUntilChanged()
        .withIndex()
        .collect { (index, user) ->
          if (index > 0) clearCaches() else lock.withLock { restorePending() }
          cachesOwner.value = CachesOwner(user)
        }
    }
  }

  private fun restorePending() {
    val user = userOf(authSession.state.value) ?: return
    if (outbox.owner == user) store.restorePending(outbox.changes.value)
  }

  suspend fun awaitCurrentAccount() {
    combine(authSession.state, cachesOwner) { state, owner -> owner != null && owner.user == userOf(state) }
      .first { it }
  }

  suspend fun clearCaches() {
    lock.withLock {
      account++
      pages.clear()
      latestPosts.clear()
      fullPosts.clear()
      strips.clear()
      refetches.clear()
      fullFetches.clear()
      stripFetches.clear()
      pageFetches.clear()
      store.clear()
      restorePending()
    }
  }

  fun posts(
    tags: List<String>,
    start: GridStart? = null,
    onPage: ((List<PostDto>) -> Unit)? = null,
    onLocked: () -> Unit = {},
    onTopPage: ((TopPage) -> Unit)? = null,
    onLoaded: (PageCursor, List<PostDto>) -> Unit = { _, _ -> },
  ): Flow<PagingData<PostDto>> {
    var from = start
    var lists = 0
    return Pager(
      PagingConfig(
        pageSize = PostsApi.PAGE_SIZE,
        initialLoadSize = PostsApi.PAGE_SIZE,
        prefetchDistance = PREFETCH_DISTANCE,
        enablePlaceholders = false,
      ),
    ) {
      val atTop = from == null
      val list = ++lists
      val searched = from?.tags ?: tags
      val cursor = from?.cursor ?: PageCursor()
      from = null
      KeysetPagingSource(PostDto::id, cursor, onLoaded, hide = PostDto::redirectToSignup, onHidden = onLocked) { at ->
        page(searched, at, onPage).also { if (atTop) onTopPage?.invoke(TopPage(list, at, it.data, more = at.after(it) != null)) }
      }
    }.flow
  }

  suspend fun locate(tags: List<String>, place: GridPlace): GridStart? {
    val saved = place.pageCursor
    if (pageOrNull(tags, saved)?.holds(place.post) == true) return GridStart(tags, saved)
    val time = place.time
    return when {
      timeOrdered(tags) && time != null -> find(datedSearch(tags, time), place.post, PLACE_DATED_PAGES)
      !isForYou(tags) -> find(tags, place.post, PLACE_SCAN_PAGES)
      else -> null
    }
  }

  private suspend fun find(tags: List<String>, post: String, pages: Int): GridStart? {
    var cursor = PageCursor()
    repeat(pages) {
      val page = pageOrNull(tags, cursor) ?: return null
      if (page.holds(post)) return GridStart(tags, cursor)
      cursor = cursor.after(page) ?: return null
    }
    return null
  }

  private suspend fun pageOrNull(tags: List<String>, cursor: PageCursor): Page<PostDto>? = try {
    page(tags, cursor)
  } catch (e: ApiError) {
    if (e is ApiError.Network || e is ApiError.RateLimited || e is ApiError.Unauthorized) throw e
    null
  }

  private fun Page<PostDto>.holds(post: String) = data.any { it.id == post }

  suspend fun forgetPages(tags: List<String>) = lock.withLock {
    pages.keys.removeAll { it.tags == tags }
    pageFetches.keys.removeAll { it.tags == tags }
  }

  fun cachedFirstPage(tags: List<String>): List<PostDto>? {
    if (!lock.tryLock()) return null
    try {
      val cached = pages[PageKey(tags, PageCursor())]?.takeIf { clock.now() - it.loadedAt < PAGE_CACHE_TTL } ?: return null
      return cached.page.data.takeIf { it.isNotEmpty() }
    } finally {
      lock.unlock()
    }
  }

  suspend fun gridStart(tags: List<String>, place: GridPlace? = null): List<PostDto> =
    page(tags, place?.pageCursor ?: PageCursor()).data

  suspend fun firstPage(tags: List<String>): Page<PostDto> = page(tags)

  internal suspend fun page(
    tags: List<String>,
    cursor: PageCursor = PageCursor(),
    onPage: ((List<PostDto>) -> Unit)? = null,
  ): Page<PostDto> {
    val key = PageKey(tags, cursor)
    val (cached, fetch) = lock.withLock {
      val fresh = pages.remove(key)?.takeIf { clock.now() - it.loadedAt < PAGE_CACHE_TTL }
      if (fresh != null) {
        pages[key] = fresh
        fresh.page to null
      } else {
        null to pageFetches.getOrPut(key) { PageFetch(fetchPage(key, account)) }.also { it.waiters++ }
      }
    }
    val page = cached ?: awaitPage(key, checkNotNull(fetch))
    if (onPage != null && lock.withLock { deliver(key, page) }) onPage(page.data)
    return page
  }

  private suspend fun awaitPage(key: PageKey, fetch: PageFetch): Page<PostDto> = try {
    fetch.deferred.start()
    fetch.deferred.await().getOrThrow()
  } finally {
    withContext(NonCancellable) {
      lock.withLock {
        fetch.waiters--
        if (fetch.waiters == 0) {
          if (pageFetches[key] === fetch) pageFetches.remove(key)
          fetch.deferred.cancel()
        }
      }
    }
  }

  private fun deliver(key: PageKey, page: Page<PostDto>): Boolean {
    val cached = pages[key]?.takeIf { it.page === page && !it.delivered } ?: return false
    cached.delivered = true
    return true
  }

  private fun fetchPage(key: PageKey, asked: Int): Deferred<Result<Page<PostDto>>> = scope.async(start = CoroutineStart.LAZY) {
    val self = coroutineContext[Job]
    val overridesWhenAsked = store.overrides.value
    val page = try {
      api.keyset(key.tags, key.cursor.next, key.cursor.number)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      lock.withLock { if (pageFetches[key]?.deferred === self) pageFetches.remove(key) }
      return@async Result.failure(e)
    }
    lock.withLock {
      val current = pageFetches[key]?.deferred === self
      if (current) pageFetches.remove(key)
      if (current && account == asked) {
        store.takeListed(page.data, overridesWhenAsked)
        pages.putBounded(key, CachedPage(page, clock.now()), PAGE_CACHE_SIZE)
        page.data.forEach { latestPosts.putBounded(it.id, it, POST_CACHE_SIZE) }
      }
    }
    Result.success(page)
  }

  suspend fun cachedIndexOf(tags: List<String>, postId: String): Int? = lock.withLock {
    var cursor = PageCursor()
    val seen = mutableSetOf<String>()
    var index = 0
    repeat(PAGE_CACHE_SIZE) {
      val cached = pages[PageKey(tags, cursor)]?.takeIf { clock.now() - it.loadedAt < PAGE_CACHE_TTL } ?: return null
      for (post in cached.page.data) {
        if (!seen.add(post.id) || post.redirectToSignup) continue
        if (post.id == postId) return index
        index++
      }
      cursor = cursor.after(cached.page) ?: return null
    }
    null
  }

  suspend fun cachedPost(postId: String): PostDto? = lock.withLock { held(postId) }

  fun heldPost(postId: String): PostDto? {
    if (!lock.tryLock()) return null
    try {
      return held(postId)
    } finally {
      lock.unlock()
    }
  }

  suspend fun hold(post: PostDto) {
    lock.withLock { if (held(post.id) == null) latestPosts.putBounded(post.id, post, POST_CACHE_SIZE) }
  }

  suspend fun cachedPosts(postIds: Collection<String>): Map<String, PostDto> = lock.withLock {
    postIds.mapNotNull { id -> held(id)?.let { id to it } }.toMap()
  }

  private fun held(postId: String): PostDto? = fullPosts[postId] ?: latestPosts[postId]

  suspend fun toggleFavourite(post: PostDto): Result<Unit> {
    val current = post.merge(store.overrides.value[post.id])
    val target = !current.isFavorited
    val favCount = (current.favCount + if (target) 1 else -1).coerceAtLeast(0)
    val generation = store.beginToggle(post.id, target, favCount)
    val likedBefore = today?.isLiked(post.id) == true
    today?.liked(post.id, target)

    val outcome = favouriteLock(post.id).withLock {
      try {
        if (target) api.favourite(post.id) else api.unfavourite(post.id)
        outbox.settled(Outbox.Kind.Favourite, post.id)
        Outcome.Sent
      } catch (e: ApiError) {
        val waits = e is ApiError.Network &&
          outbox.add(PendingChange.Favourite(post.id, target, favCount, current.isFavorited, current.favCount))
        if (waits) Outcome.Waits else Outcome.Failed(e, pending = outbox.holds(Outbox.Kind.Favourite, post.id))
      }
    }
    when (outcome) {
      Outcome.Sent -> store.setFavourite(post.id, target, favCount, inFlight = false, generation = generation)
      Outcome.Waits -> {}
      is Outcome.Failed -> {
        val reverted = store.setFavourite(post.id, current.isFavorited, current.favCount, inFlight = outcome.pending, generation = generation)
        if (reverted) today?.liked(post.id, on = likedBefore)
      }
    }
    return outcome.result
  }

  private sealed interface Outcome {
    data object Sent : Outcome

    data object Waits : Outcome

    data class Failed(val error: ApiError, val pending: Boolean) : Outcome

    val result: Result<Unit> get() = if (this is Failed) Result.failure(error) else Result.success(Unit)
  }

  suspend fun vote(post: PostDto, score: Int?): Result<Unit> {
    val current = post.merge(store.overrides.value[post.id])
    val target = votedAs(current.voteState(), score)
    val generation = store.beginVote(post.id, target)
    var counted: Int? = null
    val outcome = voteLock(post.id).withLock {
      try {
        counted = (if (score != null) api.vote(post.id, score) else api.unvote(post.id)).voteCount
        outbox.settled(Outbox.Kind.Vote, post.id)
        Outcome.Sent
      } catch (e: ApiError) {
        val waits = e is ApiError.Network && outbox.add(PendingChange.Vote(post.id, target, current.voteState()))
        if (waits) Outcome.Waits else Outcome.Failed(e, pending = outbox.holds(Outbox.Kind.Vote, post.id))
      }
    }
    when (outcome) {
      Outcome.Sent -> store.setVote(post.id, target.copy(voteCount = counted ?: target.voteCount), generation)
      Outcome.Waits -> {}
      is Outcome.Failed -> store.setVote(post.id, current.voteState(), generation, inFlight = outcome.pending)
    }
    return outcome.result
  }

  suspend fun send(change: PendingChange.Favourite): ApiError? {
    val generation = store.overrides.value[change.postId]?.generation ?: 0
    return favouriteLock(change.postId).withLock { sendLocked(change, generation) }
  }

  private suspend fun sendLocked(change: PendingChange.Favourite, generation: Long): ApiError? {
    if (change !in outbox.changes.value) return null
    val error = try {
      if (change.favourited) api.favourite(change.postId) else api.unfavourite(change.postId)
      null
    } catch (e: ApiError) {
      e
    }
    if (error?.waits() == true) return error
    outbox.remove(change)
    if (error == null) {
      store.setFavourite(change.postId, change.favourited, change.favCount, inFlight = false, generation = generation)
    } else {
      store.setFavourite(change.postId, change.wasFavourited, change.wasFavCount, inFlight = false, generation = generation)
    }
    return error
  }

  suspend fun send(change: PendingChange.Vote): ApiError? {
    val generation = store.overrides.value[change.postId]?.voteGeneration ?: 0
    return voteLock(change.postId).withLock { sendLocked(change, generation) }
  }

  private suspend fun sendLocked(change: PendingChange.Vote, generation: Long): ApiError? {
    if (change !in outbox.changes.value) return null
    val score = change.vote.userVote
    return try {
      val result = if (score != null) api.vote(change.postId, score) else api.unvote(change.postId)
      outbox.remove(change)
      store.setVote(change.postId, change.vote.copy(voteCount = result.voteCount ?: change.vote.voteCount), generation)
      null
    } catch (e: ApiError) {
      if (!e.waits()) {
        outbox.remove(change)
        store.setVote(change.postId, change.was, generation)
      }
      e
    }
  }

  suspend fun refetch(postId: String): PostDto =
    singleFlight(refetches, postId) {
      val asked = lock.withLock { account }
      api.byId(postId).also { post ->
        lock.withLock {
          if (account != asked) return@withLock
          store.setUrls(post)
          latestPosts.putBounded(postId, post, POST_CACHE_SIZE)
        }
      }
    }

  suspend fun fullPost(postId: String): PostDto {
    lock.withLock {
      fullPosts.remove(postId)?.let { cached ->
        fullPosts[postId] = cached
        return cached
      }
    }
    return singleFlight(fullFetches, postId) {
      val asked = lock.withLock { account }
      api.full(postId).also { post ->
        lock.withLock {
          if (account != asked) return@withLock
          store.setUrls(post)
          store.setServerVote(post)
          fullPosts.putBounded(postId, post, FULL_POST_CACHE_SIZE)
        }
      }
    }
  }

  suspend fun recommended(postId: String, tags: List<String> = emptyList()): List<PostDto> =
    strip(StripKey(Strip.Recommended, postId, tags)) {
      api.recommended(postId, tags).filter { it.id != postId && it.showable }
    }

  suspend fun children(postId: String, tags: List<String> = emptyList()): Children {
    val page = strip(StripKey(Strip.Children, postId, tags)) { api.children(postId, tags) }
    return Children(page.filter { it.showable }, mayHaveMore = page.size >= PostsApi.CHILDREN_LIMIT)
  }

  data class Children(val posts: List<PostDto>, val mayHaveMore: Boolean)

  suspend fun parent(parentId: String, tags: List<String> = emptyList()): PostDto? {
    val held = lock.withLock { held(parentId) }
    if (held != null && held.showable && tags.all { it == "rating:${held.rating}" }) return held
    return strip(StripKey(Strip.Parent, parentId, tags)) {
      listOfNotNull(api.byIdOrNull(parentId, tags)?.takeIf { it.showable })
    }.firstOrNull()
  }

  private suspend fun strip(key: StripKey, fetch: suspend () -> List<PostDto>): List<PostDto> {
    lock.withLock {
      strips.remove(key)?.let { cached ->
        strips[key] = cached
        return cached
      }
    }
    return singleFlight(stripFetches, key) {
      val asked = lock.withLock { account }
      val posts = fetch()
      lock.withLock { if (account == asked) strips.putBounded(key, posts, STRIP_CACHE_SIZE) }
      posts
    }
  }

  private suspend fun <K, V> singleFlight(
    inFlight: MutableMap<K, Deferred<V>>,
    key: K,
    fetch: suspend () -> V,
  ): V {
    val deferred = lock.withLock {
      inFlight.getOrPut(key) {
        scope.async(start = CoroutineStart.LAZY) {
          try {
            fetch()
          } finally {
            val self = coroutineContext[Job]
            lock.withLock { if (inFlight[key] === self) inFlight.remove(key) }
          }
        }
      }
    }
    deferred.start()
    return deferred.await()
  }

  private suspend fun favouriteLock(id: String): Mutex = lock.withLock { favouriteLocks.getOrPut(id) { Mutex() } }

  private suspend fun voteLock(id: String): Mutex = lock.withLock { voteLocks.getOrPut(id) { Mutex() } }

  private data class PageKey(val tags: List<String>, val cursor: PageCursor)

  private enum class Strip { Recommended, Parent, Children }

  private data class StripKey(val strip: Strip, val postId: String, val tags: List<String>)

  private class CachedPage(val page: Page<PostDto>, val loadedAt: Instant, var delivered: Boolean = false)

  private class PageFetch(val deferred: Deferred<Result<Page<PostDto>>>, var waiters: Int = 0)

  companion object {
    const val FULL_POST_CACHE_SIZE = 200

    const val PAGE_CACHE_SIZE = 60

    const val POST_CACHE_SIZE = 1_000

    const val STRIP_CACHE_SIZE = 64

    val PAGE_CACHE_TTL = 10.minutes

    const val PLACE_DATED_PAGES = 5

    const val PLACE_SCAN_PAGES = 20
  }
}

internal fun votedAs(current: VoteState, score: Int?): VoteState {
  val previous = current.userVote
  val count = current.voteCount + when {
    previous == null && score != null -> 1
    previous != null && score == null -> -1
    else -> 0
  }
  val total = current.totalScore - (previous ?: 0) + (score ?: 0)
  return VoteState(score, count.coerceAtLeast(0), total.coerceAtLeast(0))
}

internal fun <K, V> LinkedHashMap<K, V>.putBounded(key: K, value: V, max: Int) {
  remove(key)
  put(key, value)
  while (size > max) remove(keys.first())
}

private const val PREFETCH_DISTANCE = 12

private val PostDto.showable: Boolean get() = !redirectToSignup && previewUrl != null

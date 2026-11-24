package dev.marzahn.sankaku.features.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.CachePolicy
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.Feed
import kotlinx.coroutines.flow.drop
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.queue.FeedMarks
import dev.marzahn.sankaku.queue.FeedMarksStore
import dev.marzahn.sankaku.queue.FeedPost
import dev.marzahn.sankaku.queue.FeedReading
import dev.marzahn.sankaku.queue.FeedVisit
import kotlin.time.Clock
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.GridStart
import dev.marzahn.sankaku.data.PageCursor
import dev.marzahn.sankaku.data.TopPage
import dev.marzahn.sankaku.tabs.PreviewPage
import dev.marzahn.sankaku.tabs.SourcePreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import dev.marzahn.sankaku.data.PostOverride
import dev.marzahn.sankaku.data.PostStateStore
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.data.SearchTags
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.data.TagsRepository
import dev.marzahn.sankaku.images.PostImage
import dev.marzahn.sankaku.images.postImageRequest
import dev.marzahn.sankaku.features.post.keptSampleRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun ratingLocked(signedIn: Boolean, safeOnly: Boolean): Boolean = !signedIn || safeOnly

fun searchedTags(tags: List<String>, signedIn: Boolean, safeOnly: Boolean): List<String> =
  SearchTags.filtersFirst(if (ratingLocked(signedIn, safeOnly)) withoutRating(tags) + Rating.G.tag else tags)

fun withoutRating(tags: List<String>): List<String> = tags.filterNot(Rating::isRatingTag)

sealed interface PlaceLookup {
  data object Looking : PlaceLookup

  data class Unreachable(val error: ApiError) : PlaceLookup

  data class Found(val post: String, val jump: Boolean = false, val firstPost: String? = null) : PlaceLookup

  data object GivenUp : PlaceLookup
}

private val CONTEXT_WAIT = 3.seconds

private val ApiError.keepsPlacePending: Boolean
  get() = this is ApiError.Network || this is ApiError.RateLimited || this is ApiError.Unauthorized

@OptIn(ExperimentalCoroutinesApi::class)
class GridViewModel(
  val tags: List<String>,
  savedPlace: GridPlace?,
  private val repository: PostsRepository,
  private val session: AuthSession,
  store: PostStateStore,
  private val imageLoader: ImageLoader,
  private val context: PlatformContext,
  private val privacy: PrivacySettings,
  private val knownTags: TagsRepository,
  private val marksStore: FeedMarksStore = FeedMarksStore(InMemoryTextFile()),
  private val clock: Clock = Clock.System,
  private val followedTagIds: suspend () -> List<String>? = { null },
) : ViewModel() {
  val feed: Feed? = withoutRating(tags).singleOrNull()?.let(Feed::of)

  private val place = savedPlace?.takeUnless { feed != null && marksStore.expired(feed) }

  val startPlace: GridPlace? get() = place.takeIf { feed == null }

  private var visit: FeedVisit? = feed?.let { FeedVisit(it, marksStore.get(it), clock.now()) }

  private val _marks = MutableStateFlow(visit?.shown)

  val marks: StateFlow<FeedMarks?> = _marks.asStateFlow()

  private val _context = MutableStateFlow<String?>(null)

  val feedContext: StateFlow<String?> = _context.asStateFlow()

  private val firstContext = CompletableDeferred<String?>()

  private val contextReads = MutableStateFlow(0)

  private val jump = MutableStateFlow<Pair<GridPlace, Int>?>(null)
  private val shared = MutableSharedFlow<PagingData<PostDto>>(replay = 1)

  val posts: SharedFlow<PagingData<PostDto>> = shared.asSharedFlow()

  private val locked = MutableStateFlow(false)

  val signedOutNotice: StateFlow<Boolean> = combine(session.isSignedIn, locked) { signedIn, hidden -> !signedIn && hidden }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  private val _lookup = MutableStateFlow<PlaceLookup?>(if (place != null) PlaceLookup.Looking else null)

  val lookup: StateFlow<PlaceLookup?> = _lookup.asStateFlow()

  private val retries = Channel<Unit>(Channel.CONFLATED)
  private var lookupJob: Job? = null

  private val pagerRun = MutableStateFlow<Int?>(0)

  private var start: GridStart? = null
  private var startFor: List<String>? = null

  private val _midway = MutableStateFlow(false)

  val midway: StateFlow<Boolean> = _midway.asStateFlow()

  private val cursors = MutableStateFlow<Map<String, PageCursor>>(emptyMap())

  init {
    restartOnClear()
    readContext()
    viewModelScope.launch { session.isSignedIn.drop(1).collect { locked.value = false } }
    if (place == null) {
      repository.cachedFirstPage(searchTags())?.let { page ->
        val shown = page.filterNot { it.redirectToSignup }
        if (shown.size < page.size) locked.value = true
        if (shown.isNotEmpty()) shared.tryEmit(PagingData.from(shown))
      }
    }
    viewModelScope.launch(Dispatchers.Main) {
      if (place != null) {
        session.state.first { it !is AuthState.Restoring }
        startLookup(place).join()
      }
      combine(session.isSignedIn, privacy.gOnly, pagerRun) { signedIn, gOnly, run ->
        Triple(signedIn, searchedTags(tags, signedIn, gOnly), run)
      }
        .distinctUntilChanged()
        .combine(jump) { search, to -> search to to }
        .flatMapLatest { (search, to) ->
          val (_, searched, run) = search
          if (run == null) return@flatMapLatest flowOf(PagingData.empty())
          if (to != null) return@flatMapLatest postsFrom(searched, jumpStart(searched, to.first))
          val from = start?.takeIf { searched == startFor }
          if (from == null && (start != null || _lookup.value is PlaceLookup.Found)) {
            _midway.value = false
            placeMissed()
          }
          start = null
          postsFrom(searched, from)
        }
        .cachedIn(viewModelScope)
        .collect { shared.emit(it) }
    }
  }

  private fun postsFrom(searched: List<String>, from: GridStart?): Flow<PagingData<PostDto>> {
    var found = (_lookup.value as? PlaceLookup.Found)?.takeIf { from != null }
    val pager = synchronized(topLock) { ++pagers }
    return repository.posts(
      searched, from, onPage = ::onPage, onLocked = { locked.value = true }, onTopPage = { onTopPage(searched, pager, it) },
    ) { cursor, posts ->
      onLoaded(cursor, posts)
      found?.let { place ->
        found = null
        viewModelScope.launch(Dispatchers.Main) {
          when {
            _lookup.value != place -> Unit
            posts.none { it.id == place.post } -> placeMissed()
            else -> _lookup.value = place.copy(firstPost = posts.first().id)
          }
        }
      }
    }
  }

  fun cursorOf(postId: String): PageCursor? = cursors.value[postId]

  private fun startLookup(place: GridPlace, pastCache: Boolean = false): Job {
    _lookup.value = PlaceLookup.Looking
    return viewModelScope.launch(Dispatchers.Main) {
      if (pastCache) repository.forgetPages(searchTags())
      while (true) {
        _lookup.value = PlaceLookup.Looking
        val searched = searchTags()
        val found = try {
          if (newPostsAbove(searched)) {
            _lookup.value = null
            return@launch
          }
          repository.locate(searched, place)
        } catch (e: ApiError) {
          if (!e.keepsPlacePending) null else {
            retries.tryReceive()
            _lookup.value = PlaceLookup.Unreachable(e)
            retries.receive()
            continue
          }
        }
        start = found
        startFor = searched
        _midway.value = found?.midway == true
        if (found != null) _lookup.value = PlaceLookup.Found(place.post) else setGivenUp()
        return@launch
      }
    }.also { lookupJob = it }
  }

  fun retryPlace() {
    val place = place ?: return
    when (_lookup.value) {
      is PlaceLookup.Unreachable -> {
        _lookup.value = PlaceLookup.Looking
        retries.trySend(Unit)
      }
      PlaceLookup.GivenUp -> {
        val run = pagerRun.value ?: 0
        pagerRun.value = null
        val job = startLookup(place, pastCache = true)
        viewModelScope.launch(Dispatchers.Main) {
          try {
            job.join()
          } finally {
            pagerRun.value = run + 1
          }
        }
      }
      else -> Unit
    }
  }

  fun placeShown() {
    _lookup.value = null
  }

  fun placeMissed() {
    val found = _lookup.value as? PlaceLookup.Found ?: return
    if (found.jump) _lookup.value = null else setGivenUp()
  }

  private var givenUpSaid = false

  private fun setGivenUp() {
    givenUpSaid = false
    _lookup.value = PlaceLookup.GivenUp
  }

  fun sayGivenUp(): Boolean {
    if (givenUpSaid) return false
    givenUpSaid = true
    return true
  }

  fun toTop() {
    _midway.value = false
    cursors.value = emptyMap()
    lookupJob?.cancel()
    start = null
    _lookup.value = null
    jump.value = null
    val feed = feed ?: return
    visit = FeedVisit(feed, visit?.marks ?: marksStore.get(feed), clock.now(), _context.value)
    _marks.value = visit?.shown
    contextReads.update { it + 1 }
  }

  private fun readContext() {
    if (feed != Feed.Following) {
      firstContext.complete(null)
      return
    }
    viewModelScope.launch {
      session.state.first { it !is AuthState.Restoring }
      var last: List<String>? = null
      combine(session.isSignedIn, privacy.gOnly, contextReads) { signedIn, gOnly, _ -> searchedTags(tags, signedIn, gOnly) }
        .collectLatest { searched ->
          if (searched != last) {
            _context.value = null
            visit?.context = null
          }
          last = searched
          val context = followedTagIds()?.let { FeedReading.context(searched, it) }
          visit?.context = context
          _context.value = context
          firstContext.complete(context)
        }
    }
  }

  private suspend fun newPostsAbove(searched: List<String>): Boolean {
    if (feed != Feed.Following) return false
    val marks = _marks.value ?: return false
    val page = repository.gridStart(searched)
    val first = page.firstOrNull() ?: return false
    if (first.id != marks.top.post && (first.createdAt?.epochSeconds ?: 0) > marks.top.time) return true
    return FeedReading.late(Feed.Following, feedPostsOf(page) { null }, atTop = true, marks, withTimeoutOrNull(CONTEXT_WAIT) { firstContext.await() }).isNotEmpty()
  }

  private suspend fun jumpStart(searched: List<String>, to: GridPlace): GridStart? {
    _lookup.value = PlaceLookup.Looking
    val located = try {
      repository.locate(searched, to)
    } catch (e: ApiError) {
      null
    }
    _midway.value = located?.midway == true
    _lookup.value = located?.let { PlaceLookup.Found(to.post, jump = true) }
    return located
  }

  fun reach(posts: List<FeedPost>, first: Int, last: Int) = save { it.reach(posts, atTop = !_midway.value, first, last) }

  fun continueFromGap() {
    val stop = _marks.value?.gap?.stop ?: return
    jump.value = stop.place to ((jump.value?.second ?: 0) + 1)
  }

  fun offerShown() = save { it.offerShown() }

  fun keepGap() = save { it.keep() }

  fun letGapGo() = save { it.letGo() }

  private fun save(change: (FeedVisit) -> Unit) {
    val feed = feed ?: return
    val visit = visit ?: return
    change(visit)
    _marks.value = visit.shown
    visit.marks?.let { marksStore.set(feed, it) }
  }

  private fun restartOnClear() {
    val feed = feed ?: return
    viewModelScope.launch {
      marksStore.clears.drop(1).collect {
        visit = FeedVisit(feed, null, clock.now(), _context.value)
        _marks.value = null
      }
    }
  }

  private fun onLoaded(cursor: PageCursor, posts: List<PostDto>) {
    cursors.update { known -> known + posts.associate { it.id to cursor } }
  }

  private val topLock = Any()
  private var pagers = 0
  private var topPager = 0
  private var topList = 0
  private val topPages = mutableMapOf<Int, PreviewPage>()
  private val _preview = MutableStateFlow<SourcePreview?>(null)

  val preview: StateFlow<SourcePreview?> = _preview.asStateFlow()

  private fun onTopPage(searched: List<String>, pager: Int, page: TopPage) = synchronized(topLock) {
    val newer = compareValuesBy(pager to page.list, topPager to topList, { it.first }, { it.second })
    if (newer < 0) return@synchronized
    if (newer > 0) {
      topPager = pager
      topList = page.list
      topPages.clear()
    }
    topPages[page.cursor.number] = PreviewPage.of(page.posts, searched, page.more)
    if (1 !in topPages) return@synchronized
    SourcePreview.ofPages(topPages.entries.sortedBy { it.key }.map { it.value }, searched)?.let { _preview.value = it }
  }

  val overrides: StateFlow<Map<String, PostOverride>> = store.overrides

  suspend fun forgetPages() = repository.forgetPages(searchTags())

  private fun searchTags() = searchedTags(tags, session.isSignedIn.value, privacy.gOnly.value)

  private fun onPage(posts: List<PostDto>) {
    viewModelScope.launch { knownTags.learnFromPosts(posts.flatMap { it.tags }) }
    prefetchPreviews(posts)
  }

  fun prefetchKept(post: PostDto) {
    keptSampleRequest(context, post)?.let(imageLoader::enqueue)
  }

  private fun prefetchPreviews(posts: List<PostDto>) {
    posts.filterNot { it.redirectToSignup }.forEach { post ->
      imageLoader.enqueue(
        postImageRequest(context, PostImage.of(post, Variant.Preview)) { memoryCachePolicy(CachePolicy.DISABLED) },
      )
    }
  }
}

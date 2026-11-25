package dev.marzahn.sankaku.tabs

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.existingPosition
import dev.marzahn.sankaku.isBooks
import dev.marzahn.sankaku.isLone
import dev.marzahn.sankaku.data.BookSearch
import dev.marzahn.sankaku.data.SearchTags
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.components.CLOSE_LABEL
import dev.marzahn.sankaku.ui.components.ShortSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.api.dto.TagSuggestion
import dev.marzahn.sankaku.data.PostTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import dev.marzahn.sankaku.queue.FeedItems
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.SessionClock
import dev.marzahn.sankaku.queue.SessionId
import dev.marzahn.sankaku.queue.SourceKey
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.api.dto.TagType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

@JvmInline
value class TabId(val value: String)

class TabState(
  val id: TabId,
  routes: List<Route>,
  block: TabId? = null,
  place: GridPlace? = null,
  addedIn: SessionId = SessionId.EARLIER,
  touched: Instant = Instant.fromEpochSeconds(0),
  keptAt: Instant? = null,
  closedSource: List<String>? = null,
  private val loadedTitles: (String) -> String? = { null },
  mosaic: QueueMosaic? = null,
  gridTouched: Boolean = false,
  internal val tagNames: TagNameLookup = NoTagNames,
  openedFrom: TabId? = null,
  val refines: List<String>? = null,
  preview: SourcePreview? = null,
  val foundOn: FoundOn? = null,
  later: Boolean = false,
) : ViewModelStoreOwner {
  val backStack = NavBackStack<NavKey>(*routes.toTypedArray())

  var openedFrom: TabId? by mutableStateOf(openedFrom)
    internal set

  var closedSource: List<String>? by mutableStateOf(closedSource)
    internal set

  val kind: QueueKind get() = queueKind(top)

  val sourceKey: SourceKey?
    get() = when (val shown = top) {
      is Route.BookSeries -> backStack.lastOrNull { it is Route.Books }?.let { sourceKeyOf(it as Route) }
      else -> sourceKeyOf(shown)
    }

  val feed: Feed? get() = (top as? Route.Grid)?.takeIf { kind == QueueKind.Feed }?.let { QueueKind.feedOf(it.tags) }

  var addedIn: SessionId = addedIn
    internal set

  var touched: Instant by mutableStateOf(touched)
    private set

  private var kept by mutableStateOf(keptRoute(routes.lastOrNull())?.let { route -> keptAt?.let { route to it } })

  val keptAt: Instant? get() = keptRoute(top)?.let { route -> kept?.takeIf { it.first == route }?.second }

  internal fun touch(now: Instant) {
    touched = now
    val route = keptRoute(top) ?: return
    if (kept?.first != route) kept = route to now
  }

  internal fun keepAgain(now: Instant) {
    touched = now
    keptRoute(top)?.let { kept = it to now }
    laterFor = null
  }

  private var laterFor by mutableStateOf(keptRoute(routes.lastOrNull())?.takeIf { later })

  val later: Boolean get() = laterFor != null && keptRoute(top) == laterFor

  internal fun markLater() {
    keptRoute(top)?.let { laterFor = it }
  }

  fun seenSinceKept(seen: SeenStore, notBefore: Instant? = null): Boolean {
    val kept = keptAt ?: return false
    val at = if (notBefore != null && notBefore > kept) notBefore else kept
    return when (val route = top) {
      is Route.Post -> seen.seenSince(route.id, route.md5, at)
      is Route.Book -> seen.seenSince(bookSeenId(route.id), null, at)
      else -> false
    }
  }

  fun viewedSinceKept(seen: SeenStore): Boolean = later || seenSinceKept(seen)

  private var placed by mutableStateOf((routes.lastOrNull() as? Route.Grid)?.let { grid -> place?.let { grid to it } })

  val place: GridPlace? get() = (top as? Route.Grid)?.let(::placeFor)

  fun reportPlace(route: Route.Grid, place: GridPlace?) {
    placed = place?.let { route to it }
  }

  fun placeFor(route: Route.Grid): GridPlace? = placed?.takeIf { it.first == route }?.second

  private var mosaicFor by mutableStateOf((routes.lastOrNull() as? Route.Grid)?.let { grid -> mosaic?.let { grid to it } })

  val mosaic: QueueMosaic? get() = (top as? Route.Grid)?.let { grid -> mosaicFor?.takeIf { it.first == grid }?.second }

  fun reportMosaic(route: Route.Grid, mosaic: QueueMosaic) {
    mosaicFor = route to mosaic
  }

  private var previewFor by mutableStateOf((routes.lastOrNull() as? Route.Grid)?.let { grid -> preview?.let { grid to it } })

  val preview: SourcePreview? get() = (top as? Route.Grid)?.let { grid -> previewFor?.takeIf { it.first == grid }?.second }

  fun reportPreview(route: Route.Grid, preview: SourcePreview) {
    if (top == route) previewFor = route to preview
  }

  var block: TabId? by mutableStateOf(block)
    internal set

  val head: TabId get() = block ?: id
  override val viewModelStore = ViewModelStore()
  private val entryStores = mutableMapOf<Any, ViewModelStore>()

  fun entryOwner(contentKey: Any): ViewModelStoreOwner {
    val store = entryStores.getOrPut(contentKey) { ViewModelStore() }
    return object : ViewModelStoreOwner {
      override val viewModelStore = store
    }
  }

  fun clearEntry(contentKey: Any) {
    entryStores.remove(contentKey)?.clear()
    described.remove(contentKey)
  }

  fun clear() {
    entryStores.values.forEach { it.clear() }
    entryStores.clear()
    described.clear()
    viewModelStore.clear()
  }

  private var touchedGrids by mutableStateOf(setOfNotNull((routes.lastOrNull() as? Route.Grid)?.takeIf { gridTouched }))

  fun markTouched(route: Route.Grid) {
    if (route in touchedGrids) return
    touchedGrids = touchedGrids.filterTo(mutableSetOf()) { it in backStack } + route
  }

  fun isTouched(route: Route.Grid): Boolean = route in touchedGrids || placeFor(route) != null

  val topIsTouchedGrid: Boolean
    get() = (top as? Route.Grid)?.let(::isTouched) == true

  internal val savedTouched: Boolean? get() = true.takeIf { topIsTouchedGrid && place == null }

  internal fun pop() {
    forget(backStack.removeAt(backStack.lastIndex))
  }

  internal fun replaceTop(route: Route) {
    forget(backStack.set(backStack.lastIndex, route))
  }

  private fun forget(route: NavKey) {
    if (route in touchedGrids && route !in backStack) touchedGrids = touchedGrids - (route as Route.Grid)
    if (route in coveringRoutes && route !in backStack) coveringRoutes = coveringRoutes - (route as Route)
  }

  private var coveringRoutes by mutableStateOf(emptySet<Route>())

  val chromeCovered: Boolean get() = top?.isLone == true || top in coveringRoutes

  fun coverChrome(route: Route, covers: Boolean) {
    if (covers != (route in coveringRoutes)) coveringRoutes = if (covers) coveringRoutes + route else coveringRoutes - route
  }

  private val described = mutableMapOf<Any, Pair<Route, () -> TabContents>>()

  fun describe(contentKey: Any, route: Route, contents: () -> TabContents) {
    described[contentKey] = route to contents
  }

  fun topContents(): TabContents? = described.values.lastOrNull { it.first == top }?.second?.invoke()

  fun allContents(): List<TabContents> = described.values.map { it.second() }

  val top: Route? get() = backStack.lastOrNull() as? Route

  internal val loadedTitle: String? get() = loadedTitleOf(top, loadedTitles)

  val title: String get() = tabTitle(top, loadedTitle, tagNames)

  internal val shortTitle: ShortSource? get() = shortTitle(top, tagNames)

  val refinedFrom: String? get() = refines?.takeIf { it.isNotEmpty() && backStack.size == 1 }?.let { sourceTitle(it, tagNames) }

  val untitled: Boolean get() = tabIsUntitled(top, loadedTitle)
}

private fun loadedTitleOf(top: Route?, loaded: (String) -> String?): String? =
  (top as? Route.Post)?.takeIf { it.label == null }?.let { loaded(it.id) }

private fun keptRoute(route: Any?): Route? = (route as? Route)?.takeIf { it.isLone }

internal fun queueKind(top: Route?): QueueKind = when (top) {
  is Route.Grid -> QueueKind.ofGrid(top.tags)
  is Route.Post -> QueueKind.KeptPost
  is Route.Books, is Route.BookSeries -> QueueKind.Source
  is Route.Book -> QueueKind.KeptBook
  null -> QueueKind.Other
}

internal fun loneAtLaunch(saved: List<SavedTab>, newId: () -> String = { Uuid.random().toString() }): List<SavedTab> {
  fun searchOf(route: Route?) = (route as? Route.Grid)?.let { SourceKey.of(it.tags) ?: it.tags }
  val searches = saved.filter { it.routes.last() is Route.Grid }.mapTo(mutableSetOf()) { searchOf(it.routes.last()) }
  val heads = mutableMapOf<String, String>()
  val lone = saved.flatMap { tab ->
    val post = tab.routes.last() as? Route.Post
    if (post == null || tab.routes.size == 1) return@flatMap listOf(tab)
    val under = tab.routes[tab.routes.lastIndex - 1] as? Route.Grid
    val keep = under != null && under.at == null && searches.add(searchOf(under))
    if (!keep) return@flatMap listOf(tab.copy(routes = listOf(post)))
    val grid = tab.copy(id = newId(), routes = tab.routes.dropLast(1), kind = queueKind(under), keptAt = null)
    val block = tab.block ?: grid.id
    heads[tab.id] = block
    listOf(grid, tab.copy(routes = listOf(post), block = block))
  }
  return lone.map { tab -> tab.block?.let(heads::get)?.let { tab.copy(block = it) } ?: tab }
}

internal fun tabTitle(
  top: Route?,
  loaded: String? = null,
  names: TagNameLookup = NoTagNames,
  touch: Boolean = platformIsTouch,
): String = when (top) {
  is Route.Grid -> sourceTitle(top.tags, names)
  is Route.Post -> top.label ?: loaded ?: PostTitle.UNTITLED
  is Route.Books -> booksTitle(top.tags, names)
  is Route.BookSeries -> top.title.ifBlank { BOOKS_TITLE }
  is Route.Book -> top.title?.takeIf { it.isNotBlank() } ?: BOOK_TITLE
  null -> if (touch) "Item" else "Tab"
}

internal fun shortTitle(top: Route?, names: TagNameLookup = NoTagNames, prefix: String = ""): ShortSource? = when (top) {
  is Route.Grid -> ShortSource.of(sourceTitleTags(top.tags, names), prefix)
  is Route.Books -> ShortSource.of(sourceTitleTags(booksContent(top.tags), names), "$prefix$BOOKS_TITLE · ")
  else -> null
}

internal const val SETTINGS_TITLE = "Settings"

internal const val BOOKS_TITLE = "Books"

internal const val BOOK_TITLE = "Book"

internal fun booksTitle(tags: List<String>, names: TagNameLookup = NoTagNames): String {
  val content = booksContent(tags)
  return if (content.isEmpty()) BOOKS_TITLE else "$BOOKS_TITLE · ${sourceTitle(content, names)}"
}

private fun booksContent(tags: List<String>): List<String> =
  tags.filterNot { SearchTags.isFilter(it) || it.equals(BookSearch.ENGLISH, ignoreCase = true) }

internal fun batchTitle(size: Int, touch: Boolean = platformIsTouch): String = if (touch) "$size items" else "$size tabs"

internal fun countedItems(tabs: List<TabState>): Int = tabs.count { it.kind != QueueKind.Feed }

internal fun countedPosition(tabs: List<TabState>, index: Int): Int =
  tabs.subList(0, (index + 1).coerceIn(0, tabs.size)).count { it.kind != QueueKind.Feed }

internal fun headsKeptPosts(kind: QueueKind): Boolean = kind == QueueKind.Source || kind == QueueKind.Feed

internal fun closeLabel(tab: TabState, keptStay: Int): String =
  if (keptStay > 0 && headsKeptPosts(tab.kind)) "Close ${tab.title}${keptStaySuffix(keptStay)}" else CLOSE_LABEL

internal fun closedBlockTitle(source: List<String>, names: TagNameLookup = NoTagNames): String =
  "from ${closedSourceName(source, names)}"

internal fun closedSourceName(source: List<String>, names: TagNameLookup = NoTagNames): String =
  "${tabTitle(searchSource(source), names = names)} (closed)"

internal fun tabIsUntitled(top: Route?, loaded: String? = null): Boolean = top is Route.Post && top.label == null && loaded == null

class TabsController(
  private val store: TabStateStore = TabStateStore.None,
  scope: CoroutineScope? = null,
  private val clock: Clock = Clock.System,
  knownTags: StateFlow<Map<String, TagSuggestion>>? = null,
  private val today: TodayStore? = null,
  private val zone: () -> TimeZone = TimeZone::currentSystemDefault,
) {
  private var known by mutableStateOf(knownTags?.value.orEmpty())

  internal val tagNames: TagNameLookup = { key -> known[key]?.displayName }

  internal val tagType: (String) -> TagType? = { key -> known[key]?.category }

  private val _tabs: MutableStateFlow<List<TabState>>
  private val _active: MutableStateFlow<TabId>

  val sessions: SessionClock

  private val returns = mutableStateMapOf<TabId, TabId>()

  private val jumps = mutableStateListOf<Jump>()

  private val _closed = MutableStateFlow<List<ClosedTab>>(emptyList())

  private var keptRun by mutableStateOf<KeptRun?>(null)

  private val droppedFrom = mutableStateMapOf<TabId, Set<TabId>>()

  var laterStreak by mutableIntStateOf(0)
    private set

  private var justOpened by mutableStateOf<TabId?>(null)

  fun startKeptRun(ids: List<TabId>, at: TabId) {
    if (ids.isEmpty()) return
    val later = _tabs.value.filter { it.id in ids && it.later }.mapTo(mutableSetOf()) { it.id }
    keptRun = KeptRun(KeptRunKind.Kept, ids, decided = later)
    laterStreak = 0
    activate(at)
  }

  fun startLaterRun(ids: List<TabId>, at: TabId? = null) {
    val first = at?.takeIf { it in ids } ?: ids.firstOrNull() ?: return
    keptRun = KeptRun(KeptRunKind.Later, ids)
    laterStreak = 0
    activate(first)
  }

  fun endKeptRun() {
    keptRun = null
  }

  fun keptRunKind(): KeptRunKind? = keptRun?.takeIf { it.over || _active.value in it }?.kind

  fun runProgress(): KeptProgress? {
    val run = keptRun?.takeIf { !it.over && _active.value in it } ?: return null
    return KeptProgress(run.decided.size, run.ids.size, runLater(run))
  }

  fun keptTally(): KeptTally? {
    val run = keptRun?.takeIf { it.over || _active.value in it } ?: return null
    return KeptTally(run.kind, run.decided.size, run.dropped.values.toList(), runLater(run))
  }

  private fun runLater(run: KeptRun): Int {
    val ids = run.ids.toSet()
    return _tabs.value.count { it.id in ids && it.later }
  }

  fun blockDecisions(tabs: List<TabState>, index: Int): KeptProgress? {
    val tab = tabs.getOrNull(index)?.takeIf { it.kind.isKept } ?: return null
    val block = tab.block ?: return null
    val members = tabs.filter { it.block == block && it.kind.isKept }
    val later = members.count { it.later }
    val dropped = droppedFrom[block]?.size ?: 0
    return KeptProgress(later + dropped, members.size + dropped, later)
  }

  private fun movedOnFrom(tab: TabState) {
    if (!tab.kind.isKept) return
    tab.markLater()
    keptRun?.takeIf { !it.over && tab.id in it }?.let { keptRun = it.copy(decided = it.decided + tab.id) }
    laterStreak++
  }

  fun movedOn() {
    movedOnFrom(activeTab)
  }

  private fun dropped(tab: TabState) {
    if (!tab.kind.isKept) return
    tab.block?.let { block -> droppedFrom[block] = droppedFrom[block].orEmpty() + tab.id }
    keptRun?.takeIf { !it.over && tab.id in it }?.let {
      keptRun = it.copy(decided = it.decided + tab.id, dropped = it.dropped + (tab.id to (tab.top as? Route.Post)?.id))
    }
  }

  fun keptPlace(): Pair<Int, Int>? {
    val id = _active.value
    if (!inKeptRun(id)) return null
    val left = keptLeft(_tabs.value)
    return (left.indexOf(id) + 1) to left.size
  }

  private fun inKeptRun(id: TabId) = keptRun?.takeUnless { it.over }?.contains(id) == true

  fun keptRunOver(): Boolean = keptRun?.over == true

  private fun leaveKeptRunUnless(id: TabId?) {
    if (keptRun != null && (id == null || !inKeptRun(id))) keptRun = null
  }

  private fun keptLeft(all: List<TabState>): List<TabId> {
    val present = all.mapTo(mutableSetOf()) { it.id }
    return keptRun?.ids.orEmpty().filter { it in present }
  }

  private fun keptNeighbour(all: List<TabState>, id: TabId, forward: Boolean): TabState? {
    val run = keptRun?.ids ?: return null
    val at = run.indexOf(id)
    if (at < 0) return null
    val ids = if (forward) run.drop(at + 1) else run.take(at.coerceAtLeast(0)).asReversed()
    return ids.firstNotNullOfOrNull { next -> all.firstOrNull { it.id == next } }
  }

  private val loaded = mutableStateMapOf<String, String>()

  fun loadedTitle(route: Route.Post, title: String?) {
    if (route.label != null) return
    if (title.isNullOrBlank()) loaded.remove(route.id) else if (loaded[route.id] != title) loaded[route.id] = title
  }

  fun forgetTitles() {
    loaded.clear()
  }

  private var pinned by mutableStateOf(emptyList<Feed>())

  private var order: List<TabState>
    get() = _tabs.value
    set(value) {
      val before = _tabs.value
      _tabs.value = headingBlocks(value)
      countToday(before, _tabs.value)
    }

  private var counting = false

  private var notToday: Set<TabId> = emptySet()

  private fun countToday(before: List<TabState>, after: List<TabState>) {
    val today = today?.takeIf { counting } ?: return
    if (before === after) return
    val beforeSet = before.toHashSet()
    val afterSet = after.toHashSet()
    before.forEach { tab -> if (tab !in afterSet) tab.countedKey?.let(today::closed) }
    after.forEach { tab -> if (tab !in beforeSet) tab.countedKey?.let(today::added) }
    val keptAfter = after.mapNotNullTo(HashSet()) { it.keptPostKey }
    before.forEach { tab ->
      if (tab !in afterSet && tab.id !in notToday) tab.keptPostKey?.takeIf { it !in keptAfter }?.let(today::keptLeft)
    }
    after.forEach { tab -> if (tab !in beforeSet) tab.keptPostKey?.let(today::keptBack) }
  }

  private val TabState.countedKey: SourceKey? get() = sourceKey?.takeIf { kind == QueueKind.Source }

  private val TabState.keptPostKey: String?
    get() = when (val route = top.takeIf { kind.isKept }) {
      is Route.Post -> route.id
      is Route.Book -> bookSeenId(route.id)
      else -> null
    }

  private fun headingBlocks(tabs: List<TabState>): List<TabState> {
    tabs.forEach { if (it.kind == QueueKind.Feed && it.block != null) it.block = null }
    return tabs
  }

  init {
    val saved = store.load()
    sessions = SessionClock.atLaunch(saved?.session?.takeIf { it.isNotBlank() }?.let(::SessionId))
    val restored = loneAtLaunch(saved?.tabs.orEmpty().filter { it.routes.isNotEmpty() }.distinctBy { it.id })
      .map { tab ->
        val post = tab.routes.last() as? Route.Post
        if (post != null && tab.title != null) loadedTitle(post, tab.title)
        TabState(
          TabId(tab.id),
          tab.routes,
          tab.block?.let(::TabId),
          tab.place,
          addedIn = tab.addedIn?.let(::SessionId) ?: SessionId.EARLIER,
          touched = tab.touched?.let(Instant::fromEpochSeconds) ?: clock.now(),
          keptAt = tab.keptAt?.let(Instant::fromEpochSeconds),
          closedSource = tab.closedSource,
          loadedTitles = loaded::get,
          mosaic = QueueMosaic.restored(tab.mosaic, tab.posts),
          gridTouched = tab.gridTouched == true,
          tagNames = tagNames,
          openedFrom = tab.openedFrom?.let(::TabId),
          refines = tab.refines,
          preview = tab.preview,
          foundOn = tab.foundOn,
          later = tab.later == true,
        )
      }
    if (restored.isEmpty()) runCatching { store.clear() }
    val tabs = headingBlocks(restored.ifEmpty { listOf(newTab(listOf(Route.Grid(emptyList())))) })
    _tabs = MutableStateFlow(tabs)
    val activeId = saved?.tabs?.getOrNull(saved.activeIndex)?.id
    _active = MutableStateFlow((tabs.firstOrNull { it.id.value == activeId } ?: tabs[(saved?.activeIndex ?: 0).coerceIn(tabs.indices)]).id)
    saved?.returns.orEmpty().forEach { (head, item) -> returns[TabId(head)] = TabId(item) }
    saved?.jumps.orEmpty().forEach { jumps += Jump(TabId(it.to), TabId(it.from), it.moved) }
    saved?.faded.orEmpty().sortedBy { it.at }.filter { f -> tabs.none { it.id.value == f.tabs.firstOrNull()?.id } }.forEach(::restoreFaded)
    _closed.value = restoredClosed(saved?.closed.orEmpty())
    val atLaunch = savedState(_tabs.value, _active.value).takeIf { saved?.faded.isNullOrEmpty() }
    counting = true
    scope?.launch { changes().dropWhile { it == atLaunch }.collect { runCatching { store.save(it) } } }
    if (knownTags != null) scope?.launch { knownTags.collect { known = it } }
  }

  val tabs: StateFlow<List<TabState>> = _tabs.asStateFlow()
  val active: StateFlow<TabId> = _active.asStateFlow()

  val activeTab: TabState get() = _tabs.value.first { it.id == _active.value }

  fun open(route: Route, background: Boolean) = open(listOf(route), background)

  fun open(routes: List<Route>, background: Boolean): TabState? {
    val all = _tabs.value
    if (background) justOpened = null
    openSource(routes.lastOrNull())?.let { source ->
      if (!background) goToSource(source)
      return source
    }
    val tab = newTab(routes, foundOn = foundOnFor(routes.lastOrNull()))
    insertSource(tab, routes.lastOrNull(), default = endOfSession(all))
    if (!background) show(tab)
    return null
  }

  private fun foundOnFor(route: Route?, typed: Boolean = false): FoundOn? {
    if (typed || sourceKeyOf(route) == null) return null
    val from = activeTab
    val shownPost = from.top?.isLone == true || from.chromeCovered
    val search = when {
      from.kind == QueueKind.Source -> sourceSearch(from.top)
      from.kind == QueueKind.Feed -> sourceSearch(from.top)?.takeIf { shownPost }
      from.kind.isKept -> sourceOf(from)?.let { source ->
        sourceSearch(source.top)?.takeIf { source.kind == QueueKind.Source || source.kind == QueueKind.Feed }
      } ?: from.closedSource
      else -> null
    } ?: return null
    val source = searchSource(search)
    val feed = source is Route.Grid && QueueKind.ofGrid(source.tags) == QueueKind.Feed
    if (!feed && sourceKeyOf(source) == null) return null
    val way = if (shownPost) FindWay.Post else FindWay.Grid
    return FoundOn(search, way, clock.now().epochSeconds)
  }

  fun findsOf(search: List<String>): List<TabState> {
    val source = searchSource(search)
    val key = sourceKeyOf(source) ?: return emptyList()
    val kind = queueKind(source)
    return _tabs.value.filter { tab ->
      val found = tab.foundOn?.let { searchSource(it.search) } ?: return@filter false
      tab.kind == QueueKind.Source && tab.sourceKey != key && queueKind(found) == kind && sourceKeyOf(found) == key
    }
  }

  class BlockLeft(val search: List<String>, val landing: TabId)

  var onBlockLeft: ((BlockLeft) -> Unit)? = null

  private fun leftBlock(all: List<TabState>, index: Int, landing: TabState) {
    val listener = onBlockLeft ?: return
    val item = all[index]
    if (landing.head == item.head) return
    val head = all.firstOrNull { it.id == item.head }
    val search = head?.takeIf { it.kind == QueueKind.Source }?.let { sourceSearch(it.top) } ?: item.closedSource ?: return
    listener(BlockLeft(search, landing.id))
  }

  fun moveInLine(id: TabId, next: TabId): Boolean {
    val all = _tabs.value
    val index = all.indexOfFirst { it.id == id }
    if (index < 0 || blockStart(all, index) != index) return false
    val block = all.subList(index, blockEnd(all, index) + 1).toList()
    val rest = all - block.toSet()
    val at = rest.indexOfFirst { it.id == next }.takeIf { it >= 0 }?.let { blockStart(rest, it) } ?: return false
    order = rest.toMutableList().apply { addAll(at, block) }
    return true
  }

  private fun endOfSession(all: List<TabState>): Int {
    val last = all.indexOfLast { it.addedIn == sessions.current }
    return if (last < 0) all.size else blockEnd(all, last) + 1
  }

  private fun closedBlockFor(route: Route?): List<TabState> {
    if (route == null || sourceSearch(route) == null) return emptyList()
    val all = _tabs.value
    val first = all.indexOfFirst { tab -> tab.closedSource?.let { takesBack(route, it) } == true }
    return if (first < 0) emptyList() else all.subList(first, blockEnd(all, first) + 1).toList()
  }

  private fun takesBack(route: Route, search: List<String>): Boolean {
    val closed = searchSource(search)
    val kind = queueKind(route)
    if (kind != queueKind(closed)) return false
    return when (kind) {
      QueueKind.Source -> sourceKeyOf(route)?.let { it == sourceKeyOf(closed) } == true
      QueueKind.Feed -> QueueKind.feedOf((route as Route.Grid).tags) == QueueKind.feedOf(search)
      else -> false
    }
  }

  internal fun closedBlock(tags: List<String>): List<TabState> = closedBlockFor(Route.Grid(tags))

  private fun insertSource(tab: TabState, route: Route?, default: Int) {
    val back = closedBlockFor(route)
    val at = back.firstOrNull()?.let { first -> _tabs.value.indexOf(first) } ?: default
    takeBack(tab, back)
    insert(at, tab)
  }

  private fun takeBack(tab: TabState, back: List<TabState>) {
    val from = back.firstOrNull()?.block ?: return
    back.forEach {
      it.block = tab.id
      it.closedSource = null
    }
    _closed.value.forEach { it.adopt(from, tab.id) }
  }

  fun sourceFor(key: SourceKey): TabState? = _tabs.value.firstOrNull { it.sourceKey == key }

  fun feedFor(feed: Feed): TabState? = _tabs.value.firstOrNull { it.feed == feed }

  fun pinFeeds(feeds: List<Feed>) {
    val gone = FeedItems.leaving(pinned, feeds)
    pinned = feeds
    val all = _tabs.value
    val leaving = all.filter { it.feed in gone }
    leaving.forEach { it.clear() }
    val ids = leaving.mapTo(mutableSetOf()) { it.id }
    val rest = all.filter { it.id !in ids }
    leaving.forEach { leaveKeptPosts(it, rest) }
    feeds.forEach { feed -> rest.firstOrNull { it.feed == feed }?.backStack?.let { while (it.size > 1) it.removeAt(0) } }
    val added = FeedItems.added(feeds, rest.mapNotNull { it.feed }).map { newTab(listOf(Route.Grid(listOf(it.tag)))) }
    val back = added.associateWith { closedBlockFor(it.top) }
    back.forEach { (feed, posts) -> takeBack(feed, posts) }
    val moving = back.values.flatten().toSet()
    order = (rest.filter { it !in moving } + added.flatMap { listOf(it) + back.getValue(it) })
      .ifEmpty { listOf(newTab(listOf(Route.Grid(emptyList())))) }
    if (_active.value in ids) {
      val index = all.indexOfFirst { it.id == _active.value }
      show(all.take(index).lastOrNull { it.id !in ids } ?: _tabs.value.first())
    }
  }

  fun isFeedItem(tab: TabState): Boolean = tab.feed?.let { it in pinned && feedFor(it) === tab } == true

  fun canClose(tab: TabState): Boolean = !isFeedItem(tab)

  private fun openSource(route: Route?): TabState? {
    if (route is Route.Grid && queueKind(route) == QueueKind.Feed) return QueueKind.feedOf(route.tags)?.let(::feedFor)
    val key = sourceKeyOf(route) ?: return null
    return sourceFor(key)
  }

  fun queuePositionOf(route: Route, tabs: List<TabState> = _tabs.value): Int? = queuePositions(tabs)(route)

  fun queuePositions(tabs: List<TabState> = _tabs.value): (Route) -> Int? {
    val sources = HashMap<SourceKey, Int>()
    tabs.forEachIndexed { index, tab -> tab.sourceKey?.let { sources.getOrPut(it) { index } } }
    return { route ->
      val index = if (route is Route.Grid && queueKind(route) == QueueKind.Feed) {
        QueueKind.feedOf(route.tags)?.let { feed -> tabs.indexOfFirst { it.feed == feed } }
      } else {
        sourceKeyOf(route)?.let(sources::get)
      }
      index?.takeIf { it >= 0 }?.let { existingPosition(tabs, it) }
    }
  }

  fun keptPostsOf(tab: TabState): Int = _tabs.value.count { it.block == tab.id && it !== tab }

  fun sourceOf(tab: TabState): TabState? {
    if (!tab.kind.isKept) return null
    val head = tab.block ?: return null
    return _tabs.value.firstOrNull { it.id == head }?.takeIf { headsKeptPosts(it.kind) }
  }

  private fun goToSource(source: TabState) {
    val from = activeTab
    jump(source)
    if (from.head != source.head) returns[source.head] = from.id
  }

  fun jumpTo(id: TabId) {
    _tabs.value.firstOrNull { it.id == id }?.let(::jump)
  }

  private fun jump(target: TabState) {
    val from = activeTab
    show(target)
    remember(Jump(target.id, from.id))
  }

  private fun remember(step: Jump) {
    if (step.to == step.from) return
    jumps += step
    if (jumps.size > MAX_JUMPS) jumps.removeRange(0, jumps.size - MAX_JUMPS)
  }

  fun jumpedFrom(tab: TabState, moves: Boolean = true): TabState? = newestJump(tab, moves)?.second

  private fun newestJump(tab: TabState, moves: Boolean = true): Pair<Jump, TabState>? {
    if (tab.backStack.size != 1) return null
    val all = _tabs.value
    return jumps.asReversed().firstNotNullOfOrNull { jump ->
      if (jump.to != tab.id || (jump.moved && !moves)) null else all.firstOrNull { it.id == jump.from && it !== tab }?.let { jump to it }
    }
  }

  fun backFromJump(id: TabId, moves: Boolean = true): Boolean {
    val tab = _tabs.value.firstOrNull { it.id == id } ?: return false
    val (jump, from) = newestJump(tab, moves) ?: return false
    useUp(jump)
    show(from, Arrival.Back)
    return true
  }

  private fun useUp(step: Jump) {
    jumps.removeAt(jumps.lastIndexOf(step))
  }

  fun backReturnOf(tab: TabState, moves: Boolean = true): TabState? = jumpedFrom(tab, moves) ?: usedSourceOpener(tab)

  private fun usedSourceOpener(tab: TabState): TabState? {
    if (!aloneInItsTab(tab, posts = false) || openerOf(tab) != null) return null
    return openedFromItem(tab)
  }

  private fun aloneInItsTab(tab: TabState, posts: Boolean): Boolean {
    val top = tab.top
    return tab.backStack.size == 1 && (top is Route.Grid || top?.isBooks == true || (posts && top?.isLone == true))
  }

  private fun openedFromItem(tab: TabState): TabState? {
    val from = tab.openedFrom ?: return null
    return _tabs.value.firstOrNull { it.id == from && it !== tab }
  }

  fun backReturn(id: TabId, moves: Boolean = true): Boolean {
    if (backFromJump(id, moves)) return true
    val tab = _tabs.value.firstOrNull { it.id == id } ?: return false
    val opener = usedSourceOpener(tab) ?: return false
    tab.openedFrom = null
    show(opener, Arrival.Back)
    return true
  }

  fun exactSourceFor(route: Route): TabState? {
    val source = sourceKeyOf(route)?.let(::sourceFor) ?: return null
    val searched = sourceSearch(source.top) ?: return null
    return source.takeIf { exactSearch(searched) == exactSearch(sourceSearch(route).orEmpty()) }
  }

  private fun exactSearch(tags: List<String>): Set<String> = tags.map { it.trim().lowercase() }.filterTo(mutableSetOf()) { it.isNotEmpty() }

  fun touch(id: TabId) {
    _tabs.value.firstOrNull { it.id == id }?.touch(clock.now())
    order = _tabs.value
  }

  fun touchActive() {
    justOpened = null
    touch(_active.value)
  }

  fun keepAgain(id: TabId) {
    _tabs.value.firstOrNull { it.id == id }?.keepAgain(clock.now())
  }

  fun keptPostSeen(postId: String, seen: SeenStore): Boolean =
    indexOfPost(postId)?.let { _tabs.value[it].seenSinceKept(seen) } == true

  fun seenFromGrid(grid: TabState, postId: String, seen: SeenStore): Boolean =
    keptFrom(grid)[postId]?.any { it.seenSinceKept(seen) } == true

  fun keptFrom(grid: TabState): Map<String, List<TabState>> =
    _tabs.value.filter { tab -> tab.block == grid.id && tab.top is Route.Post && sourceOf(tab) === grid }
      .groupBy { (it.top as Route.Post).id }

  fun seenFromBooks(books: TabState, bookId: String, seen: SeenStore): Boolean =
    _tabs.value.any { tab -> (tab.top as? Route.Book)?.id == bookId && sourceOf(tab) === books && tab.seenSinceKept(seen) }

  fun keptBookSeen(bookId: String, seen: SeenStore): Boolean =
    indexOfLone(Route.Book(bookId))?.let { _tabs.value[it].seenSinceKept(seen) } == true

  fun openInBlock(routes: List<Route>) {
    val all = _tabs.value
    val index = activeIndex(all)
    val tab = newTab(routes, block = all[index].head, closedSource = all[index].closedSource)
    insert(blockEnd(all, index) + 1, tab)
    justOpened = null
  }

  fun openLone(route: Route) {
    require(route.isLone) { "Only a post or a book opens alone" }
    indexOfLone(route)?.let { index -> return jump(_tabs.value[index]) }
    val all = _tabs.value
    val index = activeIndex(all)
    val tab = newTab(listOf(route), block = all[index].head, openedFrom = all[index].id)
    insert(index + 1, tab)
    show(tab, Arrival.Opened)
  }

  fun neighbour(forward: Boolean): TabState? {
    val all = _tabs.value
    return landing(all, activeIndex(all), forward, wrap = false)
  }

  fun previous(): Boolean = step(forward = false, wrap = false)

  fun backToPrevious(moves: Boolean = true): Boolean = step(forward = false, wrap = false, back = true, moves = moves)

  fun next(): Boolean = step(forward = true, wrap = false)

  private fun openInFront(route: Route, fromItem: Boolean = false, typed: Boolean = false) {
    val all = _tabs.value
    val index = activeIndex(all)
    val from = all[index]
    val tab = newTab(
      listOf(route),
      openedFrom = if (fromItem) from.id else null,
      refines = if (fromItem && route is Route.Grid) (from.top as? Route.Grid)?.tags?.takeIf { changes(it, route.tags) } else null,
      foundOn = if (fromItem) foundOnFor(route, typed) else null,
    )
    insertSource(tab, route, default = blockStart(all, index))
    show(tab, if (fromItem) Arrival.Opened else Arrival.GoTo)
    returns[tab.id] = from.id
  }

  private fun changes(parent: List<String>, tags: List<String>): Boolean {
    val kept = parent.filterNot(SearchTags::isFilter).map { it.lowercase() }.toSet()
    return tags.any { !SearchTags.isFilter(it) && it.lowercase() in kept }
  }

  fun search(route: Route.Grid, typed: Boolean = false) {
    val tab = activeTab
    val source = openSource(route)
    when {
      source == tab -> if (tab.top != route) tab.backStack.add(route)
      source != null -> goToSource(source)
      tab.top?.isLone == true || tab.top?.isBooks == true || tab.topIsTouchedGrid || isFeedItem(tab) || closedBlockFor(route).isNotEmpty() -> openInFront(route, fromItem = true, typed = typed)
      else -> {
        val was = tab.countedKey
        tab.backStack.add(route)
        if (counting) tab.countedKey?.let { today?.replaced(was, it) }
      }
    }
    touch(_active.value)
  }

  fun step(forward: Boolean, wrap: Boolean): Boolean = step(forward, wrap, back = false, moves = true)

  private fun step(forward: Boolean, wrap: Boolean, back: Boolean, moves: Boolean): Boolean {
    val all = _tabs.value
    val index = activeIndex(all)
    val landing = landing(all, index, forward, wrap) ?: return false
    if (forward) all.getOrNull(index)?.let(::movedOnFrom)
    if (all.getOrNull(index)?.id?.let(::inKeptRun) == true) {
      all.getOrNull(index)?.let { from -> jumps.removeAll { it.to == from.id } }
      jumps.removeAll { it.to == landing.id }
      show(landing)
      return true
    }
    leaveKeptRunUnless(null)
    val left = all.getOrNull(index)?.takeIf { forward && index == blockEnd(all, index) && returnFor(all, it.head)?.id == landing.id }
    val from = all.getOrNull(index)
    show(landing)
    if (back) {
      val taken = from?.let { newestJump(it, moves) }
      if (taken?.second === landing) useUp(taken.first)
    } else {
      from?.let { remember(Jump(landing.id, it.id, moved = true)) }
    }
    if (left != null) _wayBacks.tryEmit(all.firstOrNull { it.id == left.head }?.title ?: left.title)
    if (forward && index >= 0 && index == blockEnd(all, index)) leftBlock(all, index, landing)
    return true
  }

  private val _wayBacks = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val wayBacks: SharedFlow<String> = _wayBacks.asSharedFlow()

  private fun landing(all: List<TabState>, index: Int, forward: Boolean, wrap: Boolean): TabState? {
    all.getOrNull(index)?.let { shown -> if (inKeptRun(shown.id)) return keptNeighbour(all, shown.id, forward) }
    if (forward && index == blockEnd(all, index)) returnFor(all, all[index].head)?.let { return it }
    val next = index + if (forward) 1 else -1
    if (!wrap && next !in all.indices) return null
    return all[next.mod(all.size)]
  }

  private fun returnFor(all: List<TabState>, head: TabId): TabState? =
    returns[head]?.let { id -> all.firstOrNull { it.id == id } }?.takeIf { it.head != head }

  private fun activeIndex(all: List<TabState>) = all.indexOfFirst { it.id == _active.value }

  private fun blockStart(all: List<TabState>, index: Int): Int {
    val head = all[index].head
    var start = index
    while (start > 0 && all[start].block == head && all[start - 1].head == head) start--
    return start
  }

  private fun blockEnd(all: List<TabState>, index: Int): Int {
    val head = all[index].head
    var end = index
    while (end < all.lastIndex && all[end + 1].block == head) end++
    return end
  }

  private fun insert(at: Int, tab: TabState) {
    order = _tabs.value.toMutableList().apply { add(at.coerceIn(0, size), tab) }
  }

  private enum class Arrival { Opened, Back, GoTo }

  private fun show(tab: TabState, arrival: Arrival = Arrival.GoTo) {
    justOpened = tab.id.takeIf { arrival == Arrival.Opened }
    backClosedTo = null
    clearedTo = null
    if (arrival == Arrival.GoTo && tab.id != _active.value) tab.openedFrom = null
    _active.value = tab.id
    if (!tab.kind.isKept) laterStreak = 0
    returns.entries.filter { it.value == tab.id }.forEach { returns.remove(it.key) }
    tab.touch(clock.now())
  }

  fun openPosts(tabs: List<TabState>): Set<String> =
    tabs.mapNotNullTo(mutableSetOf()) { (it.top as? Route.Post)?.id }

  fun indexOfPost(postId: String): Int? =
    _tabs.value.indexOfFirst { (it.top as? Route.Post)?.id == postId }.takeIf { it >= 0 }

  fun keptBooks(tabs: List<TabState>): Set<String> = tabs.mapNotNullTo(mutableSetOf()) { (it.top as? Route.Book)?.id }

  fun indexOfLone(route: Route): Int? = when (route) {
    is Route.Post -> indexOfPost(route.id)
    is Route.Book -> _tabs.value.indexOfFirst { (it.top as? Route.Book)?.id == route.id }.takeIf { it >= 0 }
    else -> null
  }

  fun activate(id: TabId) {
    val tab = _tabs.value.firstOrNull { it.id == id } ?: return
    jumps.removeAll { it.to == id }
    leaveKeptRunUnless(id)
    show(tab)
  }

  val closed: StateFlow<List<ClosedTab>> = _closed.asStateFlow()

  fun forgetClosed() {
    _closed.value = emptyList()
  }

  fun close(id: TabId, toPrevious: Boolean = false): ClosedTab? = closeLandingOn(id, toPrevious, landOn = null)

  private fun closeLandingOn(id: TabId, toPrevious: Boolean, landOn: TabId?): ClosedTab? {
    val all = _tabs.value
    val index = all.indexOfFirst { it.id == id }
    if (index < 0 || !canClose(all[index])) return null
    dropped(all[index])
    val rest = all - all[index]
    val landing = if (_active.value != id || rest.isEmpty()) {
      null
    } else if (inKeptRun(id)) {
      keptNeighbour(rest, id, forward = !toPrevious) ?: keptNeighbour(rest, id, forward = toPrevious)
        ?: rest[(if (toPrevious) index - 1 else index).coerceIn(0, rest.lastIndex)].also { keptRun = keptRun?.copy(over = true) }
    } else {
      val back = if (!toPrevious && index == blockEnd(all, index)) returnFor(rest, all[index].head) else null
      rest.firstOrNull { it.id == landOn } ?: back ?: rest[(if (toPrevious) index - 1 else index).coerceIn(0, rest.lastIndex)]
    }
    val wayBack = landing?.id?.takeIf { it == returns[all[index].head] }
    val closedTab = ClosedTab(
      listOf(member(all[index], index).copy(wayBack = wayBack)), activeMember = 0.takeIf { _active.value == id },
      keptStay = leaveKeptPosts(all[index], all), tagNames = tagNames, closedAt = clock.now(),
    )
    _closed.value = (_closed.value + closedTab).takeLast(REOPENABLE)
    val handedOver = if (landing != null && landOn == null && !inKeptRun(id)) newestJump(all[index])?.first else null
    all[index].clear()
    if (rest.isEmpty()) {
      val fresh = newTab(listOf(Route.Grid(emptyList())))
      order = listOf(fresh)
      show(fresh)
    } else {
      order = rest
      landing?.let { show(it, Arrival.Back) }
      if (landing != null && handedOver != null) remember(handedOver.copy(to = landing.id))
      if (landing != null && landOn == null && !toPrevious && !inKeptRun(id) && index == blockEnd(all, index)) leftBlock(all, index, landing)
    }
    return closedTab
  }

  fun openerOf(tab: TabState): TabState? {
    if (!aloneInItsTab(tab, posts = true)) return null
    if (tab.id != justOpened || tab.addedIn != sessions.current || !canClose(tab)) return null
    if (tab.topIsTouchedGrid || keptPostsOf(tab) > 0) return null
    return openedFromItem(tab)
  }

  fun reachedAgain() {
    justOpened = null
  }

  fun closeBack(id: TabId): ClosedTab? {
    val tab = _tabs.value.firstOrNull { it.id == id } ?: return null
    val opener = openerOf(tab) ?: return null
    return closeLandingOn(id, toPrevious = false, landOn = opener.id)?.also { backClosedTo = opener.id }
  }

  var backClosedTo: TabId? = null
    private set

  fun keepForLater(closedTab: ClosedTab): Boolean {
    if (closedTab !in _closed.value) return false
    val before = _tabs.value.mapTo(HashSet()) { it.id }
    bringBack(closedTab, BringBack.ForLater)
    val tab = _tabs.value.firstOrNull { it.id !in before } ?: return true
    tab.openedFrom = null
    returns.remove(tab.id)
    if (tab.top?.isLone == true) {
      val all = _tabs.value
      val index = all.indexOf(tab)
      move(index, blockEnd(all, index))
    } else {
      moveToEnd(tab.id)
    }
    return true
  }

  suspend fun awaitNextAction() {
    val queue = combine(_tabs, _active) { all, id -> all to id }.drop(1)
    val inItem = snapshotFlow { activeTab.let { it.backStack.toList() to it.topIsTouchedGrid } }.drop(1)
    merge(queue, inItem).first()
  }

  fun openBooks(route: Route, typed: Boolean = false) {
    val tab = activeTab
    val source = openSource(route)
    when {
      tab.top == route -> Unit
      source === tab -> if (route in tab.backStack) {
        while (tab.top != route) tab.pop()
      } else {
        tab.backStack.add(route)
      }
      source != null -> goToSource(source)
      tab.top?.isBooks == true && keptPostsOf(tab) == 0 && closedBlockFor(route).isEmpty() -> tab.backStack.add(route)
      else -> openInFront(route, fromItem = true, typed = typed)
    }
    touch(_active.value)
  }

  fun reopenClosed() {
    val latest = _closed.value.lastOrNull() ?: return
    val before = _tabs.value.mapTo(HashSet()) { it.id }
    reopen(latest)
    val back = _tabs.value.map { it.id }.filterNot(before::contains)
    _reopened.tryEmit(back.toSet())
  }

  private val _reopened = MutableSharedFlow<Set<TabId>>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val reopened: SharedFlow<Set<TabId>> = _reopened.asSharedFlow()

  fun reopen(closedTab: ClosedTab, closing: Closing = Closing.of(closedTab)) =
    bringBack(closedTab, if (closing.reopensInPlace) BringBack.InPlace else BringBack.ToFront)

  private enum class BringBack { ToFront, InPlace, ForLater }

  private fun bringBack(closedTab: ClosedTab, way: BringBack) {
    val position = _closed.value.indexOf(closedTab)
    if (position < 0) return
    val current = _closed.value[position]
    val strip = _tabs.value.toMutableList()
    val placed = mutableListOf<Int>()
    var activate: TabState? = null
    val renamed = mutableMapOf<TabId, TabId>()
    current.members.forEachIndexed { i, member ->
      val leftBehind = member.id?.let { id -> strip.indexOfFirst { it.block == id && it.closedSource != null } } ?: -1
      val at = if (leftBehind >= 0) leftBehind else member.index.coerceAtMost(strip.size)
      noteTitle(member)
      val tab = newTab(
        member.routes, member.block, addedIn = member.addedIn, place = member.place, keptAt = member.keptAt,
        closedSource = member.closedSource, gridTouched = member.gridTouched, mosaic = member.mosaic,
        openedFrom = member.openedFrom, refines = member.refines, preview = member.preview, foundOn = member.foundOn,
        later = member.later,
      )
      member.id?.let { renamed[it] = tab.id }
      strip.add(at, tab)
      placed += at
      if (way != BringBack.ForLater && ((current.size == 1 && way == BringBack.ToFront) || current.activeMember == i)) activate = tab
    }
    strip.forEach { tab ->
      tab.block?.let(renamed::get)?.let {
        tab.block = it
        tab.closedSource = null
      }
      tab.openedFrom?.let(renamed::get)?.let { tab.openedFrom = it }
    }
    val remembered = returns.entries.associate { (head, tab) -> (renamed[head] ?: head) to (renamed[tab] ?: tab) }
    returns.clear()
    returns.putAll(remembered)
    jumps.replaceAll { it.copy(to = renamed[it.to] ?: it.to, from = renamed[it.from] ?: it.from) }
    val back = renamed.values.toSet()
    val laterBack = strip.filter { it.id in back && it.later }.mapTo(mutableSetOf()) { it.id }
    renamed.forEach { (old, new) -> droppedFrom.remove(old)?.let { droppedFrom[new] = it } }
    droppedFrom.keys.toList().forEach { block -> droppedFrom[block] = droppedFrom.getValue(block) - renamed.keys }
    keptRun = keptRun?.reopened(renamed) { it in laterBack }
    fun shifted(index: Int) = placed.fold(index) { at, returned -> if (returned <= at) at + 1 else at }
    _closed.value = _closed.value.filterIndexed { i, _ -> i != position }.mapIndexed { i, later ->
      (if (i >= position) later.moved(::shifted) else later).renamed(renamed)
    }
    val before = _tabs.value
    val unpinned = FeedItems.leaving(emptyList(), pinned)
    val dropped = mutableMapOf<TabState, TabState?>()
    strip.removeAll { tab ->
      if (before.any { it === tab }) return@removeAll false
      val feed = tab.feed ?: return@removeAll false
      val item = if (feed in pinned) before.firstOrNull { it.feed == feed } ?: return@removeAll false else null
      if (feed !in pinned && feed !in unpinned) return@removeAll false
      if (activate === tab) activate = item
      dropped[tab] = item
      tab.clear()
      true
    }
    dropped.forEach { (tab, item) ->
      val joining = strip.filter { it.block == tab.id }
      if (item == null) {
        leaveKeptPosts(tab, strip)
      } else if (joining.isNotEmpty()) {
        joining.forEach { it.block = item.id }
        strip.removeAll(joining)
        strip.addAll(blockEnd(strip, strip.indexOf(item)) + 1, joining)
      }
    }
    order = strip
    activate?.let { show(it, if (it.openedFrom != null && it.top?.isLone != true) Arrival.Opened else Arrival.GoTo) }
    activate?.let { grid ->
      openerOf(grid)?.let { opener -> if (opener.head != grid.head && returns[grid.head] == null) returns[grid.head] = opener.id }
    }
    current.members.forEach { member ->
      val item = member.wayBack ?: return@forEach
      val head = (member.block ?: member.id)?.let { renamed[it] ?: it } ?: return@forEach
      if (returns[head] == null && _tabs.value.any { it.head == head }) returns[head] = renamed[item] ?: item
    }
  }

  private fun member(tab: TabState, index: Int) = ClosedTab.Member(
    tab.backStack.filterIsInstance<Route>(), index, tab.id, tab.block, tab.addedIn, tab.place, tab.keptAt,
    tab.closedSource, tab.loadedTitle, gridTouched = tab.savedTouched == true, mosaic = tab.mosaic,
    openedFrom = tab.openedFrom, refines = tab.refines, preview = tab.preview, foundOn = tab.foundOn, later = tab.later,
  )

  private fun noteTitle(member: ClosedTab.Member) {
    val post = member.routes.lastOrNull() as? Route.Post
    if (post != null && member.title != null) loadedTitle(post, member.title)
  }

  private fun leaveKeptPosts(closing: TabState, remaining: List<TabState>): Int {
    val search = sourceSearch(closing.top)?.takeIf { headsKeptPosts(closing.kind) } ?: return 0
    val kept = remaining.filter { it.block == closing.id && it !== closing }
    kept.forEach { it.closedSource = search }
    return kept.size
  }

  fun clearAll() {
    counting = false
    try {
      closeAll(_tabs.value.map { it.id })
      returns.clear()
      jumps.clear()
      keptRun = null
      droppedFrom.clear()
      val feeds = _tabs.value.filter(::isFeedItem)
      if (feeds.isEmpty()) return
      feeds.forEach { it.clear() }
      val browse = newTab(listOf(Route.Grid(emptyList())))
      order = listOf(browse) + feeds.mapNotNull { it.feed }.map { newTab(listOf(Route.Grid(listOf(it.tag)))) }
      show(browse)
    } finally {
      counting = true
    }
  }

  fun closeAll(ids: Collection<TabId>): ClosedTab? {
    val all = _tabs.value
    val wanted = ids.toSet() - all.filter(::isFeedItem).map { it.id }.toSet()
    val indices = all.indices.filter { all[it].id in wanted }
    when (indices.size) {
      0 -> return null
      1 -> return close(all[indices.single()].id)
    }
    val activeIndex = all.indexOfFirst { it.id == _active.value }
    val batch = ClosedTab(
      members = indices.map { member(all[it], it) },
      activeMember = indices.indexOf(activeIndex).takeIf { it >= 0 },
      tagNames = tagNames,
      closedAt = clock.now(),
    )
    _closed.value = (_closed.value + batch).takeLast(REOPENABLE)
    indices.forEach { dropped(all[it]) }
    indices.forEach { all[it].clear() }
    val rest = all.filter { it.id !in wanted }
    indices.forEach { leaveKeptPosts(all[it], rest) }
    val landing = if (batch.activeMember == null) null else after(all, activeIndex, wanted) ?: before(all, activeIndex, wanted)
    closeDown(rest, landing)
    return batch
  }

  private fun closeDown(rest: List<TabState>, landing: TabState?) {
    if (rest.isEmpty()) {
      val fresh = newTab(listOf(Route.Grid(emptyList())))
      order = listOf(fresh)
      show(fresh)
      return
    }
    order = rest
    landing?.let { show(it, Arrival.Back) }
  }

  private fun after(all: List<TabState>, index: Int, gone: Set<TabId>) = all.drop(index + 1).firstOrNull { it.id !in gone }

  private fun before(all: List<TabState>, index: Int, gone: Set<TabId>) = all.take(index).lastOrNull { it.id !in gone }

  fun viewedKeptPosts(seen: SeenStore): List<TabId> = _tabs.value
    .filter { it.kind.isKept && it.viewedSinceKept(seen) }
    .map { it.id }

  fun keptPostsLeftToView(seen: SeenStore): Int = _tabs.value.count { it.kind.isKept && !it.viewedSinceKept(seen) }

  fun keptPostsForLater(): Int = _tabs.value.count { it.kind.isKept && it.later }

  fun everyKeptPostViewed(seen: SeenStore): Boolean = _tabs.value.any { it.kind.isKept } && keptPostsLeftToView(seen) == 0

  fun clearViewed(seen: SeenStore): ClosedTab? {
    val wanted = viewedKeptPosts(seen).toSet()
    if (wanted.isEmpty()) return null
    val all = _tabs.value
    val indices = all.indices.filter { all[it].id in wanted }
    val activeIndex = activeIndex(all)
    val activeMember = indices.indexOf(activeIndex).takeIf { it >= 0 }
    val shown = all.getOrNull(activeIndex)
    val landing = if (activeMember != null) clearedLanding(all, activeIndex, wanted) else null
    val wayBack = landing?.id?.takeIf { it == returns[shown?.head] }
    val batch = ClosedTab(
      members = indices.map { member(all[it], it).let { m -> if (it == activeIndex) m.copy(wayBack = wayBack) else m } },
      activeMember = activeMember,
      cleared = true,
      tagNames = tagNames,
      closedAt = clock.now(),
    )
    val inRun = shown != null && inKeptRun(shown.id)
    _closed.value = (_closed.value + batch).takeLast(REOPENABLE)
    all.filter { it.id in wanted }.forEach { it.clear() }
    sessions.startNew()
    droppedFrom.clear()
    val startOfToday = today?.startOfToday()
    notToday = if (startOfToday == null) emptySet() else all.filter { it.id in wanted && !it.seenSinceKept(seen, startOfToday) }.mapTo(HashSet()) { it.id }
    closeDown(all.filter { it.id !in wanted }, landing)
    notToday = emptySet()
    if (activeMember != null) {
      if (inRun) leaveKeptRunUnless(_active.value)
      clearedTo = _active.value
    }
    return batch
  }

  private fun clearedLanding(all: List<TabState>, index: Int, gone: Set<TabId>): TabState? {
    val shown = all[index]
    val rest = all.filter { it.id !in gone }
    if (inKeptRun(shown.id)) {
      (keptNeighbour(rest, shown.id, forward = true) ?: keptNeighbour(rest, shown.id, forward = false))?.let { return it }
    }
    val next = after(all, index, gone)
    if (next?.head != shown.head) returnFor(rest, shown.head)?.let { return it }
    return next ?: before(all, index, gone)
  }

  var clearedTo: TabId? = null
    private set

  private fun restoreFaded(faded: SavedFaded) {
    val members = faded.tabs.filter { it.routes.isNotEmpty() }.distinctBy { it.id }
    val head = members.firstOrNull() ?: return
    members.forEach { tab ->
      val post = tab.routes.last() as? Route.Post
      if (post != null && tab.title != null) loadedTitle(post, tab.title)
    }
    fun kept(block: TabId) = members.drop(1).map { tab ->
      newTab(tab.routes, block = block, keptAt = tab.keptAt?.let(Instant::fromEpochSeconds))
    }
    val all = _tabs.value
    val open = sourceKeyOf(head.routes.last())?.let(::sourceFor)
    if (open != null) {
      order = all.toMutableList().apply { addAll(blockEnd(all, all.indexOf(open)) + 1, kept(open.id)) }
      return
    }
    val source = newTab(
      head.routes, place = head.place, gridTouched = head.gridTouched == true, mosaic = QueueMosaic.restored(head.mosaic, head.posts),
      preview = head.preview,
    )
    val back = closedBlockFor(source.top)
    back.forEach {
      it.block = source.id
      it.closedSource = null
    }
    insert(back.firstOrNull()?.let(all::indexOf) ?: all.size, source)
    val at = _tabs.value.indexOf(source) + 1
    order = _tabs.value.toMutableList().apply { addAll(at, kept(source.id)) }
  }

  fun backToStart(gOnly: Boolean) {
    _tabs.value.firstOrNull { !(gOnly && isAdult(it, null)) }?.let(::show)
  }

  fun tabsToTheRight(): List<TabId> {
    val all = _tabs.value
    return all.drop(all.indexOfFirst { it.id == _active.value } + 1).filter(::canClose).map { it.id }
  }

  fun otherTabs(): List<TabId> = _tabs.value.filter { it.id != _active.value && canClose(it) }.map { it.id }

  fun duplicateTabs(): List<TabId> {
    val all = _tabs.value
    val byPost = all.filter { it.top is Route.Post }.groupBy { (it.top as Route.Post).id }.values
    val closing = byPost.flatMapTo(mutableSetOf()) { copies ->
      val kept = copies.firstOrNull { it.id == _active.value } ?: copies.first()
      copies.filter { it !== kept }.map { it.id }
    }
    return all.map { it.id }.filter { it in closing }
  }

  fun favouritedTabs(isFavourited: (postId: String) -> Boolean): List<TabId> =
    _tabs.value.filter { tab -> (tab.top as? Route.Post)?.let { isFavourited(it.id) } == true }.map { it.id }

  fun move(from: Int, to: Int) {
    val all = _tabs.value
    if (from !in all.indices || to !in all.indices || from == to) return
    order = all.toMutableList().apply { add(to, removeAt(from)) }
  }

  fun moveInQueue(id: TabId, down: Boolean): Int? {
    val step = queueStep(id, down) ?: return null
    step.detach?.let {
      it.block = null
      it.closedSource = null
    }
    val before = _tabs.value.indexOf(step.moved)
    order = step.tabs
    return _tabs.value.indexOf(step.moved) - before
  }

  fun moveToEnd(id: TabId): Boolean {
    val all = _tabs.value
    val index = all.indexOfFirst { it.id == id }
    if (index < 0 || blockStart(all, index) != index) return false
    val end = blockEnd(all, index)
    order = all.subList(0, index) + all.subList(end + 1, all.size) + all.subList(index, end + 1)
    return true
  }

  fun canMoveInQueue(id: TabId, down: Boolean): Boolean = queueStep(id, down) != null

  private class QueueStep(val tabs: List<TabState>, val moved: TabState, val detach: TabState?)

  private fun queueStep(id: TabId, down: Boolean): QueueStep? {
    val all = _tabs.value.toMutableList()
    var index = all.indexOfFirst { it.id == id }
    val closedHead = index < 0
    if (closedHead) index = all.indexOfFirst { it.block == id && it.closedSource != null }
    if (index < 0) return null
    val tab = all[index]
    val start = blockStart(all, index)
    val end = blockEnd(all, index)
    val orphan = tab.closedSource != null
    if (!closedHead && (index != start || orphan)) {
      val firstPost = if (orphan) start else start + 1
      val within = if (down) index < end else index > firstPost
      val to = when {
        within -> index + if (down) 1 else -1
        down -> end
        else -> start
      }
      all.add(to, all.removeAt(index))
      return QueueStep(all, tab, detach = tab.takeUnless { within })
    }
    val block = all.subList(start, end + 1).toList()
    val to = if (down) {
      val next = end + 1
      if (next > all.lastIndex) return null
      blockEnd(all, next) - block.size + 1
    } else {
      val previous = start - 1
      if (previous < 0) return null
      blockStart(all, previous)
    }
    all.subList(start, end + 1).clear()
    all.addAll(to, block)
    return QueueStep(all, tab, detach = null)
  }

  fun showBrowse() {
    val browse = Route.Grid(emptyList())
    if (activeTab.top == browse) return
    openInFront(browse)
  }

  fun cycle(forward: Boolean) {
    step(forward, wrap = true)
  }

  @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
  private fun changes(): Flow<SavedTabs> = combine(_tabs, _active, _closed, ::Triple)
    .flatMapLatest { (tabs, active, closed) -> largeReadSnapshotFlow { savedState(tabs, active, closed) } }
    .distinctUntilChanged()
    .debounce(SAVE_DELAY)

  internal fun saved(): SavedTabs = savedState(_tabs.value, _active.value)

  private fun savedState(all: List<TabState>, active: TabId, closed: List<ClosedTab> = _closed.value): SavedTabs =
    savedState(all, all.indexOfFirst { it.id == active }.coerceAtLeast(0), closed)

  private fun startOfToday(): Instant = zone().let { zone -> clock.now().toLocalDateTime(zone).date.atStartOfDayIn(zone) }

  private fun restoredClosed(saved: List<SavedClosed>): List<ClosedTab> {
    val since = startOfToday().epochSeconds
    return saved.filter { it.at >= since }.takeLast(REOPENABLE).mapNotNull { entry ->
      if (entry.members.isEmpty() || entry.members.any { it.routes.isEmpty() }) return@mapNotNull null
      ClosedTab(
        entry.members.map(::restoredMember),
        activeMember = entry.active?.takeIf { it in entry.members.indices },
        cleared = entry.cleared == true,
        keptStay = entry.keptStay ?: 0,
        tagNames = tagNames,
        closedAt = Instant.fromEpochSeconds(entry.at),
      )
    }
  }

  private fun savedClosed(closed: ClosedTab) = SavedClosed(
    at = closed.closedAt.epochSeconds,
    members = closed.members.map { member ->
      SavedClosedMember(
        index = member.index,
        routes = member.routes,
        id = member.id?.value,
        block = member.block?.value,
        addedIn = member.addedIn.value,
        place = member.place,
        keptAt = member.keptAt?.epochSeconds,
        closedSource = member.closedSource,
        title = member.title,
        gridTouched = true.takeIf { member.gridTouched },
        mosaic = member.mosaic?.thumbs?.takeIf { it.isNotEmpty() },
        posts = member.mosaic?.posts,
        openedFrom = member.openedFrom?.value,
        refines = member.refines,
        preview = member.preview,
        foundOn = member.foundOn,
        wayBack = member.wayBack?.value,
        later = true.takeIf { member.later },
      )
    },
    active = closed.activeMember,
    cleared = true.takeIf { closed.cleared },
    keptStay = closed.keptStay.takeIf { it > 0 },
  )

  private fun restoredMember(saved: SavedClosedMember) = ClosedTab.Member(
    routes = saved.routes,
    index = saved.index,
    id = saved.id?.let(::TabId),
    block = saved.block?.let(::TabId),
    addedIn = saved.addedIn?.let(::SessionId) ?: SessionId.EARLIER,
    place = saved.place,
    keptAt = saved.keptAt?.let(Instant::fromEpochSeconds),
    closedSource = saved.closedSource,
    title = saved.title,
    gridTouched = saved.gridTouched == true,
    mosaic = QueueMosaic.restored(saved.mosaic, saved.posts),
    openedFrom = saved.openedFrom?.let(::TabId),
    refines = saved.refines,
    preview = saved.preview,
    foundOn = saved.foundOn,
    wayBack = saved.wayBack?.let(::TabId),
    later = saved.later == true,
  )

  private fun savedState(tabs: List<TabState>, activeIndex: Int, closed: List<ClosedTab>) = SavedTabs(
    activeIndex = activeIndex,
    tabs = tabs.map { tab ->
      SavedTab(
        tab.id.value,
        tab.backStack.filterIsInstance<Route>(),
        tab.block?.value,
        tab.place,
        kind = tab.kind,
        touched = tab.touched.epochSeconds,
        addedIn = tab.addedIn.value,
        keptAt = tab.keptAt?.epochSeconds,
        closedSource = tab.closedSource,
        title = tab.loadedTitle,
        mosaic = tab.mosaic?.thumbs?.takeIf { it.isNotEmpty() },
        posts = tab.mosaic?.posts,
        gridTouched = tab.savedTouched,
        openedFrom = tab.openedFrom?.value,
        refines = tab.refines,
        preview = tab.preview,
        foundOn = tab.foundOn,
        later = true.takeIf { tab.later },
      )
    },
    version = SavedTabs.VERSION,
    session = sessions.current.value,
    returns = returns.entries
      .filter { (head, item) -> tabs.any { it.head == head } && tabs.any { it.id == item } }
      .associate { (head, item) -> head.value to item.value },
    jumps = jumps
      .filter { jump -> tabs.any { it.id == jump.to } && tabs.any { it.id == jump.from } }
      .map { SavedJump(it.to.value, it.from.value, it.moved) },
    closed = startOfToday().let { since -> closed.filter { it.closedAt >= since }.map(::savedClosed) },
  )

  private fun newTab(
    routes: List<Route>,
    block: TabId? = null,
    addedIn: SessionId = sessions.current,
    place: GridPlace? = null,
    keptAt: Instant? = null,
    closedSource: List<String>? = null,
    gridTouched: Boolean = false,
    mosaic: QueueMosaic? = null,
    openedFrom: TabId? = null,
    refines: List<String>? = null,
    preview: SourcePreview? = null,
    foundOn: FoundOn? = null,
    later: Boolean = false,
  ): TabState {
    val now = clock.now()
    val kept = keptAt ?: now.takeIf { keptRoute(routes.lastOrNull()) != null }
    return TabState(
      TabId(Uuid.random().toString()), routes, block, place, addedIn, touched = now, keptAt = kept,
      closedSource = closedSource, loadedTitles = loaded::get, mosaic = mosaic, gridTouched = gridTouched,
      tagNames = tagNames, openedFrom = openedFrom, refines = refines, preview = preview, foundOn = foundOn, later = later,
    )
  }

  companion object {
    val SAVE_DELAY = 1.seconds

    const val REOPENABLE = 20

    const val MAX_JUMPS = 50
  }
}

internal data class Jump(val to: TabId, val from: TabId, val moved: Boolean = false)

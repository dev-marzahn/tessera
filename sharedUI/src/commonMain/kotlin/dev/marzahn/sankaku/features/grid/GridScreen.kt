package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import dev.marzahn.sankaku.shell.SharedTransitions
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.marzahn.sankaku.GridScrollbar
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.merge
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.features.post.PostActions
import dev.marzahn.sankaku.features.settings.FollowedTagsPage
import dev.marzahn.sankaku.queue.FeedHeading
import dev.marzahn.sankaku.queue.FeedReading
import dev.marzahn.sankaku.queue.LikedToday
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.TodayStore
import kotlinx.coroutines.flow.filter
import kotlin.time.Clock
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.PageCursor
import dev.marzahn.sankaku.tabs.ClosedTabUndo
import dev.marzahn.sankaku.tabs.UNKEPT
import dev.marzahn.sankaku.tabs.FeedCatchUp
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.tabs.DescribeTab
import dev.marzahn.sankaku.tabs.LocalTabEntry
import dev.marzahn.sankaku.tabs.isStartedSource
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import kotlinx.coroutines.flow.Flow
import dev.marzahn.sankaku.ui.Message
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import dev.marzahn.sankaku.tabs.QueueMosaic
import dev.marzahn.sankaku.tabs.hasPreview
import dev.marzahn.sankaku.tabs.sortDeckOrNull
import dev.marzahn.sankaku.tabs.FoundHereChip
import dev.marzahn.sankaku.tabs.followInsteadOrNull
import dev.marzahn.sankaku.features.post.TagFollow
import dev.marzahn.sankaku.ui.components.DoneWithFollow
import dev.marzahn.sankaku.tabs.TabContents
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.Tip
import dev.marzahn.sankaku.ui.Tips
import dev.marzahn.sankaku.ui.components.AppLoadingIndicator
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import androidx.compose.foundation.layout.windowInsetsPadding
import dev.marzahn.sankaku.shell.LocalOpenSearch
import dev.marzahn.sankaku.shell.signedInName
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.ui.components.ApiErrorState
import dev.marzahn.sankaku.ui.components.GridOfflineState
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.PostsApi
import dev.marzahn.sankaku.api.advice
import dev.marzahn.sankaku.shell.RetryWhenBack
import dev.marzahn.sankaku.ui.components.rememberHeldKeys
import dev.marzahn.sankaku.ui.components.KeyFocusHolder
import dev.marzahn.sankaku.ui.components.LocalKeyFocus
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.isCommand
import dev.marzahn.sankaku.ui.components.tabStep
import kotlinx.coroutines.Job
import dev.marzahn.sankaku.ui.components.LargeIndicatorSize
import dev.marzahn.sankaku.ui.components.ScrollToTopButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import dev.marzahn.sankaku.shell.GridRefresh
import dev.marzahn.sankaku.shell.LocalChromePadding
import dev.marzahn.sankaku.shell.LocalCoverChrome
import dev.marzahn.sankaku.shell.LocalTabBarSlide
import dev.marzahn.sankaku.shell.LocalUnderQueuePage
import dev.marzahn.sankaku.shell.underQueuePageMoving
import dev.marzahn.sankaku.ui.components.spatialSpec
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.components.PullToRefresh
import dev.marzahn.sankaku.ui.components.rememberAlreadyInTab
import dev.marzahn.sankaku.tabs.queueSwipe
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GridScreen(
  route: Route.Grid,
  navigator: Navigator = koinInject(),
  signIn: SignInController = koinInject(),
  refresh: GridRefresh = koinInject(),
  actions: PostActions = koinInject(),
  tabs: TabsController = koinInject(),
  cursorRequest: GridCursorRequest = koinInject(),
  tips: Tips = koinInject(),
  seenStore: SeenStore = koinInject(),
  closeUndo: ClosedTabUndo = koinInject(),
  messages: Messages = koinInject(),
  today: TodayStore = koinInject(),
  session: AuthSession = koinInject(),
  catchUp: FeedCatchUp = koinInject(),
) {
  val tabEntry = LocalTabEntry.current
  val viewModel = koinViewModel<GridViewModel>(key = "grid-${route.tags}") {
    parametersOf(route.tags, tabEntry?.tab?.placeFor(route))
  }
  val items = viewModel.posts.collectAsLazyPagingItems()
  var retryPending by remember { mutableStateOf(false) }
  RetryWhenBack {
    val pending = viewModel.lookup.value
    if (pending is PlaceLookup.Unreachable) {
      if (pending.error is ApiError.Network) viewModel.retryPlace()
    } else {
      retryPending = true
    }
  }
  if (retryPending) {
    LaunchedEffect(Unit) {
      withTimeoutOrNull(RETRY_WAIT_MS) { snapshotFlow { items.loadState.failedUnreachable() }.first { it } }
        ?.let { items.retry() }
      retryPending = false
    }
  }
  val overrides by viewModel.overrides.collectAsStateWithLifecycle()
  val gridState = rememberLazyStaggeredGridState()
  val scrollUp = rememberScrollUpWatch(gridState)
  val scope = rememberCoroutineScope()
  val openSearch = LocalOpenSearch.current
  val midway by viewModel.midway.collectAsStateWithLifecycle()
  val signedOutNotice by viewModel.signedOutNotice.collectAsStateWithLifecycle()
  val marks by viewModel.marks.collectAsStateWithLifecycle()
  val feedContext by viewModel.feedContext.collectAsStateWithLifecycle()
  val feedPosts by remember(viewModel) {
    derivedStateOf { if (viewModel.feed == null) emptyList() else feedPostsOf(items.itemSnapshotList.items, viewModel::cursorOf) }
  }
  val me = session.signedInName()
  val todayChanges = today.changes.collectAsStateWithLifecycle()
  val likedTodayShown = LikedToday.applies(route.tags, me) && !midway
  val gridItems by remember(viewModel, likedTodayShown, me) {
    derivedStateOf {
      if (likedTodayShown) {
        todayChanges.value
        val ids = items.itemSnapshotList.items.map { it.id }
        return@derivedStateOf GridItems(LikedToday.headings(route.tags, me, ids, today.counts().liked, today::isLiked))
      }
      val feed = viewModel.feed ?: return@derivedStateOf GridItems.None
      GridItems(FeedReading.headings(feed, feedPosts, atTop = !midway, marks, Clock.System.now(), feedContext))
    }
  }
  val latePosts by remember(viewModel) {
    derivedStateOf { viewModel.feed?.let { FeedReading.late(it, feedPosts, atTop = !midway, marks, feedContext) }.orEmpty() }
  }
  var showingFollowed by rememberSaveable { mutableStateOf(false) }
  if (showingFollowed) FollowedTagsPage(onDismiss = { showingFollowed = false }, onOpenTag = { showingFollowed = false })
  FeedHeadingEffects(viewModel, items, gridState, { gridItems }, { feedPosts })
  DescribeTab(route) { TabContents.grid(items.itemSnapshotList.items, gridState.firstPostIndex(gridItems)) }
  LaunchedEffect(gridState, items, route) {
    val tab = tabEntry?.tab ?: return@LaunchedEffect
    snapshotFlow { QueueMosaic.of(items.itemSnapshotList.items, gridState.firstPostIndex(gridItems)) }
      .filterNotNull()
      .distinctUntilChanged()
      .collect { tab.reportMosaic(route, it) }
  }
  if (hasPreview(route)) {
    LaunchedEffect(viewModel, route) {
      val tab = tabEntry?.tab ?: return@LaunchedEffect
      viewModel.preview.filterNotNull().collect { tab.reportPreview(route, it) }
    }
  }
  fun markTouched() {
    tabEntry?.tab?.markTouched(route)
  }
  if (tabEntry?.tab?.isTouched(route) == false) {
    LaunchedEffect(gridState) {
      snapshotFlow { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }.first { it }
      markTouched()
    }
  }
  val lookup by viewModel.lookup.collectAsStateWithLifecycle()
  LaunchedEffect(viewModel) {
    viewModel.lookup.collectLatest { found ->
      if (found !is PlaceLookup.Found || found.firstPost == null) return@collectLatest
      val at = snapshotFlow {
        val list = items.itemSnapshotList
        if (list.firstOrNull()?.id != found.firstPost) -1 else list.indexOfFirst { it?.id == found.post }
      }.first { it >= 0 }
      gridState.scrollToItem(gridItems.itemOf(at))
      markTouched()
      viewModel.placeShown()
    }
  }
  LaunchedEffect(viewModel) {
    viewModel.lookup.collect {
      if (it != PlaceLookup.GivenUp || !viewModel.sayGivenUp()) return@collect
      messages.show(Message(PLACE_GIVEN_UP, actionLabel = "Try again", isError = true, onAction = viewModel::retryPlace))
    }
  }
  LaunchedEffect(viewModel, gridState) {
    val tab = tabEntry?.tab ?: return@LaunchedEffect
    viewModel.lookup.map { it == null || it == PlaceLookup.GivenUp }.distinctUntilChanged().collectLatest { settled ->
      if (!settled) return@collectLatest
      gridState.placeReports(items, midway, viewModel::cursorOf, postOf = { gridItems.postOf(it) }, start = viewModel.startPlace)
        .drop(1)
        .collect {
          tab.reportPlace(route, it)
          tabs.touch(tab.id)
        }
    }
  }
  val tabList by tabs.tabs.collectAsStateWithLifecycle()
  val openInTabs by remember(tabList) { derivedStateOf { tabs.openPosts(tabList) } }
  val sort = sortDeckOrNull()
  val followInstead = followInsteadOrNull()
  val endOfSource = tabEntry?.tab?.takeIf { it.top == route && it.kind == QueueKind.Source }?.let { tab ->
    val keptStay = tabs.keptPostsOf(tab)
    val sorting = sort != null && sort.goneIn == tab.id
    val ended = items.itemCount > 0 && items.loadState.append.endOfPaginationReached
    if (followInstead != null && ended) LaunchedEffect(tab.id, route) { followInstead.load(tab) }
    val artist = followInstead?.offer(tab)
    remember(tab, tab.title, keptStay, sorting, artist) {
      val followAndClose = artist?.let {
        DoneWithFollow(TagFollow) {
          followInstead.followAndClose(tab) { unfollow ->
            when {
              sorting -> sort.followedInstead(tab.id, unfollow)
              platformIsTouch -> closeUndo.close(tab.id, unfollow)
              else -> tabs.close(tab.id)
            }
          }
        }
      }
      if (sorting) {
        EndOfSource(tab.title, keptStay, onClose = sort::closeAndNext, sortLater = sort::laterFromGrid, followAndClose = followAndClose)
      } else {
        EndOfSource(tab.title, keptStay, onClose = { if (platformIsTouch) closeUndo.close(tab.id) else tabs.close(tab.id) }, followAndClose = followAndClose)
      }
    }
  }

  var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
  var sharedId by rememberSaveable { mutableStateOf<String?>(null) }

  suspend fun reloadFromTop(pastCache: Boolean) {
    viewModel.toTop()
    tabEntry?.tab?.reportPlace(route, null)
    if (pastCache) viewModel.forgetPages()
    items.refresh()
    withTimeoutOrNull(REFRESH_START_TIMEOUT_MS) { snapshotFlow { items.loadState.refresh }.first { it is LoadState.Loading } }
    snapshotFlow { items.loadState.refresh }.first { it !is LoadState.Loading }
    gridState.scrollToItem(0)
  }

  LaunchedEffect(viewModel) {
    refresh.requests.collect {
      if (viewerIndex != null) return@collect
      refresh.setRunning(true)
      try {
        reloadFromTop(pastCache = true)
      } finally {
        refresh.setRunning(false)
      }
    }
  }
  if (viewModel.feed == Feed.Following) {
    LaunchedEffect(viewModel) {
      catchUp.request.filterNotNull().filter { it.tab == tabEntry?.tab?.id }.collect { asked ->
        catchUp.take(asked)
        if (viewerIndex != null) {
          viewerIndex = null
          sharedId = null
        }
        when (asked.at) {
          FeedCatchUp.At.Gap -> {
            val at = gridItems.itemOfHeading { it is FeedHeading.Continue }
            if (at != null) gridState.scrollToItem(at) else viewModel.continueFromGap()
          }
          FeedCatchUp.At.Top -> if (viewModel.midway.value || viewModel.lookup.value != null) {
            reloadFromTop(pastCache = false)
          } else {
            gridState.scrollToItem(0)
          }
        }
      }
    }
  }
  val followed = viewerIndex?.let { index -> followedIndex(index, sharedId, items.itemCount) { items.peekOrNull(it)?.id } }
  val listShown = items.itemCount > 0 || items.loadState.refresh !is LoadState.Loading
  SideEffect {
    val index = viewerIndex
    if (index != null && followed != index && listShown) {
      viewerIndex = followed
      if (followed == null) sharedId = null
    }
  }
  val viewerTransition = remember { mutableStateOf<MutableTransitionState<Boolean>?>(null) }
  val viewerOnScreen = remember { ViewerOnScreen(startOpen = viewerIndex != null) }
  val viewerSettled = viewerTransition.value?.let { it.isIdle && !it.currentState } ?: true
  val laidOut by rememberLaidOutCells(gridState) {
    viewerIndex != null || viewerTransition.value?.let { it.currentState || it.targetState } == true
  }
  val order by remember {
    derivedStateOf {
      val list = items.itemSnapshotList
      readingOrder(list.size, { list[it]?.id }, laidOut)
    }
  }
  var shownIndex by remember { mutableStateOf(0) }
  followed?.let { shownIndex = it }
  var opening by remember { mutableStateOf(0) }

  var cursor by rememberSaveable { mutableStateOf<Int?>(null) }
  var pendingFocus by remember { mutableStateOf<Int?>(null) }

  fun putCursor(index: Int?) {
    cursor = index
    pendingFocus = index
  }
  val keyReturns = LocalKeyFocus.current.returns
  LaunchedEffect(keyReturns) {
    val at = cursor ?: return@LaunchedEffect
    if (viewerIndex == null && gridState.layoutInfo.visibleItemsInfo.any { it.index == gridItems.itemOf(at) }) pendingFocus = at
  }
  LaunchedEffect(Unit) {
    if (cursorRequest.take(route.tags)) putCursor(0)
  }
  LaunchedEffect(Unit) {
    snapshotFlow {
      val load = items.loadState
      cursor?.takeIf { load.refresh is LoadState.NotLoading && load.append.endOfPaginationReached && it >= items.itemCount }
    }.filterNotNull().collect { putCursor(null) }
  }
  var cursorStep by remember { mutableStateOf<Job?>(null) }

  fun openViewer(index: Int) {
    markTouched()
    opening++
    val id = items.peekOrNull(index)?.id
    viewerOnScreen.open(id, closingId = sharedId)
    sharedId = id
    viewerIndex = index
  }

  val alreadyInTab = rememberAlreadyInTab()
  val previewHashes = koinInject<PreviewHashes>()
  val keptCopyOf = keptCopyMark(previewHashes, tabs, seenStore)

  fun keep(post: PostDto): Kept {
    markTouched()
    val kept = keepPost(post, route.tags, navigator, tabs, seenStore, keptCopyOf)
    if (!kept.already) previewHashes.recordKept(post.id)
    if (!kept.already && platformIsTouch) viewModel.prefetchKept(post)
    return kept
  }

  fun canUnkeep(post: PostDto): Boolean = platformIsTouch && keptItemOf(post, tabList, keptCopyOf) != null

  fun unkeep(post: PostDto): Boolean {
    if (!platformIsTouch) return false
    val closed = unkeepPost(post, tabs, keptCopyOf) ?: return false
    markTouched()
    closeUndo.offerOnLine(closed, UNKEPT)
    return true
  }

  fun openInTab(post: PostDto) {
    tips.used(keepTip())
    if (unkeep(post)) return
    val kept = keep(post)
    if (kept.already) alreadyInTab.show(post.id, kept.position, again = kept.again)
  }

  fun keepFromViewer(post: PostDto) {
    if (!platformIsTouch) tips.used(Tip.KeepLoop)
    messages.show(keptMessage(keep(post), platformIsTouch))
  }

  val isSeen = seenMark(route, tabEntry?.tab, tabs, seenStore, keptCopyOf)

  var keepTipId by remember { mutableStateOf<String?>(null) }

  fun closeViewer() {
    val index = viewerIndex ?: return
    val closing = opening
    scope.launch {
      try {
        gridState.scrollBackForClose(gridItems.itemOf(index), scrollUp)
      } finally {
        if (opening == closing) {
          sharedId = items.peekOrNull(index)?.id
          viewerIndex = null
          if (cursor != null) putCursor(index)
          if (sharedId != null && (tips.offer(keepTip()) || tips.current == keepTip())) keepTipId = sharedId
        }
      }
    }
  }

  val heldKeys = rememberHeldKeys()

  fun gridKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown || viewerIndex != null) return false
    if (event.isCommand || event.isAltPressed || event.tabStep != null) return false
    val move = gridMoveFor(event.key)
    if (move != null) {
      if (cursorStep?.isActive != true) {
        val from = cursor
        val page = event.isShiftPressed && (move == GridMove.Up || move == GridMove.Down)
        cursorStep = scope.launch { putCursor(gridState.moveCursor(from, move, page, items.itemCount, gridItems)) }
      }
      return true
    }
    val at = cursor ?: return false
    val post = items.peekOrNull(at)?.takeUnless { it.redirectToSignup } ?: return false
    return when (event.key) {
      Shortcuts.OpenInTab.key -> true.also { openInTab(post) }
      Shortcuts.OpenHere.key -> true.also { if (!heldKeys.repeating(event.key)) openViewer(at) }
      else -> false
    }
  }

  fun onGridKey(event: KeyEvent): Boolean = gridKey(event).also { if (it) keepTipId = null }

  val underPage = LocalUnderQueuePage.current
  val pageMoving = underQueuePageMoving()
  val coming = underPage?.transition?.let { it.targetState == EnterExitState.Visible && it.currentState != EnterExitState.Visible } == true
  val cellsWhenItCame = remember(coming) { items.itemCount > 0 }
  val cellsShown = if (coming) cellsWhenItCame else items.itemCount > 0
  val mosaicThumbs = underPage?.let {
    tabEntry?.tab?.takeIf { it.top == route && isStartedSource(it) }?.mosaic?.thumbs?.map { it.md5 }
  }.orEmpty()
  val mosaicMoves = pageMoving && cellsShown && viewerIndex == null && viewerSettled
  val holdForMove = mosaicMoves && mosaicThumbs.isNotEmpty()
  underPage?.transition?.animateFloat({ spatialSpec() }, label = "grid under the queue page") {
    if (it == EnterExitState.Visible || !holdForMove) 1f else 0f
  }

  val atTop by remember(gridState) { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

  SharedTransitions(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().then(heldKeys.modifier).onKeyEvent(::onGridKey).queueSwipe(enabled = viewerIndex == null, from = tabEntry?.tab?.id)) {
      if (viewerIndex == null) KeyFocusHolder()
      val load = items.loadState.refresh
      val refreshing by refresh.running.collectAsStateWithLifecycle()
      Column(Modifier.fillMaxSize().padding(LocalChromePadding.current)) {
        if (signedOutNotice) SignedOutNotice(onSignIn = signIn::open)
        PullToRefresh(
          refreshing,
          onRefresh = refresh::request,
          enabled = platformIsTouch,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
          when {
            load is LoadState.Loading && items.itemCount == 0 ->
              Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val shown by produceState(false) {
                  delay(LOADING_INDICATOR_DELAY_MS)
                  value = true
                }
                if (shown) AppLoadingIndicator(size = LargeIndicatorSize)
              }
            load is LoadState.Error && items.itemCount == 0 ->
              GridMessage {
                when (val error = load.error) {
                  is ApiError.Network -> GridOfflineState(onRetry = items::retry)
                  is ApiError.TagLimit -> EmptySearch(route, title = error.advice().sentence)
                  else -> ApiErrorState(error, onRetry = items::retry, onEditSearch = openSearch, onSignIn = signIn::open)
                }
              }
            load is LoadState.NotLoading && items.itemCount == 0 ->
              GridMessage { EmptySearch(route) }
            else -> {
              PostGrid(
                items = items,
                overrides = overrides,
                state = gridState,
                hiddenId = if (viewerIndex != null) sharedId else null,
                sharedId = sharedId,
                viewerClosing = viewerIndex == null,
                cursor = cursor,
                onOpen = ::openViewer,
                onOpenInNewTab = ::openInTab,
                onToggleFavourite = { post, viaDoubleTap ->
                  tabs.touchActive()
                  actions.toggleFavourite(post, viaDoubleTap, fromGrid = true)
                },
                dimUnfavourited = isFavouritesSearch(route.tags),
                openInTabs = openInTabs,
                isKeptCopy = { keptCopyOf(it) != null },
                canUnkeep = ::canUnkeep,
                onPreviewLoaded = previewHashes::offer,
                alreadyInTab = alreadyInTab,
                isSeen = isSeen,
                isLate = { it in latePosts },
                endOfSource = endOfSource,
                mosaicThumbs = mosaicThumbs,
                underPage = underPage?.takeIf { mosaicMoves },
                keepTipId = keepTipId.takeIf { viewerIndex == null && tips.current == keepTip() },
                keepTipShown = viewerSettled,
                pendingFocus = pendingFocus,
                onFocusTaken = { pendingFocus = null },
                onCellFocused = { if (viewerIndex == null) cursor = it },
                onCellKey = ::onGridKey,
                gridItems = gridItems,
                heading = { placed ->
                  FeedHeadingRow(
                    placed,
                    onContinue = {
                      val at = gridItems.itemOfHeading { it is FeedHeading.Continue }
                      if (at != null) scope.launch { gridState.animateScrollToItem(at) } else viewModel.continueFromGap()
                    },
                    onOfferShown = viewModel::offerShown,
                    onKeep = viewModel::keepGap,
                    onLetGo = viewModel::letGapGo,
                    onFurtherDown = { post -> scope.launch { gridState.animateScrollToItem(gridItems.itemOf(post)) } },
                    onFollowedTags = { showingFollowed = true },
                  )
                },
                modifier = Modifier
                  .fillMaxSize()
                  .then(if (viewerIndex != null) Modifier.semantics { hideFromAccessibility() } else Modifier),
              )
              GridScrollbar(gridState, gridItems.count(items.itemCount), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
              FoundHereChip(
                tabEntry?.tab,
                route,
                visible = viewerIndex == null && atTop,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
              )
              val barSlide = LocalTabBarSlide.current
              ScrollToTopButton(
                visible = scrollUp.backUp || (midway && atTop),
                replaced = platformIsTouch && viewerIndex != null,
                fade = platformIsTouch && (viewerIndex != null || viewerTransition.value?.let { it.currentState || it.targetState } == true),
                onClick = { if (midway) refresh.request() else scope.launch { gridState.animateScrollToItem(0) } },
                compact = platformIsTouch,
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .offset { IntOffset(0, barSlide().roundToInt()) }
                  .then(
                    if (platformIsTouch) {
                      Modifier.windowInsetsPadding(BottomRowLayout.SideInsets).padding(
                        BottomRowLayout.topButtonPadding(
                          room = (BottomRowLayout.WindowRoom - LocalChromePadding.current.calculateBottomPadding()).coerceAtLeast(0.dp),
                        ),
                      )
                    } else {
                      Modifier.padding(end = 28.dp, bottom = TOP_BOTTOM)
                    },
                  ),
              )
            }
          }
        }
      }

      val viewerShown = viewerOnScreen.state
      SideEffect { viewerTransition.value = viewerShown }
      val tab = tabEntry?.tab
      val coverChrome: (Boolean) -> Unit = remember(tab, route) { { covers -> tab?.coverChrome(route, covers) } }
      SideEffect { if (viewerShown.isIdle && !viewerShown.currentState) coverChrome(false) }
      ViewerVisibility(viewerOnScreen, visible = viewerIndex != null) {
        if (viewerIndex != null && followed == null) return@ViewerVisibility
        val index = shownIndex.coerceAtMost(items.itemCount - 1)
        if (index < 0) return@ViewerVisibility
        if (platformIsTouch) {
          CompositionLocalProvider(LocalCoverChrome provides coverChrome) {
            PhoneViewer(
              index = order.positionOf(index),
              postAt = { page -> items.getOrNull(order.indexAt(page))?.let { it.merge(overrides[it.id]) } },
              pageKey = { page -> items.viewerPageKey(page, order) },
              loadedCount = items.itemCount,
              hasMore = items.loadState.append.endOfPaginationReached.not(),
              end = viewerEnd(items.loadState.append),
              onRetryEnd = items::retry,
              sharedId = sharedId,
              visibilityScope = this,
              onShow = { page ->
                val shown = order.indexAt(page)
                viewerIndex = shown
                sharedId = items.peekOrNull(shown)?.id ?: sharedId
              },
              onClose = ::closeViewer,
              opening = opening,
              searchTags = route.tags,
              onKeep = ::keepFromViewer,
              canUnkeep = ::canUnkeep,
              onUnkeep = { unkeep(it) },
            )
          }
          return@ViewerVisibility
        }
        PostViewer(
          index = index,
          postAt = { i -> items.getOrNull(i)?.let { it.merge(overrides[it.id]) } },
          loadedCount = items.itemCount,
          hasMore = items.loadState.append.endOfPaginationReached.not(),
          end = viewerEnd(items.loadState.append),
          onRetryEnd = items::retry,
          sharedId = sharedId,
          visibilityScope = this,
          positionOf = { i -> order.positionOf(i) },
          onStep = { forward ->
            val next = order.indexAt(order.positionOf(index) + if (forward) 1 else -1)
            viewerIndex = next
            sharedId = items.peekOrNull(next)?.id ?: sharedId
          },
          onClose = ::closeViewer,
          opening = opening,
          searchTags = route.tags,
          isRepeat = heldKeys::repeating,
          onKeep = ::keepFromViewer,
        )
      }

      val cover = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(LocalChromePadding.current)
      when (val pending = lookup) {
        PlaceLookup.Looking, is PlaceLookup.Found -> if (load !is LoadState.Error) {
          Column(
            cover,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            AppLoadingIndicator(size = LargeIndicatorSize)
            Text("Loading where you were…", style = MaterialTheme.typography.bodyMedium)
          }
        }
        is PlaceLookup.Unreachable -> Box(cover, contentAlignment = Alignment.Center) {
          if (pending.error is ApiError.Network) {
            GridOfflineState(onRetry = viewModel::retryPlace)
          } else {
            ApiErrorState(pending.error, onRetry = viewModel::retryPlace, onEditSearch = openSearch, onSignIn = signIn::open)
          }
        }
        PlaceLookup.GivenUp, null -> Unit
      }
    }
  }
}

@Stable
internal class ViewerOnScreen(startOpen: Boolean) {
  var state by mutableStateOf(MutableTransitionState(startOpen))
    private set

  fun open(id: String?, closingId: String?) {
    val closing = state.currentState && !state.targetState
    if (closing && id != closingId) state = MutableTransitionState(false)
  }
}

@Composable
internal fun ViewerVisibility(onScreen: ViewerOnScreen, visible: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
  val state = onScreen.state
  state.targetState = visible
  key(state) {
    AnimatedVisibility(visibleState = state, enter = EnterTransition.None, exit = ExitTransition.None, content = content)
  }
}

internal fun followedIndex(index: Int, id: String?, count: Int, idAt: (Int) -> String?): Int? = when {
  id == null -> index
  index < count && idAt(index) == id -> index
  else -> (0 until count).firstOrNull { idAt(it) == id }
}

private class KnownPlace(val place: GridPlace?)

internal fun LazyStaggeredGridState.placeReports(
  items: LazyPagingItems<PostDto>,
  midway: Boolean,
  cursorOf: (String) -> PageCursor?,
  postOf: (item: Int) -> Int? = { it },
  start: GridPlace? = null,
): Flow<GridPlace?> =
  snapshotFlow {
    val visible = layoutInfo.visibleItemsInfo.mapNotNull { info -> postOf(info.index)?.let { it to info } }
    val first = visible.filter { (post, info) -> post < items.itemCount && info.offset.y >= 0 }.minOfOrNull { it.first }
      ?: visible.minOfOrNull { it.first }
      ?: postOf(firstVisibleItemIndex)
      ?: return@snapshotFlow null
    if (first < items.itemCount) KnownPlace(placeOf(first, items, midway, cursorOf, start)) else null
  }
    .filterNotNull()
    .map { it.place }
    .distinctUntilChanged()

private fun placeOf(
  first: Int,
  items: LazyPagingItems<PostDto>,
  midway: Boolean,
  cursorOf: (String) -> PageCursor?,
  start: GridPlace?,
): GridPlace? {
  if (first == 0 && !midway) return null
  val post = items.peekOrNull(first) ?: return null
  val cursor = cursorOf(post.id) ?: PageCursor()
  val index = if (!midway) first else resultIndex(first, items, start)
  return GridPlace(post.id, cursor.next, cursor.number, post.createdAt?.epochSeconds, index)
}

private const val START_LOOK = 2 * PostsApi.PAGE_SIZE

private fun resultIndex(first: Int, items: LazyPagingItems<PostDto>, start: GridPlace?): Int? {
  val startIndex = start?.index ?: return null
  val at = (0 until minOf(items.itemCount, START_LOOK)).firstOrNull { items.peekOrNull(it)?.id == start.post } ?: return null
  return (startIndex - at + first).takeIf { it >= 0 }
}

internal suspend fun LazyStaggeredGridState.scrollFullyIntoView(index: Int): Boolean {
  val offScreen = layoutInfo.visibleItemsInfo.none { it.index == index }
  if (offScreen) scrollToItem(index)
  val info = layoutInfo
  val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return offScreen
  val top = item.offset.y
  val bottom = top + item.size.height
  val start = 0
  val end = shownBottom()
  val delta = when {
    top < start -> top - start
    bottom > end -> minOf(bottom - end, top - start)
    else -> 0
  }
  if (delta != 0) scrollBy(delta.toFloat())
  return offScreen || delta != 0
}

private suspend fun LazyStaggeredGridState.moveCursor(
  from: Int?,
  move: GridMove,
  page: Boolean,
  postCount: Int,
  gridItems: GridItems,
): Int? {
  val cells = cellBounds(postCount, gridItems)
  val at = from?.let(cells::get) ?: return firstShown(cells, ::fullyShows) ?: from
  val height = layoutInfo.viewportSize.height.toFloat()
  val down = if (move == GridMove.Down) 1 else -1
  var scrolled = 0
  suspend fun cellsScrolled(share: Float): Map<Int, IntRect> {
    scrolled += scrollBy(down * height * share).roundToInt()
    withFrameNanos {}
    return cellBounds(postCount, gridItems) + (from to at.translate(0, -scrolled))
  }
  val next = when {
    page -> cellNear(cellsScrolled(PAGE_JUMP), at.center.x, at.center.y)
    move == GridMove.Left || move == GridMove.Right -> nextCell(cells, from, move)
    else -> nextCell(cells, from, move) ?: (1..LOOK_AHEAD_STEPS).firstNotNullOfOrNull {
      nextCell(cellsScrolled(0.5f), from, move)
    }
  }
  scrollFullyIntoView(gridItems.itemOf(next ?: from))
  return next ?: from
}

private const val PAGE_JUMP = 0.85f

private const val LOOK_AHEAD_STEPS = 4

private val TOP_BOTTOM = 20.dp

private const val LOADING_INDICATOR_DELAY_MS = 300L

private const val REFRESH_START_TIMEOUT_MS = 2_000L

private const val RETRY_WAIT_MS = 2_000L

internal const val PLACE_GIVEN_UP = "Couldn't get back to where you were"

internal fun CombinedLoadStates.failedUnreachable(): Boolean =
  listOf(refresh, prepend, append).any { (it as? LoadState.Error)?.error is ApiError.Network }

@Composable
private fun GridMessage(content: @Composable BoxScope.() -> Unit) {
  if (!platformIsTouch) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    return
  }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    Box(
      Modifier.verticalScroll(rememberScrollState()).fillMaxWidth().height(maxHeight),
      contentAlignment = Alignment.Center,
      content = content,
    )
  }
}

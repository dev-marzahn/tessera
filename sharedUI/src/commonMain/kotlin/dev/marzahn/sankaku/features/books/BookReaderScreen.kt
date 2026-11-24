package dev.marzahn.sankaku.features.books

import androidx.compose.animation.AnimatedVisibility
import dev.marzahn.sankaku.ui.components.ButtonHint
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.Disposable
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.compose.zoom.rememberZoomableState
import com.github.panpf.zoomimage.compose.zoom.zoom
import com.github.panpf.zoomimage.zoom.ScalesCalculator
import com.google.fonts.icons.close
import com.google.fonts.icons.info
import com.google.fonts.icons.more_vert
import com.google.fonts.icons.open_in_new
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.PlatformBackHandler
import dev.marzahn.sankaku.PlatformSystemBars
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.PostTitle
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.data.BookFavourite
import dev.marzahn.sankaku.data.BooksRepository
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.data.favourite
import dev.marzahn.sankaku.data.BookPlace
import dev.marzahn.sankaku.data.ReadingProgress
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.features.post.AnimatedSample
import dev.marzahn.sankaku.features.post.PostVideo
import dev.marzahn.sankaku.features.post.VideoPlayback
import dev.marzahn.sankaku.features.post.rememberPlatformVideoPlayback
import dev.marzahn.sankaku.features.post.rememberVideoPlayback
import dev.marzahn.sankaku.features.post.aspectRatio
import dev.marzahn.sankaku.features.post.isAnimated
import dev.marzahn.sankaku.features.post.isFlash
import dev.marzahn.sankaku.features.post.isVideo
import dev.marzahn.sankaku.features.post.platformPlaysVideo
import dev.marzahn.sankaku.images.ImageFailure
import dev.marzahn.sankaku.images.PostImage
import dev.marzahn.sankaku.images.failure
import dev.marzahn.sankaku.images.fittedTo
import dev.marzahn.sankaku.images.postImageRequest
import dev.marzahn.sankaku.platformBackKeys
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.shell.RetryWhenBack
import dev.marzahn.sankaku.features.tag.TagPages
import dev.marzahn.sankaku.features.tag.rememberTagPageShown
import dev.marzahn.sankaku.tabs.LocalOpenTabList
import dev.marzahn.sankaku.tabs.QueueBar
import dev.marzahn.sankaku.tabs.TabId
import dev.marzahn.sankaku.tabs.queueSwipe
import dev.marzahn.sankaku.ui.components.AppLoadingIndicator
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import dev.marzahn.sankaku.ui.components.ApiErrorState
import dev.marzahn.sankaku.ui.components.FavouriteBursts
import dev.marzahn.sankaku.ui.components.PictureBurstSize
import dev.marzahn.sankaku.ui.components.GroupedMenu
import dev.marzahn.sankaku.ui.components.HeartToggle
import dev.marzahn.sankaku.ui.components.ImageFailed
import dev.marzahn.sankaku.ui.components.KEY_SCROLL_SCREEN
import dev.marzahn.sankaku.ui.components.KeyScroll
import dev.marzahn.sankaku.ui.components.KeyScrollLine
import dev.marzahn.sankaku.ui.components.LargeIndicatorSize
import dev.marzahn.sankaku.ui.components.MenuAction
import dev.marzahn.sankaku.ui.components.MenuGroup
import dev.marzahn.sankaku.ui.components.OncePerPress
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.isCommand
import dev.marzahn.sankaku.ui.components.isPlain
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.sqrt

@Composable
internal fun BookReaderScreen(
  open: OpenBook,
  onClose: () -> Unit,
  onPlace: (BookPlace) -> Unit = {},
  active: Boolean = true,
  end: BookEnd = BookEnd(),
  series: SheetSeries? = null,
  tab: TabId? = null,
  touch: Boolean = platformIsTouch,
  navigator: Navigator = koinInject(),
  privacy: PrivacySettings = koinInject(),
  actions: BookActions = koinInject(),
  books: BooksRepository = koinInject(),
  progress: ReadingProgress = koinInject(),
  tagPages: TagPages = koinInject(),
  videoPlayback: @Composable () -> VideoPlayback = { rememberPlatformVideoPlayback() },
) {
  val viewModel = koinViewModel<BookReaderViewModel> { parametersOf(open.id) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val gOnly by privacy.gOnly.collectAsStateWithLifecycle()
  val overrides by actions.favourites.overrides.collectAsStateWithLifecycle()
  val favouriteRejected = rememberBookRejections(actions, open.id)
  val loaded = state as? BookUiState.Loaded
  val title = loaded?.book?.title?.takeIf { it.isNotBlank() } ?: open.title.orEmpty()
  val pages = loaded?.pages.orEmpty()
  var chromeShown by remember { mutableStateOf(true) }
  var sheetOpen by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()
  val zoom = rememberZoomableState()
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val close by rememberUpdatedState(onClose)
  val dragClose = remember(density) { DragToClose(closeAt = with(density) { DRAG_CLOSE_DISTANCE.toPx() }) { close() } }
  val current by remember(listState) { derivedStateOf { currentPage(listState) } }
  val savedIndex = remember(loaded) { loaded?.let { openingPageIndex(it.pages, progress.savedOf(it.book)) } ?: 0 }
  remember(loaded) { if (savedIndex > 0) listState.requestScrollToItem(savedIndex) }
  var moved by remember { mutableStateOf(false) }
  val place by rememberUpdatedState(onPlace)
  LaunchedEffect(pages, savedIndex) {
    if (pages.isEmpty()) return@LaunchedEffect
    if (pages.size == 1) moved = true
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
    val opened = current
    snapshotFlow { current to moved }.collect { (page, _) ->
      if (page != opened) moved = true
      if (moved) place(bookPlace(pages, page))
    }
  }

  RetryWhenBack { if ((viewModel.state.value as? BookUiState.Failed)?.unreachable == true) viewModel.load() }
  PlatformBackHandler(enabled = active && tab == null, onBack = onClose)
  RecordBookSeen(open.id, shown = active && loaded?.pages?.isNotEmpty() == true)
  if (active) PlatformSystemBars(hidden = !chromeShown, lightIcons = true)
  LaunchedEffect(listState) { snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { chromeShown = false } }
  val focus = remember { FocusRequester() }
  var menuOpen by remember { mutableStateOf(false) }
  val ownsFocus = active && !menuOpen && !sheetOpen && !rememberTagPageShown(tagPages)
  val keepFocus by rememberUpdatedState(ownsFocus)
  LaunchedEffect(ownsFocus) { if (ownsFocus) focus.requestFocus() }
  val favouriteKey = remember { OncePerPress() }
  val takeFocusBack: (FocusState) -> Unit = { state ->
    if (!state.hasFocus) favouriteKey.letGo()
    if (tab == null && !state.hasFocus && keepFocus) {
      scope.launch {
        withFrameNanos {}
        if (keepFocus) focus.requestFocus()
      }
    }
  }

  val keyScroll = remember(listState) { KeyScroll(listState) }
  val onKey: (KeyEvent) -> Boolean = { event ->
    val newPress = favouriteKey.isNewPress(event)
    if (event.type == KeyEventType.KeyDown && !event.isCommand && event.key == Shortcuts.Favourite.key) {
      if (newPress) loaded?.book?.let(actions::toggleFavourite)
      true
    } else if (event.type == KeyEventType.KeyDown && event.isPlain && Shortcuts.Menu.matches(event.key)) {
      if (newPress) {
        chromeShown = true
        menuOpen = true
      }
      true
    } else {
      val step = stripKeyStep(event, listState, zoom, with(density) { KeyScrollLine.toPx() })
      if (step != null) keyScroll.by(step, scope)
      step != null
    }
  }
  val appDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  SankakuTheme(isDark = true) {
    Box(
      Modifier
        .fillMaxSize()
        .graphicsLayer { translationY = dragClose.offset }
        .background(Color.Black)
        .semantics { paneTitle = title.ifEmpty { "Book" } }
        .onFocusChanged(takeFocusBack)
        .focusRequester(focus)
        .focusable(enabled = active)
        .then(if (tab == null) Modifier.platformBackKeys(onClose) else Modifier)
        .onKeyEvent(onKey)
        .queueSwipe(enabled = !zoomedIn(zoom), from = tab, claimSideways = tab != null),
    ) {
      when (val s = state) {
        BookUiState.Loading -> AppLoadingIndicator(Modifier.align(Alignment.Center), size = LargeIndicatorSize)
        is BookUiState.Failed -> ApiErrorState(
          s.error,
          onRetry = viewModel::load,
          modifier = Modifier.align(Alignment.Center),
          onSignIn = koinInject<SignInController>()::open,
        )
        is BookUiState.Loaded -> if (s.pages.isEmpty()) {
          Text(
            NO_PAGES,
            Modifier.align(Alignment.Center).padding(24.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Strip(
            pages = s.pages,
            gOnly = gOnly,
            listState = listState,
            zoom = zoom,
            dragClose = dragClose,
            current = current,
            onTap = { chromeShown = !chromeShown },
            videoPlayback = videoPlayback,
            end = { EndCard(title, s.pages.size, end, onBack = onClose) },
            active = active,
          )
        }
      }
      val hearts = remember(actions, open.id) { actions.hearts.filter { it.bookId == open.id }.map { it.favourited } }
      FavouriteBursts(hearts, Modifier.align(Alignment.Center), size = PictureBurstSize)
      CompositionLocalProvider(LocalContentColor provides Color.White) {
        AnimatedVisibility(chromeShown, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
          TopRow(
            title = title,
            pageLabel = pages.takeIf { it.isNotEmpty() }?.let { pageLabel(current, it.size) },
            favourite = loaded?.book?.favourite(overrides),
            favouriteRejected = favouriteRejected,
            onToggleFavourite = { loaded?.book?.let(actions::toggleFavourite) },
            onClose = onClose,
            onDetails = { sheetOpen = true }.takeIf { loaded != null },
            menuOpen = menuOpen,
            onMenuOpen = { menuOpen = it },
            page = pages.getOrNull(current)?.takeUnless { hiddenByGOnly(it, gOnly) || it.redirectToSignup },
            onOpenPage = { page ->
              if (tab == null) onClose()
              navigator.goTo(Route.Post(page.id, page.md5, emptyList(), PostTitle.of(page), page.rating))
            },
          )
        }
      }
      val openQueuePage = LocalOpenTabList.current
      val pillShown = touch && chromeShown
      Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(savedIndex > 0 && !moved, enter = fadeIn(), exit = fadeOut()) {
          ResumeChip(
            page = savedIndex + 1,
            onStartOver = {
              moved = true
              scope.launch { listState.scrollToItem(0) }
            },
            modifier = Modifier.padding(bottom = 12.dp),
          )
        }
        AnimatedVisibility(pillShown, enter = fadeIn(), exit = fadeOut()) {
          QueueBar(
            onOpenQueue = {
              if (tab == null) onClose()
              openQueuePage()
            },
            bottom = BottomRowLayout.WindowRoom,
            openOnly = tab == null,
            overPicture = true,
          )
        }
        if (!pillShown) Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
      }
      val shownSeries by produceState<InSeries?>(null, series, open.id, sheetOpen) {
        value = null
        if (series == null || !sheetOpen) return@produceState
        value = try {
          inSeries(books.series(series.id, ratingLocked = false), open.id)
        } catch (_: ApiError) {
          null
        }
      }
      if (sheetOpen && loaded != null) SankakuTheme(isDark = appDark) {
        val book = loaded.book
        BookSheet(
          book = book,
          pages = pages.size,
          favourite = book.favourite(overrides),
          favouriteRejected = favouriteRejected,
          series = shownSeries,
          onToggleFavourite = { actions.toggleFavourite(book) },
          onOpen = { route ->
            if (tab == null) onClose() else sheetOpen = false
            if (tab != null || !(route is Route.BookSeries && series?.listed == true)) navigator.goTo(route)
          },
          onOpenInNewTab = { route -> navigator.openInNewTab(route) },
          onOpenTag = tagPages::open,
          onDismiss = { sheetOpen = false },
        )
      }
    }
  }
}

@Composable
private fun Strip(
  pages: List<PostDto>,
  gOnly: Boolean,
  listState: LazyListState,
  zoom: ZoomableState,
  dragClose: DragToClose,
  current: Int,
  onTap: () -> Unit,
  videoPlayback: @Composable () -> VideoPlayback,
  end: @Composable () -> Unit,
  active: Boolean,
) {
  LaunchedEffect(zoom) {
    zoom.setScalesCalculator(ScalesCalculator.fixed(DOUBLE_TAP_SCALE))
    zoom.setRubberBandScale(false)
  }
  var pointers by remember { mutableIntStateOf(0) }
  val tap by rememberUpdatedState(onTap)
  val zoomed by remember(zoom) { derivedStateOf { zoomedIn(zoom) } }
  val dragEnabled = platformIsTouch && pointers < 2 && !zoomed
  SideEffect { dragClose.enabled = dragEnabled }
  val visible by remember(listState) { derivedStateOf { listState.layoutInfo.visibleItemsInfo.mapTo(HashSet()) { it.index } } }
  val loadedPages = remember { mutableStateSetOf<Int>() }
  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .pointerInput(listState, dragClose) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.count { it.pressed }
            if (pointers == 0 && down > 0) dragClose.startedAtTop = !listState.canScrollBackward
            pointers = down
          }
        }
      }
      .zoom(zoom, onTap = { _: Offset -> tap() }),
  ) {
    val width = with(LocalDensity.current) { min(maxWidth, MAX_STRIP_WIDTH).roundToPx() }
    if (active) Preload(pages, current, current in loadedPages, width, gOnly)
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().nestedScroll(dragClose),
      horizontalAlignment = Alignment.CenterHorizontally,
      userScrollEnabled = pointers < 2,
    ) {
      itemsIndexed(pages) { index, page ->
        StripPage(
          page = page,
          description = "Page ${index + 1} of ${pages.size}",
          gOnly = gOnly,
          width = width,
          scale = { zoom.transform.scaleX },
          playing = active && index in visible,
          onLoaded = { loadedPages += index },
          videoPlayback = videoPlayback,
          modifier = Modifier.widthIn(max = MAX_STRIP_WIDTH).fillMaxWidth(),
        )
      }
      item(key = END_KEY) { end() }
    }
  }
}

@Composable
private fun Preload(pages: List<PostDto>, current: Int, currentLoaded: Boolean, width: Int, gOnly: Boolean) {
  val context = LocalPlatformContext.current
  val imageLoader = koinInject<ImageLoader>()
  val inFlight = remember { HashMap<Int, Disposable>() }
  LaunchedEffect(width, gOnly) {
    inFlight.values.forEach { it.dispose() }
    inFlight.clear()
  }
  LaunchedEffect(current, currentLoaded, width, gOnly) {
    val ahead = preloadedPages(current, pages.size)
    inFlight.keys.filter { it !in ahead }.forEach { inFlight.remove(it)?.dispose() }
    if (!currentLoaded) return@LaunchedEffect
    for (index in ahead) {
      if (index in inFlight) continue
      pageRequest(context, pages[index], width, gOnly)?.let { inFlight[index] = imageLoader.enqueue(it) }
    }
  }
  DisposableEffect(Unit) {
    onDispose {
      inFlight.values.forEach { it.dispose() }
      inFlight.clear()
    }
  }
}

@Composable
private fun StripPage(
  page: PostDto,
  description: String,
  gOnly: Boolean,
  width: Int,
  scale: () -> Float,
  playing: Boolean,
  onLoaded: () -> Unit,
  modifier: Modifier,
  videoPlayback: @Composable () -> VideoPlayback,
) {
  val loaded by rememberUpdatedState(onLoaded)
  val band = when {
    hiddenByGOnly(page, gOnly) -> "Hidden by G only"
    page.redirectToSignup -> "Sign in to view this page"
    else -> null
  }
  if (band != null) {
    LaunchedEffect(Unit) { loaded() }
    Band(band, modifier.semantics { contentDescription = "$description, ${band.lowercase()}" })
    return
  }
  val context = LocalPlatformContext.current
  var attempt by remember(page.id) { mutableIntStateOf(0) }
  var failure by remember(page.id, attempt) { mutableStateOf<ImageFailure?>(null) }
  var shown by remember(page.id, attempt) { mutableStateOf(false) }
  LaunchedEffect(shown) { if (shown) loaded() }
  RetryWhenBack { if (failure == ImageFailure.Unreachable) attempt++ }
  Box(
    modifier
      .aspectRatio(aspectRatio(page))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .semantics { contentDescription = description },
  ) {
    if (!shown) {
      val preview = remember(page.id, width) { pagePreviewRequest(context, page, width) }
      if (preview != null) {
        AsyncImage(
          model = preview,
          contentDescription = null,
          imageLoader = koinInject(),
          contentScale = ContentScale.FillBounds,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
    key(attempt) {
      val request = remember(page.id, width) { pageRequest(context, page, width, gOnly = false) }
      AsyncImage(
        model = request,
        contentDescription = null,
        imageLoader = koinInject(),
        contentScale = ContentScale.FillBounds,
        onState = {
          if (it is AsyncImagePainter.State.Success) shown = true
          failure = it.failure()
        },
        modifier = Modifier.fillMaxSize(),
      )
    }
    when {
      isFlash(page) || (isVideo(page) && !platformPlaysVideo(page)) -> Unplayable(Modifier.align(Alignment.Center))
      isVideo(page) -> PostVideo(page, Modifier.fillMaxSize(), play = playing, shown = true, bookPage = true, player = rememberVideoPlayback(videoPlayback))
      isAnimated(page) -> if (playing) AnimatedSample(page, Modifier.fillMaxSize(), onShown = {})
      else -> FullFile(page, width, scale)
    }
    val failed = failure
    if (failed != null && !shown) {
      ImageFailed(noImage = failed == ImageFailure.NoImage, onRetry = { attempt++ }, modifier = Modifier.fillMaxSize())
    }
  }
}

@Composable
private fun FullFile(page: PostDto, width: Int, scale: () -> Float) {
  var decodeWidth by remember(page.id) { mutableIntStateOf(0) }
  LaunchedEffect(page.id, width) {
    snapshotFlow { if (needsFullFile(page, width * scale())) fullFileWidth(page, width, scale()) else 0 }
      .collect { if (it > decodeWidth) decodeWidth = it }
  }
  if (decodeWidth == 0) return
  val context = LocalPlatformContext.current
  val request = remember(page.id, decodeWidth) {
    postImageRequest(context, PostImage.of(page, Variant.File)) {
      placeholderMemoryCacheKey(PostImage.cacheKey(page.md5, Variant.Sample))
      fittedTo(pageSize(page, decodeWidth))
    }
  }
  var shown by remember(page.id) { mutableStateOf(false) }
  AsyncImage(
    model = request,
    contentDescription = null,
    imageLoader = koinInject(),
    contentScale = ContentScale.FillBounds,
    alpha = if (shown) 1f else 0f,
    onState = { shown = it is AsyncImagePainter.State.Success },
    modifier = Modifier.fillMaxSize(),
  )
}

internal fun fullFileWidth(page: PostDto, width: Int, scale: Float): Int {
  val fileWidth = page.fileWidth ?: return 0
  val step = ZOOM_STEPS.firstOrNull { it >= scale } ?: ZOOM_STEPS.last()
  val capped = sqrt(MAX_FULL_PIXELS * aspectRatio(page)).toInt()
  return minOf(fileWidth, capped, (width * step).toInt()).coerceAtLeast(1)
}

private val ZOOM_STEPS = listOf(1f, DOUBLE_TAP_SCALE, DOUBLE_TAP_SCALE * DOUBLE_TAP_SCALE)

@Composable
private fun Band(text: String, modifier: Modifier) {
  Box(
    modifier.height(BAND_HEIGHT).background(MaterialTheme.colorScheme.surfaceContainer),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun Unplayable(modifier: Modifier) {
  Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f), modifier = modifier) {
    Text("This page can't play here", Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
  }
}

@Composable
private fun TopRow(
  title: String,
  pageLabel: String?,
  favourite: BookFavourite?,
  favouriteRejected: Int,
  onToggleFavourite: () -> Unit,
  onClose: () -> Unit,
  onDetails: (() -> Unit)?,
  menuOpen: Boolean,
  onMenuOpen: (Boolean) -> Unit,
  page: PostDto?,
  onOpenPage: (PostDto) -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
      .windowInsetsPadding(WindowInsets.statusBars)
      .padding(horizontal = 4.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ButtonHint("Close (${Shortcuts.Back.label})") { IconButton(onClick = onClose) { Icon(close, contentDescription = "Close") } }
    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (pageLabel != null) {
        Text(pageLabel, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.75f), maxLines = 1)
      }
    }
    HeartToggle(
      checked = favourite?.on == true,
      onCheckedChange = { onToggleFavourite() },
      rejected = favouriteRejected,
      enabled = favourite != null,
    )
    ButtonHint(BOOK_SHEET) {
      IconButton(onClick = { onDetails?.invoke() }, enabled = onDetails != null) { Icon(info, contentDescription = BOOK_SHEET) }
    }
    Box {
      ButtonHint("More (${Shortcuts.Menu.label})") {
        IconButton(onClick = { onMenuOpen(true) }) { Icon(more_vert, contentDescription = "More") }
      }
      GroupedMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }) {
        MenuGroup(0, 1) {
          MenuAction(
            text = { Text("Open this page as a post") },
            onClick = {
              onMenuOpen(false)
              page?.let(onOpenPage)
            },
            leadingIcon = { Icon(open_in_new, contentDescription = null) },
            enabled = page != null,
          )
        }
      }
    }
  }
}

@Composable
private fun ResumeChip(page: Int, onStartOver: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
    shadowElevation = 3.dp,
    modifier = modifier,
  ) {
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        resumeLabel(page),
        Modifier.padding(start = 12.dp, end = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
      TextButton(onClick = onStartOver) { Text(START_OVER) }
    }
  }
}

private fun currentPage(state: LazyListState): Int {
  val info = state.layoutInfo
  val pages = info.visibleItemsInfo.filter { it.key != END_KEY }
  if (pages.isEmpty() && info.visibleItemsInfo.isNotEmpty()) return (info.totalItemsCount - 2).coerceAtLeast(0)
  return mostShownPage(pages.map { PageSpan(it.index, it.offset, it.size) }, info.viewportStartOffset, info.viewportEndOffset)
}

private fun stripKeyStep(event: KeyEvent, state: LazyListState, zoom: ZoomableState, line: Float): Float? {
  if (event.type != KeyEventType.KeyDown) return null
  val screen = state.layoutInfo.viewportSize.height * KEY_SCROLL_SCREEN
  val step = when (event.key) {
    Key.Spacebar -> if (event.isShiftPressed) -screen else screen
    Key.PageDown -> screen
    Key.PageUp -> -screen
    Key.S, Key.DirectionDown -> line
    Key.W, Key.DirectionUp -> -line
    else -> return null
  }
  return step / zoom.transform.scaleX.coerceAtLeast(1f)
}

@Stable
private class DragToClose(private val closeAt: Float, private val onClose: () -> Unit) : NestedScrollConnection {
  var offset by mutableFloatStateOf(0f)
    private set
  var enabled = false
  var startedAtTop = false

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (offset <= 0f || available.y >= 0f) return Offset.Zero
    val used = maxOf(available.y, -offset)
    offset += used
    return Offset(0f, used)
  }

  override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
    if (source != NestedScrollSource.UserInput || available.y <= 0f || !enabled || !startedAtTop) return Offset.Zero
    offset += available.y
    return Offset(0f, available.y)
  }

  override suspend fun onPreFling(available: Velocity): Velocity {
    if (offset <= 0f) return Velocity.Zero
    if (offset > closeAt || available.y > CLOSE_FLING) {
      onClose()
    } else {
      animate(offset, 0f) { value, _ -> offset = value }
    }
    return available
  }
}

internal val MAX_STRIP_WIDTH = 900.dp
private val BAND_HEIGHT = 96.dp

internal const val NO_PAGES = "This book has no pages to show"
private val DRAG_CLOSE_DISTANCE = 120.dp

private const val CLOSE_FLING = 2_000f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val ZOOMED_FROM = 1.01f

private fun zoomedIn(zoom: ZoomableState): Boolean = zoom.transform.scaleX > zoom.minScale * ZOOMED_FROM
private const val MAX_FULL_PIXELS = 12_000_000f

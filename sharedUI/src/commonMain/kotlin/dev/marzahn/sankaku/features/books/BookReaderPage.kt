package dev.marzahn.sankaku.features.books

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.marzahn.sankaku.DebugFlags
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.BooksRepository
import dev.marzahn.sankaku.features.post.aspectRatio
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.effectsSpec
import dev.marzahn.sankaku.ui.components.isCommand
import dev.marzahn.sankaku.ui.components.tabStep
import dev.marzahn.sankaku.ui.components.spatialSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BookReaderPage(reader: BookReader = koinInject(), flags: DebugFlags = koinInject()) {
  var debugOpened by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(flags.openBook) {
    if (debugOpened) return@LaunchedEffect
    debugOpened = true
    flags.openBook?.let { reader.open(it) }
  }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { reader.sendInBackground() }
  val session by reader.session.collectAsStateWithLifecycle()
  val shown = session ?: return
  key(shown.opening) { BookReaderFrame(shown, reader) }
}

@Composable
private fun BookReaderFrame(session: ReaderSession, reader: BookReader) {
  val results = session.results
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  var frame by remember { mutableStateOf(Rect.Zero) }
  var cover by remember { mutableStateOf(results?.peek(session.index)?.let { results.coverOf(it.book.id) }) }
  var coverPost by remember { mutableStateOf(results?.peek(session.index)?.book?.coverPost) }
  val progress = remember { Animatable(0f) }
  var closing by remember { mutableStateOf(false) }
  val openSpec = spatialSpec<Float>(slow = true)
  val closeSpec = spatialSpec<Float>()
  LaunchedEffect(Unit) { progress.animateTo(1f, openSpec) }
  var bookShown by remember { mutableStateOf(false) }
  val coverFade = remember { Animatable(1f) }
  val fadeSpec = effectsSpec<Float>()
  LaunchedEffect(bookShown) {
    if (!bookShown) return@LaunchedEffect
    snapshotFlow { progress.value >= LANDED }.first { it }
    coverFade.animateTo(0f, fadeSpec)
  }

  val coverGone by remember { derivedStateOf { coverFade.value == 0f } }

  val pager = rememberPagerState(initialPage = session.index) { results?.count ?: 1 }
  var loadedPage by remember { mutableIntStateOf(-1) }
  if (results != null) {
    SideEffect { results.hiddenCover = results.peek(pager.currentPage)?.book?.id }
    DisposableEffect(results) { onDispose { results.hiddenCover = null } }
  }

  fun close() {
    if (closing) return
    closing = true
    scope.launch {
      val index = pager.currentPage
      val result = results?.peek(index)
      val rowShown = result != null && reader.session.value?.book?.id == result.book.id
      if (results != null && result != null && rowShown) {
        results.hiddenCover = result.book.id
        results.reveal(index)
        repeat(2) { withFrameNanos {} }
        cover = results.coverOf(result.book.id)?.takeIf { it.overlaps(frame) }
        coverPost = result.book.coverPost
      } else {
        cover = null
      }
      progress.animateTo(0f, closeSpec)
      reader.close()
    }
  }

  fun step(forward: Boolean): Boolean {
    val target = pager.currentPage + if (forward) 1 else -1
    if (results == null || target !in 0 until pager.pageCount) return false
    scope.launch { pager.animateScrollToPage(target) }
    return true
  }

  val maxStrip = with(density) { MAX_STRIP_WIDTH.toPx() }
  val cornerPx = with(density) { COVER_CORNER.toPx() }
  val grows = cover != null && coverPost != null
  fun frameAt(p: Float): Rect {
    val whole = Rect(Offset.Zero, frame.size)
    val from = cover?.translate(-frame.topLeft) ?: return whole
    return if (grows) lerp(from, whole, p) else whole
  }

  Box(
    Modifier
      .fillMaxSize()
      .onGloballyPositioned { frame = it.boundsInWindow() }
      .onKeyEvent { event ->
        if (closing || results == null || event.isCommand || event.tabStep != null) return@onKeyEvent false
        val forward = when {
          Shortcuts.Previous.matches(event.key) -> false
          Shortcuts.Next.matches(event.key) -> true
          else -> return@onKeyEvent false
        }
        if (event.type == KeyEventType.KeyDown) step(forward)
        true
      },
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .graphicsLayer {
          val p = progress.value
          if (grows) {
            val rect = frameAt(p)
            clip = true
            shape = RectAt(rect, lerp(cornerPx, 0f, p.coerceIn(0f, 1f)))
            alpha = frameAlpha(p)
          } else {
            alpha = p.coerceIn(0f, 1f)
          }
        }
        .background(Color.Black),
    ) {
      Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (grows) stripAlpha(progress.value) else 1f }) {
        ResultsPager(
          pager = pager,
          session = session,
          results = results,
          closing = closing,
          neighbours = loadedPage >= 0,
          onClose = ::close,
          onShowing = reader::showing,
          onLoaded = { page ->
            loadedPage = page
            if (page == session.index) bookShown = true
          },
        )
      }
    }
    val flying = coverPost
    val from = cover
    if (grows && flying != null && from != null && (closing || !coverGone)) {
      val locked = results?.locked == true
      FlyingCover(
        post = flying,
        locked = locked,
        rect = {
          val p = progress.value
          val whole = frame.size
          val width = minOf(whole.width, maxStrip)
          val slot = Rect(Offset((whole.width - width) / 2f, 0f), Size(width, width / aspectRatio(flying)))
          lerp(from.translate(-frame.topLeft), slot, p)
        },
        alpha = { if (closing) 1f - stripAlpha(progress.value) else coverFade.value },
        corner = { lerp(cornerPx, 0f, progress.value.coerceIn(0f, 1f)) },
      )
    }
  }
}

@Composable
private fun ResultsPager(
  pager: PagerState,
  session: ReaderSession,
  results: BookResults?,
  closing: Boolean,
  neighbours: Boolean,
  onClose: () -> Unit,
  onShowing: (index: Int, book: OpenBook) -> Unit,
  onLoaded: (index: Int) -> Unit,
) {
  val scope = rememberCoroutineScope()
  HorizontalPager(
    state = pager,
    userScrollEnabled = results != null && !closing,
    beyondViewportPageCount = if (neighbours) 1 else 0,
    modifier = Modifier.fillMaxSize(),
  ) { page ->
    val result = if (results == null) null else results.bookAt(page) ?: return@HorizontalPager
    val seriesList = results?.seriesId != null
    ResultPage(
      start = result?.open ?: session.book,
      seriesId = result?.seriesId?.takeUnless { seriesList },
      sheetSeries = result?.seriesId?.let { SheetSeries(it, listed = seriesList) },
      nextResult = results?.peek(page + 1)?.book,
      seriesList = seriesList,
      listed = results != null,
      current = page == pager.settledPage && !closing,
      locked = results?.locked == true,
      onClose = onClose,
      onNextResult = { scope.launch { pager.animateScrollToPage(page + 1) } },
      onShowing = { onShowing(page, it) },
      onLoaded = { onLoaded(page) },
    )
  }
}

@Composable
private fun ResultPage(
  start: OpenBook,
  seriesId: String?,
  sheetSeries: SheetSeries?,
  nextResult: BookDto?,
  seriesList: Boolean,
  listed: Boolean,
  current: Boolean,
  locked: Boolean,
  onClose: () -> Unit,
  onNextResult: () -> Unit,
  onShowing: (OpenBook) -> Unit,
  onLoaded: () -> Unit,
  books: BooksRepository = koinInject(),
  reader: BookReader = koinInject(),
) {
  var book by remember(start.id) { mutableStateOf(start) }
  val showing by rememberUpdatedState(onShowing)
  LaunchedEffect(current, book) { if (current) showing(book) }
  val series by produceState<List<BookDto>?>(null, seriesId, locked, current) {
    if (seriesId == null || value != null || !current) return@produceState
    value = try {
      books.series(seriesId, locked).books
    } catch (_: ApiError) {
      null
    }
  }
  val pagesOn = seriesList && nextResult != null && book.id == start.id
  val nextVolume = if (pagesOn) nextResult else series?.let { nextInSeries(it, book.id) }
  val end = BookEnd(
    nextVolume = nextVolume,
    onNextVolume = { nextVolume?.let { if (pagesOn) onNextResult() else book = OpenBook(it.id, it.title) } },
    nextResult = nextResult?.takeUnless { seriesList || it.id == nextVolume?.id },
    onNextResult = onNextResult,
    back = if (listed) BACK_TO_BOOKS else CLOSE_BOOK,
    locked = locked,
  )
  key(book.id) {
    OwnViewModels {
      val state by koinViewModel<BookReaderViewModel> { parametersOf(book.id) }.state.collectAsStateWithLifecycle()
      val loaded by rememberUpdatedState(onLoaded)
      val settled = state !is BookUiState.Loading
      LaunchedEffect(settled, current) { if (settled && current) loaded() }
      BookReaderScreen(
        book,
        onClose = onClose,
        onPlace = { reader.report(book.id, it) },
        active = current,
        series = sheetSeries,
        end = end,
      )
    }
  }
}

@Composable
private fun OwnViewModels(content: @Composable () -> Unit) {
  val owner = remember {
    object : ViewModelStoreOwner {
      override val viewModelStore = ViewModelStore()
    }
  }
  DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
  CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}


@Composable
private fun FlyingCover(post: PostDto, locked: Boolean, rect: () -> Rect, alpha: () -> Float, corner: () -> Float) {
  BookPicture(
    post,
    locked,
    Modifier
      .layout { measurable, _ ->
        val r = rect()
        val width = r.width.toInt().coerceAtLeast(1)
        val height = r.height.toInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(r.left.toInt(), r.top.toInt()) }
      }
      .graphicsLayer {
        this.alpha = alpha()
        clip = true
        shape = RoundedCornerShape(corner())
      },
  )
}

internal fun stripAlpha(progress: Float): Float = ((progress - STRIP_FADE_FROM) / (1f - STRIP_FADE_FROM)).coerceIn(0f, 1f)

internal fun frameAlpha(progress: Float): Float = (progress / FRAME_FADE_TO).coerceIn(0f, 1f)

private const val STRIP_FADE_FROM = 0.5f
private const val FRAME_FADE_TO = 0.25f

private const val LANDED = 0.99f

private val COVER_CORNER = 8.dp

private class RectAt(private val rect: Rect, private val corner: Float) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
    Outline.Rounded(RoundRect(rect, CornerRadius(corner)))
}

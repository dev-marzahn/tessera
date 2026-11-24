package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import dev.marzahn.sankaku.ui.components.effectsSpec
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.max
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import com.google.fonts.icons.download
import com.google.fonts.icons.refresh
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.sankaku.features.post.Rejection
import dev.marzahn.sankaku.features.post.PostCounts
import dev.marzahn.sankaku.features.post.keepingFrom
import dev.marzahn.sankaku.features.post.rememberViewing
import dev.marzahn.sankaku.features.post.SaveOutcome
import dev.marzahn.sankaku.features.post.rememberRejections
import dev.marzahn.sankaku.ui.components.CornerActionChip
import dev.marzahn.sankaku.ui.components.LiveAnnouncement
import dev.marzahn.sankaku.ui.components.MenuButton
import dev.marzahn.sankaku.ui.components.MenuSaveState
import dev.marzahn.sankaku.ui.rememberActionWindowMillis
import dev.marzahn.sankaku.ui.runWindow
import com.google.fonts.icons.open_in_new
import com.google.fonts.icons.share
import com.google.fonts.icons.grid_view
import com.google.fonts.icons.bookmark
import com.google.fonts.icons.chevron_right
import com.google.fonts.icons.close
import androidx.compose.ui.semantics.Role
import dev.marzahn.sankaku.tabs.LeavingEdge
import dev.marzahn.sankaku.tabs.LocalQueueClosePeek
import dev.marzahn.sankaku.tabs.LocalQueueActions
import dev.marzahn.sankaku.tabs.LocalQueuePictureSwipe
import dev.marzahn.sankaku.tabs.PicturePeek
import dev.marzahn.sankaku.tabs.TabId
import dev.marzahn.sankaku.tabs.queueSwipe
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.marzahn.sankaku.PlatformBackHandler
import dev.marzahn.sankaku.PlatformPredictiveBackHandler
import dev.marzahn.sankaku.PlatformSystemBars
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.AccessibilitySettings
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.features.post.LocalVideoKeys
import dev.marzahn.sankaku.features.post.PlayerPreferences
import dev.marzahn.sankaku.features.post.PostActions
import dev.marzahn.sankaku.features.post.PostDetails
import dev.marzahn.sankaku.features.post.PostImageView
import dev.marzahn.sankaku.features.post.PostSaver
import dev.marzahn.sankaku.features.post.PostStats
import dev.marzahn.sankaku.features.post.PostZoom
import dev.marzahn.sankaku.features.post.VIDEO_SEEK_ROW_HEIGHT
import dev.marzahn.sankaku.features.post.VideoKeys
import dev.marzahn.sankaku.features.post.VideoProgressLine
import dev.marzahn.sankaku.features.post.VideoSeekRow
import dev.marzahn.sankaku.features.post.VideoScrubPreview
import dev.marzahn.sankaku.features.post.aspectRatio
import dev.marzahn.sankaku.features.post.fittedBox
import dev.marzahn.sankaku.features.post.decodedInMemory
import dev.marzahn.sankaku.features.post.isVideo
import dev.marzahn.sankaku.features.post.videoTaps
import dev.marzahn.sankaku.features.post.videoPlaceSaver
import dev.marzahn.sankaku.features.post.VIEWER_VIDEO_KEY
import dev.marzahn.sankaku.features.post.postByline
import dev.marzahn.sankaku.data.PostTitle
import dev.marzahn.sankaku.features.post.postPageUrl
import dev.marzahn.sankaku.images.PostImage
import dev.marzahn.sankaku.images.postImageRequest
import dev.marzahn.sankaku.tabs.MIN_FLING
import dev.marzahn.sankaku.openUrl
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.platformShareText
import dev.marzahn.sankaku.shell.CoverChrome
import dev.marzahn.sankaku.tabs.QueueBar
import dev.marzahn.sankaku.tabs.UndoHiddenWhile
import dev.marzahn.sankaku.shell.LocalTabBarSlide
import dev.marzahn.sankaku.shell.LocalQueuePageOpening
import dev.marzahn.sankaku.shell.LocalHandoffLeaving
import androidx.compose.runtime.derivedStateOf
import dev.marzahn.sankaku.tabs.LocalOpenTabList
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import dev.marzahn.sankaku.ui.components.FavouriteBursts
import dev.marzahn.sankaku.ui.Tip
import dev.marzahn.sankaku.ui.Tips
import dev.marzahn.sankaku.ui.offerLongPressTip
import dev.marzahn.sankaku.ui.components.PictureBurstSize
import dev.marzahn.sankaku.features.post.forShown
import dev.marzahn.sankaku.ui.components.GroupedMenu
import dev.marzahn.sankaku.ui.components.MenuAction
import dev.marzahn.sankaku.ui.components.MenuGroup
import dev.marzahn.sankaku.ui.components.spatialBoundsTransform
import dev.marzahn.sankaku.ui.components.spatialFadeSpec
import dev.marzahn.sankaku.ui.components.spatialSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val PEEK_HEADER = 80.dp

private val STATS_GAP = 16.dp

private val TOP_ROW = 56.dp

private val PILL_GAP = 12.dp

private val PILL_LINE = PILL_GAP + 20.dp

internal fun fittedPictureInset(seekRoom: Dp = 0.dp, compactHeight: Boolean = false): Dp =
  if (compactHeight) 0.dp else PILL_LINE + BottomRowLayout.Height / 2 + seekRoom

private val COMPACT_HEIGHT = 480.dp

internal fun isCompactHeight(height: Dp): Boolean = height < COMPACT_HEIGHT

private fun Density.pictureTop(statusTop: Int, compactHeight: Boolean): Float =
  statusTop + if (compactHeight) 0f else TOP_ROW.toPx()

private val DETAILS_FADE_IN = 48.dp

private const val HALF_TOP = 0.5f

private val IMAGE_FADE_HEIGHT = 120.dp

internal fun pillShown(room: Float, roomAtPeek: Float, fade: Float): Float {
  val over = minOf(fade, roomAtPeek)
  return if (over <= 0f) 0f else (room / over).coerceIn(0f, 1f)
}

private val DISMISS_DISTANCE = 120.dp
private const val DISMISS_VELOCITY = 1500f

private const val DISMISS_MIN_SCALE = 0.8f

private const val BACK_SHARE = 0.25f

private const val SHEET_FLING = 1200f

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PhoneViewer(
  index: Int,
  postAt: (Int) -> PostDto?,
  pageKey: ((Int) -> Any)? = null,
  loadedCount: Int,
  hasMore: Boolean,
  sharedId: String?,
  visibilityScope: AnimatedVisibilityScope,
  onShow: (Int) -> Unit,
  end: ViewerEnd = ViewerEnd.NoMore,
  onRetryEnd: () -> Unit = {},
  onClose: () -> Unit,
  opening: Int = 0,
  mode: ViewerMode = ViewerMode.Overlay,
  searchTags: List<String> = emptyList(),
  origin: PostOrigin? = null,
  onDragClose: (() -> Unit)? = null,
  queueFrom: TabId? = null,
  onKeep: ((PostDto) -> Unit)? = null,
  canUnkeep: (PostDto) -> Boolean = { false },
  onUnkeep: (PostDto) -> Unit = {},
  onDrop: (() -> Unit)? = null,
  laterHint: Boolean = false,
  actions: PostActions = koinInject(),
  playerPreferences: PlayerPreferences = koinInject(),
  tips: Tips = koinInject(),
  messages: Messages = koinInject(),
) {
  val scope = rememberCoroutineScope()
  val video = remember(playerPreferences) { VideoKeys(playerPreferences).apply { previewsDrag = true } }
  rememberSaveable(video, key = VIEWER_VIDEO_KEY, saver = remember(video) { videoPlaceSaver(video) }) { video }
  val density = LocalDensity.current
  val transition = visibilityScope.transition
  val settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
  var leaving by remember(opening) { mutableStateOf(false) }
  val currentOpening by rememberUpdatedState(opening)
  val closing = leaving || transition.targetState != EnterExitState.Visible
  var stepped by remember(opening) { mutableStateOf(false) }
  val closeSpec = spatialSpec<Float>()
  val dismiss = rememberCardDismiss(opening, closeSpec)
  val pulled by remember(dismiss) { derivedStateOf { !dismiss.atRest } }
  val attachShared = (!settled && !stepped) || leaving || (pulled && mode != ViewerMode.Tab)
  val queuePageOpening = mode == ViewerMode.Tab && LocalQueuePageOpening.current
  val attachedReady = remember(opening) { booleanArrayOf(false) }
  LaunchedEffect(opening, attachShared) {
    attachedReady[0] = false
    if (attachShared) {
      repeat(2) { withFrameNanos {} }
      attachedReady[0] = true
    }
  }
  var sampleKept by remember(opening) { mutableStateOf(false) }
  SideEffect { if (settled) sampleKept = true }
  val handoffLeaving = LocalHandoffLeaving.current
  val aboveImage = Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f) {
    mode != ViewerMode.Tab && isTransitionActive && !handoffLeaving
  }
  val touchesPass = mode != ViewerMode.Tab && transition.targetState != EnterExitState.Visible && sharedId != null && attachShared

  val openSpec = spatialSpec<Float>(slow = true)
  val openFadeSpec = effectsSpec<Float>(slow = true)
  val fadeSpec = effectsSpec<Float>()
  val cardFadeSpec = spatialFadeSpec<Float>()
  val scrimAlpha by transition.animateFloat({ if (targetState == EnterExitState.Visible) openFadeSpec else fadeSpec }) {
    if (it == EnterExitState.Visible) 1f else 0f
  }
  val progress by transition.animateFloat({ if (targetState == EnterExitState.Visible) openSpec else closeSpec }) {
    if (it == EnterExitState.Visible) 1f else 0f
  }
  val controlsShown = progress > CONTROLS_SHOWN_AT && !leaving
  val bounds = spatialBoundsTransform(slow = !closing)

  val floatingBar = mode != ViewerMode.Tab
  val dockedState = animateFloatAsState(if (controlsShown) 1f else 0f, closeSpec)
  var windowHeight by remember { mutableFloatStateOf(0f) }
  val docked: () -> Float = { dockedState.value * dragFade(dismiss.fraction(windowHeight / 2f)) }
  val floated by remember(opening) { derivedStateOf { docked() == 0f } }
  val handBack = closing && floatingBar && floated
  var handedBack by remember(opening) { mutableStateOf(false) }
  LaunchedEffect(handBack) {
    handedBack = false
    if (handBack) {
      repeat(2) { withFrameNanos {} }
      handedBack = true
    }
  }
  val tabBarSlide = LocalTabBarSlide.current
  val slideFrom = remember(opening) { tabBarSlide() }

  CoverChrome(enabled = !handBack)

  val minFling = with(density) { MIN_FLING.toPx() }
  val sheet = rememberViewerSheetState(scope, closeSpec, SHEET_FLING, postId = sharedId ?: postAt(if (mode.steps) index else 0)?.id)
  val zoom = remember { PostZoom() }
  var tapHidden by remember(opening) { mutableStateOf(false) }
  var onEndPage by remember { mutableStateOf(false) }
  val chromeShown = !tapHidden && !zoom.zoomed && !onEndPage
  val chromeLayout by animateFloatAsState(if (chromeShown && !handoffLeaving) 1f else 0f, spatialSpec())
  var zoomRoom by remember { mutableStateOf(false) }
  LaunchedEffect(zoom.zoomed, zoom.settled) { zoomRoom = zoom.zoomed && (zoomRoom || zoom.settled) }
  val imageSpring = animateFloatAsState(if (tapHidden || zoomRoom) 0f else 1f, if (zoom.zoomed) snap() else spatialSpec())
  val imageLayout by remember { derivedStateOf { imageSpring.value.coerceIn(0f, 1f) } }
  val chromeAlpha by animateFloatAsState(if (chromeShown && controlsShown) 1f else 0f, fadeSpec)

  val pager = rememberPagerState(initialPage = if (mode.steps) index else 0) { if (mode.steps) loadedCount + 1 else 1 }
  val currentIndex by rememberUpdatedState(index)
  val currentLoaded by rememberUpdatedState(loadedCount)
  if (mode.steps) {
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage >= currentLoaded }.collect { onEndPage = it } }
    LaunchedEffect(pager) {
      snapshotFlow { pager.settledPage to currentLoaded }.collect { (page, loaded) ->
        if (page != currentIndex && page < loaded) {
          stepped = true
          onShow(page)
        }
      }
    }
    LaunchedEffect(index, opening) { if (!pager.isScrollInProgress && pager.settledPage != index) pager.scrollToPage(index) }
  }
  val post = sameWhileEqual(postAt(if (mode.steps) index else 0))
  val viewing = rememberViewing(post)
  val keep = onKeep?.let(viewing::keepingFrom)

  fun close() {
    if (mode == ViewerMode.Tab) return onClose()
    if (leaving) return
    leaving = true
    if (attachedReady[0]) return onClose()
    val closingOpening = currentOpening
    scope.launch {
      repeat(2) { withFrameNanos {} }
      if (currentOpening == closingOpening) onClose()
    }
  }

  fun toggleChrome() {
    if (closing || zoom.zoomed) return
    tapHidden = !tapHidden
    if (tapHidden) sheet.animateTo(0f)
  }

  val haptics = LocalHapticFeedback.current
  fun favouriteFromPicture(shown: PostDto) {
    if (closing) return
    favouriteWithHaptic(haptics) { actions.addFavourite(shown) }
  }

  key(opening) {
    if (mode == ViewerMode.Overlay) {
      PlatformPredictiveBackHandler(
        enabled = !closing,
        onProgress = { dismiss.pullTo(it * windowHeight * BACK_SHARE) },
        onCancel = dismiss::springBack,
        onBack = ::close,
      )
    }
    PlatformBackHandler(enabled = !closing && !sheet.atPeek) { sheet.animateTo(0f) }
    PlatformBackHandler(enabled = !closing && zoom.zoomed) { scope.launch { zoom.zoomOut() } }
  }

  val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val (halfFraction, fullFraction) = sheetFractions(sheet.lift, sheet.stops)
  val windowCompact = with(density) { isCompactHeight(LocalWindowInfo.current.containerSize.height.toDp()) }
  val videoFullScreen = windowCompact && post?.let(::isVideo) == true && sheet.atPeek
  PlatformSystemBars(
    hidden = (!chromeShown || videoFullScreen) && !leaving,
    lightIcons = if (leaving || progress < 0.5f || fullFraction > 0.5f) darkTheme else true,
  )

  val keyFocus = remember { FocusRequester() }
  LaunchedEffect(video.player) { keyFocus.requestFocus() }
  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown) video.onVolumeKey(it.key)
        false
      }
      .focusRequester(keyFocus)
      .focusable(),
  ) {
    val height = constraints.maxHeight.toFloat()
    val seenBars = LocalBarInsets.current
    var maxStatusTop by remember { mutableIntStateOf(seenBars?.statusTop ?: 0) }
    var maxNavBottom by remember { mutableIntStateOf(seenBars?.navBottom ?: 0) }
    val currentStatusTop = WindowInsets.statusBars.getTop(density)
    val currentNavBottom = WindowInsets.navigationBars.getBottom(density)
    val statusTop = max(maxStatusTop, currentStatusTop)
    val navBottom = max(maxNavBottom, currentNavBottom)
    SideEffect {
      windowHeight = height
      maxStatusTop = statusTop
      maxNavBottom = navBottom
      if (seenBars != null && chromeShown) {
        if (currentStatusTop > 0) seenBars.statusTop = currentStatusTop
        if (currentNavBottom > 0) seenBars.navBottom = currentNavBottom
      }
      if (seenBars != null && seenBars.statusTop == 0 && seenBars.navBottom == 0) {
        seenBars.statusTop = statusTop
        seenBars.navBottom = navBottom
      }
    }
    val compactHeight = isCompactHeight(maxHeight)
    val videoLandscape by animateFloatAsState(
      if (compactHeight && post?.let(::isVideo) == true) 1f else 0f,
      spatialSpec(),
    )
    val peekTop = height - (with(density) { PEEK_HEADER.toPx() } + navBottom) * (1f - videoLandscape)
    val gridLine = height - navBottom - with(density) { BottomRowLayout.CentreLine.toPx() }
    LaunchedEffect(peekTop, height) { sheet.update(SheetStops(half = peekTop - height * HALF_TOP, full = peekTop)) }
    val topRow = statusTop + with(density) { TOP_ROW.toPx() }
    val pictureTop = density.pictureTop(statusTop, compactHeight) * (1f - videoLandscape)
    fun sheetTop(shown: Float = chromeLayout) = peekTop - sheet.lift + (1f - shown) * (height - peekTop + sheet.lift)
    fun barBase() = sheetTop()
    val dismissRange = height / 2f
    fun dismissFraction() = dismiss.fraction(dismissRange)
    val imageBox = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val seekRow = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun seekRowBounds(): Rect? = seekRow[0]?.takeIf { it.isAttached }?.boundsInRoot()
    val seekTrack = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val viewerOrigin = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(Modifier.onGloballyPositioned { viewerOrigin[0] = it })

    if (mode != ViewerMode.Tab) {
      Box(
        Modifier
          .fillMaxSize()
          .graphicsLayer { alpha = scrimAlpha * (1f - dismissFraction()) }
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = OVERLAY_SCRIM_ALPHA))
          .semantics { hideFromAccessibility() }
          .then(if (closing) Modifier else Modifier.pointerInput(Unit) { detectTapGestures {} }),
      )
    }
    val closePeek = LocalQueueClosePeek.current.takeIf { mode == ViewerMode.Tab && onDragClose != null && pulled }
    closePeek?.behind?.invoke()

    val cardCorner by transition.animateDp({ spatialSpec(slow = !closing) }) {
      if (it == EnterExitState.Visible || mode == ViewerMode.Tab) 0.dp else CELL_CORNER
    }
    val cardShared = sharedId != null && mode != ViewerMode.Tab
    Box(
      Modifier
        .layout { measurable, constraints ->
          val scale = 1f - (1f - DISMISS_MIN_SCALE) * dismissFraction()
          val width = (constraints.maxWidth * scale).roundToInt()
          val cardHeight = (constraints.maxHeight * scale).roundToInt()
          val placeable = measurable.measure(Constraints.fixed(width, cardHeight))
          layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place((constraints.maxWidth - width) / 2, ((constraints.maxHeight - cardHeight) / 2 + dismiss.offset * 0.6f).roundToInt())
          }
        }
        .passTouchesThrough(touchesPass)
        .then(
          if (!cardShared || sharedId == null || !attachShared) {
            Modifier
          } else {
            Modifier.sharedBounds(
              rememberSharedContentState(cardKey(sharedId)),
              visibilityScope,
              enter = EnterTransition.None,
              exit = fadeOut(cardFadeSpec),
              boundsTransform = bounds,
              resizeMode = RemeasureToBounds,
              clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(max(cardCorner, lerp(0.dp, VIEWER_CORNER, dismissFraction())))),
              zIndexInOverlay = CARD_Z,
            )
          },
        )
        .clip(RoundedCornerShape(max(cardCorner, lerp(0.dp, VIEWER_CORNER, dismissFraction()))))
        .background(Color.Black),
    ) {
      val cardShare = remember { floatArrayOf(1f) }
      var dragDismisses by remember { mutableStateOf<Boolean?>(null) }
      var fromSeekRow by remember { mutableStateOf(false) }
      val drag = rememberDraggableState { dy ->
        val dismissing = dragDismisses
          ?: dragDownCloses(dy, sheet.headedForPeek, zoom.zoomed, mode, closable = mode == ViewerMode.Overlay || onDragClose != null, fromSeekRow)
            .also { dragDismisses = it }
        if (dismissing) {
          dismiss.dragBy(dy)
        } else {
          if (dy < 0f && tapHidden) tapHidden = false
          sheet.dragBy(dy)
        }
      }
      Box(
        Modifier
          .layout { measurable, constraints ->
            val share = constraints.maxHeight / height
            cardShare[0] = share
            val top = pictureTop * imageLayout * share
            val bottom = sheetTop(imageLayout).coerceAtMost(height) * share
            val roomHeight = (bottom - top).roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, roomHeight))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, top.roundToInt()) }
          }
          .onGloballyPositioned { imageBox[0] = it }
          .draggable(
            drag,
            Orientation.Vertical,
            enabled = !closing && !zoom.zoomed,
            onDragStarted = { start ->
              dragDismisses = null
              fromSeekRow = imageBox[0]?.takeIf { it.isAttached }?.localToRoot(start)?.let { seekRowBounds()?.contains(it) } == true
            },
            onDragStopped = { velocity ->
              if (dragDismisses == true && mode == ViewerMode.Tab) {
                if (loneDragCloses(dismiss.offset, height, velocity, minFling)) onDragClose?.invoke() else dismiss.springBack()
              } else if (dragDismisses == true) {
                if (dismiss.offset > with(density) { DISMISS_DISTANCE.toPx() } || velocity > DISMISS_VELOCITY) close() else dismiss.springBack()
              } else {
                sheet.settle(velocity)
              }
              dragDismisses = null
            },
          ),
      ) {
        val queueActions = LocalQueueActions.current
        val sheetMoves = if (zoom.zoomed) emptyList() else sheetActions(sheet) { tapHidden = false }
        val loneActions = if (mode != ViewerMode.Tab) {
          emptyList()
        } else {
          queueActions + listOfNotNull(onDragClose?.let { close -> CustomAccessibilityAction(CLOSE_ACTION) { close(); true } })
        }
        val pictureSwipe = LocalQueuePictureSwipe.current.takeIf { mode == ViewerMode.Tab }
        if (pictureSwipe != null) {
          DisposableEffect(pictureSwipe) { onDispose(pictureSwipe.drawing()) }
          for ((peek, side) in listOf(pictureSwipe.previous to -1f, pictureSwipe.next to 1f)) {
            if (peek == null) continue
            val peekInset = with(density) { fittedPictureInset(seekRoom = if (peek.picture?.let { isVideo(it.post) } == true) SEEK_ROOM else 0.dp, compactHeight).toPx() }
            NeighbourPicture(
              peek,
              forward = side > 0f,
              insetPx = { peekInset * imageLayout * cardShare[0] },
              modifier = Modifier.graphicsLayer { translationX = (pictureSwipe.offset() + side * size.width) * pictureSwipe.direction },
            )
          }
        }
        HorizontalPager(
          state = pager,
          key = pageKey.takeIf { mode.steps },
          userScrollEnabled = mode.steps && !closing && dismiss.atRest,
          beyondViewportPageCount = 1,
          modifier = Modifier
            .fillMaxSize()
            .then(if (pictureSwipe == null) Modifier else Modifier.graphicsLayer { translationX = pictureSwipe.offset() * pictureSwipe.direction })
            .then(
              if (mode.steps) {
                Modifier
              } else {
                Modifier.queueSwipe(enabled = !closing && !zoom.zoomed && dismiss.atRest, from = queueFrom, claimSideways = true)
              },
            ),
        ) { page ->
          if (mode.steps && page >= loadedCount) {
            SankakuTheme(isDark = true) {
              ViewerEndPage(end, onRetry = onRetryEnd, modifier = Modifier.pointerInput(Unit) { detectTapGestures { } })
            }
            return@HorizontalPager
          }
          val shown = sameWhileEqual(postAt(if (mode.steps) page else 0)) ?: return@HorizontalPager
          val inset = fittedPictureInset(seekRoom = if (isVideo(shown)) SEEK_ROOM else 0.dp, compactHeight)
          val fullInsetPx = with(density) { inset.toPx() }
          val insetPx = { fullInsetPx * imageLayout * cardShare[0] }
          Box(Modifier.fillMaxSize()) {
            val bandTap by rememberUpdatedState(::toggleChrome)
            val bandKeys by rememberUpdatedState(video.takeIf { page == pager.settledPage })
            Box(
              Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .layout { measurable, constraints ->
                  val band = insetPx().roundToInt().coerceIn(0, constraints.maxHeight)
                  val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, band))
                  layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .then(
                  if (isVideo(shown)) {
                    Modifier.videoTaps({ bandKeys }, key = shown.id, onTap = { bandTap() }, onLongPress = null)
                  } else {
                    Modifier.pointerInput(Unit) { detectTapGestures { bandTap() } }
                  },
                ),
            )
            val pictureState = rememberSharedContentState(imageKey(shown.id))
            val leavingForRow = mode == ViewerMode.Tab && shown.id == sharedId && transition.targetState != EnterExitState.Visible &&
              pictureState.isMatchFound
            CompositionLocalProvider(LocalVideoKeys provides video.takeIf { page == pager.settledPage }) {
              PostImageView(
                shown,
                modifier = Modifier
                  .layout { measurable, constraints ->
                    val pictureHeight = (constraints.maxHeight - insetPx().roundToInt()).coerceIn(1, constraints.maxHeight)
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, pictureHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                  }
                  .graphicsLayer { alpha = if (leavingForRow) 0f else (size.height / IMAGE_FADE_HEIGHT.toPx()).coerceIn(0f, 1f) },
                imageBox = if (shown.id == sharedId && (attachShared || queuePageOpening)) {
                  Modifier.sharedElement(pictureState, visibilityScope, bounds)
                } else {
                  Modifier
                },
                sampleBox = if (cardShared && shown.id == sharedId && attachShared) {
                  Modifier.sharedBounds(
                    rememberSharedContentState(sampleKey(shown.id)),
                    visibilityScope,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    boundsTransform = bounds,
                    resizeMode = scaleToBounds(ContentScale.Crop),
                    zIndexInOverlay = SAMPLE_Z,
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(cardCorner.coerceAtLeast(0.dp))),
                  )
                } else {
                  Modifier
                },
                showSample = settled || sampleKept,
                playing = settled,
                onTap = ::toggleChrome,
                onLongPress = {
                  tips.used(Tip.LongPressFavourite)
                  favouriteFromPicture(shown)
                },
                zoom = zoom.takeIf { page == pager.currentPage },
                playVideo = page == pager.settledPage,
                onShown = { viewing.loaded(shown.id) },
                actions = sheetMoves + pictureFavouriteAction(shown) { favouriteFromPicture(shown) } + loneActions,
              )
            }
          }
        }
      }
    }

    if (post == null) return@BoxWithConstraints
    val chromeFade = { chromeAlpha * dragFade(dismissFraction()) }
    val chromeHidden = if (chromeShown && !closing && dismiss.atRest) Modifier else Modifier.semantics { hideFromAccessibility() }
    val chrome = Modifier
      .passTouchesThrough(touchesPass)
      .then(aboveImage)
      .graphicsLayer { alpha = chromeFade() }
      .then(chromeHidden)

    val landscapeScrubFade by animateFloatAsState(if (compactHeight && video.draggedTo != null) 0f else 1f, fadeSpec)

    TopRow(
      position = if (mode.steps) positionLabel(index, loadedCount, hasMore) else null,
      positionSpoken = if (mode.steps) positionDescription(index, loadedCount, hasMore) else null,
      origin = origin.takeIf { mode == ViewerMode.Tab },
      title = PostTitle.of(post),
      titleAlpha = { 1f - halfFraction },
      statusTop = with(density) { statusTop.toDp() },
      modifier = chrome.graphicsLayer {
        translationY = -(1f - chromeLayout) * topRow
        alpha = landscapeScrubFade
      },
    )

    if (mode == ViewerMode.Tab) {
      AnimatedVisibility(
        visible = laterHint && chromeShown && !zoom.zoomed,
        modifier = Modifier.align(Alignment.CenterEnd),
        enter = fadeIn(fadeSpec),
        exit = fadeOut(fadeSpec),
      ) {
        LaterHint(Modifier.graphicsLayer { alpha = 1f - halfFraction })
      }
    }

    var levelShown by remember { mutableStateOf(false) }
    LaunchedEffect(zoom.level, zoom.zoomed) {
      levelShown = zoom.zoomed
      if (levelShown) {
        delay(ZOOM_LEVEL_SHOWN_MS)
        levelShown = false
      }
    }
    val levelAlpha by animateFloatAsState(if (levelShown) 1f else 0f, fadeSpec)
    if (levelAlpha > 0f) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = with(density) { statusTop.toDp() } + 16.dp)
          .graphicsLayer { alpha = levelAlpha },
      ) {
        Text(zoomLevelLabel(zoom.level), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
      }
    }

    val seekShown = remember(post.id) { Animatable(if (isVideo(post) && !stepped) 1f else 0f) }
    LaunchedEffect(seekShown) { if (isVideo(post)) seekShown.animateTo(1f, fadeSpec) }
    val seekStaying = { (1f - 2f * abs(pager.currentPage - pager.settledPage + pager.currentPageOffsetFraction)).coerceIn(0f, 1f) }
    if (isVideo(post)) {
      if (chromeAlpha > 0f) VideoSeekRow(
        video,
        preferences = playerPreferences,
        onDetails = if (compactHeight) ({ sheet.animateTo(sheet.stops.half) }) else null,
        onTrackPlaced = { seekTrack[0] = it },
        flush = videoLandscape == 1f,
        modifier = Modifier
          .offset {
            val room = VIDEO_SEEK_ROW_HEIGHT.toPx() + SEEK_ROW_GAP.toPx() * (1f - videoLandscape)
            IntOffset(0, (barBase() - room * chromeLayout).roundToInt())
          }
          .then(chrome)
          .graphicsLayer {
            val room = sheetTop().coerceAtMost(height) - pictureTop - fittedPictureInset(SEEK_ROOM, compactHeight).toPx()
            alpha = (room / IMAGE_FADE_HEIGHT.toPx()).coerceIn(0f, 1f) * seekShown.value * seekStaying()
          }
          .padding(horizontal = SEEK_ROW_SIDE * (1f - videoLandscape))
          .fillMaxWidth()
          .onGloballyPositioned { seekRow[0] = it },
      )
      val dragging = video.draggedTo != null
      var previewMade by remember(post.id) { mutableStateOf(false) }
      LaunchedEffect(dragging) { if (dragging) previewMade = true }
      if (chromeAlpha > 0f && previewMade) VideoScrubPreview(
        video,
        post,
        shown = dragging,
        modifier = Modifier
          .then(aboveImage)
          .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
              val origin = viewerOrigin[0]?.takeIf { it.isAttached }
              val track = seekTrack[0]?.takeIf { it.isAttached }
              if (origin == null || track == null) return@layout
              val bounds = origin.localBoundingBoxOf(track)
              val inset = SLIDER_THUMB_INSET.toPx()
              val x = bounds.left + inset + (bounds.width - 2 * inset) * (video.draggedTo ?: 0f)
              val left = (x - placeable.width / 2f).coerceIn(0f, (constraints.maxWidth - placeable.width).toFloat().coerceAtLeast(0f))
              val top = bounds.top - PREVIEW_GAP.toPx() - placeable.height
              placeable.place(left.roundToInt(), top.roundToInt())
            }
          }
          .semantics { hideFromAccessibility() },
      )
      val lineAlpha by animateFloatAsState(if (!chromeShown && !closing) 1f else 0f, fadeSpec)
      VideoProgressLine(
        video,
        Modifier
          .align(Alignment.BottomCenter)
          .then(aboveImage)
          .graphicsLayer { alpha = lineAlpha }
          .semantics { hideFromAccessibility() },
      )
    }

    val scroll = rememberSaveable(post.id, saver = ScrollState.Saver, key = viewerDetailsKey(post.id)) { ScrollState(0) }
    var detailsFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(post.id, settled) {
      if (!settled) return@LaunchedEffect
      withFrameNanos {}
      detailsFor = post.id
    }
    val composeDetails by remember(post.id) { derivedStateOf { detailsComposed(detailsFor == post.id, sheet.lift) } }
    Column(
      Modifier
        .offset { IntOffset(0, sheetTop().roundToInt()) }
        .then(chrome)
        .then(if (videoLandscape == 1f && sheet.atPeek) Modifier.semantics { hideFromAccessibility() } else Modifier)
        .fillMaxWidth()
        .height(with(density) { height.toDp() })
        .clip(RoundedCornerShape(topStart = lerp(SHEET_CORNER, 0.dp, fullFraction), topEnd = lerp(SHEET_CORNER, 0.dp, fullFraction)))
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .viewerSheet(sheet),
    ) {
      SheetHeader(
        post = post,
        sheet = sheet,
        halfFraction = { halfFraction },
        fullFraction = { fullFraction },
        statusTop = with(density) { statusTop.toDp() },
        onThumbnail = { sheet.animateTo(0f) },
        onToggleFavourite = {
          haptics.performHapticFeedback(if (post.isFavorited) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
          if (tips.offerLongPressTip(wasFavourited = post.isFavorited, ran = actions.toggleFavourite(post))) {
            messages.show(Tip.LongPressFavourite.text)
          }
        },
        onVote = {
          haptics.performHapticFeedback(HapticFeedbackType.Confirm)
          actions.vote(post, it)
        },
        menu = {
          PostMenu(
            post,
            shown = chromeShown,
            onKeep = keep?.let { keepShown -> { keepShown(post) } },
            onRemove = if (keep != null && canUnkeep(post)) ({ onUnkeep(post) }) else null,
          )
        },
        onDrop = onDrop.takeIf { mode == ViewerMode.Tab },
        modifier = Modifier.draggable(
          rememberDraggableState { sheet.dragBy(it) },
          Orientation.Vertical,
          enabled = !closing,
          onDragStopped = { sheet.settle(it) },
        ),
      )
      if (!composeDetails) Spacer(Modifier.weight(1f)) else PostDetails(
        post,
        scroll = scroll,
        showStats = false,
        showUploader = false,
        searchTags = searchTags,
        modifier = Modifier.weight(1f).graphicsLayer {
          alpha = (sheet.lift / (navBottom + DETAILS_FADE_IN.toPx())).coerceIn(0f, 1f)
        },
      )
      Spacer(Modifier.height(with(density) { navBottom.toDp() }))
    }

    val seekRoom by animateFloatAsState(if (isVideo(post)) with(density) { SEEK_ROOM.toPx() } else 0f, spatialSpec())
    val scrubbing = video.draggedTo != null
    val pillAlpha by animateFloatAsState(if (scrubbing) 0f else 1f, fadeSpec)
    val rowHalf = with(density) { (BottomRowLayout.Height / 2).toPx() }
    val pillAbove = { with(density) { PILL_LINE.toPx() } + seekRoom }
    val pillLine = { barBase() - pillAbove() * chromeLayout + rowHalf * (1f - chromeLayout) }
    val pillRoom = {
      pillShown(
        room = pillLine() - rowHalf - topRow,
        roomAtPeek = peekTop - pillAbove() - rowHalf - topRow,
        fade = with(density) { IMAGE_FADE_HEIGHT.toPx() },
      )
    }
    val pillAway by remember(pillRoom) { derivedStateOf { pillRoom() < 0.05f } }
    UndoHiddenWhile(!chromeShown || pillAway)
    QueueBar(
      onOpenQueue = LocalOpenTabList.current,
      swipesQueue = mode.swipesQueue,
      overPicture = true,
      modifier = Modifier
        .passTouchesThrough(touchesPass || pillAway)
        .then(aboveImage)
        .then(if (scrubbing || pillAway) Modifier.semantics { hideFromAccessibility() } else chromeHidden)
        .graphicsLayer {
          val line = if (floatingBar) {
            alpha = (if (handedBack) 0f else 1f) * pillAlpha * (if (closing) 1f else pillRoom())
            val from = gridLine + if (closing) 0f else slideFrom
            from + (pillLine() - from) * docked()
          } else {
            alpha = chromeFade() * pillAlpha * pillRoom()
            pillLine()
          }
          translationY = line - rowHalf
        },
    )

    val currentId by rememberUpdatedState(post.id)
    val hearts = remember(actions) { actions.hearts.forShown { currentId } }
    val burstInset = with(density) { fittedPictureInset(seekRoom = if (isVideo(post)) SEEK_ROOM else 0.dp, compactHeight).toPx() }
    val overPictureCentre = Modifier
      .align(Alignment.TopCenter)
      .offset {
        val half = PictureBurstSize.toPx() / 2
        val centre = phoneBurstCentre(
          pictureTop = pictureTop * imageLayout,
          sheetTop = sheetTop(imageLayout).coerceAtMost(height),
          inset = burstInset * imageLayout,
          minCentre = statusTop + half,
        )
        IntOffset(0, (centre - half).roundToInt())
      }
    FavouriteBursts(hearts, overPictureCentre.then(aboveImage), size = PictureBurstSize)


    closePeek?.Line(
      Modifier
        .align(Alignment.TopCenter)
        .offset { IntOffset(0, (statusTop + 8.dp.toPx()).roundToInt()) }
        .graphicsLayer { alpha = (dismissFraction() * CLOSE_LINE_FADE_IN).coerceAtMost(1f) },
    )
  }
}

private const val CLOSE_LINE_FADE_IN = 4f

internal fun pictureFavouriteAction(post: PostDto, favourite: () -> Unit): List<CustomAccessibilityAction> =
  if (post.isFavorited) emptyList() else listOf(CustomAccessibilityAction(FAVOURITE_ACTION) { favourite(); true })

internal const val FAVOURITE_ACTION = "Favourite"

internal fun favouriteWithHaptic(haptics: HapticFeedback, favourite: () -> Unit) {
  haptics.performHapticFeedback(HapticFeedbackType.Confirm)
  favourite()
}

internal fun phoneBurstCentre(pictureTop: Float, sheetTop: Float, inset: Float, minCentre: Float): Float =
  maxOf(pictureTop + (sheetTop - inset - pictureTop) / 2f, minCentre)

internal fun dragDownCloses(dy: Float, atPeek: Boolean, zoomed: Boolean, mode: ViewerMode, closable: Boolean, fromSeekRow: Boolean): Boolean =
  dy > 0f && atPeek && !zoomed && closable && (mode == ViewerMode.Overlay || !fromSeekRow)

internal const val LONE_CLOSE_SHARE = 0.2f

internal fun loneDragCloses(offset: Float, height: Float, velocity: Float, minFling: Float): Boolean =
  offset > 0f && (offset > height * LONE_CLOSE_SHARE || velocity > minFling)

private const val CLOSE_ACTION = "Close"

private const val SAMPLE_Z = 0.5f

private const val CARD_Z = -0.5f

class PostOrigin(val label: String, val open: () -> Unit)

@Composable
private fun OriginChip(origin: PostOrigin, wrap: Boolean = false) {
  Surface(
    onClick = origin.open,
    shape = if (wrap) RoundedCornerShape(20.dp) else CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
    modifier = Modifier.minimumInteractiveComponentSize().widthIn(max = if (wrap) Dp.Unspecified else ORIGIN_MAX_WIDTH),
  ) {
    Row(
      Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(bookmark, contentDescription = null, modifier = Modifier.size(14.dp))
      Text(origin.label, style = MaterialTheme.typography.labelMedium, maxLines = if (wrap) 3 else 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

private val ORIGIN_MAX_WIDTH = 240.dp

private fun dragFade(fraction: Float): Float = (1f - fraction * 3f).coerceAtLeast(0f)

private const val CONTROLS_SHOWN_AT = 0.9f

private val SEEK_ROW_GAP = 12.dp

internal val SEEK_ROOM = SEEK_ROW_GAP + VIDEO_SEEK_ROW_HEIGHT

private val SEEK_ROW_SIDE = 4.dp

private val PREVIEW_GAP = 12.dp

private val SLIDER_THUMB_INSET = 10.dp

private val SHEET_CORNER = 28.dp

@Composable
private fun TopRow(
  position: String?,
  positionSpoken: String?,
  origin: PostOrigin?,
  title: String?,
  titleAlpha: () -> Float,
  statusTop: Dp,
  modifier: Modifier,
) {
  val stacked = LocalDensity.current.fontScale >= STACK_TOP_ROW_AT
  Box(
    modifier
      .fillMaxWidth()
      .heightIn(min = statusTop + TOP_ROW)
      .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))),
  ) {
    val pills: @Composable () -> Unit = {
      if (position != null && positionSpoken != null) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
          modifier = Modifier.clearAndSetSemantics { contentDescription = positionSpoken },
        ) {
          Row(
            Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(grid_view, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(position, style = MaterialTheme.typography.labelMedium)
          }
        }
      }
      if (origin != null) OriginChip(origin, wrap = stacked)
    }
    val titleText: @Composable (Modifier) -> Unit = { titleModifier ->
      Text(
        title ?: PostTitle.UNTITLED,
        style = MaterialTheme.typography.titleSmall,
        color = if (title == null) Color.White.copy(alpha = 0.7f) else Color.White,
        maxLines = if (stacked) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = titleModifier.graphicsLayer { alpha = titleAlpha() },
      )
    }
    if (stacked) {
      Column(
        Modifier.padding(top = statusTop, start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { pills() }
        titleText(Modifier)
      }
    } else {
      Row(
        Modifier.padding(top = statusTop, start = 16.dp, end = 16.dp).height(TOP_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        pills()
        titleText(Modifier)
      }
    }
  }
}

private const val STACK_TOP_ROW_AT = 1.3f

@Composable
private fun SheetHeader(
  post: PostDto,
  sheet: ViewerSheetState,
  halfFraction: () -> Float,
  fullFraction: () -> Float,
  statusTop: Dp,
  onThumbnail: () -> Unit,
  onToggleFavourite: () -> Unit,
  onVote: (Int?) -> Unit,
  menu: @Composable () -> Unit,
  modifier: Modifier,
  onDrop: (() -> Unit)? = null,
  actions: PostActions = koinInject(),
  accessibility: AccessibilitySettings = koinInject(),
) {
  val leftHanded by accessibility.leftHanded.collectAsStateWithLifecycle()
  val voteRejected = rememberRejections(actions, Rejection.Vote(post.id))
  val favouriteRejected = rememberRejections(actions, Rejection.Favourite(post.id))
  val signedIn by actions.isSignedIn.collectAsStateWithLifecycle()
  Column(modifier.fillMaxWidth()) {
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    Column(Modifier.drawBehind { drawRect(barColor.copy(alpha = fullFraction())) }) {
      Spacer(Modifier.layout { _, constraints -> layout(constraints.maxWidth, (statusTop.roundToPx() * fullFraction()).roundToInt()) {} })
      SheetHandle(sheet, fullFraction)
      Box(
        Modifier
          .fillMaxWidth()
          .clipToBounds()
          .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            layout(placeable.width, (placeable.height * halfFraction()).roundToInt()) { placeable.place(0, 0) }
          },
      ) {
        TitleRow(post, fullFraction, onThumbnail)
      }
    }
    Spacer(Modifier.layout { _, constraints -> layout(constraints.maxWidth, (STATS_GAP.roundToPx() * fullFraction()).roundToInt()) {} })
    PostStats(
      post,
      onToggleFavourite = onToggleFavourite,
      onVote = onVote,
      voteRejected = voteRejected,
      favouriteRejected = favouriteRejected,
      onSignIn = if (signedIn) null else actions::openSignIn,
      trailing = menu,
      countsApart = true,
      mirrored = leftHanded,
      leading = onDrop?.let { drop -> { DropButton(drop) } },
      modifier = Modifier.padding(start = if (leftHanded) 4.dp else 16.dp, end = if (leftHanded) 16.dp else 4.dp, bottom = 16.dp),
    )
    PostCounts(post, Modifier.graphicsLayer { alpha = halfFraction() }.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
  }
}

@Composable
internal fun TitleRow(post: PostDto, fullFraction: () -> Float, onThumbnail: () -> Unit) {
  val named = PostTitle.of(post)
  val title = named ?: PostTitle.UNTITLED
  val titleColor = if (named == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
  val byline = postByline(post)
  Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier
        .layout { measurable, constraints ->
          val placeable = measurable.measure(constraints)
          layout((placeable.width * fullFraction()).roundToInt(), placeable.height) { placeable.place(0, 0) }
        }
        .graphicsLayer { alpha = fullFraction() },
    ) {
      PostThumbnail(post, Modifier.padding(end = 12.dp).size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onThumbnail))
    }
    Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
      Row(Modifier.graphicsLayer { alpha = 1f - fullFraction() }, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = titleColor, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
          byline,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
      }
      Column(Modifier.graphicsLayer { alpha = fullFraction() }) {
        Text(title, color = titleColor, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(byline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
      }
    }
  }
}

@Composable
private fun PostThumbnail(post: PostDto, modifier: Modifier, imageLoader: ImageLoader = koinInject()) {
  val context = LocalPlatformContext.current
  val decoded = remember(post.md5) { decodedInMemory(imageLoader, post.md5) }
  if (decoded != null) {
    Image(decoded.asPainter(context), contentDescription = "Back to the image", contentScale = ContentScale.Crop, modifier = modifier)
  } else {
    AsyncImage(
      model = postImageRequest(context, PostImage.of(post, Variant.Preview)),
      contentDescription = "Back to the image",
      contentScale = ContentScale.Crop,
      modifier = modifier,
    )
  }
}

@Composable
internal fun PostMenu(
  post: PostDto,
  shown: Boolean,
  onKeep: (() -> Unit)? = null,
  onRemove: (() -> Unit)? = null,
  saver: PostSaver = koinInject(),
  messages: Messages = koinInject(),
) {
  var open by remember { mutableStateOf(false) }
  val saving by saver.saving.collectAsStateWithLifecycle()
  val saved by saver.saved.collectAsStateWithLifecycle()
  val fresh by saver.fresh.collectAsStateWithLifecycle()
  DisposableEffect(post.id) {
    val release = saver.claim(post.id)
    onDispose(release)
  }
  val file = saved[post.id]
  val outcome = fresh[post.id]?.takeUnless { it is SaveOutcome.Failed && it.noFile }
  LaunchedEffect(fresh[post.id]) { if (fresh[post.id] != outcome) saver.settle(post.id) }
  val haptics = LocalHapticFeedback.current
  LaunchedEffect(outcome) { if (outcome is SaveOutcome.Saved) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
  val window = rememberActionWindowMillis()
  val chipInteractions = remember { MutableInteractionSource() }
  val chipPressed by chipInteractions.collectIsPressedAsState()
  val chipFocused by chipInteractions.collectIsFocusedAsState()
  var left by remember { mutableFloatStateOf(1f) }
  val showing by rememberUpdatedState(shown)
  val pressed by rememberUpdatedState(chipPressed || chipFocused)
  LaunchedEffect(outcome) {
    if (outcome == null) return@LaunchedEffect
    runWindow(window, isPaused = { !showing || pressed }) { left = it }
    saver.settle(post.id)
  }
  val openFile = file?.let { f -> { f.open() } }
  Box {
    when (outcome) {
      is SaveOutcome.Saved -> CornerActionChip(
        open_in_new,
        "Open",
        error = false,
        onClick = {
          saver.settle(post.id)
          outcome.file.open()
        },
        left = { left },
        interactionSource = chipInteractions,
      )
      is SaveOutcome.Failed -> CornerActionChip(
        refresh,
        "Retry",
        error = true,
        onClick = { saver.save(post) },
        left = { left },
        interactionSource = chipInteractions,
      )
      null -> MenuButton(
        state = when {
          post.id in saving -> MenuSaveState.Saving
          file != null -> MenuSaveState.Saved
          else -> MenuSaveState.Idle
        },
        onClick = { open = true },
        description = if (post.id in saving) "More, saving" else "More",
        modifier = if (openFile != null) {
          Modifier.semantics { customActions = listOf(CustomAccessibilityAction("Open saved file") { openFile(); true }) }
        } else {
          Modifier
        },
      )
    }
    LiveAnnouncement(if (outcome is SaveOutcome.Saved) "Saved. Open available" else "")
    GroupedMenu(expanded = open, onDismissRequest = { open = false }) {
      val groups = if (onKeep != null) 2 else 1
      if (onKeep != null) {
        MenuGroup(0, groups) {
          MenuAction(
            text = { Text(if (onRemove != null) REMOVE_FROM_QUEUE else KEEP_FOR_LATER) },
            leadingIcon = { Icon(if (onRemove != null) close else bookmark, contentDescription = null) },
            onClick = {
              open = false
              (onRemove ?: onKeep)()
            },
          )
        }
      }
      MenuGroup(groups - 1, groups) {
        MenuAction(
          text = { Text("Share") },
          leadingIcon = { Icon(share, contentDescription = null) },
          onClick = {
            open = false
            platformShareText(postPageUrl(post))
          },
        )
        if (openFile != null) {
          MenuAction(
            text = { Text("Open saved file") },
            leadingIcon = { Icon(open_in_new, contentDescription = null) },
            onClick = {
              open = false
              openFile()
            },
          )
        } else {
          MenuAction(
            text = { Text("Save") },
            leadingIcon = { Icon(download, contentDescription = null) },
            onClick = {
              open = false
              saver.save(post)
            },
          )
        }
        MenuAction(
          text = { Text("Open in browser") },
          leadingIcon = { Icon(open_in_new, contentDescription = null) },
          onClick = {
            open = false
            messages.openUrl(postPageUrl(post))
          },
        )
      }
    }
  }
}

private fun Modifier.passTouchesThrough(enabled: Boolean): Modifier =
  graphicsLayer {
    clip = enabled
    shape = if (enabled) NoArea else RectangleShape
  }

private val NoArea = object : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = Outline.Rectangle(Rect.Zero)
}

internal fun detailsComposed(shownAFrame: Boolean, lift: Float): Boolean = shownAFrame || lift > 0f

@Composable
private fun <T> sameWhileEqual(value: T): T = remember(value) { value }

private const val ZOOM_LEVEL_SHOWN_MS = 1200L

internal fun viewerDetailsKey(id: String): String = "viewer-details-$id"

internal fun zoomLevelLabel(level: Float): String = "${(level * 10).roundToInt() / 10.0}×"

@Composable
private fun NeighbourPicture(peek: PicturePeek, forward: Boolean, insetPx: () -> Float, modifier: Modifier) {
  val context = LocalPlatformContext.current
  Box(modifier.fillMaxSize().semantics { hideFromAccessibility() }) {
    Box(
      Modifier.layout { measurable, constraints ->
        val pictureHeight = (constraints.maxHeight - insetPx().roundToInt()).coerceIn(1, constraints.maxHeight)
        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, pictureHeight))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
      },
      contentAlignment = Alignment.Center,
    ) {
      val loaded = peek.picture
      if (loaded != null) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          val (width, height) = fittedBox(maxWidth, maxHeight, aspectRatio(loaded.post))
          Image(loaded.picture.asPainter(context), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.size(width, height))
        }
      } else {
        Column(
          Modifier.padding(horizontal = 32.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(peek.kind, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
          Text(
            peek.title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    peek.leaving?.let { LeavingEdge(it, forward) }
  }
}

@Stable
class BarInsets(statusTop: Int = 0, navBottom: Int = 0) {
  var statusTop by mutableIntStateOf(statusTop)
    internal set
  var navBottom by mutableIntStateOf(navBottom)
    internal set
}

val LocalBarInsets = staticCompositionLocalOf<BarInsets?> { null }

@Composable
fun rememberBarInsets(): BarInsets {
  val size = LocalWindowInfo.current.containerSize
  return remember(size) { BarInsets() }
}

@Composable
internal fun LonePostPeek(
  post: PostDto,
  picture: coil3.Image,
  origin: PostOrigin?,
  seenBars: BarInsets?,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val context = LocalPlatformContext.current
  BoxWithConstraints(modifier.fillMaxSize().background(Color.Black).semantics { hideFromAccessibility() }) {
    val height = constraints.maxHeight.toFloat()
    val statusTop = max(seenBars?.statusTop ?: 0, WindowInsets.statusBars.getTop(density))
    val navBottom = max(seenBars?.navBottom ?: 0, WindowInsets.navigationBars.getBottom(density))
    with(density) {
      val compactHeight = isCompactHeight(maxHeight)
      val pictureTop = pictureTop(statusTop, compactHeight)
      val peekTop = height - PEEK_HEADER.toPx() - navBottom
      val roomHeight = (peekTop.coerceAtMost(height) - pictureTop).roundToInt().coerceAtLeast(1)
      val inset = fittedPictureInset(seekRoom = if (isVideo(post)) SEEK_ROOM else 0.dp, compactHeight).toPx().roundToInt()
      val pictureHeight = (roomHeight - inset).coerceIn(1, roomHeight)
      BoxWithConstraints(
        Modifier.offset { IntOffset(0, pictureTop.roundToInt()) }.fillMaxWidth().height(pictureHeight.toDp()),
        contentAlignment = Alignment.Center,
      ) {
        val (width, boxHeight) = fittedBox(maxWidth, maxHeight, aspectRatio(post))
        Image(picture.asPainter(context), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.size(width, boxHeight))
      }
      Box(
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height((height - peekTop).coerceAtLeast(0f).toDp())
          .clip(RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER))
          .background(MaterialTheme.colorScheme.surfaceContainerLow),
      )
      TopRow(
        position = null,
        positionSpoken = null,
        origin = origin,
        title = PostTitle.of(post),
        titleAlpha = { 1f },
        statusTop = statusTop.toDp(),
        modifier = Modifier,
      )
    }
  }
}

@Composable
private fun DropButton(onDrop: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Box(
    Modifier
      .size(48.dp)
      .clickable(role = Role.Button, onClick = onDrop)
      .semantics { contentDescription = DROP_LABEL },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh),
      contentAlignment = Alignment.Center,
    ) {
      Icon(close, contentDescription = null, tint = colors.error, modifier = Modifier.size(22.dp))
    }
  }
}

internal const val DROP_LABEL = "Drop"

@Composable
private fun LaterHint(modifier: Modifier) {
  Row(
    modifier.padding(end = 10.dp).semantics { hideFromAccessibility() },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(LATER_HINT, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
    Icon(chevron_right, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
  }
}

private const val LATER_HINT = "Later"

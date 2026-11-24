package dev.marzahn.sankaku.features.grid

import dev.marzahn.sankaku.shell.LocalOpenSearch
import dev.marzahn.sankaku.shell.RetryWhenBack
import dev.marzahn.sankaku.shell.sharedMosaicThumb
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionDefaults
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.google.fonts.icons.lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import dev.marzahn.sankaku.ui.components.Shortcuts
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import dev.marzahn.sankaku.gridScrollbarRoom
import dev.marzahn.sankaku.platformCellGap
import dev.marzahn.sankaku.platformGridEdge
import dev.marzahn.sankaku.features.post.decodedInMemory
import dev.marzahn.sankaku.features.post.PostMediaBadge
import dev.marzahn.sankaku.features.post.cellDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.marzahn.sankaku.tabs.LocalQueueActions
import org.koin.compose.koinInject
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.queue.FeedHeading
import dev.marzahn.sankaku.queue.PlacedHeading
import dev.marzahn.sankaku.data.PostOverride
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.data.merge
import androidx.compose.runtime.mutableIntStateOf
import dev.marzahn.sankaku.images.ImageFailure
import dev.marzahn.sankaku.images.PostImage
import dev.marzahn.sankaku.images.failure
import dev.marzahn.sankaku.images.postImageRequest
import dev.marzahn.sankaku.ui.components.ImageFailed
import dev.marzahn.sankaku.ui.components.FocusRingGap
import dev.marzahn.sankaku.ui.components.FocusRingWidth
import androidx.compose.runtime.withFrameNanos
import dev.marzahn.sankaku.ui.theme.Amber
import dev.marzahn.sankaku.ui.components.AppLoadingIndicator
import dev.marzahn.sankaku.ui.components.CLOSE_TO_KEPT
import dev.marzahn.sankaku.ui.components.CLOSE_NEXT_CARD
import dev.marzahn.sankaku.ui.components.DoneWith
import dev.marzahn.sankaku.ui.components.DoneWithFollow
import dev.marzahn.sankaku.ui.components.NewBadge
import dev.marzahn.sankaku.ui.components.ApiErrorState
import dev.marzahn.sankaku.ui.components.FavouriteBurst
import dev.marzahn.sankaku.ui.components.TipAnchor
import dev.marzahn.sankaku.ui.Tip
import dev.marzahn.sankaku.ui.Tips
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import dev.marzahn.sankaku.features.post.PostActions
import dev.marzahn.sankaku.features.post.Rejection
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.components.AlreadyInTab
import dev.marzahn.sankaku.ui.components.seenImage
import dev.marzahn.sankaku.ui.components.spatialBoundsTransform
import dev.marzahn.sankaku.ui.components.effectsSpec
import dev.marzahn.sankaku.ui.components.spatialSpec
import dev.marzahn.sankaku.ui.dragToScroll
import dev.marzahn.sankaku.ui.onRightClick
import dev.marzahn.sankaku.ui.openOnClick
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.round
import dev.marzahn.sankaku.ui.reflowWhenSettled

val GRID_TOP_GAP: Dp = 8.dp

const val OVERLAY_SCRIM_ALPHA = 0.6f

val CELL_CORNER: Dp = 8.dp

internal fun openInBackgroundLabel(touch: Boolean = platformIsTouch): String = if (touch) "Keep" else "Open in background tab"

internal fun keepTip(touch: Boolean = platformIsTouch): Tip = if (touch) Tip.GridGestures else Tip.KeepLoop

private val CURSOR_CORNER: Dp = 16.dp
internal val CURSOR_GAP: Dp = FocusRingGap
internal val CURSOR_RING: Dp = FocusRingWidth

internal val CURSOR_REACH: Dp = CURSOR_GAP + CURSOR_RING

private fun LazyStaggeredGridState.clipLine(index: Int): Float =
  -(layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset?.y ?: 0).toFloat()

private inline fun DrawScope.clipAbove(top: Float, block: DrawScope.() -> Unit) {
  val room = CURSOR_REACH.toPx() * 2
  clipRect(left = -room, top = top, right = size.width + room, bottom = size.height + room, block = block)
}

val VIEWER_CORNER: Dp = 28.dp
val CELL_SHAPE = RoundedCornerShape(CELL_CORNER)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PostGrid(
  items: LazyPagingItems<PostDto>,
  overrides: Map<String, PostOverride>,
  state: LazyStaggeredGridState,
  hiddenId: String?,
  sharedId: String?,
  viewerClosing: Boolean,
  cursor: Int?,
  onOpen: (index: Int) -> Unit,
  onOpenInNewTab: (PostDto) -> Unit,
  onToggleFavourite: (post: PostDto, viaDoubleTap: Boolean) -> Boolean,
  modifier: Modifier = Modifier,
  dimUnfavourited: Boolean = false,
  openInTabs: Set<String> = emptySet(),
  isKeptCopy: (PostDto) -> Boolean = { false },
  canUnkeep: (PostDto) -> Boolean = { false },
  onPreviewLoaded: (PostDto, Image) -> Unit = { _, _ -> },
  alreadyInTab: AlreadyInTab? = null,
  isSeen: (PostDto) -> Boolean = { false },
  isLate: (id: String) -> Boolean = { false },
  keepTipId: String? = null,
  keepTipShown: Boolean = false,
  endOfSource: EndOfSource? = null,
  mosaicThumbs: List<String> = emptyList(),
  underPage: AnimatedVisibilityScope? = null,
  pendingFocus: Int? = null,
  onFocusTaken: () -> Unit = {},
  onCellFocused: (index: Int?) -> Unit = {},
  onCellKey: (KeyEvent) -> Boolean = { false },
  gridItems: GridItems = GridItems.None,
  heading: @Composable (PlacedHeading) -> Unit = {},
) {
  val context = LocalPlatformContext.current
  val haptics = LocalHapticFeedback.current
  val imageLoader = koinInject<ImageLoader>()
  val tips = koinInject<Tips>()
  val queueActions = LocalQueueActions.current
  val actions = koinInject<PostActions>()
  val rejected = remember { mutableStateMapOf<String, Int>() }
  val composedCells = remember { mutableSetOf<String>() }
  if (platformIsTouch) {
    LaunchedEffect(actions) {
      actions.rejections.collect {
        if (it is Rejection.Favourite && it.viaDoubleTap && it.postId in composedCells) {
          rejected[it.postId] = (rejected[it.postId] ?: 0) + 1
        }
      }
    }
  }
  val bounds = spatialBoundsTransform(slow = !viewerClosing)
  val cornerSpec = spatialSpec<Dp>(slow = !viewerClosing)
  val cellFade = effectsSpec<Float>()
  val postCell: @Composable (item: Int) -> Unit = cell@{ item ->
    val index = gridItems.postOf(item) ?: return@cell
    var loaded by remember(items, index) { mutableStateOf(Snapshot.withoutReadObservation { items.peekOrNull(index) }) }
    LaunchedEffect(items, index) { snapshotFlow { items.peekOrNull(index) }.collect { loaded = it } }
    Snapshot.withoutReadObservation { items.getOrNull(index) }
    val post = loaded?.let { it.merge(overrides[it.id]) } ?: return@cell
    val focus = remember { FocusRequester() }
    var ownFocus by remember { mutableStateOf(false) }
    if (index == pendingFocus) {
      LaunchedEffect(Unit) {
        withFrameNanos {}
        focus.requestFocus()
        onFocusTaken()
      }
    }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val atCursor = index == cursor
    val openInTab = post.id in openInTabs || isKeptCopy(post)
    val unkeeps = openInTab && canUnkeep(post)
    val holdLabel = if (unkeeps) REMOVE_FROM_QUEUE else openInBackgroundLabel()
    val alreadyTab = alreadyInTab?.of(post.id)
    val seen = isSeen(post)
    val late = isLate(post.id)
    val ring = Amber
    val cellCorner by animateDpAsState(if (atCursor) CURSOR_CORNER else CELL_CORNER, spatialSpec())
    var burst by remember { mutableStateOf<Burst?>(null) }
    DisposableEffect(post.id) {
      composedCells += post.id
      onDispose {
        composedCells -= post.id
        rejected.remove(post.id)
      }
    }
    val cellRejected = rejected[post.id]
    LaunchedEffect(cellRejected) {
      if (cellRejected == null) return@LaunchedEffect
      rejected.remove(post.id)
      haptics.performHapticFeedback(HapticFeedbackType.Reject)
      burst = Burst(post.isFavorited, (burst?.id ?: 0) + 1)
    }
    val favouriteFromCell = {
      if (onToggleFavourite(post, true)) {
        val favourited = !post.isFavorited
        haptics.performHapticFeedback(if (favourited) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        burst = Burst(favourited, (burst?.id ?: 0) + 1)
      }
    }
    val unfavourited = dimUnfavourited && overrides[post.id]?.isFavourited == false
    var menuAt by remember(post.id) { mutableStateOf<Offset?>(null) }
    Box(
      Modifier
        .then(if (platformIsTouch) Modifier else Modifier.onRightClick { menuAt = it })
        .fillMaxWidth()
        .aspectRatio(gridCellRatio(post))
        .then(
          when (val at = mosaicThumbs.indexOf(post.md5)) {
            -1 -> Modifier
            else -> Modifier.sharedMosaicThumb(post.md5, underPage, enabled = true, clip = CELL_SHAPE, cell = true).testTag(mosaicCellTag(at))
          },
        )
        .hoverable(hover)
        .zIndex(if (atCursor) 1f else 0f)
        .drawBehind {
          if (!atCursor) return@drawBehind
          val out = (CURSOR_GAP + CURSOR_RING / 2).toPx()
          val line = state.clipLine(item)
          clipAbove(if (line > 0f) line else -out * 2) {
            drawRoundRect(
              ring,
              topLeft = Offset(-out, -out),
              size = Size(size.width + 2 * out, size.height + 2 * out),
              cornerRadius = CornerRadius(cellCorner.toPx() + out),
              style = Stroke(CURSOR_RING.toPx()),
            )
          }
        }
        .drawWithContent { clipAbove(state.clipLine(item)) { this@drawWithContent.drawContent() } },
    ) {
      AnimatedVisibility(visible = post.id != hiddenId, enter = EnterTransition.None, exit = ExitTransition.None) {
        val corner by transition.animateDp({ cornerSpec }) { if (it == EnterExitState.Visible) CELL_CORNER else VIEWER_CORNER }
        val sharing = sharingConfig(post.id == sharedId)
        Surface(
          shape = RoundedCornerShape(cellCorner),
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier = Modifier
            .fillMaxSize()
            .sharedBounds(
              rememberSharedContentState(cardKey(post.id), sharing),
              animatedVisibilityScope = this,
              enter = EnterTransition.None,
              exit = fadeOut(cellFade),
              boundsTransform = bounds,
              resizeMode = RemeasureToBounds,
              clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner)),
            )
            .focusRequester(focus)
            .onFocusChanged {
              ownFocus = it.isFocused
              if (it.hasFocus && !platformIsTouch) onCellFocused(index)
            }
            .onPreviewKeyEvent { ownFocus && it.key == Shortcuts.OpenHere.key && onCellKey(it) }
            .openOnClick(
              onOpen = { onOpen(index) },
              onOpenInNewTab = { onOpenInNewTab(post) },
              inTab = openInTab && !unkeeps,
              longPressLabel = holdLabel,
              focusFill = platformIsTouch,
            )
            .testTag(gridCellTag(index))
            .semantics {
              contentDescription = cellDescription(
                post,
                openInTab,
                unfavourited,
                alreadyTab?.position,
                seen,
                keptAgain = alreadyTab?.again == true,
                new = late,
              )
              if (alreadyTab != null) liveRegion = LiveRegionMode.Polite
              selected = atCursor
              customActions = listOf(
                CustomAccessibilityAction(holdLabel) {
                  onOpenInNewTab(post)
                  true
                },
                CustomAccessibilityAction(if (post.isFavorited) "Unfavourite" else "Favourite") {
                  favouriteFromCell()
                  true
                },
              ) + queueActions
            },
        ) {
          val sample = remember(post.md5, post.id == sharedId) {
            if (post.id == sharedId) decodedInMemory(imageLoader, post.md5, previewToo = false) else null
          }
          var attempt by remember(post.id) { mutableIntStateOf(0) }
          var failure by remember(post.id) { mutableStateOf<ImageFailure?>(null) }
          Box(Modifier.fillMaxSize().sharedElement(rememberSharedContentState(imageKey(post.id), sharing), this@AnimatedVisibility, bounds)) {
            if (sample != null) {
              Image(
                sample.asPainter(context),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().seenImage(seen),
              )
            } else {
              RetryWhenBack {
                if (failure == ImageFailure.Unreachable) {
                  failure = null
                  attempt++
                }
              }
              key(attempt) {
                AsyncImage(
                  model = gridCellRequest(context, post),
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  onState = {
                    failure = it.failure()
                    if (it is AsyncImagePainter.State.Success) onPreviewLoaded(post, it.result.image)
                  },
                  modifier = Modifier.fillMaxSize().seenImage(seen),
                )
              }
            }
          }
          if (sample == null) {
            failure?.let {
              ImageFailed(
                noImage = it == ImageFailure.NoImage,
                onRetry = {
                  failure = null
                  attempt++
                },
                compact = true,
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
          if (post.id == sharedId) {
            Box(
              Modifier.fillMaxSize().sharedBounds(
                rememberSharedContentState(sampleKey(post.id), sharing),
                this@AnimatedVisibility,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                boundsTransform = bounds,
              ),
            )
          }
          val marksOnTop = Modifier.renderInSharedTransitionScopeOverlay(MARKS_Z) {
            post.id == sharedId && viewerClosing && isTransitionActive
          }
          CellOverlay(
            favourited = post.isFavorited,
            unfavourited = unfavourited,
            hovered = hovered,
            onToggleFavourite = { onToggleFavourite(post, false) },
            openInTab = openInTab,
            alreadyTab = alreadyTab,
            seen = seen,
            keyHints = atCursor && !openInTab,
            modifier = marksOnTop.clearAndSetSemantics {},
          )
          Box(Modifier.fillMaxSize().then(marksOnTop).clearAndSetSemantics {}) {
            Row(Modifier.align(Alignment.TopStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              PostMediaBadge(post)
              if (late) NewBadge()
            }
          }
          burst?.let {
            key(it.id) { FavouriteBurst(it.favourited, onFinished = { burst = null }, Modifier.align(Alignment.Center)) }
          }
        }
      }
      if (post.id == keepTipId) {
        TipAnchor(keepTip(), tips, keepTipShown, Modifier.matchParentSize())
      }
      menuAt?.let { at ->
        Box(Modifier.offset { at.round() }) {
          CellMenu(
            favourited = post.isFavorited,
            onDismiss = { menuAt = null },
            onOpen = { onOpen(index) },
            onOpenInBackground = { onOpenInNewTab(post) },
            onToggleFavourite = favouriteFromCell,
          )
        }
      }
    }
  }
  LazyVerticalStaggeredGrid(
    columns = StaggeredGridCells.Adaptive(gridMinCellWidth()),
    state = state,
    contentPadding = PaddingValues(
      start = platformGridEdge,
      top = CURSOR_REACH,
      end = platformGridEdge + gridScrollbarRoom,
      bottom = platformGridEdge,
    ),
    verticalItemSpacing = platformCellGap,
    horizontalArrangement = Arrangement.spacedBy(platformCellGap),
    modifier = modifier.reflowWhenSettled().padding(top = GRID_TOP_GAP - CURSOR_REACH).dragToScroll(state),
  ) {
    val postKey = items.itemKey { it.id }
    items(
      count = gridItems.count(items.itemCount),
      key = { item -> gridItems.postOf(item)?.let(postKey) ?: gridItems.headingAt(item)?.let(::headingKey) ?: headingKeyOf(item) },
      contentType = { item -> if (gridItems.postOf(item) == null) HEADING_CONTENT else POST_CONTENT },
      span = { item -> if (gridItems.postOf(item) == null) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane },
    ) { item ->
      if (gridItems.postOf(item) == null) gridItems.headingAt(item)?.let { heading(it) } else postCell(item)
    }
    item(span = StaggeredGridItemSpan.FullLine) { Footer(items, endOfSource) }
  }
}

internal fun headingKey(placed: PlacedHeading): Long {
  val kind = when (placed.heading) {
    is FeedHeading.New -> 0
    FeedHeading.CaughtUp -> 1
    is FeedHeading.Continue -> 2
    is FeedHeading.UpToDate -> 3
    is FeedHeading.LikedToday -> 4
    FeedHeading.Earlier -> 5
  }
  return -(placed.before.toLong() * HEADING_KINDS + kind) - 1
}

private const val HEADING_KINDS = 6

private fun headingKeyOf(item: Int): Long = Long.MIN_VALUE + item

private const val HEADING_CONTENT = 1
private const val POST_CONTENT = 0

private data class Burst(val favourited: Boolean, val id: Int)

private const val MARKS_Z = 0.75f

@Composable
internal fun SignedOutNotice(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    modifier = modifier.fillMaxWidth().padding(start = platformGridEdge, end = platformGridEdge, top = GRID_TOP_GAP),
  ) {
    Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(lock, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
        "Some posts need an account",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
      )
      TextButton(onClick = onSignIn) { Text("Sign in") }
    }
  }
}

class EndOfSource(
  val name: String,
  val keptStay: Int,
  val onClose: () -> Unit,
  val sortLater: (() -> Unit)? = null,
  val followAndClose: DoneWithFollow? = null,
) {
  val closeLabel: String get() = when {
    keptStay > 0 -> CLOSE_TO_KEPT
    sortLater != null -> CLOSE_NEXT_CARD
    else -> "Close $name"
  }
}

@Composable
private fun Footer(items: LazyPagingItems<PostDto>, endOfSource: EndOfSource?, signIn: SignInController = koinInject()) {
  Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
    when (val append = items.loadState.append) {
      is LoadState.Loading -> AppLoadingIndicator(size = 64.dp)
      is LoadState.Error -> ApiErrorState(append.error, onRetry = items::retry, onEditSearch = LocalOpenSearch.current, onSignIn = signIn::open)
      is LoadState.NotLoading ->
        if (endOfSource != null && append.endOfPaginationReached && items.itemCount > 0) {
          DoneWith(
            endOfSource.name,
            endOfSource.keptStay,
            endOfSource.onClose,
            closeLabel = endOfSource.closeLabel,
            onLater = endOfSource.sortLater,
            followAndClose = endOfSource.followAndClose,
          )
        }
    }
  }
}

@OptIn(ExperimentalSharedTransitionApi::class)
internal fun sharingConfig(enabled: Boolean): SharedTransitionScope.SharedContentConfig =
  if (enabled) SharingOn else SharingOff

@OptIn(ExperimentalSharedTransitionApi::class)
private val SharingOn = SharedTransitionDefaults.SharedContentConfig

@OptIn(ExperimentalSharedTransitionApi::class)
private val SharingOff = object : SharedTransitionScope.SharedContentConfig {
  override val SharedTransitionScope.SharedContentState.isEnabled: Boolean get() = false
  override val shouldKeepEnabledForOngoingAnimation: Boolean get() = false
}

internal fun mosaicCellTag(index: Int) = "mosaic cell $index"

fun cardKey(id: String) = "card-$id"

fun imageKey(id: String) = "image-$id"

fun sampleKey(id: String) = "sample-$id"

internal fun gridCellRequest(context: PlatformContext, post: PostDto): ImageRequest =
  postImageRequest(context, PostImage.of(post, Variant.Preview))

internal fun gridCellRatio(post: PostDto): Float = cellRatio(post)

private fun cellRatio(post: PostDto): Float {
  val w = post.previewWidth ?: return 1f
  val h = post.previewHeight ?: return 1f
  if (w <= 0 || h <= 0) return 1f
  return (w.toFloat() / h).coerceIn(0.3f, 3f)
}

package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.AnimatedContent
import dev.marzahn.sankaku.ui.components.ButtonHint
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.google.fonts.icons.chevron_left
import com.google.fonts.icons.chevron_right
import dev.marzahn.sankaku.api.dto.PostDto
import androidx.compose.runtime.CompositionLocalProvider
import dev.marzahn.sankaku.features.post.LocalVideoKeys
import dev.marzahn.sankaku.features.post.PlayerPreferences
import dev.marzahn.sankaku.features.post.keepingFrom
import dev.marzahn.sankaku.features.post.rememberViewing
import dev.marzahn.sankaku.features.post.PostActions
import dev.marzahn.sankaku.features.post.PostDetails
import dev.marzahn.sankaku.features.post.PostImageView
import dev.marzahn.sankaku.features.post.PostTitle
import dev.marzahn.sankaku.features.post.RightPanelClose
import dev.marzahn.sankaku.features.post.RightPanelOpen
import dev.marzahn.sankaku.features.post.VideoKeys
import dev.marzahn.sankaku.features.post.forShown
import dev.marzahn.sankaku.features.post.isVideo
import dev.marzahn.sankaku.features.post.postShortcuts
import dev.marzahn.sankaku.shell.BackOverride
import dev.marzahn.sankaku.ui.components.FavouriteBursts
import dev.marzahn.sankaku.ui.components.PictureBurstSize
import dev.marzahn.sankaku.ui.components.KbdBadge
import dev.marzahn.sankaku.ui.components.isPlain
import dev.marzahn.sankaku.ui.components.KeyFocusHolder
import dev.marzahn.sankaku.ui.components.LocalKeyFocus
import dev.marzahn.sankaku.ui.components.PanelToggleButton
import dev.marzahn.sankaku.ui.components.Shortcut
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.effectsSpec
import dev.marzahn.sankaku.ui.components.entrance
import dev.marzahn.sankaku.ui.components.fastSpatialSpec
import dev.marzahn.sankaku.ui.components.isCommand
import dev.marzahn.sankaku.ui.components.tabStep
import dev.marzahn.sankaku.ui.components.spatialBoundsTransform
import dev.marzahn.sankaku.ui.components.spatialSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

val INFO_PANEL_WIDTH: Dp = 300.dp

private val INFO_PANEL_ELEVATION = 6.dp
private val PANEL_GAP = 10.dp

private val INNER_CORNER = 8.dp

private const val SPLIT_AT = 0.97f
private const val CONTROLS_AT = SPLIT_AT

private const val MERGED = 0.05f

val TAB_INSET: Dp = 12.dp

private val TOGGLE_CLEARANCE = 80.dp

enum class ViewerMode {
  Overlay,

  Tab,
  ;

  val inset: PaddingValues get() = if (this == Overlay) PaddingValues(horizontal = 48.dp, vertical = 24.dp) else PaddingValues(TAB_INSET)

  val steps: Boolean get() = this == Overlay

  val swipesQueue: Boolean get() = this == Tab
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PostViewer(
  index: Int,
  postAt: (Int) -> PostDto?,
  loadedCount: Int,
  hasMore: Boolean,
  sharedId: String?,
  visibilityScope: AnimatedVisibilityScope,
  onStep: (forward: Boolean) -> Unit,
  end: ViewerEnd = ViewerEnd.NoMore,
  onRetryEnd: () -> Unit = {},
  positionOf: (index: Int) -> Int = { it },
  onClose: () -> Unit,
  opening: Int = 0,
  mode: ViewerMode = ViewerMode.Overlay,
  searchTags: List<String> = emptyList(),
  isRepeat: (Key) -> Boolean = { false },
  onKeep: ((PostDto) -> Unit)? = null,
  actions: PostActions = koinInject(),
  preferences: ViewerPreferences = koinInject(),
  playerPreferences: PlayerPreferences = koinInject(),
) {
  val scope = rememberCoroutineScope()
  val tagListFocus = remember { FocusRequester() }
  val videoKeys = remember(playerPreferences) { VideoKeys(playerPreferences) }
  var infoHasKeys by remember { mutableStateOf(false) }
  val keyFocus = LocalKeyFocus.current
  val transition = visibilityScope.transition
  val settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
  var leaving by remember(opening) { mutableStateOf(false) }
  val currentOpening by rememberUpdatedState(opening)
  val closing = leaving || transition.targetState != EnterExitState.Visible
  var stepped by remember(opening) { mutableStateOf(false) }
  val attachShared = (!settled && !stepped) || leaving
  fun step(forward: Boolean) {
    stepped = true
    onStep(forward)
  }
  var atEnd by remember(opening) { mutableStateOf(false) }
  val position = positionOf(index)
  fun next() {
    if (position >= loadedCount - 1) atEnd = true else step(true)
  }
  fun previous() {
    if (atEnd) atEnd = false else step(false)
  }
  LaunchedEffect(atEnd, position, loadedCount) {
    if (atEnd && position < loadedCount - 1) {
      atEnd = false
      step(true)
    }
  }
  val viewing = rememberViewing(postAt(index))
  val keep = onKeep?.let(viewing::keepingFrom)
  val aboveImage = Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)

  val openSpec = spatialSpec<Float>(slow = true)
  val closeSpec = spatialSpec<Float>()
  val progress by transition.animateFloat({ if (targetState == EnterExitState.Visible) openSpec else closeSpec }) {
    if (it == EnterExitState.Visible) 1f else 0f
  }
  val panelColor by transition.animateColor({ effectsSpec() }) {
    if (it == EnterExitState.Visible) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHighest
  }
  val openDp = spatialSpec<Dp>(slow = true)
  val closeDp = spatialSpec<Dp>()
  val corner by transition.animateDp({ if (targetState == EnterExitState.Visible) openDp else closeDp }) {
    if (it == EnterExitState.Visible) VIEWER_CORNER else CELL_CORNER
  }
  val bounds = spatialBoundsTransform(slow = !closing)
  val slide = spatialSpec<IntOffset>()

  val split = remember { Animatable(if (settled && !preferences.infoCollapsed) 1f else 0f) }
  val splitIn = fastSpatialSpec<Float>()
  val splitOut = fastSpatialSpec<Float>().withoutBounce()
  val splitClose = fastSpatialSpec<Float>().withoutBounce(stiffnessScale = 4f)
  val splitExpand = splitOut
  val wantSplit = progress > SPLIT_AT && !preferences.infoCollapsed && !leaving
  var toggling by remember(opening) { mutableStateOf(false) }
  LaunchedEffect(wantSplit) {
    toggling = settled && !leaving
    split.animateTo(
      if (wantSplit) 1f else 0f,
      when {
        leaving -> splitClose
        !wantSplit -> splitOut
        toggling -> splitExpand
        else -> splitIn
      },
    )
  }
  val splitAt = splitFraction(split.value)
  val controlsShown = progress > CONTROLS_AT && !leaving
  val canStep = controlsShown
  val slider = key(opening) { updateTransition(index, label = "slide") }

  fun close() {
    if (mode == ViewerMode.Tab) return onClose()
    if (leaving) return
    leaving = true
    val closing = currentOpening
    scope.launch {
      repeat(2) { withFrameNanos {} }
      snapshotFlow { split.value < MERGED && !slider.isRunning && slider.currentState == slider.targetState }.first { it }
      if (currentOpening == closing) onClose()
    }
  }
  val spaceCloses = mode == ViewerMode.Overlay && postAt(index)?.let(::isVideo) == false
  if (mode == ViewerMode.Overlay) {
    BackOverride(enabled = !closing, onBack = ::close, alsoKey = Shortcuts.CloseViewer.takeIf { spaceCloses })
  }

  Box(
    Modifier
      .fillMaxSize()
      .postShortcuts(post = { postAt(index) }, actions = actions, onBack = ::close, enabled = { !closing })
      .onKeyEvent { event ->
        if (closing || event.type != KeyEventType.KeyDown) return@onKeyEvent false
        if (event.isCommand || event.tabStep != null) return@onKeyEvent false
        when {
          videoKeys.onKey(event.key) -> true.also {
            if (videoKeys.scrubbing && infoHasKeys) keyFocus.returnToContent()
          }
          Shortcuts.CloseViewer.matches(event.key) ->
            (spaceCloses && !isRepeat(event.key)).also { if (it) close() }
          Shortcuts.Previous.matches(event.key) -> (mode.steps && canStep && (position > 0 || atEnd)).also { if (it) previous() }
          Shortcuts.Next.matches(event.key) -> (mode.steps && canStep && !atEnd).also { if (it) next() }
          event.key == Shortcuts.Details.key -> true.also { preferences.infoCollapsed = !preferences.infoCollapsed }
          event.key == Shortcuts.OpenInTab.key && event.isPlain && keep != null ->
            (!isRepeat(event.key)).also { if (it) postAt(index)?.let(keep) }
          event.key == Shortcuts.TagList.key -> true.also {
            videoKeys.stopScrubbing()
            preferences.infoCollapsed = false
            scope.launch {
              repeat(2) { withFrameNanos {} }
              tagListFocus.requestFocus()
            }
          }
          else -> false
        }
      },
  ) {
    key(opening) { KeyFocusHolder() }
    if (mode != ViewerMode.Tab) {
      Box(
        Modifier
          .fillMaxSize()
          .padding(top = GRID_TOP_GAP)
          .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = OVERLAY_SCRIM_ALPHA))
          .semantics { hideFromAccessibility() }
          .then(if (closing) Modifier else Modifier.swallowClicks(onClick = ::close)),
      )
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(mode.inset)) {
      val region = IntSize(constraints.maxWidth, constraints.maxHeight)
      val layout = cardLayout(maxWidth, split.value, parting = !toggling, collapsed = preferences.infoCollapsed, corner = corner)
      val shape = PanelShape(start = corner, end = layout.panelEndCorner, endInset = layout.panelEndInset)
      val post = postAt(index)

      if (post != null && layout.infoShown) {
        InfoPanel(
          post = post,
          split = splitAt,
          color = if (toggling) MaterialTheme.colorScheme.surfaceContainerLowest else lerp(panelColor, MaterialTheme.colorScheme.surfaceContainerLowest, splitAt),
          alpha = if (toggling) infoPanelAlpha(splitAt) else 1f,
          innerCorner = layout.infoInnerCorner,
          regionHeight = region.height,
          heightFollowsSplit = !toggling,
          controlsShown = controlsShown,
          tagListFocus = tagListFocus,
          elevation = if (mode == ViewerMode.Overlay) INFO_PANEL_ELEVATION else 0.dp,
          searchTags = searchTags,
          modifier = Modifier.offset(x = layout.infoX).width(layout.infoWidth).onFocusChanged { infoHasKeys = it.hasFocus },
        )
      }

      Box(
        Modifier
          .width(layout.panelWidth)
          .fillMaxHeight()
          .then(
            if (sharedId == null || !attachShared) {
              Modifier
            } else {
              Modifier.sharedBounds(
                rememberSharedContentState(cardKey(sharedId)),
                visibilityScope,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                boundsTransform = bounds,
                resizeMode = RemeasureToBounds,
                clipInOverlayDuringTransition = OverlayClip(shape),
              )
            },
          )
          .clip(shape)
          .background(panelColor),
      ) {
        Box(
          Modifier
            .width(layout.contentWidth)
            .fillMaxHeight()
            .then(if (closing) Modifier else Modifier.swallowClicks {})
            .semantics { isTraversalGroup = true },
        ) {
          slider.AnimatedContent(
            modifier = Modifier.semantics { traversalIndex = 1f },
            transitionSpec = {
              val forward = positionOf(targetState) > positionOf(initialState)
              slideInHorizontally(slide) { if (forward) it else -it } togetherWith
                slideOutHorizontally(slide) { if (forward) -it else it }
            },
          ) { shown ->
            val shownPost = postAt(shown) ?: return@AnimatedContent
            CompositionLocalProvider(LocalVideoKeys provides videoKeys) {
              PostImageView(
                shownPost,
                modifier = Modifier.fillMaxSize(),
                imageBox = if (shownPost.id == sharedId && attachShared) {
                  Modifier.sharedElement(rememberSharedContentState(imageKey(shownPost.id)), visibilityScope, bounds)
                } else {
                  Modifier
                },
                showSample = settled,
                playing = settled && !atEnd,
                onShown = { viewing.loaded(shownPost.id) },
              )
            }
          }
          if (atEnd) {
            ViewerEndPage(
              end,
              onRetry = onRetryEnd,
              modifier = Modifier.background(panelColor).swallowClicks {}.semantics { traversalIndex = 1f },
            )
          }
          if (mode.steps) {
            Surface(
              shape = CircleShape,
              color = overImage(),
              contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .then(aboveImage)
                .entrance(controlsShown, delayMillis = 140, offsetY = (-16).dp),
            ) {
              Text(
                positionLabel(position, loadedCount, hasMore),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }
          if (mode.steps && (position > 0 || atEnd)) {
            StepButton(chevron_left, "Previous post", Shortcuts.Previous, Alignment.CenterStart, controlsShown, towards = -1, aboveImage) {
              if (canStep) previous()
            }
          }
          if (mode.steps && !atEnd) {
            StepButton(chevron_right, "Next post", Shortcuts.Next, Alignment.CenterEnd, controlsShown, towards = 1, aboveImage) {
              if (canStep) next()
            }
          }
          val currentId by rememberUpdatedState(post?.id)
          LaunchedEffect(actions, currentId) { currentId?.let(actions::showing) }
          val hearts = remember(actions) { actions.hearts.forShown { currentId } }
          FavouriteBursts(hearts, Modifier.align(Alignment.Center), size = PictureBurstSize)
        }
      }

      DetailsToggle(
        collapsed = preferences.infoCollapsed,
        shown = controlsShown,
        modifier = Modifier.align(Alignment.TopEnd).then(aboveImage),
        onClick = { preferences.infoCollapsed = !preferences.infoCollapsed },
      )
    }
  }
}

@Composable
private fun InfoPanel(
  post: PostDto,
  split: Float,
  color: Color,
  alpha: Float,
  innerCorner: Dp,
  regionHeight: Int,
  heightFollowsSplit: Boolean,
  controlsShown: Boolean,
  tagListFocus: FocusRequester,
  elevation: Dp,
  searchTags: List<String>,
  modifier: Modifier,
) {
  Surface(
    shape = RoundedCornerShape(topStart = innerCorner, topEnd = VIEWER_CORNER, bottomEnd = VIEWER_CORNER, bottomStart = innerCorner),
    color = color,
    shadowElevation = elevation * split,
    modifier = modifier.graphicsLayer { this.alpha = alpha }.layout { measurable, constraints ->
      val width = constraints.maxWidth
      val content = measurable.measure(Constraints(maxWidth = width, maxHeight = regionHeight))
      val height = infoPanelHeight(regionHeight, content.height, split, heightFollowsSplit)
      layout(width, height) { content.place(0, 0) }
    },
  ) {
    Box(Modifier.layout { measurable, constraints ->
      val full = INFO_PANEL_WIDTH.roundToPx()
      val placeable = measurable.measure(constraints.copy(minWidth = full, maxWidth = full))
      layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
    }) {
      Column {
        val scroll = rememberScrollState()
        PostTitle(
          post,
          endInset = TOGGLE_CLEARANCE,
          scrolled = scroll.value > 0,
          modifier = Modifier.entrance(controlsShown, delayMillis = 60L, offsetY = 14.dp),
        )
        PostDetails(
          post,
          scroll = scroll,
          searchTags = searchTags,
          tagListFocus = tagListFocus,
          modifier = Modifier.weight(1f, fill = false).animateContentSize(spatialSpec()),
          section = { i -> Modifier.entrance(controlsShown, delayMillis = 90L + i * 30L, offsetY = 14.dp) },
        )
      }
    }
  }
}

internal fun infoPanelHeight(regionHeight: Int, contentHeight: Int, split: Float, followsSplit: Boolean): Int =
  if (followsSplit) (regionHeight + (contentHeight - regionHeight) * split).roundToInt() else contentHeight

internal data class CardLayout(
  val panelWidth: Dp,
  val panelEndInset: Dp,
  val panelEndCorner: Dp,
  val contentWidth: Dp,
  val infoShown: Boolean,
  val infoX: Dp,
  val infoWidth: Dp,
  val infoInnerCorner: Dp,
)

internal fun cardLayout(width: Dp, split: Float, parting: Boolean, collapsed: Boolean, corner: Dp): CardLayout {
  val beside = INFO_PANEL_WIDTH + PANEL_GAP
  if (!parting) {
    val at = splitFraction(split)
    val image = lerp(width, width - beside, at)
    val infoX = image + PANEL_GAP * at
    return CardLayout(image, 0.dp, lerp(corner, INNER_CORNER, at), image, at > 0.001f, infoX, width - infoX, INNER_CORNER)
  }
  val content = if (collapsed) width else width - beside
  if (split < MERGED) return CardLayout(width, 0.dp, corner, content, false, width, 0.dp, 0.dp)
  val gap = PANEL_GAP * split
  val infoX = width - INFO_PANEL_WIDTH - PANEL_GAP / 2 + gap / 2
  val seam = INNER_CORNER * split
  return CardLayout(width, width - infoX + gap, seam, content, true, infoX, width - infoX, seam)
}

private class PanelShape(private val start: Dp, private val end: Dp, private val endInset: Dp) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val width = (size.width - with(density) { endInset.toPx() }).coerceAtLeast(0f)
    return RoundedCornerShape(topStart = start, topEnd = end, bottomEnd = end, bottomStart = start)
      .createOutline(Size(width, size.height), layoutDirection, density)
  }
}

internal fun splitFraction(split: Float): Float = split.coerceIn(0f, 1f)

internal fun <T> FiniteAnimationSpec<T>.withoutBounce(stiffnessScale: Float = 1f): FiniteAnimationSpec<T> =
  if (this is SpringSpec) spring(Spring.DampingRatioNoBouncy, stiffness * stiffnessScale, visibilityThreshold) else this

internal fun infoPanelAlpha(split: Float): Float = (split / INFO_FADE_SPLIT).coerceIn(0f, 1f)

private const val INFO_FADE_SPLIT = 0.1f

@Composable
private fun overImage(): Color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)

internal fun positionLabel(index: Int, loadedCount: Int, hasMore: Boolean): String =
  "${index + 1} of $loadedCount${if (hasMore) "+" else ""}"

internal fun positionDescription(index: Int, loadedCount: Int, hasMore: Boolean): String =
  "Post ${index + 1} of $loadedCount${if (hasMore) " or more" else ""}"

@Composable
private fun DetailsToggle(collapsed: Boolean, shown: Boolean, modifier: Modifier, onClick: () -> Unit) {
  Row(
    modifier.padding(top = 8.dp, end = 8.dp).entrance(shown, delayMillis = 110, offsetY = (-24).dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    KbdBadge(Shortcuts.Details.label)
    PanelToggleButton(
      checked = !collapsed,
      onCheckedChange = { onClick() },
      checkedContainer = MaterialTheme.colorScheme.surfaceContainerLowest,
      uncheckedContainer = MaterialTheme.colorScheme.surfaceContainerLowest,
      modifier = Modifier.focusProperties { canFocus = false },
    ) {
      Icon(
        if (collapsed) RightPanelOpen else RightPanelClose,
        contentDescription = "${if (collapsed) "Show" else "Hide"} details (${Shortcuts.Details.label})",
      )
    }
  }
}

@Composable
private fun BoxScope.StepButton(
  icon: ImageVector,
  label: String,
  shortcut: Shortcut,
  alignment: Alignment,
  shown: Boolean,
  towards: Int,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  Column(
    Modifier.align(alignment).padding(12.dp).then(modifier),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    ButtonHint("$label (${shortcut.label})") {
      IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = overImage()),
        modifier = Modifier.entrance(shown, offsetX = (28 * towards).dp, fromScale = 0.85f),
      ) { Icon(icon, contentDescription = label) }
    }
    KbdBadge(shortcut.label, Modifier.entrance(shown, delayMillis = 70, offsetY = 12.dp, fromScale = 0.8f, fast = true))
  }
}

private fun Modifier.swallowClicks(onClick: () -> Unit): Modifier = composed {
  val handler by rememberUpdatedState(onClick)
  pointerInput(Unit) { detectTapGestures { handler() } }
}

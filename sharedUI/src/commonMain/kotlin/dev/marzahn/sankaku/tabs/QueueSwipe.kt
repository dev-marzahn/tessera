package dev.marzahn.sankaku.tabs

import androidx.compose.animation.core.AnimationConstants
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.sankaku.PlatformPredictiveBackHandler
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.isLone
import dev.marzahn.sankaku.features.grid.BarInsets
import dev.marzahn.sankaku.features.grid.LocalBarInsets
import dev.marzahn.sankaku.features.grid.LonePostPeek
import dev.marzahn.sankaku.features.grid.rememberBarInsets
import dev.marzahn.sankaku.features.grid.PostOrigin
import dev.marzahn.sankaku.features.grid.Rating
import dev.marzahn.sankaku.features.post.postOriginLabel
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.shell.LocalChromePadding
import dev.marzahn.sankaku.ui.components.BlockEndLine
import dev.marzahn.sankaku.ui.components.spatialSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.sign

internal enum class SwipeStart {
  Wait,

  Take,

  LetGo,
}

internal fun swipeStart(dx: Float, dy: Float, slop: Float): SwipeStart = when {
  abs(dy) >= slop -> SwipeStart.LetGo
  abs(dx) >= 2 * slop -> SwipeStart.Take
  else -> SwipeStart.Wait
}

internal enum class QueueMove { Previous, Next, Stay, Close, Return, Back }

internal fun swipeTarget(
  offset: Float,
  velocity: Float,
  width: Float,
  minFling: Float,
  hasPrevious: Boolean,
  hasNext: Boolean,
): QueueMove {
  val towards = when {
    abs(velocity) >= minFling -> sign(velocity)
    abs(offset) >= width / 2 -> sign(offset)
    else -> 0f
  }
  return when {
    towards > 0f && offset > 0f && hasPrevious -> QueueMove.Previous
    towards < 0f && offset < 0f && hasNext -> QueueMove.Next
    else -> QueueMove.Stay
  }
}

internal data class SwipeWays(val back: Boolean, val forward: Boolean)

internal fun swipeWays(cardUp: Boolean, previous: Boolean, ahead: Ahead): SwipeWays =
  if (cardUp) SwipeWays(back = true, forward = false) else SwipeWays(back = previous, forward = ahead != Ahead.Nothing)

internal enum class QueueBack { Previous, Close, Return, QueuePage, Tab, Deck }

internal fun queueBack(
  cardUp: Boolean,
  lonePost: Boolean,
  previous: Boolean,
  opened: Boolean = false,
  sorting: Boolean = false,
  deckWaiting: Boolean = false,
  jumped: Boolean = false,
  keptRun: Boolean = false,
): QueueBack = when {
  cardUp -> QueueBack.Previous
  sorting -> QueueBack.Deck
  keptRun -> QueueBack.QueuePage
  jumped -> QueueBack.Return
  opened -> QueueBack.Close
  lonePost && previous -> QueueBack.Previous
  deckWaiting -> QueueBack.Deck
  else -> QueueBack.Tab
}

internal val TabState.backsLikeALonePost: Boolean
  get() = backStack.size == 1 && top.let { it?.isLone == true || it is Route.BookSeries }

internal val MIN_FLING = 400.dp

@Stable
class QueueSwipeState internal constructor(
  private val scope: CoroutineScope,
  private val spec: AnimationSpec<Float>,
  private val move: (QueueMove) -> Boolean,
) {
  var offset by mutableFloatStateOf(0f)
    private set

  internal var width by mutableIntStateOf(0)
  internal var hasPrevious = false
  internal var hasNext = false

  internal var overscroll: OverscrollEffect? = null

  internal var direction = 1f

  internal var leaving by mutableStateOf<TabId?>(null)
    private set

  private var job: Job? = null

  internal var closing by mutableStateOf(false)
    private set

  val active: Boolean get() = offset != 0f || job?.isActive == true

  internal fun offsetFor(id: TabId): Float = if (leaving != null && leaving != id) 0f else offset

  internal fun start() {
    job?.cancel()
    closing = false
  }

  internal fun dragBy(towardsEnd: Float) {
    val effect = overscroll
    if (effect == null) {
      offset = clamp(offset + towardsEnd)
      return
    }
    effect.applyToScroll(Offset(towardsEnd * direction, 0f), NestedScrollSource.UserInput) { available ->
      val before = offset
      offset = clamp(before + available.x * direction)
      Offset((offset - before) * direction, 0f)
    }
  }

  private fun clamp(value: Float): Float {
    val w = width.toFloat()
    return value.coerceIn(if (hasNext) -w else 0f, if (hasPrevious || closing) w else 0f)
  }

  internal fun release(velocity: Float, minFling: Float, from: TabId) {
    val target = swipeTarget(offset, velocity, width.toFloat(), minFling, hasPrevious, hasNext)
    val atEnd = offset == 0f && ((velocity > 0f && !hasPrevious) || (velocity < 0f && !hasNext))
    job = scope.launch {
      val effect = overscroll
      if (effect == null) {
        settle(target, from, velocity)
        return@launch
      }
      effect.applyToFling(Velocity(velocity * direction, 0f)) { available ->
        settle(target, from, available.x * direction)
        if (atEnd) Velocity.Zero else available
      }
    }
  }

  internal fun pullBack(progress: Float, move: QueueMove = QueueMove.Back) {
    job?.cancel()
    closing = move.bringsItsOwnItem
    offset =clamp(progress * width * BACK_SHARE)
  }

  internal fun springBack() {
    job = scope.launch {
      slide(0f, velocity = 0f)
      closing = false
    }
  }

  internal fun back(from: TabId, move: QueueMove = QueueMove.Back) {
    job?.cancel()
    closing = move.bringsItsOwnItem
    job =scope.launch { settle(move, from) }
  }

  private val QueueMove.bringsItsOwnItem get() = this == QueueMove.Close || this == QueueMove.Return

  private suspend fun settle(target: QueueMove, from: TabId, velocity: Float = 0f) {
    val to = when (target) {
      QueueMove.Previous, QueueMove.Back, QueueMove.Close, QueueMove.Return -> width.toFloat()
      QueueMove.Next -> -width.toFloat()
      QueueMove.Stay -> 0f
    }
    slide(to, velocity)
    if (target == QueueMove.Stay) return
    leaving = from
    if (!move(target)) landed()
  }

  private suspend fun slide(to: Float, velocity: Float) {
    val from = offset
    if (from == to) return
    val animation = TargetBasedAnimation(spec, Float.VectorConverter, from, to, Float.VectorConverter.convertToVector(velocity))
    var start = AnimationConstants.UnspecifiedTime
    var done = false
    while (!done) {
      withFrameNanos { now ->
        if (start == AnimationConstants.UnspecifiedTime) start = now - FIRST_FRAME_NANOS
        val played = now - start
        val value = animation.getValueFromNanos(played)
        done = animation.isFinishedFromNanos(played) || (value - to) * (from - to) <= 0f || abs(value - to) < LAND_PX
        offset = if (done) to else value
      }
    }
  }

  internal fun landed() {
    leaving = null
    offset = 0f
    closing = false
  }
}

private const val BACK_SHARE = 0.25f

private const val FIRST_FRAME_NANOS = 16_000_000L

private const val LAND_PX = 1f

val LocalQueueSwipe = staticCompositionLocalOf<QueueSwipeState?> { null }

val LocalQueueActions = compositionLocalOf<List<CustomAccessibilityAction>> { emptyList() }

data class PicturePeek(val title: String, val kind: String, val picture: LoadedNeighbour?, val leaving: String? = null)

@Stable
class QueuePictureSwipe internal constructor(
  private val state: QueueSwipeState,
  private val from: TabId,
  val previous: PicturePeek?,
  val next: PicturePeek?,
  private val drawnBy: MutableState<Int>,
) {
  val drawn: Boolean get() = drawnBy.value > 0

  fun drawing(): () -> Unit {
    drawnBy.value++
    return { drawnBy.value-- }
  }

  fun offset(): Float {
    val offset = state.offsetFor(from)
    return if (drawn && ((offset > 0f && previous != null) || (offset < 0f && next != null))) offset else 0f
  }

  val direction: Float get() = state.direction
}

val LocalQueuePictureSwipe = compositionLocalOf<QueuePictureSwipe?> { null }

val LocalQueueClosePeek = compositionLocalOf<ClosePeek?> { null }

@Stable
class ClosePeek internal constructor(val next: String, val behind: @Composable () -> Unit) {
  @Composable
  fun Line(modifier: Modifier = Modifier) {
    BlockEndLine(nextLine(next), modifier.semantics { hideFromAccessibility() }.padding(horizontal = 16.dp))
  }
}

fun Modifier.queueSwipe(enabled: Boolean, from: TabId?, claimSideways: Boolean = false): Modifier = composed {
  val state = LocalQueueSwipe.current
  if ((state == null || from == null) && !claimSideways) return@composed this
  val isEnabled by rememberUpdatedState(enabled && state != null && from != null)
  val tab by rememberUpdatedState(from)
  val insets = WindowInsets.systemGestures
  val density = LocalDensity.current
  val direction = LocalLayoutDirection.current
  val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
  onGloballyPositioned { placed[0] = it }.pointerInput(state, insets, density, direction, claimSideways) {
    val minFling = MIN_FLING.toPx()
    val towardsEnd = if (direction == LayoutDirection.Ltr) 1f else -1f
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      if (down.type != PointerType.Touch) return@awaitEachGesture
      val coordinates = placed[0]?.takeIf { it.isAttached } ?: return@awaitEachGesture
      val downX = coordinates.localToWindow(down.position).x
      val windowWidth = coordinates.findRootCoordinates().size.width
      val inEdge = downX < insets.getLeft(density, direction) || downX >= windowWidth - insets.getRight(density, direction)
      val canMove = isEnabled && !inEdge
      if (!canMove && !claimSideways) return@awaitEachGesture
      val slop = viewConfiguration.touchSlop
      var claimed = false
      var movable = canMove
      fun windowX(change: PointerInputChange) = coordinates.localToWindow(change.position).x
      var fingerX = downX
      val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, Offset(fingerX, 0f)) }
      while (true) {
        val event = awaitPointerEvent()
        if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
        if (!change.pressed || (change.isConsumed && !claimed)) return@awaitEachGesture
        if (!coordinates.isAttached) return@awaitEachGesture
        fingerX = windowX(change)
        velocity.addPosition(change.uptimeMillis, Offset(fingerX, 0f))
        val dx = fingerX - downX
        val start = swipeStart(dx, change.position.y - down.position.y, slop)
        if (start == SwipeStart.LetGo) {
          if (!claimed) return@awaitEachGesture
          movable = false
        }
        if (start == SwipeStart.Take && movable) {
          change.consume()
          break
        }
        if (claimed || (claimSideways && abs(dx) >= slop)) {
          claimed = true
          change.consume()
        }
      }
      val swipe = state ?: return@awaitEachGesture
      val from = tab ?: return@awaitEachGesture
      swipe.start()
      swipe.dragBy((fingerX - downX) * towardsEnd)
      var fling = 0f
      try {
        val ended = horizontalDrag(down.id) { change ->
          if (coordinates.isAttached) {
            val now = windowX(change)
            velocity.addPosition(change.uptimeMillis, Offset(now, 0f))
            swipe.dragBy((now - fingerX) * towardsEnd)
            fingerX = now
          }
          change.consume()
        }
        if (ended) fling = velocity.calculateVelocity().x * towardsEnd
      } finally {
        swipe.release(fling, minFling, from)
      }
    }
  }
}

@Composable
fun QueueSwipeHost(
  active: TabState,
  content: @Composable () -> Unit,
  tabs: TabsController = koinInject(),
  upNext: UpNextState = koinInject(),
  preload: @Composable (shown: TabState, neighbours: List<TabState>) -> Map<String, LoadedNeighbour> = { shown, near -> PreloadNeighbours(shown, near) },
  undo: ClosedTabUndo = koinInject(),
  onMoved: () -> Unit = {},
) {
  val closeUndo by rememberUpdatedState(undo)
  val moved by rememberUpdatedState(onMoved)
  val goneInto = upNext.sortGoneInto
  val deckWaiting = goneInto != null && active.backStack.size == 1
  val deckWaitingNow by rememberUpdatedState(deckWaiting)
  val tabList by tabs.tabs.collectAsStateWithLifecycle()
  val gOnly by upNext.gOnly.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  val spec = spatialSpec<Float>()
  val state = remember(tabs) {
    QueueSwipeState(scope, spec) { move ->
      when (move) {
        QueueMove.Previous -> if (upNext.card != null) {
          upNext.dismiss()
          false
        } else {
          tabs.previous()
        }
        QueueMove.Back -> if (upNext.card != null) {
          upNext.dismiss()
          false
        } else {
          tabs.backToPrevious(moves = !deckWaitingNow)
        }
        QueueMove.Next -> upNext.forward().also { moved() }
        QueueMove.Stay -> false
        QueueMove.Close -> closeUndo.closeBack(tabs.activeTab.id)
        QueueMove.Return -> tabs.backReturn(tabs.activeTab.id, moves = !deckWaitingNow)
      }
    }
  }
  val index = tabList.indexOf(active)
  val previous = remember(tabList, active) { tabs.neighbour(forward = false) }
  val opener = tabs.openerOf(active)
  val returnTo = tabs.backReturnOf(active, moves = !deckWaiting)
  val inKeptRun = tabs.keptPlace() != null
  val card = upNext.card
  val ahead = upNext.ahead()
  val next = (ahead as? Ahead.Item)?.next
  val pane = ahead as? Ahead.Pane
  val ways = swipeWays(cardUp = card != null, previous = previous != null, ahead = ahead)
  val overscroll = rememberOverscrollEffect()
  val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
  SideEffect {
    state.hasPrevious = ways.back
    state.hasNext = ways.forward
    state.overscroll = overscroll
    state.direction = direction
  }
  LaunchedEffect(active.id) { if (state.leaving != null && state.leaving != active.id) state.landed() }
  val loaded = preload(
    active,
    if (card != null) listOf(active) else listOfNotNull(previous, next),
  )
  val back = queueBack(
    cardUp = card != null,
    lonePost = active.backsLikeALonePost,
    previous = previous != null,
    opened = opener != null,
    sorting = goneInto == active.id && active.backStack.size == 1,
    deckWaiting = deckWaiting,
    jumped = returnTo != null,
    keptRun = inKeptRun && active.backStack.size == 1,
  )
  val openQueuePage = LocalOpenTabList.current
  val backMove = when (back) {
    QueueBack.Previous -> QueueMove.Back
    QueueBack.Close -> QueueMove.Close
    QueueBack.Return -> if (active.backsLikeALonePost && returnTo === previous) QueueMove.Back else QueueMove.Return
    else -> null
  }
  val backTo = if (back == QueueBack.Return) returnTo else opener
  PlatformPredictiveBackHandler(
    enabled = back != QueueBack.Tab,
    onProgress = { if (backMove != null) state.pullBack(it, backMove) },
    onCancel = { if (backMove != null) state.springBack() },
    onBack = {
      when {
        backMove != null -> state.back(active.id, backMove)
        back == QueueBack.Deck -> upNext.backToSort()
        else -> {
          if (inKeptRun) tabs.endKeptRun()
          openQueuePage()
        }
      }
    },
  )

  val cardBackLabel = if (card != null) backToLabel(active.title) else null
  val hasPrevious = previous != null
  val actions = remember(cardBackLabel, hasPrevious, ahead, upNext, tabs) {
    if (cardBackLabel != null) {
      listOf(CustomAccessibilityAction(cardBackLabel) { upNext.dismiss(); true })
    } else {
      listOfNotNull(
        if (hasPrevious) CustomAccessibilityAction(PREVIOUS_ITEM) { tabs.previous() } else null,
        aheadAction(ahead, upNext),
      )
    }
  }
  val place = blockPlace(tabList, index)
  fun leavingFor(tab: TabState?): String? = if (tab != null && tab.head != active.head) leavingLabel(place) else null
  fun picturePeek(tab: TabState?): PicturePeek? {
    val route = tab?.top as? Route.Post ?: return null
    val picture = loaded[route.id]?.takeIf { !(gOnly && Rating.ofCode(it.post.rating) != Rating.G) }
    return PicturePeek(tab.title, kindLabel(tab, tabs.sourceOf(tab)), picture, leavingFor(tab))
  }
  val previousPeek = picturePeek(previous.takeIf { card == null && active.top is Route.Post })
  val nextPeek = picturePeek(next.takeIf { active.top is Route.Post })
  val barInsets = rememberBarInsets()
  val closeLoaded = (next?.top as? Route.Post)?.let { loaded[it.id] }
  val closePeek = when {
    next != null -> ClosePeek(next.title) {
      QueuePeek(
        next,
        source = tabs.sourceOf(next),
        loaded = closeLoaded,
        origin = null,
        gOnly = gOnly,
        barInsets = barInsets,
        leaving = leavingFor(next),
        forward = true,
        modifier = Modifier,
      )
    }
    else -> ahead.closePane(previous != null)?.let { closing -> ClosePeek(closing.title) { CardPeek(closing.title, leavingLabel(place), Modifier) } }
  }
  val drawnBy = remember(state, active.id) { mutableStateOf(0) }
  val pictureSwipe = remember(state, active.id, previousPeek, nextPeek) {
    if (previousPeek == null && nextPeek == null) null else QueuePictureSwipe(state, active.id, previousPeek, nextPeek, drawnBy)
  }
  val showing by remember(state, active.id) { derivedStateOf { state.offsetFor(active.id).sign } }
  Box(Modifier.fillMaxSize().onSizeChanged { state.width = it.width }.clipToBounds()) {
    val pictureSide = pictureSwipe?.drawn == true && ((showing > 0f && previousPeek != null) || (showing < 0f && nextPeek != null))
    val neighbour = when {
      pictureSide -> null
      showing > 0f -> if (card != null) active else if (state.closing && backTo != null) backTo else previous
      showing < 0f -> next
      else -> null
    }
    val peekLayer = Modifier.graphicsLayer {
      val offset = state.offsetFor(active.id)
      translationX = (offset - showing * state.width) * state.direction
    }
    if (neighbour != null) {
      val route = neighbour.top as? Route.Post
      QueuePeek(
        neighbour,
        source = tabs.sourceOf(neighbour),
        loaded = route?.let { loaded[it.id] },
        origin = route?.let { postOriginLabel(neighbour, it, tabs) },
        gOnly = gOnly,
        barInsets = barInsets,
        leaving = if (card == null) leavingFor(neighbour) else null,
        forward = showing < 0f,
        modifier = peekLayer,
      )
    } else if (pictureSide) {
    } else if (showing < 0f && pane != null) {
      CardPeek(pane.title, leavingLabel(place), peekLayer)
    }
    val description = queueItemDescription(
      countedPosition(tabList, index),
      countedItems(tabList),
      active,
      tabs.sourceOf(active),
      place = place,
      next = ahead.title,
    )
    Box(
      Modifier
        .fillMaxSize()
        .graphicsLayer { translationX = (state.offsetFor(active.id) - (pictureSwipe?.offset() ?: 0f)) * state.direction }
        .overscroll(overscroll)
        .semantics {
          if (card == null) {
            paneTitle = description
            customActions = actions
          }
        },
    ) {
      CompositionLocalProvider(
        LocalQueueSwipe provides state,
        LocalQueueActions provides actions,
        LocalQueuePictureSwipe provides pictureSwipe,
        LocalQueueClosePeek provides closePeek,
        LocalBarInsets provides barInsets,
      ) {
        if (card != null) {
          CardPage(active.id, upNext)
        } else {
          content()
        }
      }
    }
  }
}

@Composable
private fun CardPage(from: TabId, upNext: UpNextState) {
  Box(Modifier.fillMaxSize().queueSwipe(enabled = true, from = from)) { UpNextCardView(state = upNext) }
}

internal const val PREVIOUS_ITEM = "Previous queue item"
internal const val NEXT_ITEM = "Next queue item"

internal fun backToLabel(title: String) = "Back to $title"

@Composable
private fun QueuePeek(
  tab: TabState,
  source: TabState?,
  loaded: LoadedNeighbour?,
  origin: String?,
  gOnly: Boolean,
  barInsets: BarInsets,
  leaving: String?,
  forward: Boolean,
  modifier: Modifier,
) {
  Box(modifier.fillMaxSize()) {
    if (loaded != null && !(gOnly && Rating.ofCode(loaded.post.rating) != Rating.G)) {
      LonePostPeek(loaded.post, loaded.picture, origin?.let { PostOrigin(it) {} }, barInsets)
    } else {
      TextPeek(tab, source, Modifier)
    }
    if (leaving != null) LeavingEdge(leaving, forward)
  }
}

@Composable
internal fun BoxScope.LeavingEdge(label: String, forward: Boolean) {
  val colors = MaterialTheme.colorScheme
  val edge = if (forward) Alignment.TopStart else Alignment.TopEnd
  Box(Modifier.matchParentSize().semantics { hideFromAccessibility() }) {
    Box(Modifier.align(if (forward) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().width(3.dp).background(colors.primary))
    Surface(
      shape = RoundedCornerShape(8.dp),
      color = colors.primaryContainer,
      contentColor = colors.onPrimaryContainer,
      modifier = Modifier
        .align(edge)
        .fillMaxHeight(LEAVING_LABEL_TOP)
        .wrapContentHeight(Alignment.Bottom)
        .padding(horizontal = 12.dp),
    ) {
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      )
    }
  }
}

private const val LEAVING_LABEL_TOP = 0.33f

@Composable
private fun TextPeek(tab: TabState, source: TabState?, modifier: Modifier) {
  val post = tab.top is Route.Post
  Box(
    modifier
      .fillMaxSize()
      .background(if (post) Color.Black else MaterialTheme.colorScheme.surface)
      .then(if (post) Modifier else Modifier.padding(LocalChromePadding.current))
      .semantics { hideFromAccessibility() },
    contentAlignment = Alignment.Center,
  ) {
    val color = if (post) Color.White else MaterialTheme.colorScheme.onSurface
    Column(
      Modifier.padding(horizontal = 32.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(kindLabel(tab, source), style = MaterialTheme.typography.labelLarge, color = color.copy(alpha = 0.7f))
      Text(
        tab.title,
        style = MaterialTheme.typography.titleLarge,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun CardPeek(title: String, leaving: String?, modifier: Modifier) {
  Box(modifier.fillMaxSize()) {
    Box(
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
        .padding(LocalChromePadding.current)
        .semantics { hideFromAccessibility() },
      contentAlignment = Alignment.Center,
    ) {
      Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    }
    if (leaving != null) LeavingEdge(leaving, forward = true)
  }
}

internal fun kindLabel(tab: TabState, source: TabState?): String = when (tab.kind) {
  QueueKind.KeptPost -> source?.let { "Kept · from ${it.title}" } ?: "Kept post"
  QueueKind.KeptBook -> source?.let { "Kept book · from ${it.title}" } ?: "Kept book"
  QueueKind.Source -> "Source"
  QueueKind.Feed -> "Feed"
  QueueKind.Other -> "Queue item"
}

internal fun queueItemDescription(
  position: Int,
  count: Int,
  tab: TabState,
  source: TabState?,
  place: BlockPlace? = null,
  next: String? = null,
): String {
  if (place is BlockPlace.Kept) {
    return "Queue item $position of $count, ${place.spoken} from ${place.source}${place.closedSuffix}${blockEndSpoken(place, next)}"
  }
  val follow = (place as? BlockPlace.Head)?.let { ", ${it.follow}" }.orEmpty()
  if (tab.kind == QueueKind.Feed) {
    val where = if (position <= 0) "first" else "after $position ${if (position == 1) "item" else "items"}"
    return "Queue, feed ${tab.title}, $where$follow"
  }
  if (place is BlockPlace.Head) return "Queue item $position of $count, source ${tab.title}$follow"
  val what = when (tab.kind) {
    QueueKind.KeptPost -> source?.let { "kept post from ${it.title}" } ?: "kept post, ${tab.title}"
    QueueKind.KeptBook -> source?.let { "kept book ${tab.title} from ${it.title}" } ?: "kept book, ${tab.title}"
    QueueKind.Source -> "source ${tab.title}"
    QueueKind.Feed -> "feed ${tab.title}"
    QueueKind.Other -> tab.title
  }
  return "Queue item $position of $count, $what"
}

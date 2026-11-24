package dev.marzahn.sankaku.tabs

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.google.fonts.icons.check
import com.google.fonts.icons.chevron_right
import com.google.fonts.icons.close
import com.google.fonts.icons.grid_view
import com.google.fonts.icons.history
import com.google.fonts.icons.schedule
import com.google.fonts.icons.subdirectory_arrow_right
import kotlinx.datetime.TimeZone
import dev.marzahn.sankaku.PlatformBackHandler
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.TagType
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.features.grid.searchedTags
import dev.marzahn.sankaku.queue.TodayCounts
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.images.PostImage
import dev.marzahn.sankaku.images.postImageRequest
import dev.marzahn.sankaku.ui.components.OutlineIconButton
import dev.marzahn.sankaku.ui.components.PrimaryIconButton
import dev.marzahn.sankaku.ui.components.TextActionButton
import dev.marzahn.sankaku.ui.components.TonalButton
import dev.marzahn.sankaku.ui.components.UndoLine
import dev.marzahn.sankaku.ui.components.spatialSpec
import dev.marzahn.sankaku.ui.theme.color
import dev.marzahn.sankaku.features.post.TagFollow
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.compose.koinInject

@Composable
fun SortDeckPage(
  onGoIn: () -> Unit,
  onToQueue: () -> Unit,
  deck: SortDeck = koinInject(),
  previews: SourcePreviews = koinInject(),
  undo: ClosedTabUndo = koinInject(),
  privacy: PrivacySettings = koinInject(),
  session: AuthSession = koinInject(),
  today: TodayStore? = null,
  followInstead: FollowInstead? = followInsteadOrNull(),
  clock: Clock = Clock.System,
) {
  if (!deck.running || !deck.shown) return
  val gOnly by privacy.gOnly.collectAsStateWithLifecycle()
  val auth = session.state.collectAsStateWithLifecycle().value
  val searchedFor = { tab: TabState ->
    if (auth is AuthState.Restoring) null else (tab.top as? Route.Grid)?.let { searchedTags(it.tags, auth is AuthState.SignedIn, gOnly) }
  }
  val card = deck.card
  val next = deck.nextCard
  for (tab in listOfNotNull(card, next)) {
    val searched = searchedFor(tab) ?: continue
    key(tab.id, searched) { LaunchedEffect(Unit) { deck.loadPosts(tab, searched, previews) } }
  }
  if (followInstead != null) {
    for (tab in listOfNotNull(card, next)) key(tab.id) { LaunchedEffect(Unit) { followInstead.load(tab) } }
  }
  val followOf = { tab: TabState ->
    followInstead?.takeIf { it.offer(tab) != null }?.let { follow ->
      { follow.followAndClose(tab) { unfollow -> deck.followedInstead(tab.id, unfollow) }; Unit }
    }
  }
  val newest = card?.let { shown ->
    val searched = searchedFor(shown)
    val offered = followInstead?.offer(shown) != null
    key(shown.id, searched) {
      produceState<Instant?>(null, offered) {
        if (searched != null && offered) value = previews.newestUpload(searched)
      }.value
    }
  }
  val postsOf = { tab: TabState -> searchedFor(tab)?.let { deck.shownPosts(tab, it, CARD_POSTS) } }
  var looking by remember { mutableStateOf<PostDto?>(null) }
  val goesOnTo = deck.goesOnTo
  val toQueue = {
    deck.end()
    if (goesOnTo != null) onGoIn() else onToQueue()
  }
  val toQueueLabel = goesOnTo?.let { if (deck.goesBack) "Back to ${it.title}" else "Next: ${it.title}" } ?: TO_THE_QUEUE
  val goInLabel = if (deck.wentIn != null) BACK_IN else GO_IN
  val pending by undo.pending.collectAsStateWithLifecycle()
  val line = deck.line
  LaunchedEffect(line) {
    if (line == null) return@LaunchedEffect
    undo.awaitNextAction()
    deck.endLine(line)
  }
  PlatformBackHandler(enabled = true) {
    when {
      looking != null -> looking = null
      !deck.stopped -> deck.stop()
      else -> toQueue()
    }
  }
  Surface(Modifier.fillMaxSize().testTag(SORT_PAGE).semantics { paneTitle = SORT }) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
      val wide = maxWidth - maxHeight >= WIDE_MIN_EXTRA
      Column(Modifier.fillMaxSize()) {
        SortHeader(
          counter = if (deck.stopped) null else "${deck.position + 1} of ${deck.total}",
          progress = if (deck.total == 0) 0f else deck.position.toFloat() / deck.total,
          onClose = { if (deck.stopped) toQueue() else deck.stop() },
          closeLabel = if (deck.stopped) toQueueLabel else STOP_SORTING,
        )
        val answer = deck.lastAnswer.takeIf { pending != null || it?.kind == SortDeck.Answer.Kind.Later }
        val said = answer?.let { @Composable { modifier: Modifier -> AnswerLine(it, deck::undoAnswer, modifier) } }
        if (deck.stopped || card == null) {
          today?.changes?.collectAsStateWithLifecycle()?.value
          SortTally(
            deck.tally, deck.left, deck.stillNew, today?.counts(), onKeepSorting = deck::keepSorting, onToQueue = toQueue,
            toQueueLabel = toQueueLabel, foundOn = deck.foundOn, modifier = Modifier.weight(1f),
          )
          said?.invoke(Modifier.fillMaxWidth().padding(16.dp))
        } else {
          val cards = @Composable { modifier: Modifier ->
            Box(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
              Box(if (wide) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                if (next != null) {
                  SortCard(
                    next,
                    postsOf(next),
                    onLook = {},
                    now = clock.now(),
                    newest = null,
                    onFollowInstead = followInstead?.offer(next)?.let { {} },
                    modifier = (if (wide) Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = PEEK) else Modifier.matchParentSize().padding(horizontal = 12.dp).offset(y = PEEK))
                      .testTag(SORT_CARD_BEHIND)
                      .graphicsLayer { alpha = 0.6f }
                      .clearAndSetSemantics {},
                    behind = true,
                    wide = wide,
                  )
                }
                key(card.id) {
                  SwipeLater(onLater = deck::later, modifier = if (wide) Modifier.fillMaxHeight().padding(bottom = PEEK) else Modifier) { offset ->
                    val follow = followOf(card)
                    SortCard(
                      card,
                      postsOf(card),
                      onLook = { looking = it },
                      now = clock.now(),
                      newest = newest,
                      onFollowInstead = follow,
                      wentIn = deck.wentIn,
                      modifier = (if (wide) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).offset { IntOffset(offset().roundToInt(), 0) }.semantics {
                        customActions = listOfNotNull(
                          CustomAccessibilityAction(goInLabel) { goIn(deck, onGoIn); true },
                          CustomAccessibilityAction(LATER) { deck.later(); true },
                          CustomAccessibilityAction(DONE) { deck.done(); true },
                          follow?.let { CustomAccessibilityAction(followInsteadName(card.title)) { it(); true } },
                        )
                      },
                      wide = wide,
                    )
                  }
                }
              }
            }
          }
          if (wide) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
              Box(Modifier.weight(1f).fillMaxHeight()) {
                cards(Modifier.fillMaxSize())
                said?.invoke(Modifier.align(Alignment.BottomEnd).padding(horizontal = 28.dp, vertical = 16.dp).widthIn(max = SAID_WIDE_MAX))
              }
              SortButtonColumn(onDone = deck::done, onLater = deck::later, onGoIn = { goIn(deck, onGoIn) }, goInLabel = goInLabel)
            }
          } else {
            cards(Modifier.weight(1f).fillMaxWidth())
            SortButtons(onDone = deck::done, onLater = deck::later, onGoIn = { goIn(deck, onGoIn) }, goInLabel = goInLabel, said = said)
          }
        }
      }
    }
  }
  looking?.let { post -> LookAtPost(post, onClose = { looking = null }) }
}

private fun goIn(deck: SortDeck, onGoIn: () -> Unit) {
  deck.goIn()
  onGoIn()
}

@Composable
private fun SortHeader(counter: String?, progress: Float, onClose: () -> Unit, closeLabel: String) {
  Column {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onClose) { Icon(close, contentDescription = closeLabel) }
      Text(SORT, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
      if (counter != null) {
        Text(
          counter,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(end = 12.dp),
        )
      }
    }
    if (counter != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
  }
}

@Composable
private fun SwipeLater(onLater: () -> Unit, modifier: Modifier = Modifier, content: @Composable (offset: () -> Float) -> Unit) {
  var offset by remember { mutableFloatStateOf(0f) }
  val spec = spatialSpec<Float>()
  val flingPx = with(LocalDensity.current) { MIN_FLING.toPx() }
  BoxWithConstraints(modifier.fillMaxWidth()) {
    val width by rememberUpdatedState(constraints.maxWidth.toFloat())
    Box(
      Modifier.testTag(SORT_CARD).draggable(
        orientation = Orientation.Horizontal,
        state = rememberDraggableState { delta -> offset = (offset + delta).coerceAtMost(width / 8) },
        onDragStopped = { velocity ->
          val later = sortSwipeLater(offset, velocity, width, flingPx)
          animate(offset, if (later) -width * 1.2f else 0f, velocity, spec) { value, _ -> offset = value }
          if (later) onLater()
        },
      ),
    ) { content { offset } }
  }
}

internal fun sortSwipeLater(offset: Float, velocity: Float, width: Float, flingPx: Float): Boolean =
  offset < -width / 3 || (velocity < -flingPx && offset < 0f)

@Composable
private fun SortCard(
  tab: TabState,
  posts: CardPosts?,
  onLook: (PostDto) -> Unit,
  now: Instant,
  newest: Instant?,
  onFollowInstead: (() -> Unit)?,
  modifier: Modifier = Modifier,
  behind: Boolean = false,
  wide: Boolean = false,
  wentIn: SortDeck.WentIn? = null,
) {
  val loaded = (posts as? CardPosts.Loaded)?.posts.orEmpty()
  Surface(
    modifier,
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation = if (behind) 0.dp else 2.dp,
  ) {
    val grid = @Composable { gridModifier: Modifier, rows: Int ->
      ThumbGrid(loaded, failed = posts is CardPosts.Failed, onLook = if (behind) null else onLook, rows = rows, modifier = gridModifier)
    }
    if (wide) {
      BoxWithConstraints(Modifier.padding(12.dp)) {
        val side = minOf(maxHeight, maxWidth - WIDE_WORDS_MIN - WIDE_GAP).coerceAtLeast(0.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(WIDE_GAP)) {
          grid(Modifier.size(side), SQUARE_ROWS)
          Box(Modifier.weight(1f).height(side).verticalScroll(rememberScrollState())) {
            CardWords(tab, loaded, now, newest, onFollowInstead, wentIn, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
          }
        }
      }
    } else {
      Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f, fill = false)) {
          val shape = uprightGrid(maxWidth, maxHeight, loaded = if (posts == null) null else loaded.size)
          grid(Modifier.size(shape.width, shape.height), shape.rows)
        }
        CardWords(tab, loaded, now, newest, onFollowInstead, wentIn, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp))
      }
    }
  }
}

@Composable
private fun CardWords(
  tab: TabState,
  loaded: List<PostDto>,
  now: Instant,
  newest: Instant?,
  onFollowInstead: (() -> Unit)?,
  wentIn: SortDeck.WentIn?,
  modifier: Modifier,
) {
  val route = tab.top as? Route.Grid
  Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      val type = route?.let { cardTagType(loaded, it.tags) }
      if (type != null) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(type.color))
        Spacer(Modifier.size(8.dp))
      }
      Text(tab.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val newestText = newest?.takeIf { onFollowInstead != null }?.let { newestLine(it, now) }
    route?.let { listOfNotNull(cardMeta(it.tags, tab.preview?.size), newestText).joinToString(" · ") }?.let {
      Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    tab.foundOn?.let { found ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(subdirectory_arrow_right, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          foundCardText(found, Clock.System.now(), TimeZone.currentSystemDefault(), tab.tagNames),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 6.dp),
        )
      }
    }
    wentIn?.let { went ->
      Row(
        Modifier
          .padding(top = 8.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.primaryContainer)
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .testTag(SORT_WENT_IN)
          .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(history, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(wentInText(went), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
      }
    }
    if (onFollowInstead != null) {
      Row(
        Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlineIconButton(FOLLOW_INSTEAD, TagFollow, onFollowInstead, Modifier.semantics { contentDescription = followInsteadName(tab.title) })
        Text(
          FOLLOW_INSTEAD_NOTE,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun ThumbGrid(posts: List<PostDto>, failed: Boolean, onLook: ((PostDto) -> Unit)?, rows: Int, modifier: Modifier = Modifier) {
  val context = LocalPlatformContext.current
  Box(modifier) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(THUMB_GAP)) {
      for (row in 0 until rows) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(THUMB_GAP)) {
          for (column in 0 until CARD_COLUMNS) {
            val n = row * CARD_COLUMNS + column
            val post = posts.getOrNull(n)
            Box(
              Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(
                  if (post != null && onLook != null) {
                    Modifier.clickable(onClickLabel = LOOK) { onLook(post) }.semantics { contentDescription = "Post ${n + 1}" }
                  } else {
                    Modifier
                  },
                ),
            ) {
              if (post != null) {
                AsyncImage(
                  model = postImageRequest(context, PostImage.of(post, Variant.Preview)),
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize(),
                )
              }
            }
          }
        }
      }
    }
    if (failed) {
      Text(
        CARD_FAILED,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(16.dp),
      )
    }
  }
}

@Composable
private fun AnswerLine(answer: SortDeck.Answer, onUndo: () -> Unit, modifier: Modifier = Modifier) =
  UndoLine(if (answer.kind == SortDeck.Answer.Kind.Later) schedule else check, answerText(answer), onUndo, modifier)

internal fun answerText(answer: SortDeck.Answer): String = when (answer.kind) {
  SortDeck.Answer.Kind.Later ->
    if (answer.stillNew) "${answer.title} is last in the queue, still new" else "${answer.title} moved to the end of the queue"
  SortDeck.Answer.Kind.Done ->
    if (answer.kept > 0) "${answer.title} closed${keptStaySuffix(answer.kept)}" else "${answer.title} closed · in Recently closed"
  SortDeck.Answer.Kind.Followed -> "Following ${answer.title} · its search closed${keptStaySuffix(answer.kept)}"
}

internal fun wentInText(went: SortDeck.WentIn): String {
  val position = went.progress?.position
  val size = went.progress?.size
  val place = when {
    position != null && size != null -> "${grouped(position)} of ${sizeWords(size, alone = false)}"
    position != null -> "at post ${grouped(position)}"
    else -> null
  }
  val kept = went.kept.takeIf { it > 0 }?.let { "$it kept" }
  val liked = went.liked.takeIf { it > 0 }?.let { "$it liked" }
  return listOfNotNull("You went in", place, kept, liked).joinToString(" · ")
}

@Composable
private fun SortButtons(onDone: () -> Unit, onLater: () -> Unit, onGoIn: () -> Unit, goInLabel: String, said: (@Composable (Modifier) -> Unit)?) {
  Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    said?.invoke(Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
      OutlineIconButton(DONE, check, onDone)
      TonalButton(LATER, schedule, onLater)
      PrimaryIconButton(goInLabel, grid_view, onGoIn)
    }
  }
}

@Composable
private fun SortButtonColumn(onDone: () -> Unit, onLater: () -> Unit, onGoIn: () -> Unit, goInLabel: String) {
  BoxWithConstraints(Modifier.fillMaxHeight()) {
    Column(
      Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(end = 16.dp, top = 8.dp, bottom = 8.dp)
        .width(IntrinsicSize.Max),
      verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      PrimaryIconButton(goInLabel, grid_view, onGoIn, Modifier.fillMaxWidth())
      TonalButton(LATER, schedule, onLater, Modifier.fillMaxWidth())
      OutlineIconButton(DONE, check, onDone, Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun SortTally(
  tally: SortDeck.Tally,
  left: Int,
  stillNew: Int,
  today: TodayCounts?,
  onKeepSorting: () -> Unit,
  onToQueue: () -> Unit,
  toQueueLabel: String = TO_THE_QUEUE,
  foundOn: String? = null,
  modifier: Modifier = Modifier,
) {
  Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag(SORT_TALLY)) {
    Surface(
      Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      shadowElevation = 2.dp,
    ) { TallyWords(tally, left, stillNew, today, onKeepSorting, onToQueue, toQueueLabel, foundOn) }
  }
}

@Composable
private fun TallyWords(
  tally: SortDeck.Tally,
  left: Int,
  stillNew: Int,
  today: TodayCounts?,
  onKeepSorting: () -> Unit,
  onToQueue: () -> Unit,
  toQueueLabel: String,
  foundOn: String?,
) {
  Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text("${tally.sorted} sorted", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    foundOn?.let {
      Text(
        "found on $it",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
    Spacer(Modifier.height(16.dp))
    for (kind in TALLY_ROWS) {
      val undone = tally.undone.filter { it.kind == kind }
      if (kind == SortDeck.Answer.Kind.Followed && tally.followed == 0 && undone.isEmpty()) continue
      TallyLine(tallyLabel(kind), tally.count(kind))
      tally.wentIn(kind).takeIf { it > 0 }?.let { TallySubLine(GONE_INTO_FIRST, it) }
      for (taken in undone) TallySubLine(undoneText(taken), null)
    }
    TallyLine(if (stillNew > 0) STILL_NEW else NO_NEW_LEFT, stillNew.takeIf { it > 0 })
    if (today != null) TallyLine(QUEUE_TODAY, null, todayLine(today), quiet = true)
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (left > 0) PrimaryIconButton(sortMoreLabel(left), chevron_right, onKeepSorting)
      TextActionButton(toQueueLabel, onClick = onToQueue)
    }
  }
}

@Composable
private fun TallyLine(label: String, count: Int?, figure: String? = count?.let { "$it" }, quiet: Boolean = false) {
  val color = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
  HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
  Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp).semantics(mergeDescendants = true) {}) {
    Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
    figure?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = color) }
  }
}

@Composable
private fun TallySubLine(label: String, count: Int?) {
  Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, bottom = 8.dp).semantics(mergeDescendants = true) {}) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    count?.let { Text("$it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
  }
}

private val TALLY_ROWS = listOf(SortDeck.Answer.Kind.Done, SortDeck.Answer.Kind.Later, SortDeck.Answer.Kind.Followed)

internal fun tallyLabel(kind: SortDeck.Answer.Kind): String = when (kind) {
  SortDeck.Answer.Kind.Later -> LATER
  SortDeck.Answer.Kind.Done -> DONE
  SortDeck.Answer.Kind.Followed -> FOLLOWED_INSTEAD
}

internal fun undoneText(undone: SortDeck.Undone): String =
  "undone: ${undone.title}, ${if (undone.kind == SortDeck.Answer.Kind.Later) "back in its place" else "back in the queue"}"

internal fun sortMoreLabel(left: Int): String = "Sort $left more"

@Composable
private fun LookAtPost(post: PostDto, onClose: () -> Unit) {
  val context = LocalPlatformContext.current
  PlatformBackHandler(enabled = true, onBack = onClose)
  Box(
    Modifier
      .fillMaxSize()
      .background(Color.Black)
      .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = BACK_TO_CARD, onClick = onClose)
      .testTag(SORT_LOOK),
    contentAlignment = Alignment.Center,
  ) {
    AsyncImage(
      model = postImageRequest(context, PostImage.of(post, Variant.Sample)),
      contentDescription = LOOKED_AT,
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

internal const val LOOKED_AT = "Post from the card"

private val WIDE_WORDS_MIN = 220.dp
private val WIDE_GAP = 8.dp

internal val WIDE_MIN_EXTRA = 320.dp

private val SAID_WIDE_MAX = 480.dp

private val PEEK = 10.dp

private val THUMB_GAP = 4.dp

internal data class CardGrid(val rows: Int, val width: Dp, val height: Dp)

internal fun uprightGrid(maxWidth: Dp, maxHeight: Dp, loaded: Int?): CardGrid {
  val side = minOf(maxWidth, maxHeight)
  val square = CardGrid(SQUARE_ROWS, side, side)
  if (maxHeight == Dp.Infinity || maxWidth == Dp.Infinity) return square
  val cell = (maxWidth - THUMB_GAP * (CARD_COLUMNS - 1)) / CARD_COLUMNS
  if (cell <= 0.dp) return square
  val fit = ((maxHeight + THUMB_GAP) / (cell + THUMB_GAP) + 0.001f).toInt()
  if (fit <= SQUARE_ROWS) return square
  val filled = loaded?.let { it / CARD_COLUMNS } ?: Int.MAX_VALUE
  val rows = minOf(fit, CARD_POSTS / CARD_COLUMNS, filled).coerceAtLeast(SQUARE_ROWS)
  val tallest = cell * CELL_TALLEST * rows + THUMB_GAP * (rows - 1)
  return CardGrid(rows, maxWidth, minOf(maxHeight, tallest))
}

private const val CARD_COLUMNS = 3

private const val SQUARE_ROWS = 3

private const val CELL_TALLEST = 4f / 3

internal fun cardTagType(posts: List<PostDto>, tags: List<String>): TagType? {
  val tag = oneTag(tags) ?: return null
  return posts.firstNotNullOfOrNull { post -> post.tags.firstOrNull { it.searchName.equals(tag, ignoreCase = true) }?.category }
}

internal fun cardMeta(tags: List<String>, size: SourceSize?): String {
  val order = filterNotes(tags).lastOrNull()?.replaceFirstChar { it.uppercase() } ?: "Newest"
  val posts = when (size) {
    is SourceSize.About -> "about ${grouped(size.posts)} ${if (size.posts == 1) "post" else "posts"}"
    is SourceSize.Counted -> if (size.more) "${grouped(size.posts)}+ posts" else "${grouped(size.posts)} ${if (size.posts == 1) "post" else "posts"}"
    null -> null
  }
  return listOfNotNull(order, posts).joinToString(" · ")
}


internal fun todayLine(today: TodayCounts): String = "+${grouped(today.found)} found · ${grouped(today.done)} done"

internal fun newestLine(newest: Instant, now: Instant): String {
  val days = (now - newest).inWholeDays.coerceAtLeast(0)
  val ago = when {
    days == 0L -> "today"
    days == 1L -> "yesterday"
    days < 60 -> "$days days ago"
    days < 730 -> "${days / 30} months ago"
    else -> "${days / 365} years ago"
  }
  return "newest $ago"
}

internal fun followInsteadName(name: String) = "Follow $name instead"

internal const val FOLLOW_INSTEAD = "Follow instead"
internal const val FOLLOW_INSTEAD_NOTE = "New posts come in Following; this search closes"
internal const val FOLLOWED_INSTEAD = "Followed instead"
internal const val QUEUE_TODAY = "Queue today"
internal const val GO_IN = "Go in"
internal const val BACK_IN = "Back in"
internal const val LATER = "Later"
internal const val DONE = "Done"
internal const val GONE_INTO_FIRST = "of those, gone into first"
internal const val STILL_NEW = "Still new"
internal const val NO_NEW_LEFT = "No new sources left"
internal const val TO_THE_QUEUE = "To the queue"
internal const val STOP_SORTING = "Stop sorting"
internal const val LOOK = "Look"
internal const val BACK_TO_CARD = "Back to the card"
internal const val CARD_FAILED = "Couldn't load these posts"
internal const val SORT_PAGE = "Sort page"
internal const val SORT_CARD = "Sort card"
internal const val SORT_CARD_BEHIND = "Sort card behind"
internal const val SORT_TALLY = "Sort tally"
internal const val SORT_WENT_IN = "Sort went in"
internal const val SORT_LOOK = "Sort look"

package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.google.fonts.icons.replay
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.ui.components.PrimaryIconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.fonts.icons.bookmark
import com.google.fonts.icons.chevron_right
import com.google.fonts.icons.style
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.shell.LocalChromePadding
import dev.marzahn.sankaku.ui.components.PrimaryButton
import dev.marzahn.sankaku.ui.components.TextActionButton
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import org.koin.compose.koinInject
import org.koin.mp.KoinPlatform

@Composable
fun UpNextCardView(
  modifier: Modifier = Modifier,
  state: UpNextState = koinInject(),
  leftovers: EndLeftoverSources? = endLeftoverSourcesOrNull(),
) {
  val card = state.card ?: return
  BoxWithConstraints(
    modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .padding(LocalChromePadding.current)
      .padding(bottom = PILL_ROOM)
      .semantics {
        paneTitle = if (card == UpNextCard.KeptEnd) (state.keptTally()?.kind ?: KeptRunKind.Kept).endPane else END_PANE
      },
  ) {
    Box(
      Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
      contentAlignment = Alignment.Center,
    ) {
      key(card) { CardContent(card, state, leftovers) }
    }
  }
}

private class CardText(val label: String, val title: String, val detail: String, val spoken: String)

private fun cardText(card: UpNextCard, kept: KeptEndCounts?, waiting: Boolean): CardText = when (card) {
  UpNextCard.End -> {
    val detail = if (waiting) END_WAITING else END_DETAIL
    CardText(END_PANE, END_TITLE, detail, "$END_PANE. $END_TITLE. $detail")
  }
  UpNextCard.KeptEnd -> {
    val label = (kept?.kind ?: KeptRunKind.Kept).endPane
    val title = kept?.let { "${grouped(it.goneThrough)} gone through" } ?: KEPT_END_TITLE
    val detail = kept?.let { keptEndDetail(it.later) }.orEmpty()
    CardText(label, title, detail, "$label. $title. ${keptEndParts(kept).joinToString(", ")}. $detail")
  }
}

private class KeptEndCounts(val kind: KeptRunKind, val goneThrough: Int, val dropped: Int, val liked: Int, val later: Int)

private class TallyPart(val count: Int, val word: String, val later: Boolean = false) {
  override fun toString() = "${grouped(count)} $word"
}

private fun keptEndParts(counts: KeptEndCounts?): List<TallyPart> = if (counts == null) {
  emptyList()
} else {
  listOf(TallyPart(counts.dropped, "dropped"), TallyPart(counts.liked, "liked"), TallyPart(counts.later, "for later", later = true))
    .filter { it.count > 0 }
}

internal fun keptEndDetail(later: Int): String = when {
  later <= 0 -> ""
  later == 1 -> "It stays in its block, across a relaunch, until you drop it or press Clear viewed."
  else -> "The ${grouped(later)} stay in their blocks, across a relaunch, until you drop them or press Clear viewed."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardContent(card: UpNextCard, state: UpNextState, sources: EndLeftoverSources?) {
  val focus = remember { FocusRequester() }
  LaunchedEffect(card) { focus.requestFocus() }
  val colors = MaterialTheme.colorScheme
  val keptCard = card == UpNextCard.KeptEnd
  val clearViewed = if (keptCard) koinInject<ClearViewed>() else null
  val clearCount = clearViewed?.status()?.count ?: 0
  val tally = if (keptCard) state.keptTally() else null
  val later = if (keptCard) rememberLaterPosts() else emptyList()
  val liked = if (keptCard) rememberLiked(tally?.dropped.orEmpty()) else 0
  val counts = tally?.let { KeptEndCounts(it.kind, it.goneThrough, it.dropped.size - liked, liked, it.later) }
  val toQueue = LocalOpenTabList.current
  val kept = KeptEndActions(
    clearCount = clearCount,
    clear = { clearViewed?.invoke() },
    toQueue = {
      state.endKept()
      toQueue()
    },
    laterCount = later.size,
    goThroughLater = { state.goThroughLater(later.map { it.id }) },
  )
  val waiting = if (card == UpNextCard.End && sources != null) rememberEndLeftovers(state, sources) else EndLeftovers.NONE
  val rows = leftoverRows(waiting, state)
  val text = cardText(card, counts, rows.isNotEmpty())
  val actions = rows.map { row -> CustomAccessibilityAction(row.spoken) { row.onClick(); true } } +
    cardActions(card, state, kept) + LocalQueueActions.current
  Surface(
    modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 480.dp).fillMaxWidth().testTag(END_CARD),
    shape = MaterialTheme.shapes.extraLarge,
    color = colors.surfaceContainerHigh,
    contentColor = colors.onSurface,
  ) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(text.label.uppercase(), style = MaterialTheme.typography.labelMedium)
      Column(
        Modifier
          .focusRequester(focus)
          .focusable()
          .semantics(mergeDescendants = true) {
            heading()
            contentDescription = text.spoken
            customActions = actions
          },
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(text.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (counts != null) KeptTallyChips(counts)
        if (text.detail.isNotEmpty()) Text(text.detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
      }
      if (rows.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          rows.forEach { LeftoverRow(it) }
        }
      }
      FlowRow(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
      ) {
        when (card) {
          UpNextCard.End -> {
            val startLabel = Modifier.semantics { contentDescription = "$BACK_TO_START of the queue" }
            val browseLabel = Modifier.semantics { contentDescription = BROWSE_LABEL }
            if (rows.isEmpty()) {
              PrimaryButton(BACK_TO_START, state::backToStart, startLabel)
              OutlinedButton(onClick = state::browse, modifier = browseLabel) { Text(BROWSE) }
            } else {
              TextActionButton(BACK_TO_START, state::backToStart, startLabel)
              TextActionButton(BROWSE, state::browse, browseLabel)
            }
          }
          UpNextCard.KeptEnd -> {
            val buttons = keptEndButtons(kept)
            buttons.shown.forEach { KeptEndButtonView(it, leads = it == buttons.lead, kept) }
          }
        }
      }
    }
  }
}

private enum class KeptEndButton(val action: String) {
  GoThroughLater(GO_THROUGH_LATER),
  ClearViewed(CLEAR_VIEWED),
  ToTheQueue(TO_THE_QUEUE),
}

private class KeptEndButtons(val lead: KeptEndButton, val shown: List<KeptEndButton>)

private fun keptEndButtons(kept: KeptEndActions): KeptEndButtons {
  val lead = if (kept.laterCount > 0) KeptEndButton.GoThroughLater else KeptEndButton.ToTheQueue
  val shown = listOfNotNull(
    KeptEndButton.GoThroughLater.takeIf { lead == it },
    KeptEndButton.ClearViewed.takeIf { kept.clearCount > 0 },
    KeptEndButton.ToTheQueue,
  )
  return KeptEndButtons(lead, shown)
}

private fun KeptEndActions.press(button: KeptEndButton) = when (button) {
  KeptEndButton.GoThroughLater -> goThroughLater()
  KeptEndButton.ClearViewed -> clear()
  KeptEndButton.ToTheQueue -> toQueue()
}

@Composable
private fun KeptEndButtonView(button: KeptEndButton, leads: Boolean, kept: KeptEndActions) {
  when (button) {
    KeptEndButton.GoThroughLater -> PrimaryIconButton(goThroughLaterLabel(kept.laterCount), replay, kept.goThroughLater)
    KeptEndButton.ClearViewed ->
      OutlinedButton(onClick = kept.clear, modifier = Modifier.semantics { contentDescription = clearViewedLabel(kept.clearCount) }) {
        Text("$CLEAR_VIEWED · ${kept.clearCount}")
      }
    KeptEndButton.ToTheQueue ->
      if (leads) PrimaryButton(TO_THE_QUEUE, kept.toQueue) else OutlinedButton(onClick = kept.toQueue) { Text(TO_THE_QUEUE) }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeptTallyChips(counts: KeptEndCounts) {
  val colors = MaterialTheme.colorScheme
  val parts = keptEndParts(counts)
  if (parts.isEmpty()) return
  FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    parts.forEach { part ->
      val (count, word, later) = Triple(part.count, part.word, part.later)
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (later) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (later) colors.onPrimaryContainer else colors.onSurfaceVariant,
      ) {
        Text(
          buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = if (later) colors.onPrimaryContainer else colors.onSurface)) { append(grouped(count)) }
            append(" $word")
          },
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
      }
    }
  }
}

@Composable
internal fun rememberLaterPosts(controller: TabsController = koinInject(), posts: TabPosts = koinInject(), privacy: PrivacySettings = koinInject()): List<TabState> {
  val tabs by controller.tabs.collectAsStateWithLifecycle()
  val gOnly by privacy.gOnly.collectAsStateWithLifecycle()
  val ids = controller.openPosts(tabs)
  val known by remember(ids) { posts.of(ids) }.collectAsStateWithLifecycle(emptyMap())
  return laterPosts(tabs, gOnly) { tab -> (tab.top as? Route.Post)?.let { known[it.id] } }
}

@Composable
private fun rememberLiked(dropped: List<String?>, posts: TabPosts = koinInject()): Int {
  val ids = dropped.filterNotNull().toSet()
  val known by remember(ids) { posts.of(ids) }.collectAsStateWithLifecycle(emptyMap())
  return ids.count { known[it]?.favourited == true }
}

internal fun goThroughLaterLabel(count: Int) = "Go through later · ${grouped(count)}"

private fun cardActions(card: UpNextCard, state: UpNextState, kept: KeptEndActions): List<CustomAccessibilityAction> = when (card) {
  UpNextCard.End -> listOf(
    CustomAccessibilityAction(BACK_TO_START) { state.backToStart(); true },
    CustomAccessibilityAction(BROWSE) { state.browse(); true },
  )
  UpNextCard.KeptEnd -> keptEndButtons(kept).shown.map { button -> CustomAccessibilityAction(button.action) { kept.press(button); true } }
}

private class KeptEndActions(
  val clearCount: Int,
  val clear: () -> Unit,
  val toQueue: () -> Unit,
  val laterCount: Int,
  val goThroughLater: () -> Unit,
)

class EndLeftoverSources(
  val seen: SeenStore?,
  val posts: TabPosts = TabPosts.None,
  val today: TodayStore? = null,
)

@Composable
internal fun endLeftoverSourcesOrNull(): EndLeftoverSources? = remember {
  KoinPlatform.getKoinOrNull()?.let { koin ->
    EndLeftoverSources(
      seen = koin.getOrNull<SeenStore>(),
      posts = koin.getOrNull<TabPosts>() ?: TabPosts.None,
      today = koin.getOrNull<TodayStore>(),
    )
  }
}

@Composable
private fun rememberEndLeftovers(state: UpNextState, sources: EndLeftoverSources): EndLeftovers {
  val tabs by state.tabs.tabs.collectAsStateWithLifecycle()
  val gOnly by state.gOnly.collectAsStateWithLifecycle()
  val seen = sources.seen
  val seenRevision = seen?.revision?.collectAsStateWithLifecycle()?.value
  val todayChange = sources.today?.changes?.collectAsStateWithLifecycle()?.value
  val postIds = state.tabs.openPosts(tabs)
  val known by remember(postIds, sources.posts) { sources.posts.of(postIds) }.collectAsStateWithLifecycle(emptyMap())
  val zone = remember { TimeZone.currentSystemDefault() }
  return remember(tabs, gOnly, known, seenRevision, todayChange, sources) {
    endLeftovers(
      tabs = tabs,
      gOnly = gOnly,
      post = { tab -> (tab.top as? Route.Post)?.let { known[it.id] } },
      seen = { tab -> seen != null && tab.seenSinceKept(seen) },
      foundToday = sources.today?.counts()?.found ?: 0,
      now = Clock.System.now(),
      zone = zone,
    )
  }
}

private class LeftoverRow(val icon: ImageVector, val title: String, val detail: String?, val onClick: () -> Unit) {
  val spoken: String get() = listOfNotNull(title, detail).joinToString(", ")
}

private fun leftoverRows(waiting: EndLeftovers, state: UpNextState): List<LeftoverRow> = buildList {
  val start = waiting.keptStart
  if (waiting.keptUnseen > 0 && start != null) {
    add(LeftoverRow(bookmark, keptNotSeen(waiting.keptUnseen), waiting.keptWhere) { state.goThroughKept(waiting.kept, start) })
  }
  if (waiting.later.isNotEmpty()) {
    add(LeftoverRow(replay, "${grouped(waiting.later.size)} for later", waiting.laterWhere) { state.goThroughLater(waiting.later.map { it.id }) })
  }
  if (waiting.newSources > 0) {
    val found = waiting.foundToday.takeIf { it > 0 }?.let { "found today: ${grouped(it)}" }
    add(LeftoverRow(style, "$SORT ${grouped(waiting.newSources)} new", found) { state.sort() })
  }
}

private fun keptNotSeen(count: Int): String = "${grouped(count)} kept ${if (count == 1) "post" else "posts"} not seen"

@Composable
private fun LeftoverRow(row: LeftoverRow) {
  val colors = MaterialTheme.colorScheme
  Surface(
    onClick = row.onClick,
    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
      role = Role.Button
      contentDescription = row.spoken
    },
    shape = MaterialTheme.shapes.large,
    color = colors.surfaceContainerLow,
    contentColor = colors.onSurface,
  ) {
    Row(
      Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(row.icon, contentDescription = null, tint = colors.primary)
      Column(Modifier.weight(1f)) {
        Text(row.title, style = MaterialTheme.typography.titleSmall)
        row.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
      }
      Icon(chevron_right, contentDescription = null, tint = colors.primary)
    }
  }
}

private val PILL_ROOM = 96.dp

internal const val END_PANE = "End of the queue"
internal const val END_CARD = "End of the queue card"
private const val END_TITLE = "You've reached the end"
private const val END_DETAIL = "Nothing else is waiting."
private const val END_WAITING = "Still waiting earlier in the queue:"
private const val BACK_TO_START = "Back to the start"
private const val BROWSE = "Browse"
private const val BROWSE_LABEL = "Browse, a new grid"

internal const val KEPT_END_PANE = "End of the kept posts"
private const val KEPT_END_TITLE = "That's every kept post"
private const val CLEAR_VIEWED = "Clear viewed"

internal const val LATER_END_PANE = "End of the later posts"
internal const val GO_THROUGH_LATER = "Go through later"

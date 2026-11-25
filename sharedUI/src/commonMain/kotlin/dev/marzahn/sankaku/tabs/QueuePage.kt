package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.memory.MemoryCache
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.images.PostImage
import dev.marzahn.sankaku.shell.LocalQueuePageVisibility
import dev.marzahn.sankaku.shell.MenuBadge
import dev.marzahn.sankaku.shell.MenuButton
import dev.marzahn.sankaku.shell.sharedPicture
import dev.marzahn.sankaku.shell.sharedMosaicThumb
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.fonts.icons.arrow_back
import com.google.fonts.icons.arrow_upward
import androidx.compose.runtime.derivedStateOf
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.platformUses24HourClock
import kotlinx.coroutines.launch
import com.google.fonts.icons.check
import com.google.fonts.icons.close
import com.google.fonts.icons.drag_handle
import com.google.fonts.icons.expand_more
import com.google.fonts.icons.more_vert
import com.google.fonts.icons.play_arrow
import com.google.fonts.icons.replay
import com.google.fonts.icons.search
import com.google.fonts.icons.shield
import com.google.fonts.icons.undo
import com.google.fonts.icons.view_agenda
import com.google.fonts.icons.visibility_off
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.api.dto.TagType
import dev.marzahn.sankaku.ui.theme.color
import dev.marzahn.sankaku.PlatformBackHandler
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.isLone
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.features.grid.Rating
import dev.marzahn.sankaku.features.settings.ClearEverythingConfirm
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.components.ConnectedButtonLine
import dev.marzahn.sankaku.ui.components.FocusOnFirstShow
import dev.marzahn.sankaku.ui.components.GroupedMenu
import dev.marzahn.sankaku.ui.components.LARGE_FONT_SCALE
import dev.marzahn.sankaku.ui.components.LineButton
import dev.marzahn.sankaku.ui.components.LineButtonStyle
import dev.marzahn.sankaku.ui.components.LiveAnnouncement
import dev.marzahn.sankaku.ui.components.MenuAction
import dev.marzahn.sankaku.ui.components.MenuGroup
import dev.marzahn.sankaku.ui.components.PrimaryIconButton
import dev.marzahn.sankaku.ui.components.TonalToggleIconButton
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

@Composable
fun QueuePage(
  onDismiss: () -> Unit,
  onMenu: (() -> Unit)? = null,
  menuBadge: MenuBadge = MenuBadge.None,
  controller: TabsController = koinInject(),
  posts: TabPosts = koinInject(),
  privacy: PrivacySettings = koinInject(),
  seen: SeenStore? = koinInject<SeenStore>(),
  clearViewed: ClearViewed = koinInject(),
  undo: ClosedTabUndo = koinInject(),
  interacted: () -> Boolean = koinInject<QueueStart>()::interacted,
  onInteract: () -> Unit = koinInject<QueueStart>()::interact,
  onListTop: (Dp) -> Unit = {},
  leaving: Boolean = false,
  previews: SourcePreviews? = null,
  today: TodayStore? = null,
  onSort: (() -> Unit)? = null,
  folding: Boolean = true,
  chipMemory: QueueStart? = null,
  following: FollowingNewsSource? = null,
  catchUp: FeedCatchUp? = null,
) {
  val interact by rememberUpdatedState(onInteract)
  val dismiss = {
    interact()
    onDismiss()
  }
  PlatformBackHandler(enabled = !leaving, onBack = dismiss)
  val liveTabs by controller.tabs.collectAsStateWithLifecycle()
  val active by controller.active.collectAsStateWithLifecycle()
  val liveClosed by controller.closed.collectAsStateWithLifecycle()
  val livePending by undo.pending.collectAsStateWithLifecycle()
  val gOnly by privacy.gOnly.collectAsStateWithLifecycle()
  val live = ShownQueue(liveTabs, liveClosed, livePending, blurAdult = gOnly || !interacted())
  val lastShown = remember { arrayOfNulls<ShownQueue>(1) }
  val shown = lastShown[0]?.takeIf { leaving } ?: live
  SideEffect { if (!leaving) lastShown[0] = live }
  val (tabs, closed, _, blurAdult) = shown
  var reopened by remember { mutableStateOf<Long?>(null) }
  if (!leaving && reopened != null) SideEffect { reopened = null }
  val seenRevision = seen?.revision?.collectAsStateWithLifecycle()?.value
  val seenSince: (TabState) -> Boolean = remember(seen, seenRevision) {
    { tab -> seen != null && tab.seenSinceKept(seen) }
  }
  val postIds = controller.openPosts(tabs) + closedPosts(closed)
  val known by remember(postIds) { posts.of(postIds) }.collectAsStateWithLifecycle(emptyMap())
  val post: (TabState) -> TabPost? = { tab -> (tab.top as? Route.Post)?.let { known[it.id] } }
  val closedListed = remember(closed, gOnly, known) { closedRows(closed, gOnly) { known[it.id] } }
  val focused = remember { mutableSetOf<Long>() }
  val inline = shown.undoOf(Closing.OnQueuePage)
  var query by remember { mutableStateOf<String?>(null) }
  val finding = query?.takeIf { it.isNotBlank() }
  val ownChip = rememberSaveable(stateSaver = QueueFilterSaver) { mutableStateOf(QueueFilter.All) }
  var filter by (chipMemory?.chipState ?: ownChip)
  var open by rememberSaveable(stateSaver = TabIdSetSaver) {
    mutableStateOf(setOfNotNull(liveTabs.firstOrNull { it.id == active }?.takeIf { it.kind.isKept }?.head))
  }
  val folds = open.takeIf { finding == null && folding }
  val rows = queueRows(tabs, closed, inline, finding, gOnly, filter, folds, seenSince, post)
  val movable = finding == null && filter == QueueFilter.All
  val listedSource = { tab: TabState -> !(gOnly && isAdult(tab, post(tab))) }
  val newSources = newSources(tabs, gOnly).size
  val startedSources = tabs.count { isStartedSource(it) && listedSource(it) }
  val keptPosts = remember(tabs, gOnly, known) { keptListed(tabs, gOnly, post) }
  val goThroughKept = { at: TabState? ->
    val start = at ?: keptRunStart(keptPosts, seenSince)
    controller.startKeptRun(keptPosts.map { it.id }, start.id)
    dismiss()
  }
  val later = laterPosts(tabs, gOnly, post)
  val goThroughLater = { at: TabState? ->
    controller.startLaterRun(later.map { it.id }, at?.id)
    dismiss()
  }
  val todayChange = today?.changes?.collectAsStateWithLifecycle()?.value
  val counts = today?.counts()
  val seenToday = remember(seen, seenRevision, todayChange, today) { today?.let { seen?.countSeenSince(it.startOfToday()) } ?: 0 }
  val goneThrough = remember(tabs, seen, seenRevision, todayChange, today) {
    if (today == null || counts == null) 0 else goneThroughToday(tabs, counts, seen, today.startOfToday())
  }
  val currentRows by rememberUpdatedState(rows)
  val counted = numberedBlocks(tabs)
  val opensAt = (rows.indexOfFirst { it.isTab(active) } - ROWS_ABOVE_CURRENT).coerceAtLeast(0)
  val list = rememberLazyListState(initialFirstVisibleItemIndex = opensAt)
  var cueAbove by rememberSaveable { mutableStateOf(opensAt > 0) }
  val followingTab = tabs.firstOrNull { it.feed == Feed.Following }
  val newsState = remember { mutableStateOf(following?.known() ?: FollowingNews(null)) }
  if (followingTab != null && following != null) {
    LaunchedEffect(followingTab.id, gOnly) { newsState.value = following.load(followingTab) }
  }
  val followingNews = newsState.value
  val rowHeight = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
  val reorder = remember { Reorder() }
  var showCurrent by remember { mutableIntStateOf(0) }
  LaunchedEffect(showCurrent) {
    if (showCurrent == 0) return@LaunchedEffect
    val at = (currentRows.indexOfFirst { it.isTab(controller.active.value) } - ROWS_ABOVE_CURRENT).coerceAtLeast(0)
    list.scrollToItem(at)
    cueAbove = at > 0
  }
  LaunchedEffect(list) {
    snapshotFlow { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 }.collect { top -> if (top) cueAbove = false }
  }
  val above by remember(list) { derivedStateOf { list.firstVisibleItemIndex } }
  val cueCount = if (cueAbove && query == null) above else 0
  LaunchedEffect(list) {
    snapshotFlow { list.isScrollInProgress }.first { it }
    interact()
  }
  LaunchedEffect(query) {
    if (!query.isNullOrBlank()) list.scrollToItem(0)
  }
  var showClosed by remember { mutableIntStateOf(0) }
  LaunchedEffect(showClosed) {
    if (showClosed > 0) list.animateScrollToItem(currentRows.size)
  }
  var reveal by remember { mutableStateOf<Long?>(null) }
  LaunchedEffect(reveal) {
    val key = reveal ?: return@LaunchedEffect
    val at = currentRows.indexOfFirst { it is QueueRow.Inline && it.closed.key == key }
    if (at >= 0) list.scrollToItem((at - 1).coerceAtLeast(0))
  }
  val bulk = listOf(
    BulkClose("Close everything below", controller.tabsToTheRight()),
    BulkClose("Close duplicates", controller.duplicateTabs()),
    BulkClose("Close favourited", controller.favouritedTabs { known[it]?.favourited == true }),
    BulkClose("Close all but current", controller.otherTabs()),
  )
  var clearingEverything by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val activeTab = liveTabs.firstOrNull { it.id == active }
  val clearStatus = clearViewed.status()
  val clearedShown = shown.undoOf(Closing.Cleared)
  val sort = onSort?.takeIf { newSources > 0 }
  val lineButtons = buildList {
    if (sort != null) add(sortButton(newSources, sort))
    when {
      clearedShown != null -> add(
        undoClearButton(clearedShown, FocusOnFirstShow(clearedShown.key) { focused.add(clearedShown.key) }) {
          interact()
          undo.undo()
        },
      )
      clearStatus.count > 0 -> add(clearViewedButton(clearStatus) { clearViewed() })
    }
    if (later.isNotEmpty()) add(goThroughLaterButton(later.size) { goThroughLater(null) })
    if (activeTab != null) add(continueButton(activeTab.title, dismiss))
  }
  var bottomRoom by remember { mutableStateOf(if (lineButtons.isNotEmpty()) BOTTOM_LINE_ROOM else 0.dp) }

  var moved by remember { mutableStateOf("") }
  LaunchedEffect(moved) {
    if (moved.isNotEmpty()) {
      delay(ANNOUNCEMENT_MILLIS)
      moved = ""
    }
  }

  fun step(id: TabId, down: Boolean): Int? {
    interact()
    val before = currentRows.indexOfFirst { it.isTab(id) }
    fun rowsNow() = queueRows(controller.tabs.value, closed, inline, finding, gOnly, filter, folds, seenSince, post)
    var moves = controller.moveInQueue(id, down) ?: return null
    var rowsAfter = rowsNow()
    while (moves != 0 && rowsAfter.indexOfFirst { it.isTab(id) } == before) {
      moves = controller.moveInQueue(id, down) ?: break
      rowsAfter = rowsNow()
    }
    moved = when (val row = rowsAfter.firstOrNull { it.isTab(id) }) {
      is QueueRow.Item -> row.shownNumber?.let { "${row.tab.title} moved to item $it of ${numberedBlocks(controller.tabs.value)}" }
        ?: "${row.tab.title} moved ${if (down) "down" else "up"}"
      is QueueRow.ClosedBlock -> "${row.title} moved ${if (down) "down" else "up"}"
      else -> moved
    }
    return rowsAfter.indexOfFirst { it.isTab(id) } - before
  }

  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
          interact()
        }
      }
      .pointerInput(Unit) { detectTapGestures {} }
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    val sideColumn = query == null && maxHeight < SIDE_COLUMN_MAX_HEIGHT && maxWidth >= SIDE_COLUMN_MIN_WIDTH
    val sideScroll = rememberScrollState()
    val listBottom = if (sideColumn) 0.dp else bottomRoom
    LiveAnnouncement(moved)
    val controls: @Composable () -> Unit = {
      if (counts != null) TodayStrip(counts, goneThrough, seenToday)
      QueueChips(
        filter,
        keptPosts.size,
        newSources,
        startedSources,
        onFilter = {
          filter = it
          cueAbove = false
        },
        later = later.size,
      )
      if (filter == QueueFilter.Kept && keptPosts.isNotEmpty()) {
        GoThroughKeptButton(keptPosts.size, onClick = { goThroughKept(null) })
      }
      if (followingTab != null) {
        FollowingLine(
          followingNews,
          kept = keptInBlock(tabs, followingTab.id, gOnly, post),
          onCatchUp = {
            controller.endKeptRun()
            controller.jumpTo(followingTab.id)
            followingNews.catchUpAt?.let { catchUp?.ask(followingTab.id, it) }
            dismiss()
          },
        )
      }
    }
    val actions: @Composable () -> Unit = {
      if (lineButtons.isNotEmpty()) {
        ConnectedButtonLine(
          lineButtons,
          modifier = Modifier.fillMaxWidth().testTag(QUEUE_ACTIONS_TAG).padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
    }
    val density = LocalDensity.current
    val cue: @Composable () -> Unit = {
      if (cueCount > 0) {
        MoreAbove(cueCount, onClick = {
          interact()
          cueAbove = false
          scope.launch { list.animateScrollToItem(0) }
        })
      }
    }
    val listPane: @Composable (Modifier) -> Unit = { modifier ->
      Box(modifier.testTag(QUEUE_PAGE_LIST_TAG).onPlaced { onListTop(with(density) { it.positionInRoot().y.toDp() }) }) {
        QueueItems(
          onOpenKept = when {
            filter == QueueFilter.Kept && keptPosts.isNotEmpty() -> ({ goThroughKept(it) })
            filter == QueueFilter.Later && later.isNotEmpty() -> ({ tab -> if (tab.later) goThroughLater(tab) else goThroughLater(null) })
            else -> null
          },
          rows = rows,
          total = counted,
          active = active,
          listed = if (query == null) closedListed else ClosedRows(emptyList(), 0),
          finding = finding,
          movable = movable,
          gOnly = gOnly,
          previews = previews,
          tagType = controller.tagType,
          onToggleFold = { head ->
            interact()
            open = if (head in open) open - head else open + head
          },
          list = list,
          reorder = reorder,
          rowHeight = rowHeight,
          bottomRoom = listBottom,
          onAnnounce = { moved = it },
          controller = controller,
          seenSince = seenSince,
          rating = { tab -> postRating(tab, post(tab)) },
          blurAdult = blurAdult,
          step = ::step,
          onClose = { id ->
            interact()
            undo.close(id, Closing.OnQueuePage)
          },
          firstShow = focused::add,
          onUndo = {
            interact()
            undo.undo(it, Closing.OnQueuePage)
          },
          reopened = reopened,
          onReopen = {
            reopened = it.key
            controller.reopen(it)
          },
          onDismiss = dismiss,
        )
        TabScrubber(
          list = list,
          rowCount = rows.size,
          rowHeight = ROW_HEIGHT,
          label = { item -> "${positionNear(rows, item).coerceAtMost(counted.coerceAtLeast(1))} / $counted" },
          modifier = Modifier.matchParentSize().padding(bottom = listBottom),
        )
      }
    }
    Column(Modifier.fillMaxSize()) {
      val typed = query
      if (typed == null) {
        Header(
          onMenu = onMenu,
          menuBadge = menuBadge,
          onClose = dismiss,
          count = counted,
          gOnly = gOnly,
          gOnlyEnabled = !privacy.gOnlyForced,
          onGOnly = privacy::setGOnly,
          bulk = bulk,
          onFind = { query = "" },
          onBulk = { ids ->
            controller.closeAll(ids)?.let {
              undo.opened(it, Closing.OnQueuePage)
              reveal = it.key
            }
          },
          onClearEverything = { clearingEverything = true },
          closedCount = closedListed.rows.size,
          onRecentlyClosed = { showClosed++ },
        )
        if (!sideColumn) controls()
      } else {
        FindField(
          query = typed,
          onQuery = { new ->
            if (new.isBlank() && typed.isNotBlank()) showCurrent++
            query = new
          },
          onClose = {
            query = null
            showCurrent++
          },
        )
      }
      Row(Modifier.weight(1f)) {
        if (sideColumn) {
          Column(Modifier.width(SIDE_COLUMN_WIDTH).fillMaxHeight().testTag(SIDE_COLUMN_TAG)) {
            Column(Modifier.weight(1f).verticalScroll(sideScroll)) { controls() }
            actions()
          }
          Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
          cue()
          listPane(Modifier.weight(1f).fillMaxWidth())
        }
      }
    }
    if (!sideColumn && query == null) {
      Box(
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceContainerLow)
          .onSizeChanged { bottomRoom = with(density) { it.height.toDp() } },
      ) {
        actions()
      }
    }
    if (leaving) {
      Box(
        Modifier.matchParentSize().pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) awaitPointerEvent().changes.forEach { it.consume() }
          }
        },
      )
    }
  }
  if (clearingEverything) ClearEverythingConfirm(scope = scope, onDismiss = { clearingEverything = false })
}

private val QueueFilterSaver = Saver<QueueFilter, String>(save = { it.name }, restore = { QueueFilter.valueOf(it) })

private val TabIdSetSaver = Saver<Set<TabId>, ArrayList<String>>(
  save = { ids -> ArrayList(ids.map { it.value }) },
  restore = { values -> values.mapTo(mutableSetOf(), ::TabId) },
)

internal fun QueueRow.isTab(id: TabId) = (this is QueueRow.Item && tab.id == id) || (this is QueueRow.ClosedBlock && head == id)

private class BulkClose(val label: String, val ids: List<TabId>)

private data class ShownQueue(
  val tabs: List<TabState>,
  val closed: List<ClosedTab>,
  val pending: ClosedTabUndo.Pending?,
  val blurAdult: Boolean,
) {
  fun undoOf(closing: Closing): ClosedTab? = pending?.takeIf { it.closing == closing && it.closed in closed }?.closed
}

private fun positionNear(rows: List<QueueRow>, row: Int): Int {
  if (rows.isEmpty()) return 1
  val at = row.coerceIn(rows.indices)
  fun numberAt(index: Int): Int? = when (val r = rows[index]) {
    is QueueRow.Item -> r.number
    is QueueRow.ClosedBlock -> r.number
    else -> null
  }
  return (at downTo 0).firstNotNullOfOrNull(::numberAt) ?: (at..rows.lastIndex).firstNotNullOfOrNull(::numberAt) ?: 1
}

@Composable
private fun Header(
  onMenu: (() -> Unit)?,
  menuBadge: MenuBadge,
  onClose: () -> Unit,
  count: Int,
  gOnly: Boolean,
  gOnlyEnabled: Boolean,
  onGOnly: (Boolean) -> Unit,
  bulk: List<BulkClose>,
  onFind: () -> Unit,
  onBulk: (List<TabId>) -> Unit,
  onClearEverything: () -> Unit,
  closedCount: Int,
  onRecentlyClosed: () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  HeaderLayout(
    modifier = Modifier.fillMaxWidth().padding(start = if (onMenu != null) 8.dp else 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    titleIndent = if (onMenu != null) 8.dp else 0.dp,
    lead = {
      if (onMenu != null) {
        Row {
          MenuButton(open = false, badge = menuBadge, onClick = onMenu)
          Spacer(Modifier.width(4.dp))
        }
      }
    },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Queue", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.width(8.dp))
        Text(
          "$count",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.semantics { contentDescription = if (count == 1) "1 item" else "$count items" },
        )
      }
    },
  ) {
    IconButton(onClick = onFind) { Icon(search, contentDescription = "Find in queue") }
    TonalToggleIconButton(checked = gOnly, onCheckedChange = onGOnly, enabled = gOnlyEnabled) {
      Icon(shield, contentDescription = "G only")
    }
    IconButton(onClick = onClose) { Icon(expand_more, contentDescription = "Close queue") }
    Box {
      IconButton(onClick = { menu = true }) { Icon(more_vert, contentDescription = "More") }
      GroupedMenu(expanded = menu, onDismissRequest = { menu = false }) {
        MenuGroup(0, 3) {
          MenuAction(
            text = { Text("Recently closed ($closedCount)") },
            enabled = closedCount > 0,
            onClick = {
              menu = false
              onRecentlyClosed()
            },
          )
        }
        MenuGroup(1, 3) {
          bulk.forEach { close ->
            MenuAction(
              text = { Text("${close.label} (${close.ids.size})") },
              enabled = close.ids.isNotEmpty(),
              onClick = {
                menu = false
                onBulk(close.ids)
              },
            )
          }
        }
        MenuGroup(2, 3) {
          MenuAction(
            text = { Text("Clear everything…") },
            onClick = {
              menu = false
              onClearEverything()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun HeaderLayout(
  modifier: Modifier,
  titleIndent: Dp,
  lead: @Composable () -> Unit,
  title: @Composable () -> Unit,
  actions: @Composable () -> Unit,
) {
  Layout(listOf(lead, title, { Row(verticalAlignment = Alignment.CenterVertically) { actions() } }), modifier) {
      (leads, titles, actionRows), constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val leadPlaced = leads.map { it.measure(loose) }
    val actionsPlaced = actionRows.single().measure(loose)
    val leadWidth = leadPlaced.sumOf { it.width }
    val beside = constraints.maxWidth - leadWidth - actionsPlaced.width
    val titleMeasurable = titles.single()
    val oneLine = titleMeasurable.maxIntrinsicWidth(constraints.maxHeight) <= beside
    val titlePlaced = titleMeasurable.measure(loose.copy(maxWidth = if (oneLine) beside.coerceAtLeast(0) else constraints.maxWidth))
    val firstLine = maxOf(leadPlaced.maxOfOrNull { it.height } ?: 0, actionsPlaced.height, if (oneLine) titlePlaced.height else 0)
    val height = if (oneLine) firstLine else firstLine + titlePlaced.height
    layout(constraints.maxWidth, constraints.constrainHeight(height)) {
      var x = 0
      leadPlaced.forEach {
        it.placeRelative(x, (firstLine - it.height) / 2)
        x += it.width
      }
      actionsPlaced.placeRelative(constraints.maxWidth - actionsPlaced.width, (firstLine - actionsPlaced.height) / 2)
      if (oneLine) {
        titlePlaced.placeRelative(x, (firstLine - titlePlaced.height) / 2)
      } else {
        titlePlaced.placeRelative(titleIndent.roundToPx(), firstLine)
      }
    }
  }
}

private fun sortButton(count: Int, onClick: () -> Unit) =
  LineButton(id = "sort", label = "Sort ${grouped(count)} new", icon = view_agenda, style = LineButtonStyle.Tonal, onClick = onClick)

private fun clearViewedButton(status: ClearViewed.Status, onClick: () -> Unit) = LineButton(
  id = "clear viewed",
  label = "Clear viewed · ${status.count}",
  icon = check,
  style = if (status.suggested) LineButtonStyle.Emphasized else LineButtonStyle.Tonal,
  onClick = onClick,
  name = clearViewedLabel(status.count),
)

private fun undoClearButton(cleared: ClosedTab, focusOnFirstShow: FocusOnFirstShow, onUndo: () -> Unit): LineButton {
  val words = Closing.Cleared.words(cleared)
  val label = "Undo · cleared ${cleared.size}"
  return LineButton(
    id = "undo clear",
    label = label,
    icon = undo,
    style = LineButtonStyle.Inverse,
    onClick = onUndo,
    name = "$label, ${words.undo}",
    announcedState = words.spoken,
    focusOnFirstShow = focusOnFirstShow,
  )
}

private fun goThroughLaterButton(count: Int, onClick: () -> Unit) =
  LineButton(id = "go through later", label = goThroughLaterLabel(count), icon = replay, style = LineButtonStyle.Tonal, onClick = onClick)

private fun continueButton(title: String, onClick: () -> Unit) = LineButton(
  id = "continue",
  label = "Continue",
  icon = play_arrow,
  style = LineButtonStyle.Filled,
  onClick = onClick,
  name = "Continue · $title",
)

@Composable
private fun MoreAbove(count: Int, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
    Modifier
      .padding(horizontal = 12.dp, vertical = 2.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClickLabel = "Scroll to the top", onClick = onClick)
      .heightIn(min = 32.dp)
      .padding(horizontal = 10.dp, vertical = 4.dp)
      .testTag(MORE_ABOVE_TAG),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(arrow_upward, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
    Text("${grouped(count)} more above", style = MaterialTheme.typography.labelLarge, color = colors.primary)
  }
}

@Composable
private fun GoThroughKeptButton(count: Int, onClick: () -> Unit) {
  PrimaryIconButton("Go through kept · ${grouped(count)}", play_arrow, onClick, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
}

@Composable
private fun FindField(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  Row(
    Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onClose) { Icon(arrow_back, contentDescription = "Close find") }
    TextField(
      value = query,
      onValueChange = onQuery,
      singleLine = true,
      placeholder = { Text("Find by title or tag") },
      trailingIcon = if (query.isEmpty()) {
        null
      } else {
        { IconButton(onClick = { onQuery("") }) { Icon(close, contentDescription = "Clear") } }
      },
      colors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
      ),
      shape = RoundedCornerShape(28.dp),
      modifier = Modifier.weight(1f).focusRequester(focus),
    )
  }
}

@Composable
private fun QueueItems(
  onOpenKept: ((TabState) -> Unit)?,
  rows: List<QueueRow>,
  total: Int,
  active: TabId,
  listed: ClosedRows,
  finding: String?,
  movable: Boolean,
  gOnly: Boolean,
  previews: SourcePreviews?,
  tagType: (String) -> TagType?,
  onToggleFold: (TabId) -> Unit,
  list: LazyListState,
  reorder: Reorder,
  rowHeight: Float,
  bottomRoom: Dp,
  onAnnounce: (String) -> Unit,
  controller: TabsController,
  seenSince: (TabState) -> Boolean,
  rating: (TabState) -> Rating?,
  blurAdult: Boolean,
  step: (TabId, down: Boolean) -> Int?,
  onClose: (TabId) -> Unit,
  firstShow: (Long) -> Boolean,
  onUndo: (ClosedTab) -> Unit,
  reopened: Long?,
  onReopen: (ClosedTab) -> Unit,
  onDismiss: () -> Unit,
) {
  val currentRows by rememberUpdatedState(rows)
  val currentStep by rememberUpdatedState(step)
  val handleFor: (TabId) -> Modifier = { id ->
    Modifier.pointerInput(id) {
      detectVerticalDragGestures(
        onDragStart = {
          currentRows.firstOrNull { it.isTab(id) }?.let { reorder.start(id, rowKey(it), list) }
        },
        onDragEnd = reorder::release,
        onDragCancel = reorder::release,
        onVerticalDrag = { change, dy ->
          change.consume()
          reorder.drag(dy)
        },
      )
    }
  }
  val now = remember(rows) { Clock.System.now() }
  val zone = remember { TimeZone.currentSystemDefault() }
  val hours24 = platformUses24HourClock()
  val keptOf = { at: Instant? -> at?.let { keptWords(it, now, zone, hours24) } }
  val density = LocalDensity.current
  LaunchedEffect(reorder.drags) {
    val id = reorder.dragging ?: return@LaunchedEffect
    with(density) {
      reorder.run(
        list = list,
        rows = { currentRows },
        rowKey = ::rowKey,
        step = { down -> currentStep(id, down) },
        rowHeight = rowHeight,
        edge = DRAG_SCROLL_EDGE.toPx(),
        speed = DRAG_SCROLL_SPEED.toPx(),
      )
    }
  }
  LazyColumn(state = list, contentPadding = PaddingValues(bottom = bottomRoom), modifier = Modifier.fillMaxWidth()) {
    if (finding != null && rows.none { it is QueueRow.Item }) {
      item(key = NO_MATCH) {
        Text(
          "Nothing in the queue matches “$finding”",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(16.dp),
        )
      }
    }
    rows.forEach { row ->
      when (row) {
        is QueueRow.Inline -> inlineUndoRow(row.closed, firstShow, onUndo)
        is QueueRow.ClosedBlock -> item(key = rowKey(row)) {
          PinWhile(reorder.dragging == row.head)
          ClosedBlockRow(
            row = row,
            kept = keptOf(row.lastKept),
            blurAdult = blurAdult,
            onToggleFold = { onToggleFold(row.head) },
            onMoveUp = if (movable && controller.canMoveInQueue(row.head, down = false)) ({ step(row.head, false) }) else null,
            onMoveDown = if (movable && controller.canMoveInQueue(row.head, down = true)) ({ step(row.head, true) }) else null,
            handle = if (movable) handleFor(row.head) else null,
            modifier = if (reorder.dragging == row.head) {
              Modifier.lifted(reorder, list, held = true)
            } else {
              Modifier.animateItem()
            },
          )
        }
        is QueueRow.HiddenByGOnly -> item(key = rowKey(row)) {
          SectionHeading("Hidden by G only · ${row.count}", Modifier.animateItem())
        }
        is QueueRow.Item -> item(key = rowKey(row)) {
          val tab = row.tab
          val dragged = reorder.dragging?.let { it == tab.id || (row.indented && it == tab.head) } == true
          PinWhile(reorder.dragging == tab.id)
          val source = !row.indented && hasPreview(tab.top)
          var loads by remember(tab.id) { mutableIntStateOf(0) }
          if (source && previews != null) {
            LaunchedEffect(tab.id, tab.top, gOnly) {
              previews.load(tab)
              loads++
            }
          }
          QueueItemRow(
            row = row,
            kept = keptOf(row.lastKept),
            source = if (source) {
              SourceRow(
                preview = if (previews != null) previews.shown(tab, gOnly).takeIf { loads >= 0 } else tab.preview,
                picture = { previews?.picture(it) ?: it.image },
                dot = (tab.top as? Route.Grid)?.let { oneTag(it.tags) }?.let { tagType(it.lowercase()) }?.color,
              )
            } else {
              null
            },
            onToggleFold = { onToggleFold(tab.id) },
            total = total,
            selected = tab.id == active,
            sharing = tab.id == active && reopened == null,
            seen = tab.kind.isKept && (tab.later || seenSince(tab)),
            onMarkUnseen = {
              controller.keepAgain(tab.id)
              onAnnounce("${tab.title} marked unseen")
            },
            rating = rating(tab),
            blurAdult = blurAdult,
            onOpen = {
              if (onOpenKept != null && tab.kind.isKept) {
                onOpenKept(tab)
              } else {
                controller.endKeptRun()
                controller.jumpTo(tab.id)
                onDismiss()
              }
            },
            onClose = { onClose(tab.id) },
            closable = controller.canClose(tab),
            onMoveUp = if (movable && controller.canMoveInQueue(tab.id, down = false)) ({ step(tab.id, false) }) else null,
            onMoveDown = if (movable && controller.canMoveInQueue(tab.id, down = true)) ({ step(tab.id, true) }) else null,
            handle = if (movable) handleFor(tab.id) else null,
            closeLabel = closeLabel(tab, controller.keptPostsOf(tab)),
            modifier = if (dragged) {
              Modifier.lifted(reorder, list, held = reorder.dragging == tab.id)
            } else {
              Modifier.animateItem()
            },
          )
        }
      }
    }
    if (listed.rows.isNotEmpty() || listed.hidden > 0) {
      item(key = CLOSED_HEADER) { SectionHeading("Recently closed", Modifier.animateItem()) }
      items(listed.rows, key = { "$CLOSED_PREFIX${it.closed.key}" }) { row ->
        RecentlyClosedRow(
          row,
          blurAdult = blurAdult,
          sharing = row.closed.key == reopened,
          onReopen = {
            onReopen(row.closed)
            onDismiss()
          },
          modifier = Modifier.animateItem(),
        )
      }
      if (listed.hidden > 0) {
        item(key = CLOSED_HIDDEN) { SectionHeading("Hidden by G only · ${listed.hidden}", Modifier.animateItem()) }
      }
    }
  }
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
  )
}

@Composable
private fun ClosedBlockRow(
  row: QueueRow.ClosedBlock,
  kept: String?,
  blurAdult: Boolean,
  onToggleFold: () -> Unit,
  onMoveUp: (() -> Unit)?,
  onMoveDown: (() -> Unit)?,
  handle: Modifier?,
  modifier: Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val lineColor = colors.outlineVariant
  val fold = row.fold
  Row(
    modifier
      .fillMaxWidth()
      .background(colors.surfaceContainerLow)
      .drawBehind {
        if (fold == null) {
          blockLine(BlockLine.Head, lineColor, headBottom = size.height - CLOSED_ROW_TEXT_BOTTOM.toPx())
        } else if (fold.open) {
          blockLine(BlockLine.Head, lineColor, headBottom = size.height)
        }
      }
      .heightIn(min = ROW_HEIGHT)
      .semantics(mergeDescendants = true) {
        heading()
        customActions = listOfNotNull(
          onMoveUp?.let { move -> CustomAccessibilityAction("Move up") { move(); true } },
          onMoveDown?.let { move -> CustomAccessibilityAction("Move down") { move(); true } },
        )
      }
      .padding(start = 8.dp, end = SCRUBBER_WIDTH),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      row.number?.toString().orEmpty(),
      style = MaterialTheme.typography.labelLarge,
      color = colors.onSurfaceVariant,
      textAlign = TextAlign.End,
      maxLines = 1,
      modifier = Modifier.width(POSITION_WIDTH).clearAndSetSemantics {},
    )
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(row.title, style = MaterialTheme.typography.titleSmall)
      Text(
        listOfNotNull(if (row.count == 1) "1 kept post stays" else "${row.count} kept posts stay", kept).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
      )
      if (fold != null) FoldLine(fold, blurAdult, onToggleFold, Modifier.padding(top = 4.dp).fillMaxWidth())
    }
    if (handle != null) {
      Box(handle.size(48.dp).semantics { contentDescription = "Move" }, contentAlignment = Alignment.Center) {
        Icon(drag_handle, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
      }
    } else {
      Spacer(Modifier.width(12.dp))
    }
  }
}

private fun LazyListScope.inlineUndoRow(closedTab: ClosedTab, firstShow: (Long) -> Boolean, onUndo: (ClosedTab) -> Unit) {
  item(key = rowKey(QueueRow.Inline(closedTab))) {
    UndoRow(
      closedTab,
      firstShow = firstShow,
      onUndo = { onUndo(closedTab) },
      modifier = Modifier.animateItem().padding(horizontal = 12.dp, vertical = 4.dp),
    )
  }
}

private class SourceRow(val preview: SourcePreview?, val picture: (QueueThumb) -> PostImage, val dot: Color?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueItemRow(
  row: QueueRow.Item,
  kept: String?,
  source: SourceRow?,
  onToggleFold: () -> Unit,
  total: Int,
  selected: Boolean,
  sharing: Boolean,
  seen: Boolean,
  onMarkUnseen: () -> Unit,
  rating: Rating?,
  blurAdult: Boolean,
  onOpen: () -> Unit,
  onClose: () -> Unit,
  closable: Boolean,
  onMoveUp: (() -> Unit)?,
  onMoveDown: (() -> Unit)?,
  handle: Modifier?,
  closeLabel: String,
  modifier: Modifier,
) {
  val tab = row.tab
  val number = row.shownNumber
  val closeTab by rememberUpdatedState(onClose)
  val swipe = rememberSwipeToDismissBoxState(
    confirmValueChange = { value -> (value == SwipeToDismissBoxValue.EndToStart).also { if (it) closeTab() } },
    positionalThreshold = { distance -> distance / 2 },
  )
  val colors = MaterialTheme.colorScheme
  val primary = colors.primary
  val lineColor = colors.outlineVariant
  var menu by remember { mutableStateOf(false) }
  SwipeToDismissBox(
    state = swipe,
    modifier = modifier,
    enableDismissFromStartToEnd = false,
    enableDismissFromEndToStart = closable,
    backgroundContent = {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.fillMaxHeight().width(ROW_HEIGHT).background(colors.error), contentAlignment = Alignment.Center) {
          Icon(close, contentDescription = null, tint = colors.onError)
        }
      }
    },
  ) {
    val fold = row.fold
    Row(
      Modifier
        .fillMaxWidth()
        .heightIn(min = ROW_HEIGHT)
        .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
        .drawBehind { if (selected) drawRect(primary, size = Size(4.dp.toPx(), size.height)) }
        .drawBehind {
          val headBottom = if (source != null || fold != null) size.height else size.height / 2 + (THUMBNAIL_SIZE / 2).toPx()
          blockLine(row.line, lineColor, headBottom = headBottom)
        }
        .combinedClickable(
          onClickLabel = "Show",
          onLongClickLabel = if (seen) MARK_UNSEEN else null,
          onLongClick = if (seen) ({ menu = true }) else null,
          onClick = onOpen,
        )
        .semantics {
          customActions = listOfNotNull(
            CustomAccessibilityAction(closeLabel) { closeTab(); true }.takeIf { closable },
            if (seen) CustomAccessibilityAction(MARK_UNSEEN) { onMarkUnseen(); true } else null,
            onMoveUp?.let { move -> CustomAccessibilityAction("Move up") { move(); true } },
            onMoveDown?.let { move -> CustomAccessibilityAction("Move down") { move(); true } },
          )
        }
        .padding(start = 8.dp, end = SCRUBBER_WIDTH, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        number?.toString().orEmpty(),
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.widthIn(min = POSITION_WIDTH).then(
          if (number != null) Modifier.semantics { contentDescription = "Item $number of $total" } else Modifier,
        ),
      )
      if (source != null) {
        if (tab.mosaic != null && isStartedSource(tab)) {
          QueueThumbnail(
            top = tab.top,
            mosaic = tab.mosaic?.thumbs,
            blurred = { thumb -> blurAdult && (thumb?.isAdult ?: row.adult) },
            size = THUMBNAIL_SIZE,
            sharing = sharing,
          )
        }
        SourceRowContent(tab, source, selected, rating, blurAdult, row.adult, fold, onToggleFold, Modifier.weight(1f), kept)
      } else {
        Column(Modifier.weight(1f)) {
          Row(
            Modifier.fillMaxWidth().padding(start = if (row.indented) INDENT else 0.dp).alpha(if (seen && !tab.later) SEEN_ALPHA else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            QueueThumbnail(
              top = tab.top,
              mosaic = tab.mosaic?.thumbs,
              blurred = { thumb -> blurAdult && (thumb?.isAdult ?: row.adult) },
              size = if (row.indented) KEPT_THUMBNAIL_SIZE else THUMBNAIL_SIZE,
              sharing = sharing,
            )
            Box(Modifier.weight(1f)) {
              RowText(
                tab.title,
                tab.untitled,
                listOfNotNull(queueSubtitle(tab, selected, seen, rating, from = row.from.takeUnless { row.indented }), kept).joinToString(" · "),
                Modifier,
                spokenKind = row.from?.let { queueSubtitle(tab, selected, seen, rating, from = it) },
              )
              GroupedMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuGroup(0, 1) {
                  MenuAction(
                    text = { Text(MARK_UNSEEN) },
                    onClick = {
                      menu = false
                      onMarkUnseen()
                    },
                  )
                }
              }
            }
          }
          if (fold != null) FoldLine(fold, blurAdult, onToggleFold, Modifier.padding(top = 4.dp, bottom = 4.dp).fillMaxWidth())
        }
      }
      if (handle != null) {
        Box(handle.size(48.dp).semantics { contentDescription = "Move" }, contentAlignment = Alignment.Center) {
          Icon(drag_handle, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
      } else {
        Spacer(Modifier.width(12.dp))
      }
    }
  }
}

@Composable
private fun SourceRowContent(
  tab: TabState,
  source: SourceRow,
  selected: Boolean,
  rating: Rating?,
  blurAdult: Boolean,
  adult: Boolean,
  fold: Fold?,
  onToggleFold: () -> Unit,
  modifier: Modifier,
  kept: String? = null,
) {
  val new = isNewSource(tab)
  val progress = SourceProgress(position = tab.place?.index?.plus(1), size = source.preview?.size)
  Column(modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      if (new && !selected) NewLabel()
      source.dot?.let { TagDot(it) }
      Text(
        tab.title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = rowTextLines(),
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
    }
    Text(
      listOfNotNull(sourceLine(tab, progress, selected, rating), kept).joinToString(" · "),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = rowTextLines(),
      overflow = TextOverflow.Ellipsis,
    )
    if (new && source.preview?.foundNothing != true) {
      PreviewStrip(
        source.preview?.thumbs,
        source.picture,
        blurred = { thumb -> blurAdult && (thumb.isAdult || adult) },
        Modifier.padding(top = 5.dp),
      )
    }
    progressFraction(progress)?.let { ProgressLine(it, Modifier.padding(top = 5.dp)) }
    if (fold != null) FoldLine(fold, blurAdult, onToggleFold, Modifier.padding(top = 4.dp).fillMaxWidth())
  }
}

@Composable
private fun QueueThumbnail(
  top: Route?,
  mosaic: List<QueueThumb>?,
  blurred: (QueueThumb?) -> Boolean,
  size: Dp,
  sharing: Boolean,
) {
  var anyBlurred = false
  Box(
    Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    val rowVisibility = LocalQueuePageVisibility.current
    val target = rowVisibility?.transition?.targetState
    val arrivals = remember { intArrayOf(0) }
    val lastTarget = remember { arrayOfNulls<EnterExitState>(1) }
    if (target != lastTarget[0]) {
      if (target == EnterExitState.Visible) arrivals[0]++
      lastTarget[0] = target
    }
    key(arrivals[0]) {
      if (mosaic.isNullOrEmpty()) {
        val lone = top?.isLone == true
        val blur = lone && blurred(null)
        anyBlurred = blur
        val kept = (top as? Route.Post)?.takeIf { it.md5 != null }
        Box(
          (if (kept == null) Modifier else Modifier.sharedPicture(kept.id, rowVisibility, enabled = sharing && !blur, slow = false, clip = RoundedCornerShape(12.dp)))
            .then(if (blur) Modifier.blur(BLUR).testTag(BLURRED_TAG) else Modifier),
        ) {
          if (kept != null) KeptPicture(kept, size) else TabIcon(top, size = if (lone) size else 24.dp, decorative = true)
        }
      } else {
        val cell = (size - 2.dp) / 2
        val mosaicBlurred = mosaic.any(blurred)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          mosaic.chunked(2).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
              row.forEachIndexed { c, thumb ->
                val blur = blurred(thumb)
                if (blur) anyBlurred = true
                val tag = if (blur) BLURRED_TAG else if (sharing) mosaicThumbTag(r * 2 + c) else null
                PreviewImage(
                  thumb.image,
                  Modifier
                    .sharedMosaicThumb(thumb.md5, rowVisibility, enabled = sharing && !mosaicBlurred, clip = RoundedCornerShape(6.dp), cell = false)
                    .size(cell)
                    .then(if (blur) Modifier.blur(BLUR) else Modifier)
                    .then(if (tag != null) Modifier.testTag(tag) else Modifier),
                )
              }
            }
          }
        }
      }
    }
    if (anyBlurred) {
      Box(
        Modifier.size(24.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(visibility_off, contentDescription = "Blurred", tint = Color.White, modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
private fun KeptPicture(post: Route.Post, size: Dp) {
  val context = LocalPlatformContext.current
  val md5 = post.md5
  val preview = remember(md5) {
    md5?.let { SingletonImageLoader.get(context).memoryCache?.get(MemoryCache.Key(PostImage.cacheKey(it, Variant.Preview)))?.image }
  }
  if (preview == null) return TabIcon(post, size = size, decorative = true)
  Image(
    preview.asPainter(context),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier.size(size).clip(RoundedCornerShape(4.dp)),
  )
}

@Composable
private fun RecentlyClosedRow(row: ClosedRow, blurAdult: Boolean, sharing: Boolean, onReopen: () -> Unit, modifier: Modifier) {
  val closedTab = row.closed
  Row(
    modifier
      .fillMaxWidth()
      .heightIn(min = ROW_HEIGHT)
      .clickable(onClickLabel = "Reopen", onClick = onReopen)
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    QueueThumbnail(
      top = row.shown.top,
      mosaic = row.shown.mosaic?.thumbs,
      blurred = { thumb -> blurAdult && (thumb?.isAdult ?: row.adult) },
      size = THUMBNAIL_SIZE,
      sharing = sharing,
    )
    val kind = when {
      closedTab.cleared -> "Cleared"
      closedTab.size == 1 -> kindOf(closedTab.top)
      else -> "Closed together"
    }
    RowText(closedTab.title, closedTab.untitled, kind, Modifier.weight(1f))
  }
}

@Composable
private fun RowText(title: String, untitled: Boolean, kind: String, modifier: Modifier, spokenKind: String? = null) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      title,
      color = if (untitled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = rowTextLines(),
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      kind,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = rowTextLines(),
      overflow = TextOverflow.Ellipsis,
      modifier = if (spokenKind != null) Modifier.semantics { contentDescription = spokenKind } else Modifier,
    )
  }
}

private fun kindOf(route: Route?): String = when (route) {
  is Route.Post -> "Post"
  is Route.Grid -> "Grid"
  is Route.Books, is Route.BookSeries -> "Books"
  is Route.Book -> "Book"
  else -> "Item"
}

private val ROW_HEIGHT = 72.dp

@Composable
internal fun rowTextLines(): Int = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) Int.MAX_VALUE else 1
private val THUMBNAIL_SIZE = 48.dp
private val KEPT_THUMBNAIL_SIZE = 40.dp

private val INDENT = 28.dp

private val BLOCK_LINE_X get() = 8.dp + POSITION_WIDTH + 10.dp + INDENT / 2
private val BLOCK_LINE_WIDTH = 2.dp

private val CLOSED_ROW_TEXT_BOTTOM = 6.dp

private fun DrawScope.blockLine(line: BlockLine?, color: Color, headBottom: Float) {
  val (top, bottom) = when (line ?: return) {
    BlockLine.Head -> headBottom to size.height
    BlockLine.Through -> 0f to size.height
    BlockLine.End -> 0f to size.height / 2
  }
  val width = BLOCK_LINE_WIDTH.toPx()
  val start = BLOCK_LINE_X.toPx() - width / 2
  val x = if (layoutDirection == LayoutDirection.Rtl) size.width - start - width else start
  drawRect(color, topLeft = Offset(x, top), size = Size(width, bottom - top))
}

internal const val SEEN_ALPHA = 0.6f

private const val MARK_UNSEEN = "Mark unseen"

private val BLUR = 10.dp

internal const val BLURRED_TAG = "blurred"

internal fun mosaicThumbTag(index: Int) = "mosaic thumb $index"

private val BOTTOM_LINE_ROOM = 72.dp

internal val SIDE_COLUMN_MAX_HEIGHT = 480.dp

internal val SIDE_COLUMN_MIN_WIDTH = 600.dp

private val SIDE_COLUMN_WIDTH = 300.dp

internal const val SIDE_COLUMN_TAG = "queue side column"

internal const val QUEUE_PAGE_LIST_TAG = "queue page list"

internal const val MORE_ABOVE_TAG = "more above"

internal const val QUEUE_ACTIONS_TAG = "queue actions"

private val POSITION_WIDTH = 28.dp

private const val ROWS_ABOVE_CURRENT = 2

private const val ANNOUNCEMENT_MILLIS = 3_000L
private const val CLOSED_HEADER = "closed-header"
private const val CLOSED_HIDDEN = "closed-hidden-by-g-only"
private const val CLOSED_PREFIX = "closed-"
private const val NO_MATCH = "closed-no-match"
private const val HIDDEN_BY_G_ONLY = "hidden-by-g-only"

private const val BLOCK_PREFIX = "closed-source-"

private const val INLINE_PREFIX = "inline-"

private fun rowKey(row: QueueRow): Any = when (row) {
  is QueueRow.Item -> row.tab.id.value
  is QueueRow.ClosedBlock -> "$BLOCK_PREFIX${row.head.value}"
  is QueueRow.Inline -> "$CLOSED_PREFIX$INLINE_PREFIX${row.closed.key}"
  is QueueRow.HiddenByGOnly -> HIDDEN_BY_G_ONLY
}

@Composable
private fun Modifier.lifted(reorder: Reorder, list: LazyListState, held: Boolean): Modifier {
  val page = MaterialTheme.colorScheme.surfaceContainerLow
  return zIndex(1f)
    .then(if (held) Modifier.onPlaced { reorder.placed(it.positionInParent().y) } else Modifier)
    .graphicsLayer {
      translationY = reorder.offset(list)
      shadowElevation = DRAGGED_ELEVATION.toPx()
    }
    .background(page)
}

private val DRAGGED_ELEVATION = 6.dp

private val DRAG_SCROLL_EDGE = 64.dp
private val DRAG_SCROLL_SPEED = 1200.dp

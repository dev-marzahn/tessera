package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import dev.marzahn.sankaku.ui.components.ButtonHint
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.stateDescription
import dev.marzahn.sankaku.queue.SeenStore
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import com.google.fonts.icons.add
import com.google.fonts.icons.close
import com.google.fonts.icons.favorite_filled
import com.google.fonts.icons.grid_view
import com.google.fonts.icons.image
import com.google.fonts.icons.more_vert
import com.google.fonts.icons.menu_book
import dev.marzahn.sankaku.isBooks
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.images.PostImage
import dev.marzahn.sankaku.images.cachedPostImageRequest
import dev.marzahn.sankaku.ui.components.GroupedMenu
import dev.marzahn.sankaku.ui.components.KbdBadge
import dev.marzahn.sankaku.ui.components.MenuAction
import dev.marzahn.sankaku.ui.components.MenuGroup
import dev.marzahn.sankaku.ui.components.ReopenTabBadge
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.rememberPulse
import dev.marzahn.sankaku.ui.openOnClick
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds

private val TabShape = RoundedCornerShape(8.dp)

private val WheelStep = 10.dp

@Composable
fun TabStrip(
  modifier: Modifier = Modifier,
  controller: TabsController = koinInject(),
  clearViewed: ClearViewed = koinInject(),
  tabPosts: TabPosts = koinInject(),
  seen: SeenStore? = null,
  onNewTab: () -> Unit = {},
  onShowAll: (() -> Unit)? = null,
  onShortcuts: (() -> Unit)? = null,
) {
  val tabs by controller.tabs.collectAsStateWithLifecycle()
  val seenRevision = seen?.revision?.collectAsStateWithLifecycle()?.value ?: 0
  val stripBlocks = stripBlocks(tabs)
  val tones = blockTones()
  val active by controller.active.collectAsStateWithLifecycle()
  val postIds = remember(tabs) { tabs.mapNotNullTo(mutableSetOf()) { (it.top as? Route.Post)?.id } }
  val posts by remember(tabPosts, postIds) { tabPosts.of(postIds) }.collectAsStateWithLifecycle(emptyMap())
  val scroll = rememberScrollState()
  val spans = remember { mutableStateMapOf<TabId, IntRange>() }
  var viewport by remember { mutableIntStateOf(0) }

  var content by remember { mutableIntStateOf(0) }
  var room by remember { mutableIntStateOf(0) }
  var available by remember { mutableIntStateOf(0) }
  val density = LocalDensity.current
  val tabMax = with(density) {
    val shared = sharedTabWidth(available.toDp(), tabs.size, stripBlocks.count { it.startsBlock } - 1)
    if (content > room && viewport > 0) minOf(shared, viewport.toDp() - StripStartGap) else shared
  }
  val focusers = remember { mutableMapOf<TabId, FocusRequester>() }
  LaunchedEffect(tabs) { focusers.keys.retainAll(tabs.map { it.id }.toSet()) }
  val drag = rememberDraggableState { delta -> scroll.dispatchRawDelta(-delta) }
  val closed by controller.closed.collectAsStateWithLifecycle()
  var pulse by remember { mutableStateOf(Pulse(emptySet(), 0)) }
  LaunchedEffect(controller) { controller.reopened.collect { pulse = Pulse(it, pulse.serial + 1) } }
  val twins by remember(tabs) { derivedStateOf { twinSuffixes(tabs.map { it.title }) } }

  LaunchedEffect(active, spans[active], viewport) { spans[active]?.let { scroll.bringIntoView(it, viewport) } }

  Row(
    modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    val overflows = content > room
    val (before, after) =
      if (overflows) hiddenCounts(tabs.mapNotNull { spans[it.id] }, scroll.value, viewport) else 0 to 0
    Row(
      Modifier
        .weight(1f, fill = false)
        .layout { measurable, constraints ->
          available = constraints.maxWidth
          val placeable = measurable.measure(constraints)
          layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        .onSizeChanged { room = it.width },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (overflows) {
        HiddenCountSlot(before, widest = tabs.size, Alignment.CenterStart, onShowAll, before = true, Modifier.testTag(HIDDEN_BEFORE_TAG))
      }
      Box(Modifier.weight(1f, fill = false).testTag(TABS_VIEWPORT_TAG).onSizeChanged { size: IntSize -> viewport = size.width }) {
        Row(
          Modifier
            .horizontalScroll(scroll)
            .onSizeChanged { content = it.width }
            .pointerInput(scroll) {
              awaitPointerEventScope {
                while (true) {
                  val event = awaitPointerEvent()
                  if (event.type != PointerEventType.Scroll) continue
                  val change = event.changes.firstOrNull() ?: continue
                  val delta = change.scrollDelta
                  if (abs(delta.y) <= abs(delta.x)) continue
                  scroll.dispatchRawDelta(delta.y * WheelStep.toPx())
                  change.consume()
                }
              }
            }
            .draggable(drag, Orientation.Horizontal)
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
            .focusProperties { onEnter = { focusers[active]?.requestFocus() } }
            .focusGroup()
            .semantics { selectableGroup() },
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          tabs.forEachIndexed { index, tab ->
            key(tab.id) {
              val place = stripBlocks.getOrNull(index)
              Tab(
                tab,
                twins[index],
                selected = tab.id == active,
                favourited = (tab.top as? Route.Post)?.let { posts[it.id]?.favourited } == true,
                controller,
                pulse = pulse.serial.takeIf { tab.id in pulse.tabs },
                seen = seen != null && seenRevision >= 0 && tab.kind.isKept && tab.seenSinceKept(seen),
                underline = place?.takeIf { it.underlined }?.let { tones[it.block % tones.size] },
                joinsNext = place?.joinsNext == true,
                maxWidth = tabMax,
                focus = focusers.getOrPut(tab.id) { FocusRequester() },
                modifier = Modifier
                  .padding(start = if (place?.startsBlock == true && index > 0) BlockGap else 0.dp)
                  .onPlaced {
                    val x = it.positionInParent().x.toInt()
                    spans[tab.id] = x until x + it.size.width
                  },
              )
            }
          }
        }
      }
      if (overflows) {
        HiddenCountSlot(after, widest = tabs.size, Alignment.CenterEnd, onShowAll, before = false, Modifier.testTag(HIDDEN_AFTER_TAG))
      }
    }
    ReopenTabBadge(canReopen = closed.isNotEmpty(), onReopen = controller::reopenClosed) {
      ButtonHint(NEW_TAB_LABEL) {
        IconButton(onClick = onNewTab) {
          Icon(add, contentDescription = NEW_TAB_LABEL)
        }
      }
    }
    StripMenu(viewed = { clearViewed.status().count }, onClearViewed = { clearViewed() }, onShortcuts = onShortcuts)
  }
}

@Composable
private fun StripMenu(viewed: () -> Int, onClearViewed: () -> Unit, onShortcuts: (() -> Unit)?) {
  var open by remember { mutableStateOf(false) }
  val groups = if (onShortcuts != null) 2 else 1
  Box {
    ButtonHint(MORE_LABEL) { IconButton(onClick = { open = true }) { Icon(more_vert, contentDescription = MORE_LABEL) } }
    GroupedMenu(expanded = open, onDismissRequest = { open = false }) {
      MenuGroup(0, groups) {
        val count = viewed()
        MenuAction(
          text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              Text("Clear viewed · $count", modifier = Modifier.semantics { contentDescription = clearViewedLabel(count) })
              KbdBadge(Shortcuts.ClearViewed.label)
            }
          },
          enabled = count > 0,
          onClick = {
            open = false
            onClearViewed()
          },
        )
      }
      if (onShortcuts != null) {
        MenuGroup(1, groups) {
          MenuAction(
            text = {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(SHORTCUTS_ITEM, Modifier.weight(1f, fill = false))
                KbdBadge(Shortcuts.Help.label)
              }
            },
            onClick = {
              open = false
              onShortcuts()
            },
          )
        }
      }
    }
  }
}

internal const val SHORTCUTS_ITEM = "Keyboard shortcuts"

private suspend fun ScrollState.bringIntoView(span: IntRange, width: Int) {
  if (width <= 0) return
  val target = when {
    span.first < value -> span.first
    span.last >= value + width -> span.last + 1 - width
    else -> return
  }
  animateScrollTo(target.coerceIn(0, maxValue))
}

internal fun mostlyHidden(span: IntRange, scrolled: Int, viewport: Int): Boolean {
  val shown = minOf(span.last + 1, scrolled + viewport) - maxOf(span.first, scrolled)
  return shown * 2 < span.last + 1 - span.first
}

internal fun hiddenCounts(spans: List<IntRange>, scrolled: Int, viewport: Int): Pair<Int, Int> {
  if (viewport <= 0) return 0 to 0
  val hidden = spans.filter { mostlyHidden(it, scrolled, viewport) }
  val before = hidden.count { it.first < scrolled }
  return before to hidden.size - before
}

internal fun twinSuffixes(titles: List<String>): List<String> {
  var run = 0
  return titles.mapIndexed { index, title ->
    run = if (index > 0 && titles[index - 1] == title) run + 1 else 1
    if (run == 1) "" else " · $run"
  }
}

@Composable
internal fun TwinTitle(title: String, twin: String, untitled: Boolean, maxLines: Int, modifier: Modifier = Modifier) {
  val color = if (untitled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
  val style = MaterialTheme.typography.labelLarge
  Row(modifier, verticalAlignment = Alignment.Bottom) {
    Text(
      title,
      color = color,
      style = style,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false).testTag(TAB_TITLE_TAG),
    )
    if (twin.isNotEmpty()) Text(twin, color = color, style = style, maxLines = 1, modifier = Modifier.testTag(TAB_TWIN_TAG))
  }
}

internal const val HIDDEN_BEFORE_TAG = "tabs-hidden-before"
internal const val HIDDEN_AFTER_TAG = "tabs-hidden-after"
internal const val TAB_TITLE_TAG = "tab-title"
internal const val TAB_TWIN_TAG = "tab-twin"
internal const val TABS_VIEWPORT_TAG = "tabs-viewport"

@Composable
private fun HiddenCountSlot(
  count: Int,
  widest: Int,
  alignment: Alignment,
  onShowAll: (() -> Unit)?,
  before: Boolean,
  modifier: Modifier = Modifier,
) {
  Box(modifier.padding(horizontal = 4.dp), contentAlignment = alignment) {
    HiddenCount(widest, Modifier.alpha(0f).clearAndSetSemantics {})
    if (count > 0) {
      val name = hiddenCountLabel(count, before)
      ButtonHint(SHOW_ALL_TABS_HINT.takeIf { onShowAll != null }) {
        HiddenCount(
          count,
          if (onShowAll == null) {
            Modifier
          } else {
            Modifier
              .clip(CircleShape)
              .clickable(role = Role.Button, onClickLabel = SHOW_ALL_TABS, onClick = onShowAll)
              .clearAndSetSemantics { contentDescription = name }
          },
        )
      }
    }
  }
}

internal fun hiddenCountLabel(count: Int, before: Boolean): String =
  "$count more ${if (count == 1) "tab" else "tabs"} ${if (before) "before" else "after"}"

internal const val SHOW_ALL_TABS = "Show all tabs"

internal val SHOW_ALL_TABS_HINT = "Show all tabs (${Shortcuts.TabSwitcher.label})"

internal val NEW_TAB_LABEL = "New tab (${Shortcuts.NewTab.label})"

internal const val MORE_LABEL = "More"

internal val TabMinWidth = 120.dp

internal val TabMaxWidth = 240.dp

internal fun sharedTabWidth(available: Dp, count: Int, blockGaps: Int): Dp {
  if (count <= 0 || available <= 0.dp) return TabMaxWidth
  val room = available - StripStartGap - TabGap * (count - 1) - BlockGap * blockGaps.coerceAtLeast(0)
  val share = floor((room / count).value).dp
  return if (share < TabMinWidth) TabMaxWidth else share.coerceAtMost(TabMaxWidth)
}

private val StripStartGap = 8.dp

@Composable
private fun HiddenCount(count: Int, modifier: Modifier = Modifier) {
  Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp, modifier = modifier) {
    Text(
      "+$count",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
    )
  }
}

private data class Pulse(val tabs: Set<TabId>, val serial: Int)

private const val PULSE_SCALE = 1.08f

private val TabGap = 4.dp

private val FocusRingWidth = 2.dp

internal const val CLOSE_TAB = "Close tab"

private val BlockGap = 6.dp

private val UnderlineWidth = 3.dp

private val UnderlineOffset = 2.dp


@Composable
private fun blockTones(): List<Color> = MaterialTheme.colorScheme.run { listOf(primary, tertiary, secondary, outline) }

@Composable
private fun Tab(
  tab: TabState,
  twin: String,
  selected: Boolean,
  favourited: Boolean,
  controller: TabsController,
  pulse: Int?,
  seen: Boolean = false,
  underline: Color? = null,
  joinsNext: Boolean = false,
  maxWidth: Dp = TabMaxWidth,
  focus: FocusRequester = remember { FocusRequester() },
  modifier: Modifier = Modifier,
) {
  val swell = rememberPulse(pulse, PULSE_SCALE)
  var focused by remember { mutableStateOf(false) }
  val closable = controller.canClose(tab)
  val name = tab.title + twin
  val isActive = selected
  val gap = with(LocalDensity.current) { TabGap.toPx() }
  val interaction = remember { MutableInteractionSource() }
  val hovered by interaction.collectIsHoveredAsState()
  var previewing by remember { mutableStateOf(false) }
  LaunchedEffect(hovered) {
    if (hovered) delay(PREVIEW_DELAY)
    previewing = hovered
  }
  Surface(
    shape = TabShape,
    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    modifier = modifier
      .drawBehind {
        if (underline == null) return@drawBehind
        val y = size.height + UnderlineOffset.toPx()
        val end = size.width + if (joinsNext) gap else 0f
        drawLine(underline, Offset(0f, y), Offset(end, y), strokeWidth = UnderlineWidth.toPx())
      }
      .alpha(if (seen) SEEN_ALPHA else 1f)
      .semantics(mergeDescendants = true) {
        contentDescription = name
        role = Role.Tab
        this.selected = isActive
        tabStates(seen, favourited)?.let { stateDescription = it }
        if (closable) customActions = listOf(CustomAccessibilityAction(CLOSE_TAB) { controller.close(tab.id); true })
      }
      .graphicsLayer {
        scaleX = swell.value
        scaleY = swell.value
      }
      .height(36.dp)
      .widthIn(min = TabMinWidth, max = maxOf(TabMinWidth, maxWidth))
      .hoverable(interaction)
      .border(
        width = if (focused) FocusRingWidth else 0.dp,
        color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = TabShape,
      )
      .clip(TabShape)
      .focusRequester(focus)
      .onFocusChanged { focused = it.isFocused }
      .openOnClick(onOpen = { controller.jumpTo(tab.id) }, onOpenInNewTab = { controller.close(tab.id) }),
  ) {
    if (previewing) TabPreview(tab, twin)
    Row(Modifier.padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Box {
        TabIcon(tab.top, decorative = true)
        if (favourited) FavouritedMark(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp), spoken = false)
      }
      Spacer(Modifier.width(8.dp))
      TwinTitle(tab.title, twin, tab.untitled, maxLines = 1, Modifier.weight(1f))
      Spacer(Modifier.width(8.dp))
      if (closable) {
        Box(
          Modifier
            .size(16.dp)
            .clip(CircleShape)
            .focusProperties { canFocus = false }
            .openOnClick({ controller.close(tab.id) }, { controller.close(tab.id) }),
          contentAlignment = Alignment.Center,
        ) {
          Icon(close, contentDescription = CLOSE_TAB, modifier = Modifier.size(14.dp))
        }
      } else {
        Spacer(Modifier.size(16.dp))
      }
    }
  }
}

@Composable
internal fun FavouritedMark(modifier: Modifier, spoken: Boolean = true) {
  Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, modifier = modifier) {
    Icon(
      favorite_filled,
      contentDescription = FAVOURITED_TAB.takeIf { spoken },
      modifier = Modifier.padding(2.dp).size(8.dp).testTag(FAVOURITED_MARK_TAG),
    )
  }
}

internal const val FAVOURITED_MARK_TAG = "favourited-mark"

internal fun tabStates(seen: Boolean, favourited: Boolean): String? =
  listOfNotNull("Seen".takeIf { seen }, FAVOURITED_TAB.takeIf { favourited }).joinToString(", ").ifEmpty { null }

internal const val FAVOURITED_TAB = "Favourited"

@Composable
internal fun TabIcon(route: Route?, size: Dp = 18.dp, decorative: Boolean = false, picture: Boolean = true) {
  val iconModifier = Modifier.size(size)
  val picture = if (!picture) null else when (route) {
    is Route.Post -> route.md5?.let { PostImage(route.id, it, Variant.Preview, null) }
    is Route.Book -> route.cover?.let { PostImage(route.id, it, Variant.Preview, null) }
    else -> null
  }
  val thumbnail = picture?.let { rememberAsyncImagePainter(cachedPostImageRequest(LocalPlatformContext.current, it)) }
  val failed = thumbnail?.state?.collectAsState()?.value is AsyncImagePainter.State.Error
  when {
    thumbnail != null && !failed -> Image(
      thumbnail,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = iconModifier.clip(RoundedCornerShape(4.dp)),
    )
    route is Route.Post -> Icon(image, contentDescription = "Post".takeUnless { decorative }, modifier = iconModifier)
    route?.isBooks == true || route is Route.Book -> Icon(menu_book, contentDescription = BOOKS_TITLE.takeUnless { decorative }, modifier = iconModifier)
    else -> Icon(grid_view, contentDescription = "Grid".takeUnless { decorative }, modifier = iconModifier)
  }
}

private val PREVIEW_DELAY = 600.milliseconds

private val PreviewWidth = 264.dp

@Composable
private fun TabPreview(tab: TabState, twin: String, imageLoader: ImageLoader = koinInject()) {
  val contents = remember(tab) { tab.topContents() }
  val bytes = remember(tab) {
    tab.allContents().takeIf { it.isNotEmpty() }
      ?.let { estimateTabBytes(it, imageLoader.memoryCache?.decodedBytes().orEmpty()) }
  }
  val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
  Popup(popupPositionProvider = remember(gap) { BelowAnchor(gap) }, properties = PopupProperties(focusable = false)) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shadowElevation = 6.dp,
      modifier = Modifier.width(PreviewWidth),
    ) {
      Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TwinTitle(tab.title, twin, tab.untitled, maxLines = 2, Modifier.padding(horizontal = 4.dp))
        val images = contents?.preview.orEmpty()
        when {
          images.isEmpty() -> Unit
          tab.top is Route.Post -> PreviewImage(images.first(), Modifier.fillMaxWidth().height(160.dp))
          else -> images.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              row.forEach { PreviewImage(it, Modifier.weight(1f).aspectRatio(1f)) }
              repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
          }
        }
        if (bytes != null) {
          Text(
            "Memory usage ≈ ${formatBytes(bytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
          )
        }
      }
    }
  }
}

@Composable
internal fun PreviewImage(image: PostImage, modifier: Modifier) {
  Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
    AsyncImage(
      model = cachedPostImageRequest(LocalPlatformContext.current, image),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.matchParentSize(),
    )
  }
}

private class BelowAnchor(private val gap: Int) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ) = IntOffset(
    anchorBounds.left.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)),
    anchorBounds.bottom + gap,
  )
}

package dev.marzahn.sankaku.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.fonts.icons.style
import com.google.fonts.icons.subdirectory_arrow_right
import dev.marzahn.sankaku.PlatformBackHandler
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.ui.components.PrimaryIconButton
import dev.marzahn.sankaku.ui.components.TextActionButton
import dev.marzahn.sankaku.ui.theme.color
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.mp.KoinPlatform

@Composable
internal fun FindRow(
  tab: TabState,
  line: String,
  blurAdult: Boolean,
  modifier: Modifier = Modifier,
  onOpen: (() -> Unit)? = null,
  tabs: TabsController = koinInject(),
  previews: SourcePreviews? = koinOrNull(),
  gOnly: Boolean = false,
) {
  var loads by remember(tab.id) { mutableIntStateOf(0) }
  if (previews != null) {
    LaunchedEffect(tab.id, tab.top, gOnly) {
      previews.load(tab)
      loads++
    }
  }
  val preview = if (previews != null) previews.shown(tab, gOnly).takeIf { loads >= 0 } else tab.preview
  val dot = (tab.top as? Route.Grid)?.let { oneTag(it.tags) }?.let { tabs.tagType(it.lowercase()) }?.color
  val adult = isAdult(tab.top) { null }
  Column(
    modifier
      .fillMaxWidth()
      .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .testTag(FIND_ROW),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      dot?.let { TagDot(it) }
      Text(tab.title, style = MaterialTheme.typography.bodyLarge, maxLines = rowTextLines(), overflow = TextOverflow.Ellipsis)
    }
    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = rowTextLines())
    if (preview?.foundNothing != true) {
      PreviewStrip(
        preview?.thumbs,
        picture = { previews?.picture(it) ?: it.image },
        blurred = { thumb -> blurAdult && (thumb.isAdult || adult) },
        modifier = Modifier.padding(top = 5.dp),
      )
    }
  }
}

internal fun findLine(tab: TabState, item: Int?): String = listOfNotNull(
  tab.foundOn?.let(::foundWayText),
  item?.let { "item $it" },
  tab.preview?.size?.let { sizeWords(it, alone = true) },
).joinToString(" · ")

@Composable
fun FoundHereChip(
  tab: TabState?,
  route: Route.Grid,
  visible: Boolean,
  modifier: Modifier = Modifier,
  touch: Boolean = platformIsTouch,
  tabs: TabsController = koinInject(),
  deck: SortDeck? = sortDeckOrNull(),
  privacy: PrivacySettings? = koinOrNull(),
) {
  if (!touch || tab == null) return
  val list by tabs.tabs.collectAsStateWithLifecycle()
  val gOnly = privacy?.gOnly?.collectAsStateWithLifecycle()?.value == true
  if (tab.top != route || tab.kind != QueueKind.Source) return
  val search = sourceSearch(route) ?: return
  val finds = remember(list, search, gOnly) { tabs.findsOf(search).filter { !(gOnly && isAdult(it.top) { null }) } }
  var open by remember { mutableStateOf(false) }
  AnimatedVisibility(visible && finds.isNotEmpty(), modifier, enter = fadeIn(), exit = fadeOut()) {
    FoundHereButton(finds.size) { open = true }
  }
  if (open && finds.isNotEmpty()) {
    FoundHereSheet(tab, finds, onDismiss = { open = false }, tabs = tabs, deck = deck)
  }
}

@Composable
internal fun FoundHereButton(count: Int, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(17.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shadowElevation = 3.dp,
    modifier = Modifier.heightIn(min = 34.dp).testTag(FOUND_HERE_CHIP).semantics { contentDescription = foundHereSpoken(count) },
  ) {
    Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(subdirectory_arrow_right, contentDescription = null, modifier = Modifier.size(18.dp))
      Text("$FOUND_HERE · $count", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoundHereSheet(
  source: TabState,
  finds: List<TabState>,
  onDismiss: () -> Unit,
  tabs: TabsController = koinInject(),
  deck: SortDeck? = sortDeckOrNull(),
  privacy: PrivacySettings? = koinOrNull(),
) {
  val sheet = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
  val scope = rememberCoroutineScope()
  var chosen by remember { mutableStateOf(false) }
  fun choose(action: () -> Unit) {
    if (chosen) return
    chosen = true
    scope.launch { sheet.hide() }.invokeOnCompletion {
      onDismiss()
      action()
    }
  }
  val gOnly = privacy?.gOnly?.collectAsStateWithLifecycle()?.value == true
  val list by tabs.tabs.collectAsStateWithLifecycle()
  val name = source.title
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheet,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.semantics { paneTitle = "$FOUND_ON $name" },
  ) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp).testTag(FOUND_HERE_SHEET)) {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("$FOUND_ON $name", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(
          "${searchesWord(finds.size)} opened from here · in the queue",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (deck != null) {
        PrimaryIconButton(
          sortTheseLabel(finds.size),
          style,
          onClick = { choose { deck.startFinds(finds, next = null, then = source.id, foundOn = name) } },
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
      for (find in finds) {
        key(find.id) {
          val index = list.indexOf(find)
          FindRow(
            find,
            findLine(find, index.takeIf { it >= 0 }?.let { countedPosition(list, it) }),
            blurAdult = gOnly,
            onOpen = { choose { tabs.jumpTo(find.id) } },
            tabs = tabs,
            gOnly = gOnly,
          )
        }
      }
    }
  }
}

@Composable
fun BlockFindsCard(
  state: BlockFinds = koinInject(),
  privacy: PrivacySettings? = koinOrNull(),
  tabs: TabsController = koinInject(),
  contentPadding: PaddingValues = PaddingValues(),
) {
  val offer = state.offer ?: return
  tabs.tabs.collectAsStateWithLifecycle().value
  val finds = state.finds
  val gOnly = privacy?.gOnly?.collectAsStateWithLifecycle()?.value == true
  val title = "${finds.size} found on ${offer.name}"
  val nextTitle = state.next?.title
  val notNow = if (nextTitle != null) "$NOT_NOW · next: $nextTitle" else NOT_NOW
  PlatformBackHandler(enabled = true, onBack = state::notNow)
  val focus = remember { FocusRequester() }
  LaunchedEffect(offer) { focus.requestFocus() }
  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .padding(top = contentPadding.calculateTopPadding())
      .background(MaterialTheme.colorScheme.surface)
      .padding(bottom = contentPadding.calculateBottomPadding() + 96.dp)
      .semantics { paneTitle = title },
  ) {
    Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight), contentAlignment = Alignment.TopCenter) {
      Surface(
        modifier = Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth().testTag(BLOCK_FINDS_CARD),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp,
      ) {
        Column(Modifier.padding(top = 22.dp, bottom = 18.dp, start = 8.dp, end = 8.dp)) {
          Text(
            "Leaving ${offer.name}".uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
          )
          Column(
            Modifier
              .padding(horizontal = 12.dp)
              .focusRequester(focus)
              .focusable()
              .semantics(mergeDescendants = true) {
                heading()
                customActions = listOf(
                  CustomAccessibilityAction(SORT_THEM_NOW) { state.sortNow(); true },
                  CustomAccessibilityAction(notNow) { state.notNow(); true },
                )
              },
          ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
            Text(
              "Searches you opened from ${offer.name} and its kept posts, not looked at yet",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
          }
          for (find in finds.take(CARD_FINDS)) {
            key(find.id) { FindRow(find, findLine(find, item = null), blurAdult = gOnly, tabs = tabs, gOnly = gOnly) }
          }
          if (finds.size > CARD_FINDS) {
            Text(
              "and ${finds.size - CARD_FINDS} more",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
          }
          Column(
            Modifier.fillMaxWidth().padding(top = 18.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            PrimaryIconButton(SORT_THEM_NOW, style, onClick = state::sortNow, modifier = Modifier.fillMaxWidth())
            TextActionButton(notNow, onClick = state::notNow)
          }
        }
      }
    }
  }
}

@Composable
private inline fun <reified T : Any> koinOrNull(): T? = remember { KoinPlatform.getKoinOrNull()?.getOrNull<T>() }

private fun searchesWord(count: Int) = if (count == 1) "1 search" else "$count searches"

internal fun sortTheseLabel(count: Int) = if (count == 1) "Sort this one" else "Sort these $count"

internal fun foundHereSpoken(count: Int) = "$FOUND_HERE, ${searchesWord(count)}"

private const val CARD_FINDS = 3

internal const val FOUND_HERE = "Found here"
internal const val SORT_THEM_NOW = "Sort them now"
internal const val NOT_NOW = "Not now"
internal const val FIND_ROW = "Find row"
internal const val FOUND_HERE_CHIP = "Found here chip"
internal const val FOUND_HERE_SHEET = "Found here sheet"
internal const val BLOCK_FINDS_CARD = "Block finds card"

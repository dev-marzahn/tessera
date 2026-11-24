package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.rememberUpdatedState
import com.google.fonts.icons.arrow_back
import dev.marzahn.sankaku.ui.components.QueueMark
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.components.FullScreenSearchBar
import dev.marzahn.sankaku.ui.components.FullScreenSearchState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.ui.components.centredOnLine
import dev.marzahn.sankaku.ui.components.GroupedMenu
import dev.marzahn.sankaku.ui.components.MenuGroup
import dev.marzahn.sankaku.ui.components.AppLoadingIndicator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import dev.marzahn.sankaku.ui.components.effectsSpec
import com.google.fonts.icons.star_filled
import com.google.fonts.icons.star
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.IconButton
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import dev.marzahn.sankaku.ui.components.chooseOnTab
import dev.marzahn.sankaku.ui.components.chosenMenuItem
import dev.marzahn.sankaku.ui.components.sidewaysFocusKeys
import dev.marzahn.sankaku.ui.components.isCommand
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.fonts.icons.close
import com.google.fonts.icons.search
import dev.marzahn.sankaku.api.dto.TagSuggestion
import dev.marzahn.sankaku.data.RecentSearches
import dev.marzahn.sankaku.data.SearchTags
import dev.marzahn.sankaku.data.TagsRepository
import dev.marzahn.sankaku.features.post.compactCount
import dev.marzahn.sankaku.ui.components.KbdBadge
import dev.marzahn.sankaku.ui.components.KeyHint
import dev.marzahn.sankaku.ui.components.PrimaryButton
import dev.marzahn.sankaku.ui.components.MenuCheck
import dev.marzahn.sankaku.ui.components.MenuChoice
import dev.marzahn.sankaku.ui.components.MenuChip
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.ButtonHint
import dev.marzahn.sankaku.ui.components.HoverHint
import dev.marzahn.sankaku.ui.components.TagBadge
import dev.marzahn.sankaku.ui.components.TagBadgeHeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal const val SUGGEST_DELAY_MS = 350L
private val BarHeight = 56.dp
private val TouchBarHeight = 48.dp

@Composable
fun SearchSummary(
  tags: List<String>,
  ratings: Set<Rating>,
  known: Map<String, TagSuggestion>,
  onOpen: () -> Unit,
  modifier: Modifier = Modifier,
  me: String? = null,
  signedOut: Boolean = false,
) {
  Surface(
    onClick = onOpen,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = modifier.heightIn(min = if (platformIsTouch) TouchBarHeight else BarHeight),
  ) {
    Row(
      Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(if (platformIsTouch) 10.dp else 8.dp),
    ) {
      Icon(
        search,
        contentDescription = null,
        tint = if (platformIsTouch) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
        modifier = if (platformIsTouch) Modifier.size(20.dp) else Modifier,
      )
      if (ratings.isNotEmpty()) {
        val described = ratingsDescription(ratings, signedOut)
        val spoken = AnnotatedString(described)
        ButtonHint(described) {
          RatingStack(ratings, size = RatingBadgeSize.Tag, modifier = Modifier.clearAndSetSemantics { text = spoken })
        }
      }
      if (tags.isEmpty()) {
        Text(
          "Search tags",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
      } else {
        OverflowRow(
          overflow = { hidden -> SummaryChip("+$hidden", muted = true) },
          modifier = Modifier.weight(1f),
        ) { tags.forEach { SearchTag(it, known, me) } }
      }
      if (!platformIsTouch) KbdBadge(Shortcuts.Search.label)
    }
  }
}

@Composable
private fun SummaryChip(text: String, muted: Boolean = false) {
  Surface(
    shape = CircleShape,
    color = if (muted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondaryContainer,
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun SearchTag(tag: String, known: Map<String, TagSuggestion>, me: String?) = SearchToken(tag, known, me)

@Composable
internal fun RemovableSearchToken(
  tag: String,
  known: Map<String, TagSuggestion>,
  me: String?,
  onRemove: () -> Unit,
  flagged: Boolean = false,
  touch: Boolean = platformIsTouch,
) = SearchToken(tag, known, me, onRemove, flagged, touch)

@Composable
private fun SearchToken(
  tag: String,
  known: Map<String, TagSuggestion>,
  me: String?,
  onRemove: (() -> Unit)? = null,
  flagged: Boolean = false,
  touch: Boolean = platformIsTouch,
) {
  if (onRemove != null && touch) {
    TouchRemovable("Remove ${tokenName(tag, known, me)}", onRemove) {
      SearchTokenPill(tag, known, me, removable = true, onClick = null, removeLabel = null, flagged = flagged)
    }
  } else {
    SearchTokenPill(tag, known, me, onRemove != null, onRemove, "Remove ${tokenName(tag, known, me)}", flagged)
  }
}

@Composable
internal fun TouchRemovable(removeLabel: String, onRemove: () -> Unit, pill: @Composable () -> Unit) {
  Box(Modifier.heightIn(min = RemoveTarget).padding(end = RemoveTarget - CrossZone), contentAlignment = Alignment.CenterStart) {
    pill()
    Box(
      Modifier
        .align(Alignment.CenterEnd)
        .offset(x = RemoveTarget - CrossZone)
        .size(RemoveTarget)
        .clickable(role = Role.Button, onClick = onRemove, indication = null, interactionSource = null)
        .semantics { contentDescription = removeLabel },
    )
  }
}

private val RemoveTarget = 48.dp

private val CrossZone = 28.dp

@Composable
private fun SearchTokenPill(
  tag: String,
  known: Map<String, TagSuggestion>,
  me: String?,
  removable: Boolean,
  onClick: (() -> Unit)?,
  removeLabel: String?,
  flagged: Boolean,
) {
  val onRemove = onClick
  val remove: (@Composable () -> Unit)? = if (removable) {
    { Icon(close, contentDescription = removeLabel, modifier = Modifier.size(16.dp)) }
  } else {
    null
  }
  val favourites = Favourites.of(tag, me)?.label ?: Favourites.shortLabel(tag)
  val choice = Filter.optionFor(tag)
  val ratings = Rating.of(tag)
  val label: @Composable () -> Unit = when {
    favourites != null -> { { Text(favourites, style = MaterialTheme.typography.labelLarge) } }
    ratings != null -> { { RatingStack(ratings, size = RatingBadgeSize.Small) } }
    choice == null -> return KnownTagBadge(tag, known, onClick = onRemove, trailing = remove, flagged = flagged)
    choice.first == Filter.Threshold -> { { Stars(choice.second) } }
    else -> { { Text(choice.second.label, style = MaterialTheme.typography.labelLarge) } }
  }
  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = Modifier.heightIn(min = TagBadgeHeight).then(if (onRemove != null) Modifier.clickable(onClick = onRemove) else Modifier),
  ) {
    Row(
      Modifier.padding(start = 12.dp, end = if (remove == null) 12.dp else 4.dp, top = 2.dp, bottom = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      label()
      remove?.invoke()
    }
  }
}

private fun tokenName(tag: String, known: Map<String, TagSuggestion>, me: String?): String =
  Favourites.of(tag, me)?.label
    ?: Favourites.shortLabel(tag)
    ?: Rating.of(tag)?.let { ratings -> "rating " + ratings.joinToString(", ") { it.badge } }
    ?: Filter.optionFor(tag)?.second?.label
    ?: knownLabel(tag, known)

private fun knownLabel(tag: String, known: Map<String, TagSuggestion>): String =
  known[tag.removePrefix("-").lowercase()]?.let { (if (tag.startsWith("-")) "-" else "") + it.displayName } ?: tag

@Composable
private fun Stars(option: FilterOption) {
  val minimum = option.tag.substringAfter(':').toIntOrNull() ?: 0
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Text("≥", style = MaterialTheme.typography.labelLarge.centredOnLine())
    Row {
      repeat(minimum) { Icon(star_filled, contentDescription = null, tint = StarColor, modifier = Modifier.size(16.dp)) }
    }
  }
}

@Composable
private fun KnownTagBadge(
  tag: String,
  known: Map<String, TagSuggestion>,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  flagged: Boolean = false,
) {
  val found = known[tag.removePrefix("-").lowercase()]
  TagBadge(AnnotatedString(knownLabel(tag, known)), found?.category, found?.hint(), onClick = onClick, flagged = flagged, trailing = trailing)
}

private fun TagSuggestion.hint(): String {
  val key = tagName.removePrefix("-")
  return listOfNotNull(category.name, posts?.let { "${compactCount(it)} posts" }, key.takeIf { it != displayName })
    .joinToString(" · ")
}

@Composable
private fun OverflowRow(
  overflow: @Composable (hidden: Int) -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  SubcomposeLayout(modifier) { constraints ->
    val gap = 8.dp.roundToPx()
    val loose = Constraints(maxHeight = constraints.maxHeight)
    val items = subcompose("items", content).map { it.measure(loose) }
    fun width(count: Int) = items.takeLast(count).sumOf { it.width } + gap * (count - 1).coerceAtLeast(0)
    var shown = 0
    while (shown < items.size && width(shown + 1) <= constraints.maxWidth) shown++
    var badge = if (shown < items.size) subcompose("overflow-${items.size - shown}") { overflow(items.size - shown) }
      .single().measure(loose) else null
    while (badge != null && shown > 0 && width(shown) + gap + badge.width > constraints.maxWidth) {
      shown--
      badge = subcompose("overflow-${items.size - shown}") { overflow(items.size - shown) }.single().measure(loose)
    }
    val visible = items.takeLast(shown)
    val height = (visible.map { it.height } + listOfNotNull(badge?.height)).maxOrNull() ?: 0
    layout(constraints.maxWidth, height) {
      var x = 0
      badge?.let {
        it.placeRelative(x, (height - it.height) / 2)
        x += it.width + gap
      }
      visible.forEach {
        it.placeRelative(x, (height - it.height) / 2)
        x += it.width + gap
      }
    }
  }
}

internal fun highlightStep(event: KeyEvent): Int? = when {
  !event.isAltPressed || event.isCommand || event.isShiftPressed -> null
  event.key == Key.W -> -1
  event.key == Key.S -> 1
  else -> null
}

internal fun steppedHighlight(highlighted: Int, count: Int, by: Int): Int = when {
  count == 0 -> highlighted
  by > 0 -> if (highlighted >= count - 1) -1 else highlighted + 1
  else -> if (highlighted < 0) count - 1 else highlighted - 1
}

private sealed interface Entry {
  data class Tag(val suggestion: TagSuggestion) : Entry
  data class Recent(val tags: List<String>) : Entry
}

@Composable
fun SearchPanel(
  initial: List<String>,
  signedIn: Boolean,
  safeOnly: Boolean,
  recent: RecentSearches,
  suggest: suspend (String) -> List<TagSuggestion>,
  onSubmit: (List<String>) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  known: Map<String, TagSuggestion> = emptyMap(),
  me: String? = null,
  onSignIn: () -> Unit = {},
  onTurnOffGOnly: (() -> Unit)? = null,
  inQueue: (String) -> Int? = { null },
) {
  val editor = rememberSearchEditor(
    initial, signedIn, me, safeOnly, recent, suggest, onSubmit, highlightFirst = true, onSignIn, onTurnOffGOnly,
  )
  val tagInfo = known + editor.picked
  val recentSearches by recent.searches.collectAsStateWithLifecycle()
  val focus = remember { FocusRequester() }
  val entries = editor.entries(recentSearches, tagInfo)
  var inputFocused by remember { mutableStateOf(false) }
  var dropTyped by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    withFrameNanos {}
    focus.requestFocus()
  }

  Column(
    modifier = modifier.onPreviewKeyEvent { event ->
      if (event.type == KeyEventType.Unknown && dropTyped) return@onPreviewKeyEvent true.also { dropTyped = false }
      if (event.type == KeyEventType.KeyUp) dropTyped = false
      val altStep = if (inputFocused) highlightStep(event) else null
      if (altStep != null) {
        if (event.type == KeyEventType.KeyDown) {
          editor.highlighted = steppedHighlight(editor.highlighted, entries.size, altStep)
          dropTyped = true
        }
        return@onPreviewKeyEvent true
      }
      if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
      if (event.key == Shortcuts.Back.key) return@onPreviewKeyEvent true.also { onClose() }
      if (!inputFocused) return@onPreviewKeyEvent false
      if (event.key == Key.Tab && event.isShiftPressed) {
        if (!editor.typing || entries.isEmpty()) return@onPreviewKeyEvent false
        editor.highlighted = steppedHighlight(editor.highlighted, entries.size, -1)
        return@onPreviewKeyEvent true
      }
      val target = entries.getOrNull(editor.highlighted)
      when (event.key) {
        Key.DirectionDown -> editor.highlighted = steppedHighlight(editor.highlighted, entries.size, 1)
        Key.DirectionUp -> editor.highlighted = steppedHighlight(editor.highlighted, entries.size, -1)
        Key.Enter, Key.NumPadEnter -> when {
          event.isShiftPressed -> editor.addTyped()
          event.isMetaPressed || event.isCtrlPressed -> {
            if (target is Entry.Tag) editor.pick(target)
            editor.submit()
          }
          else -> editor.act(entries)
        }
        Key.Tab -> when {
          target is Entry.Tag -> editor.pick(target)
          editor.typing -> editor.addTyped()
          else -> return@onPreviewKeyEvent false
        }
        Key.Backspace -> if (!editor.removeLast()) return@onPreviewKeyEvent false
        else -> return@onPreviewKeyEvent false
      }
      true
    },
  ) {
    Surface(
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceBright,
      shadowElevation = 8.dp,
    ) {
      Column {
        DraftChips(
          editor.draft,
          tagInfo,
          editor.input,
          focus,
          onRemove = {
            editor.remove(it)
            focus.requestFocus()
          },
          onSearch = editor::submit,
          loading = editor.loading,
          modifier = Modifier.onFocusChanged { inputFocused = it.hasFocus }.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        SuggestionList(
          entries = entries,
          highlighted = editor.highlighted,
          typed = editor.input.text.toString(),
          status = editor.shownStatus,
          known = tagInfo,
          me = editor.draft.me,
          onPick = editor::pick,
          notice = editor.notice,
          inQueue = inQueue,
          modifier = Modifier.padding(8.dp).heightIn(max = 360.dp),
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
          FilterRow(
            editor = editor,
            signedIn = signedIn,
            onMenuClosed = { focus.requestFocus() },
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
          )
        }
      }
    }
    KeyHints(editor.typing, Modifier.padding(top = 12.dp))
  }
}

@Composable
fun FullScreenSearch(
  state: FullScreenSearchState,
  open: Boolean,
  initial: List<String>,
  signedIn: Boolean,
  safeOnly: Boolean,
  recent: RecentSearches,
  suggest: suspend (String) -> List<TagSuggestion>,
  onSubmit: (List<String>) -> Unit,
  onClose: () -> Unit,
  known: Map<String, TagSuggestion> = emptyMap(),
  me: String? = null,
  onSignIn: () -> Unit = {},
  onTurnOffGOnly: (() -> Unit)? = null,
  inQueue: (String) -> Int? = { null },
) {
  var drafts by remember { mutableIntStateOf(0) }
  var stale by remember { mutableStateOf(false) }
  LaunchedEffect(open) {
    if (open) {
      if (stale) drafts++
      stale = false
    } else {
      stale = true
      snapshotFlow { state.isCollapsed }.first { it }
      drafts++
      stale = false
    }
  }
  val editor = key(drafts, initial) {
    rememberSearchEditor(
      initial, signedIn, me, safeOnly, recent, suggest, onSubmit, highlightFirst = false, onSignIn, onTurnOffGOnly,
    )
  }
  val tagInfo = known + editor.picked
  val recentSearches by recent.searches.collectAsStateWithLifecycle()
  val entries = editor.entries(recentSearches, tagInfo)
  val focus = remember { FocusRequester() }
  FullScreenSearchBar(
    state = state,
    open = open,
    onClose = onClose,
    inputField = {
      TouchSearchInput(
        editor = editor,
        known = tagInfo,
        focus = focus,
        onAction = { hideKeyboard -> if (!editor.touchAct()) hideKeyboard() },
        onClose = onClose,
      )
    },
  ) {
    SuggestionList(
      entries = entries,
      highlighted = editor.highlighted,
      typed = editor.input.text.toString(),
      status = editor.shownStatus,
      known = tagInfo,
      me = editor.draft.me,
      onPick = editor::pick,
      notice = editor.notice,
      inQueue = inQueue,
      modifier = Modifier.weight(1f).padding(8.dp),
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
      TouchFilterBand(editor = editor, signedIn = signedIn)
    }
  }
}

@Composable
private fun TouchSearchInput(
  editor: SearchEditor,
  known: Map<String, TagSuggestion>,
  focus: FocusRequester,
  onAction: (hideKeyboard: () -> Unit) -> Unit,
  onClose: () -> Unit,
) {
  val scroll = rememberScrollState()
  LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    ButtonHint("Close search (${Shortcuts.Back.label})") {
      IconButton(onClick = onClose) { Icon(arrow_back, contentDescription = "Close search") }
    }
    Row(
      Modifier.weight(1f).horizontalScroll(scroll),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      editor.draft.chips.forEach { chip ->
        TouchRemovable("Remove $chip", onRemove = { editor.remove(chip) }) {
          KnownTagBadge(chip, known, trailing = { Icon(close, contentDescription = null, modifier = Modifier.size(16.dp)) })
        }
      }
      BasicTextField(
        state = editor.input,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions =
          KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
          ),
        onKeyboardAction = { performDefaultAction -> onAction(performDefaultAction) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorator = { field ->
          Box(contentAlignment = Alignment.CenterStart) {
            if (editor.input.text.isEmpty()) {
              Text(
                if (editor.draft.chips.isEmpty()) "Search tags" else "Add a tag",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
              )
            }
            field()
          }
        },
        modifier = Modifier.widthIn(min = 160.dp).padding(end = 16.dp).focusRequester(focus),
      )
    }
    LoadingSlot(editor.loading, Modifier.padding(end = 8.dp))
  }
  LaunchedEffect(Unit) {
    withFrameNanos {}
    focus.requestFocus()
  }
}

@Composable
private fun LoadingSlot(loading: Boolean, modifier: Modifier = Modifier) {
  val shown by animateFloatAsState(if (loading) 1f else 0f, effectsSpec())
  Box(modifier.size(32.dp).graphicsLayer { alpha = shown }, contentAlignment = Alignment.Center) {
    if (shown > 0f) AppLoadingIndicator(size = 32.dp)
  }
}

@Composable
private fun rememberSearchEditor(
  initial: List<String>,
  signedIn: Boolean,
  me: String?,
  safeOnly: Boolean,
  recent: RecentSearches,
  suggest: suspend (String) -> List<TagSuggestion>,
  onSubmit: (List<String>) -> Unit,
  highlightFirst: Boolean,
  onSignIn: () -> Unit,
  onTurnOffGOnly: (() -> Unit)?,
): SearchEditor {
  val reason by rememberUpdatedState(gOnlyReason(signedIn, safeOnly, canTurnOff = onTurnOffGOnly != null))
  val gOnly by rememberUpdatedState(safeOnly)
  val latestSubmit by rememberUpdatedState(onSubmit)
  val latestSuggest by rememberUpdatedState(suggest)
  val latestSignIn by rememberUpdatedState(onSignIn)
  val latestTurnOff by rememberUpdatedState(onTurnOffGOnly)
  val editor = remember {
    SearchEditor(
      initial,
      me,
      highlightFirst,
      reason = { reason },
      onReasonAction = { if (reason == GOnlyReason.SignedOut) latestSignIn() else latestTurnOff?.invoke() },
      gOnly = { gOnly },
    ) { tags ->
      recent.add(tags)
      latestSubmit(tags)
    }
  }
  LaunchedEffect(editor) { editor.suggestWhileTyping { latestSuggest(it) } }
  LaunchedEffect(editor, me) { editor.withMe(me) }
  return editor
}

@Stable
private class SearchEditor(
  initial: List<String>,
  me: String?,
  private val highlightFirst: Boolean,
  private val reason: () -> GOnlyReason?,
  val onReasonAction: () -> Unit,
  private val gOnly: () -> Boolean,
  private val run: (List<String>) -> Unit,
) {
  var draft by mutableStateOf(unlocked(SearchDraft.of(initial, me)))
    private set

  var notice by mutableStateOf<String?>(null)
    private set

  private var noticeLeft: String? = null
  val input = TextFieldState()
  private var found by mutableStateOf<List<TagSuggestion>>(emptyList())
  private var status by mutableStateOf(SuggestStatus.Idle)
  var highlighted by mutableIntStateOf(-1)

  val picked = mutableStateMapOf<String, TagSuggestion>()

  val typing: Boolean get() = input.text.isNotBlank()
  val loading: Boolean get() = typing && status == SuggestStatus.Loading
  val shownStatus: SuggestStatus get() = if (typing) status else SuggestStatus.Idle
  val lockReason: GOnlyReason? get() = reason()

  private val offered: List<TagSuggestion> get() = offeredSuggestions(found, gOnly())

  fun entries(recentSearches: List<List<String>>, known: Map<String, TagSuggestion>): List<Entry> =
    if (typing) {
      offered.filterNot { it.tagName in draft.chips }.map { Entry.Tag(it) }
    } else {
      shownRecentSearches(recentSearches, known, gOnly()).map(Entry::Recent)
    }

  private fun unlocked(draft: SearchDraft, typed: Boolean = false): SearchDraft {
    val why = reason() ?: return draft
    if (typed && (draft.ratings.any { it != Rating.G } || draft.chips.any(Rating::isRatingTag))) notice = why.droppedRating
    return draft.withRatings(emptySet())
  }

  fun submit() {
    draft = unlocked(draft.add(asKey(input.text.toString())), typed = true)
    run(draft.tags)
  }

  fun addTyped() {
    draft = unlocked(draft.add(asKey(input.text.toString())), typed = true)
    input.clearText()
    highlighted = -1
  }

  fun pick(entry: Entry) {
    when (entry) {
      is Entry.Tag -> {
        picked[entry.suggestion.tagName.removePrefix("-")] = entry.suggestion
        draft = draft.add(entry.suggestion.tagName)
        input.clearText()
        highlighted = -1
      }
      is Entry.Recent -> run(entry.tags)
    }
  }

  fun act(entries: List<Entry>) {
    val target = entries.getOrNull(highlighted)
    when {
      target != null -> pick(target)
      typing -> addTyped()
      else -> submit()
    }
  }

  fun touchAct(): Boolean {
    val typed = asKey(input.text.toString())
    val match = if (loading) null else offered.firstOrNull { it.tagName.equals(typed, ignoreCase = true) }
    when {
      match != null -> {
        pick(Entry.Tag(match))
        submit()
      }
      typing -> addTyped()
      draft.tags.isNotEmpty() -> submit()
      else -> return false
    }
    return true
  }

  fun remove(tag: String) {
    draft = draft.remove(tag)
  }

  fun withRatings(ratings: Set<Rating>) {
    draft = draft.withRatings(ratings)
  }

  fun with(filter: Filter, option: FilterOption?) {
    draft = draft.with(filter, option)
  }

  fun withFavourites(favourites: Favourites?) {
    draft = draft.withFavourites(favourites)
  }

  fun withMe(me: String?) {
    if (me != draft.me) draft = unlocked(draft.withMe(me))
  }

  fun removeLast(): Boolean {
    if (input.text.isNotEmpty() || draft.chips.isEmpty()) return false
    draft = draft.remove(draft.chips.last())
    return true
  }

  suspend fun suggestWhileTyping(suggest: suspend (String) -> List<TagSuggestion>) {
    var previous = ""
    snapshotFlow { input.text.toString() }.distinctUntilChanged().collectLatest { text ->
      highlighted = -1
      if (text.isNotBlank() && text != noticeLeft) notice = null
      if (text.isBlank()) noticeLeft = null
      val added = inserted(previous, text)
      val grew = text.length - previous.length
      previous = text
      fun cleared() {
        found = emptyList()
        status = SuggestStatus.Idle
      }
      val pasted = if (highlightFirst) grew > 1 && added.any(Char::isWhitespace) else isPastedSearch(added)
      if (pasted) {
        draft = unlocked(draft.add(text), typed = true)
        input.clearText()
        cleared()
        return@collectLatest
      }
      val end = text.lastIndexOf(',')
      if (end >= 0) {
        text.substring(0, end).split(',').forEach { draft = unlocked(draft.add(asKey(it)), typed = true) }
        input.edit { replace(0, end + 1, "") }
        noticeLeft = input.text.toString()
        cleared()
        return@collectLatest
      }
      val key = asKey(text)
      if (key.removePrefix("-").length < TagsRepository.MIN_PREFIX) {
        cleared()
        return@collectLatest
      }
      status = SuggestStatus.Loading
      delay(SUGGEST_DELAY_MS)
      status = try {
        found = suggest(key)
        highlighted = if (highlightFirst && offered.any { it.tagName !in draft.chips }) 0 else -1
        when {
          offered.isNotEmpty() -> SuggestStatus.Idle
          found.isNotEmpty() -> SuggestStatus.HiddenByGOnly
          else -> SuggestStatus.NoMatch
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        found = emptyList()
        SuggestStatus.Failed
      }
    }
  }
}

private enum class SuggestStatus { Idle, Loading, NoMatch, HiddenByGOnly, Failed }

internal fun offeredSuggestions(found: List<TagSuggestion>, gOnly: Boolean): List<TagSuggestion> =
  if (gOnly) found.filterNot { Rating.ofCode(it.rating) in NOT_G } else found

private val NOT_G = setOf(Rating.R15, Rating.R18)

private val SPACES = Regex("\\s+")

private fun inserted(before: String, after: String): String {
  val start = before.commonPrefixWith(after).length
  val maxSuffix = minOf(before.length, after.length) - start
  val common = before.drop(start).commonSuffixWith(after.drop(start)).length.coerceAtMost(maxSuffix)
  return after.substring(start, after.length - common)
}

private fun isPastedSearch(added: String): Boolean = added.trim().split(SPACES).size > 1

internal fun asKey(typed: String): String = typed.trim().replace(SPACES, "_")


@Composable
private fun DraftChips(
  draft: SearchDraft,
  known: Map<String, TagSuggestion>,
  input: TextFieldState,
  focus: FocusRequester,
  onRemove: (String) -> Unit,
  onSearch: () -> Unit,
  loading: Boolean,
  modifier: Modifier = Modifier,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
    modifier = modifier,
  ) {
    Icon(
      search,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = 4.dp),
    )
    draft.chips.forEach { chip ->
      KnownTagBadge(
        chip,
        known,
        onClick = { onRemove(chip) },
        trailing = { Icon(close, contentDescription = "Remove $chip", modifier = Modifier.size(16.dp)) },
      )
    }
    BasicTextField(
      state = input,
      lineLimits = TextFieldLineLimits.SingleLine,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      onKeyboardAction = { onSearch() },
      textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      decorator = { field ->
        Box(contentAlignment = Alignment.CenterStart) {
          if (input.text.isEmpty()) {
            Text(
              if (draft.chips.isEmpty()) "Search tags" else "Add a tag",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          field()
        }
      },
      modifier = Modifier.weight(1f).widthIn(min = 160.dp).heightIn(min = 32.dp).focusRequester(focus),
    )
    LoadingSlot(loading)
  }
}

@Composable
private fun FilterRow(
  editor: SearchEditor,
  signedIn: Boolean,
  onMenuClosed: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FlowRow(
    modifier = modifier.fillMaxWidth().sidewaysFocusKeys(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    FilterChips(editor, signedIn, onMenuOpened = {}, onMenuClosed = onMenuClosed)
    Spacer(Modifier.weight(1f))
    PrimaryButton("Search", onClick = editor::submit)
  }
}

@Composable
private fun TouchFilterBand(editor: SearchEditor, signedIn: Boolean) {
  val focusManager = LocalFocusManager.current
  Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FilterChips(editor, signedIn, onMenuOpened = { focusManager.clearFocus() }, onMenuClosed = {})
    }
    PrimaryButton("Search", onClick = editor::submit, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth())
  }
}

@Composable
private fun FilterChips(
  editor: SearchEditor,
  signedIn: Boolean,
  onMenuOpened: () -> Unit,
  onMenuClosed: () -> Unit,
) {
  val draft = editor.draft
  val reason = editor.lockReason
  val onChoose = { filter: Filter, option: FilterOption? ->
    editor.with(filter, option)
    onMenuClosed()
  }
  if (reason != null) {
    LockedRating(why = reason.hint, reason = reason, onReasonAction = editor.onReasonAction)
  } else {
    RatingMenu(draft.ratings, editor::withRatings, onMenuOpened, onMenuClosed)
  }
  Filter.entries.forEach { filter ->
    when (filter) {
      Filter.Threshold -> ScoreMenu(draft.filters[filter], onMenuOpened, onChoose = { onChoose(filter, it) })
      Filter.Feed -> {
        FilterMenu(
          filter,
          draft.filters[filter],
          onMenuOpened,
          options = filter.options.filter { signedIn || it.tag != Feed.Following.tag },
          onChoose = { onChoose(filter, it) },
        )
        if (draft.me != null) {
          ChoiceMenu(
            label = Favourites.LABEL,
            anyLabel = Favourites.ANY_LABEL,
            chosen = draft.favourites,
            options = Favourites.entries,
            optionLabel = Favourites::label,
            onOpen = onMenuOpened,
            onChoose = {
              editor.withFavourites(it)
              onMenuClosed()
            },
          )
        }
      }
      else -> FilterMenu(filter, draft.filters[filter], onMenuOpened, onChoose = { onChoose(filter, it) })
    }
  }
}

@Composable
private fun RatingMenu(ratings: Set<Rating>, onRatings: (Set<Rating>) -> Unit, onOpen: () -> Unit, onClosed: () -> Unit) {
  var open by remember { mutableStateOf(false) }
  fun close() {
    open = false
    onClosed()
  }
  Box {
    MenuChip(
      selected = ratings.isNotEmpty(),
      onClick = {
        onOpen()
        open = true
      },
      label = {
        if (ratings.isEmpty()) Text("Rating") else RatingStack(ratings)
      },
    )
    GroupedMenu(expanded = open, onDismissRequest = ::close) {
      Column(
        Modifier.onPreviewKeyEvent { event ->
          val esc = event.key == Shortcuts.Back.key
          if (esc && event.type == KeyEventType.KeyDown) open = false
          esc
        },
      ) {
        MenuGroup(0, 2) {
          MenuChoice(
            selected = ratings.isEmpty(),
            onClick = {
              onRatings(emptySet())
              close()
            },
          ) { Text("All ratings") }
        }
        MenuGroup(1, 2) {
          Rating.entries.forEach { rating ->
            MenuCheck(
              checked = rating in ratings,
              onCheckedChange = { onRatings(if (it) ratings + rating else ratings - rating) },
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingBadge(rating)
                Text(rating.label)
              }
            }
          }
        }
      }
    }
  }
}

private val StarColor = Color(0xFFE8A200)

@Composable
private fun ScoreMenu(chosen: FilterOption?, onOpen: () -> Unit, onChoose: (FilterOption?) -> Unit) {
  val minimum = chosen?.tag?.substringAfter(':')?.toIntOrNull()
  var open by remember { mutableStateOf(false) }
  Box {
    MenuChip(
      selected = minimum != null,
      onClick = {
        onOpen()
        open = true
      },
      label = {
        if (chosen == null) Text(Filter.Threshold.label) else Stars(chosen)
      },
      modifier = Modifier.semantics { contentDescription = minimum?.let { "Minimum score $it" } ?: "Minimum score" },
    )
    GroupedMenu(expanded = open, onDismissRequest = { open = false }) {
      MenuGroup(0, 2) {
        MenuChoice(
          selected = minimum == null,
          onClick = {
            open = false
            onChoose(null)
          },
        ) { Text(Filter.Threshold.anyLabel) }
      }
      MenuGroup(1, 2) {
        StarPicker(minimum ?: 0) { stars ->
          open = false
          onChoose(Filter.Threshold.optionFor("threshold:$stars"))
        }
      }
    }
  }
}

private val StarCell = 40.dp

@Composable
private fun StarPicker(chosen: Int, onPick: (Int) -> Unit) {
  var hovered by remember { mutableIntStateOf(0) }
  var focused by remember { mutableIntStateOf(0) }
  val shown = when {
    hovered > 0 -> hovered
    focused > 0 -> focused
    else -> chosen
  }
  Row(
    Modifier
      .sidewaysFocusKeys()
      .padding(horizontal = 12.dp, vertical = 4.dp)
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent()
            val x = event.changes.firstOrNull()?.position?.x ?: continue
            hovered = if (event.type == PointerEventType.Exit) 0 else (x / StarCell.toPx()).toInt().coerceIn(0, 4) + 1
          }
        }
      },
  ) {
    for (stars in 1..5) {
      Box(
        Modifier
          .then(if (stars == chosen) Modifier.chosenMenuItem() else Modifier)
          .onFocusChanged { state ->
            if (state.isFocused) focused = stars else if (focused == stars) focused = 0
          }
          .chooseOnTab { onPick(stars) }
          .size(StarCell)
          .clip(CircleShape)
          .clickable { onPick(stars) },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          if (stars <= shown) star_filled else star,
          contentDescription = "At least $stars",
          tint = if (stars <= shown) StarColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun FilterMenu(
  filter: Filter,
  chosen: FilterOption?,
  onOpen: () -> Unit,
  onChoose: (FilterOption?) -> Unit,
  options: List<FilterOption> = filter.options,
) {
  ChoiceMenu(filter.label, filter.anyLabel, chosen, options, FilterOption::label, onOpen, onChoose)
}

@Composable
private fun <T> ChoiceMenu(
  label: String,
  anyLabel: String,
  chosen: T?,
  options: List<T>,
  optionLabel: (T) -> String,
  onOpen: () -> Unit,
  onChoose: (T?) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  Box {
    MenuChip(
      selected = chosen != null,
      onClick = {
        onOpen()
        open = true
      },
      label = { Text(chosen?.let(optionLabel) ?: label) },
    )
    GroupedMenu(expanded = open, onDismissRequest = { open = false }) {
      MenuGroup(0, 2) {
        MenuChoice(
          selected = chosen == null,
          onClick = {
            open = false
            onChoose(null)
          },
        ) { Text(anyLabel) }
      }
      MenuGroup(1, 2) {
        options.forEach { option ->
          MenuChoice(
            selected = option == chosen,
            onClick = {
              open = false
              onChoose(option)
            },
          ) { Text(optionLabel(option)) }
        }
      }
    }
  }
}

internal fun ratingsDescription(ratings: Set<Rating>, signedOut: Boolean): String = when {
  ratings == setOf(Rating.G) -> if (signedOut) GOnlyReason.SignedOut.text else "G only"
  else -> "Ratings " + Rating.entries.filter { it in ratings }.joinToString(", ") { it.badge }
}

@Composable
private fun LockedRating(why: String, reason: GOnlyReason, onReasonAction: () -> Unit) {
  HoverHint(why) {
    MenuChip(
      selected = true,
      onClick = {},
      enabled = false,
      label = { RatingStack(setOf(Rating.G)) },
    )
  }
  GOnlyReasonLine(reason, onReasonAction, badge = false)
}

@Composable
private fun SuggestionList(
  entries: List<Entry>,
  highlighted: Int,
  typed: String,
  status: SuggestStatus,
  known: Map<String, TagSuggestion>,
  me: String?,
  onPick: (Entry) -> Unit,
  modifier: Modifier = Modifier,
  notice: String? = null,
  inQueue: (String) -> Int? = { null },
) {
  val hint = when {
    status == SuggestStatus.Failed ->
      if (platformIsTouch) "Suggestions didn't load. Search still works." else "Suggestions didn't load. Enter still searches."
    status == SuggestStatus.NoMatch -> "No tags match “$typed”."
    status == SuggestStatus.HiddenByGOnly -> "Some tags are hidden while G only is on."
    entries.isNotEmpty() -> null
    typed.isBlank() -> "Type 2 or more letters for suggestions. Start with - to exclude a tag."
    status == SuggestStatus.Loading -> null
    else -> "Type 2 or more letters for suggestions."
  }
  Column(modifier.verticalScroll(rememberScrollState())) {
    notice?.let {
      Text(
        it,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
    if (entries.firstOrNull() is Entry.Recent) {
      Text(
        "Recent searches",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
      )
    }
    if (!platformIsTouch && typed.isNotBlank() && highlighted < 0 && entries.firstOrNull() is Entry.Tag) {
      Row(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        KbdBadge("↵")
        Text(
          "adds “${typed.trim()}” as typed",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    entries.forEachIndexed { index, entry ->
      val queued = (entry as? Entry.Tag)?.suggestion?.tagName?.takeUnless { it.startsWith("-") }?.let(inQueue)
      EntryRow(entry, typed, known, highlighted = index == highlighted, me = me, inQueue = queued, onClick = { onPick(entry) })
    }
    hint?.let {
      Text(
        it,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      )
    }
  }
}

@Composable
private fun EntryRow(
  entry: Entry,
  typed: String,
  known: Map<String, TagSuggestion>,
  highlighted: Boolean,
  me: String?,
  onClick: () -> Unit,
  inQueue: Int? = null,
) {
  val rowColor = if (highlighted) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
  Row(
    Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .background(rowColor)
      .focusProperties { canFocus = false }
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    when (entry) {
      is Entry.Recent -> {
        val ratings = Rating.inTags(entry.tags)
        if (ratings.isNotEmpty()) RatingStack(ratings, size = RatingBadgeSize.Tag)
        OverflowRow(
          overflow = { hidden -> SummaryChip("+$hidden", muted = true) },
          modifier = Modifier.weight(1f),
        ) { SearchTags.filtersFirst(entry.tags).filterNot(Rating::isRatingTag).forEach { SearchTag(it, known, me) } }
        if (highlighted && !platformIsTouch) KbdBadge("↵")
      }
      is Entry.Tag -> {
        val tag = entry.suggestion
        Box(Modifier.weight(1f)) {
          val name = (if (tag.tagName.startsWith("-")) "-" else "") + tag.displayName
          TagBadge(matched(name, typed), tag.category, tag.hint())
        }
        inQueue?.let { QueueMark(it, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (highlighted && !platformIsTouch) {
          KbdBadge("Tab")
          KbdBadge("↵")
        }
        Rating.ofCode(tag.rating)?.let { RatingBadge(it) }
        tag.posts?.let {
          Text(
            compactCount(it),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 40.dp),
          )
        }
      }
    }
  }
}

private fun matched(name: String, typed: String): AnnotatedString = buildAnnotatedString {
  fun loose(text: String) = text.lowercase().replace('_', ' ')
  val length = if (typed.isNotEmpty() && loose(name).startsWith(loose(typed))) typed.length else 0
  withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name.take(length)) }
  withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(name.drop(length)) }
}

@Composable
private fun KeyHints(typing: Boolean, modifier: Modifier = Modifier) {
  val hints = if (typing) {
    listOf(
      "↑↓ ⌥W⌥S" to "choose",
      "Tab ↵" to "add",
      "⌘↵" to "search",
      "," to "add as typed",
      Shortcuts.Back.label to "close",
    )
  } else {
    listOf("↑↓ ⌥W⌥S" to "choose", "↵" to "search", "Tab" to "filters", Shortcuts.Back.label to "close")
  }
  FlowRow(
    modifier.padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    for ((key, label) in hints) KeyHint(key, label, color = Color.White.copy(alpha = 0.92f))
  }
}

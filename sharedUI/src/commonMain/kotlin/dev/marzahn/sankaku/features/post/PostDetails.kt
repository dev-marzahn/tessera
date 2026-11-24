package dev.marzahn.sankaku.features.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.google.fonts.icons.download
import com.google.fonts.icons.open_in_new
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.data.SearchTags
import dev.marzahn.sankaku.api.dto.TagType
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.features.books.FromTheBook
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.TagsRepository
import dev.marzahn.sankaku.features.grid.GridCursorRequest
import dev.marzahn.sankaku.features.tag.TagPages
import dev.marzahn.sankaku.features.grid.Rating
import dev.marzahn.sankaku.features.grid.RatingBadge
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.openUrl
import dev.marzahn.sankaku.ui.components.HoverHint
import dev.marzahn.sankaku.ui.components.QueueMark
import dev.marzahn.sankaku.ui.components.KeyHint
import dev.marzahn.sankaku.ui.components.LocalKeyFocus
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.TagChip
import dev.marzahn.sankaku.ui.components.TagChipState
import dev.marzahn.sankaku.ui.components.TagLater
import dev.marzahn.sankaku.ui.components.TagLaterState
import dev.marzahn.sankaku.ui.components.goToQueuedLabel
import dev.marzahn.sankaku.ui.components.openForLaterLabel
import dev.marzahn.sankaku.ui.components.effectsSpec
import dev.marzahn.sankaku.ui.components.fastSpatialSpec
import dev.marzahn.sankaku.ui.components.isCommand
import dev.marzahn.sankaku.ui.components.spatialSpec
import dev.marzahn.sankaku.ui.onRightClick
import dev.marzahn.sankaku.ui.openOnClick
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.theme.color
import org.koin.compose.koinInject

@Composable
fun PostDetails(
  post: PostDto,
  modifier: Modifier = Modifier,
  scroll: ScrollState = rememberScrollState(),
  showStats: Boolean = true,
  showUploader: Boolean = true,
  searchTags: List<String> = emptyList(),
  tagListFocus: FocusRequester = remember { FocusRequester() },
  section: (index: Int) -> Modifier = { Modifier },
  navigator: Navigator = koinInject(),
  actions: PostActions = koinInject(),
  repository: PostsRepository = koinInject(),
  session: AuthSession = koinInject(),
  saver: PostSaver = koinInject(),
  tabs: TabsController = koinInject(),
  messages: Messages = koinInject(),
  cursorRequest: GridCursorRequest = koinInject(),
  blacklisting: TagBlacklisting = koinInject(),
  tagPages: TagPages = koinInject(),
  touch: Boolean = platformIsTouch,
) {
  fun open(route: Route) = navigator.goTo(route)
  val signedIn by session.isSignedIn.collectAsState()
  val followOverrides by actions.follows.overrides.collectAsState()
  val queue = tabs.tabs.collectAsState()
  val inQueue by remember(tabs, queue) { derivedStateOf { tabs.queuePositions(queue.value) } }
  val followRejections = remember { mutableStateMapOf<String, Int>() }
  if (platformIsTouch) {
    LaunchedEffect(actions) {
      actions.rejections.collect { if (it is Rejection.Follow) followRejections[it.tagName] = (followRejections[it.tagName] ?: 0) + 1 }
    }
  }
  val openedLater = remember(post.id) { mutableStateSetOf<String>() }
  val tagActions = TagActions(
    search = searchTags,
    onOpen = { actions.actedOn(post); open(it) },
    onOpenInNewTab = { actions.actedOn(post); navigator.openInNewTab(it) },
    isFollowing = { actions.follows.isFollowing(it, followOverrides) },
    onToggleFollow = if (signedIn) { tag -> actions.actedOn(post); actions.toggleFollow(tag) } else null,
    onKeySearch = { actions.actedOn(post); cursorRequest.request(it.tags); open(it) },
    followRejected = { followRejections[it] ?: 0 },
    onAlreadySearching = { messages.show("Already searching ${it.displayName}") },
    onBlacklist = if (signedIn) { tag -> actions.actedOn(post); blacklisting.blacklist(tag) } else null,
    usedBlacklistRules = blacklisting::usedRules,
    onAbout = { actions.actedOn(post); tagPages.open(it) },
    inQueue = inQueue,
    touch = touch,
    onOpenLater = if (touch) {
      { tag, route ->
        actions.actedOn(post)
        openedLater += tag.searchName
        openTagForLater(tag, route, navigator, tabs, messages)
      }
    } else {
      null
    },
    openedLater = { it in openedLater },
  )

  Column(
    modifier.verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (showStats) {
      PostStats(
        post,
        onToggleFavourite = { actions.toggleFavourite(post) },
        onVote = { actions.vote(post, it) },
        onSignIn = if (signedIn) null else actions::openSignIn,
        modifier = section(0),
      )
    }

    var factsOnScreen by remember { mutableStateOf(false) }
    Column(
      section(1).onGloballyPositioned { factsOnScreen = it.boundsInWindow().let { b -> b.width > 0f && b.height > 0f } },
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      if (showUploader) {
        post.author?.let { Fact("Uploader", it.name) }
        post.createdAt?.let { Fact("Uploaded", it.toString().take(10)) }
      }
      if (post.fileWidth != null && post.fileHeight != null) Fact("Size", "${post.fileWidth} × ${post.fileHeight}")
      Fact("File", listOfNotNull(post.fileSize?.let(::humanSize), post.fileType).joinToString(" · "))
      val rating = Rating.ofCode(post.rating)
      if (rating == null) {
        Fact("Rating", post.rating.uppercase())
      } else {
        Fact("Rating") {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RatingBadge(rating)
            Text(rating.label, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }

    FromTheBook(post, section(1), onScreen = { factsOnScreen }, onOpen = { actions.actedOn(post) }, navigator = navigator)

    post.parentId?.let { parent ->
      PostStrip(SheetStrip.Parent, post.id, section(2), navigator = navigator, repository = repository, tabs = tabs) {
        StripPosts(listOfNotNull(repository.parent(parent, it)))
      }
    }
    if (post.hasChildren) {
      PostStrip(SheetStrip.Children, post.id, section(2), navigator = navigator, repository = repository, tabs = tabs) {
        val children = repository.children(post.id, it)
        StripPosts(children.posts, more = Route.Grid(listOf("parent:${post.id}")).takeIf { children.mayHaveMore })
      }
    }

    RecommendedPosts(post.id, section(3), navigator, repository, tabs)

    Column(section(4), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      HorizontalDivider()
      val fullTags = fullTags(post.id, repository)
      val (tags, typed) = when {
        fullTags != null -> fullTags to true
        post.tagNames.isNotEmpty() -> post.tagNames.map { TagDto(name = it.replace('_', ' '), tagName = it) } to false
        else -> post.tags to true
      }
      TagList(tags, tagActions, tagListFocus, typed)
      HorizontalDivider()
    }

    Row(section(5), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (!platformIsTouch) {
        val saving by saver.saving.collectAsState()
        DetailsButton(if (post.id in saving) "Saving…" else "Save", download, enabled = post.id !in saving) { saver.save(post) }
      }
      DetailsButton("Open in browser", open_in_new) { messages.openUrl(postPageUrl(post)) }
    }
  }
}

@Composable
private fun DetailsButton(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
  TextButton(onClick = onClick, enabled = enabled) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(label)
  }
}

@Composable
private fun Fact(label: String, value: String) = Fact(label) { Text(value, style = MaterialTheme.typography.bodySmall) }

@Composable
private fun Fact(label: String, value: @Composable () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
    value()
  }
}

@Composable
private fun fullTags(postId: String, repository: PostsRepository, tags: TagsRepository = koinInject()): List<TagDto>? {
  val loaded by produceState(shownTags[postId]?.let { postId to it }, postId) {
    value = try {
      repository.fullPost(postId).tags.takeIf { it.isNotEmpty() }?.also { tags.learnFromPosts(it) }
        ?.also { shownTags.putBounded(postId, it, SHOWN_TAGS_MAX) }?.let { postId to it }
    } catch (_: ApiError) {
      value
    }
  }
  return loaded?.takeIf { it.first == postId }?.second
}

private val shownTags = LinkedHashMap<String, List<TagDto>>()
private const val SHOWN_TAGS_MAX = 32

private fun <K, V> LinkedHashMap<K, V>.putBounded(key: K, value: V, max: Int) {
  remove(key)
  put(key, value)
  while (size > max) remove(keys.first())
}

internal class TagActions(
  val search: List<String>,
  val onOpen: (Route) -> Unit,
  val onOpenInNewTab: (Route) -> Unit,
  val isFollowing: (TagDto) -> Boolean,
  val onToggleFollow: ((TagDto) -> Unit)?,
  val onKeySearch: (Route.Grid) -> Unit,
  val followRejected: (String) -> Int = { 0 },
  val onAlreadySearching: (TagDto) -> Unit = {},
  val onBlacklist: ((TagDto) -> Unit)? = null,
  val usedBlacklistRules: suspend () -> Int? = { null },
  val onAbout: ((TagDto) -> Unit)? = null,
  val inQueue: (Route.Grid) -> Int? = { null },
  val touch: Boolean = platformIsTouch,
  val onOpenLater: ((TagDto, Route.Grid) -> Unit)? = null,
  val openedLater: (String) -> Boolean = { false },
)

internal val TagListOrder: List<TagType> =
  TagType.entries.filter { it != TagType.Medium && it != TagType.General }.let { types ->
    val afterSetting = types.indexOf(TagType.Setting) + 1
    val automatic = types.indexOf(TagType.Automatic)
    types.take(afterSetting) + TagType.General + types.subList(afterSetting, automatic) + TagType.Medium +
      types.drop(automatic)
  }

@Composable
private fun TagList(tags: List<TagDto>, actions: TagActions, focus: FocusRequester, typed: Boolean = true) {
  val groups = if (typed) tags.groupBy { it.category }.entries.sortedBy { TagListOrder.indexOf(it.key) }.map { it.key to it.value } else listOf(null to tags)
  val ordered = groups.flatMap { it.second }
  val keyFocus = LocalKeyFocus.current
  var focused by remember { mutableStateOf(false) }
  val stillFocused by rememberUpdatedState(focused)
  DisposableEffect(Unit) { onDispose { if (stillFocused) keyFocus.returnToContent() } }
  var selectedName by remember { mutableStateOf<String?>(null) }
  val selected = ordered.indexOfFirst { it.searchName == selectedName }.coerceAtLeast(0)
  var menuName by remember { mutableStateOf<String?>(null) }
  val chipBounds = remember { HashMap<String, Rect>() }

  fun onKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown || ordered.isEmpty()) return false
    val tag = ordered[selected]
    val newTab = event.isShiftPressed || event.isCommand
    fun change(tags: List<String>?) = true.also {
      val route = Route.Grid(tags ?: return@also)
      if (newTab) actions.onOpenInNewTab(route) else actions.onKeySearch(route)
    }
    fun select(index: Int) = true.also { selectedName = ordered[index.coerceIn(ordered.indices)].searchName }
    fun vertical(down: Boolean) = true.also {
      chipAboveOrBelow(ordered.map { chipBounds[it.searchName] }, selected, down)?.let(::select)
    }
    val key = event.key
    return when {
      Shortcuts.Previous.matches(key) -> select(selected - 1)
      Shortcuts.Next.matches(key) -> select(selected + 1)
      Shortcuts.Up.matches(key) -> vertical(down = false)
      Shortcuts.Down.matches(key) -> vertical(down = true)
      Shortcuts.Include.matches(key) -> change(searchAfterKey(actions.search, tag.searchName, TagKey.Include))
      Shortcuts.Exclude.matches(key) -> change(searchAfterKey(actions.search, tag.searchName, TagKey.Exclude))
      Shortcuts.Remove.matches(key) -> change(searchAfterKey(actions.search, tag.searchName, TagKey.Remove))
      Shortcuts.OpenHere.matches(key) -> change(SearchTags.searchForOpenedTag(tag.searchName))
      Shortcuts.Menu.matches(key) && !newTab -> true.also { menuName = tag.searchName }
      Shortcuts.Back.matches(key) -> true.also { keyFocus.returnToContent() }
      else -> false
    }
  }

  Column(
    Modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused }.onKeyEvent(::onKey).focusable(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    TagListHeader(count = ordered.size, focused = focused)
    groups.forEach { (type, group) ->
      Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (type != null) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(type.color, CircleShape))
            Text(type.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(if (actions.touch) 6.dp else 5.dp),
          verticalArrangement = Arrangement.spacedBy(if (actions.touch) 0.dp else 5.dp),
        ) {
          group.forEach { tag ->
            key(tag.searchName) {
              TagChipItem(
                tag, type, actions,
                selected = focused && tag === ordered.getOrNull(selected),
                menu = menuName == tag.searchName,
                onMenu = { open -> menuName = if (open) tag.searchName else null },
                onPlaced = { chipBounds[tag.searchName] = it },
              )
            }
          }
        }
      }
    }
  }
}

internal fun chipAboveOrBelow(chips: List<Rect?>, from: Int, down: Boolean): Int? {
  val at = chips.getOrNull(from) ?: return null
  val side = chips.withIndex().mapNotNull { (i, r) -> r?.takeIf { i != from }?.let { i to it } }
    .filter { (_, r) -> if (down) r.top >= at.center.y else r.bottom <= at.center.y }
  val nearest = side.minByOrNull { (_, r) -> if (down) r.top else -r.bottom } ?: return null
  return side.filter { it == nearest || (it.second.top < nearest.second.bottom && it.second.bottom > nearest.second.top) }
    .minBy { (_, r) -> abs(r.center.x - at.center.x) }.first
}

@Composable
private fun TagListHeader(count: Int, focused: Boolean) {
  val colors = MaterialTheme.colorScheme
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    if (focused) Box(Modifier.size(width = 3.dp, height = 14.dp).background(colors.primary, CircleShape))
    Text("Tags", style = MaterialTheme.typography.labelLarge, color = if (focused) colors.primary else colors.onSurface)
    Text("$count", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    Spacer(Modifier.weight(1f))
    if (platformIsTouch) return@Row
    if (focused) {
      KeyHint("W A S D", "move")
      KeyHint(Shortcuts.Back.label, "back")
    } else {
      KeyHint(Shortcuts.TagList.label, "keys")
    }
  }
}

@Composable
private fun TagChipItem(
  tag: TagDto,
  type: TagType?,
  actions: TagActions,
  selected: Boolean,
  menu: Boolean,
  onMenu: (Boolean) -> Unit,
  onPlaced: (Rect) -> Unit,
) {
  val route = Route.Grid(SearchTags.searchForOpenedTag(tag.searchName))
  val state = inSearch(tag.searchName, actions.search)
  val following = actions.isFollowing(tag)
  val queued = actions.inQueue(route)
  val inQueue = if (state == InSearch.Included) null else queued
  val openNow = { if (state == InSearch.Included) actions.onAlreadySearching(tag) else actions.onOpen(route) }
  val later = actions.onOpenLater?.let { openLater ->
    when {
      queued == null -> TagLater(TagLaterState.Open, openForLaterLabel(tag.displayName)) { openLater(tag, route) }
      inQueue == null -> null
      else -> {
        val shown = if (actions.openedLater(tag.searchName)) TagLaterState.Opened else TagLaterState.InQueue
        TagLater(shown, goToQueuedLabel(tag.displayName, inQueue), openNow)
      }
    }
  }
  val interactions = remember { MutableInteractionSource() }
  val inView = remember { BringIntoViewRequester() }
  LaunchedEffect(selected) { if (selected) inView.bringIntoView() }
  val ringed = selected || (actions.touch && menu)
  Box(Modifier.zIndex(if (ringed) 1f else 0f).bringIntoViewRequester(inView).onGloballyPositioned { onPlaced(Rect(it.positionInWindow(), it.size.toSize())) }) {
    TagChip(
      name = tag.displayName,
      type = type,
      count = tag.postCount?.let(::compactCount),
      state = when (state) {
        InSearch.Included -> TagChipState.Included
        InSearch.Excluded -> TagChipState.Excluded
        InSearch.No -> TagChipState.Plain
      },
      selected = ringed,
      interactionSource = interactions,
      rejected = actions.followRejected(tag.name),
      modifier = Modifier
        .onContextClick { onMenu(true) }
        .openOnClick(
          onOpen = openNow,
          onOpenInNewTab = { actions.onOpenInNewTab(route) },
          interactionSource = interactions,
          touch = actions.touch,
          onLongPress = { onMenu(true) },
        ),
      touch = actions.touch,
      later = later,
      trailing = { gap ->
        val unfollow = actions.onToggleFollow?.takeUnless { actions.touch }?.let { toggle -> { toggle(tag) } }
        FollowBell(following, onClick = unfollow, gap)
        if (actions.onOpenLater == null) inQueue?.let { QueueMark(it, Modifier.padding(start = gap)) }
      },
    )
    if (selected && !menu) TagKeysCard(tag, type, state)
    TagMenu(
      expanded = menu, onDismiss = { onMenu(false) }, tag = tag, type = type, following = following, actions = actions,
    )
  }
}

@Composable
private fun TagKeysCard(tag: TagDto, type: TagType?, state: InSearch) {
  val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
  Popup(popupPositionProvider = remember(gap) { BelowAnchor(gap) }, properties = PopupProperties(focusable = false)) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.inverseSurface,
      contentColor = MaterialTheme.colorScheme.inverseOnSurface,
      shadowElevation = 4.dp,
      modifier = Modifier.widthIn(max = 240.dp),
    ) {
      Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          listOfNotNull(tag.displayName, type?.name).joinToString(" · "),
          style = MaterialTheme.typography.labelMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val hint = MaterialTheme.colorScheme.inverseOnSurface
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          KeyHint(Shortcuts.Include.label, if (state == InSearch.Included) "remove" else "add", hint)
          KeyHint(Shortcuts.Exclude.label, if (state == InSearch.Excluded) "remove" else "exclude", hint)
          KeyHint("␣", "only this", hint)
          KeyHint(Shortcuts.Menu.label, "menu", hint)
          KeyHint("⇧", "new tab", hint)
        }
      }
    }
  }
}

private class BelowAnchor(private val gap: Int) : PopupPositionProvider {
  override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
    IntOffset(
      anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0),
      anchorBounds.bottom + gap,
    )
}

@Composable
private fun FollowBell(following: Boolean, onClick: (() -> Unit)?, gap: Dp) {
  val swing = remember { Animatable(0f) }
  var rang by remember { mutableStateOf(following) }
  LaunchedEffect(following) {
    if (following && !rang) {
      swing.animateTo(0f, keyframes {
        durationMillis = 700
        18f at 90
        -16f at 220
        10f at 350
        -6f at 480
      })
    }
    rang = following
  }
  AnimatedVisibility(
    following,
    enter = scaleIn(spatialSpec()) + fadeIn(effectsSpec()),
    exit = scaleOut(fastSpatialSpec()) + fadeOut(effectsSpec()),
  ) {
    Box(Modifier.padding(start = gap)) {
      HoverHint(if (onClick != null) "Following · click to unfollow" else "Following") {
        Icon(
          TagFollowed,
          contentDescription = "Following",
          tint = LocalContentColor.current,
          modifier = Modifier
            .size(13.dp)
            .graphicsLayer {
              rotationZ = swing.value
              transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        )
      }
    }
  }
}


internal enum class InSearch { No, Included, Excluded }

internal fun inSearch(tag: String, search: List<String>): InSearch {
  val key = tag.lowercase()
  return when {
    search.any { it.lowercase() == key } -> InSearch.Included
    search.any { it.lowercase() == "-$key" } -> InSearch.Excluded
    else -> InSearch.No
  }
}

internal fun withTag(search: List<String>, tag: String, state: InSearch): List<String> {
  val key = tag.lowercase()
  val entry = when (state) {
    InSearch.No -> null
    InSearch.Included -> tag
    InSearch.Excluded -> "-$tag"
  }
  val at = search.indexOfFirst { it.lowercase().removePrefix("-") == key }
  val next = when {
    at < 0 -> if (entry == null) search else search + entry
    else -> search.toMutableList().apply { if (entry == null) removeAt(at) else set(at, entry) }
  }
  return SearchTags.filtersFirst(next)
}

internal enum class TagKey { Include, Exclude, Remove }

internal fun searchAfterKey(search: List<String>, tag: String, key: TagKey): List<String>? {
  val now = inSearch(tag, search)
  val next = when (key) {
    TagKey.Include -> if (now == InSearch.Included) InSearch.No else InSearch.Included
    TagKey.Exclude -> if (now == InSearch.Excluded) InSearch.No else InSearch.Excluded
    TagKey.Remove -> InSearch.No
  }
  return if (next == now) null else withTag(search, tag, next)
}

private fun Modifier.onContextClick(onMenu: () -> Unit): Modifier = composed {
  val handler by rememberUpdatedState(onMenu)
  this
    .onRightClick { handler() }
    .pointerInput(Unit) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.type != PointerType.Touch) return@awaitEachGesture
        awaitLongPressOrCancellation(down.id)?.let {
          it.consume()
          handler()
        }
      }
    }
}

internal fun humanSize(bytes: Long): String = when {
  bytes >= 1_000_000 -> "${(bytes / 100_000) / 10.0} MB"
  bytes >= 1_000 -> "${bytes / 1_000} kB"
  else -> "$bytes B"
}

internal fun compactCount(count: Int): String = when {
  count >= 1_000_000 -> "${(count / 100_000) / 10.0}M"
  count >= 1_000 -> "${(count / 100) / 10.0}k"
  else -> count.toString()
}

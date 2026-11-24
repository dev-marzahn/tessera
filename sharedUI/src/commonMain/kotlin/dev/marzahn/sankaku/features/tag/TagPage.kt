package dev.marzahn.sankaku.features.tag

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventType
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import dev.marzahn.sankaku.api.dto.TagNames
import dev.marzahn.sankaku.ui.components.EmphasizedAccelerate
import dev.marzahn.sankaku.ui.components.EmphasizedDecelerate
import dev.marzahn.sankaku.ui.components.SCRIM_ALPHA
import dev.marzahn.sankaku.ui.components.SHEET_IN_MS
import dev.marzahn.sankaku.ui.components.SHEET_OUT_MS
import dev.marzahn.sankaku.ui.components.SideSheetWidth
import dev.marzahn.sankaku.ui.components.ButtonHint
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.shell.RetryWhenBack
import dev.marzahn.sankaku.ui.components.AppSnackbarHost
import dev.marzahn.sankaku.ui.components.Shortcuts
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.fonts.icons.arrow_back
import com.google.fonts.icons.chevron_right
import com.google.fonts.icons.grid_view
import com.google.fonts.icons.menu_book
import com.google.fonts.icons.more_vert
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.PlatformBackHandler
import dev.marzahn.sankaku.PlatformSystemBars
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.TagAndWiki
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.data.SearchTags
import dev.marzahn.sankaku.data.TagsRepository
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.features.books.BookReader
import dev.marzahn.sankaku.features.post.BlacklistConfirmation
import dev.marzahn.sankaku.features.post.PostActions
import dev.marzahn.sankaku.features.post.TagBlacklisting
import dev.marzahn.sankaku.features.post.compactCount
import dev.marzahn.sankaku.plainTextClip
import dev.marzahn.sankaku.platformBackKeys
import dev.marzahn.sankaku.tabs.TabId
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.ui.components.AppLoadingIndicator
import dev.marzahn.sankaku.ui.components.ApiErrorState
import dev.marzahn.sankaku.ui.components.GroupedMenu
import dev.marzahn.sankaku.ui.components.MenuAction
import dev.marzahn.sankaku.ui.components.MenuGroup
import dev.marzahn.sankaku.ui.openOnClick
import dev.marzahn.sankaku.ui.theme.color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class OpenTagPage(val tag: TagDto, val item: TabId?, val over: Route?, val typed: Boolean = true)

class TagPages(internal val tabs: TabsController? = null) {
  private val _stack = MutableStateFlow<List<OpenTagPage>>(emptyList())

  val stack: StateFlow<List<OpenTagPage>> = _stack.asStateFlow()

  private val _shown = MutableStateFlow<OpenTagPage?>(null)

  val shown: StateFlow<OpenTagPage?> = _shown.asStateFlow()

  fun open(tag: TagDto) {
    val active = tabs?.activeTab
    set(listOf(OpenTagPage(tag, active?.id, active?.top)))
  }

  internal fun openLinked(tag: WikiTag) {
    val top = _stack.value.lastOrNull() ?: return
    val open = _stack.value.indexOfFirst { it.tag.searchName == tag.searchName }
    if (open >= 0) {
      set(_stack.value.take(open + 1))
      return
    }
    val shown = tag.shown.substringBefore('#').trim()
    val name = if (shown == shown.lowercase()) TagNames.ofKey(tag.searchName) else shown
    set(_stack.value + top.copy(tag = TagDto(name = name, tagName = tag.searchName), typed = false))
  }

  fun back() {
    set(_stack.value.dropLast(1))
  }

  fun close() {
    set(emptyList())
  }

  private fun set(pages: List<OpenTagPage>) {
    _stack.value = pages
    _shown.value = pages.lastOrNull()
  }

  internal var keyReturns by mutableIntStateOf(0)
    private set

  internal fun takeKeys() {
    keyReturns++
  }
}

internal fun tagPageShown(page: OpenTagPage, active: TabId?, activeTop: Route?): Boolean =
  page.item == null || (page.item == active && page.over == activeTop)

@Composable
fun rememberTagPageShown(pages: TagPages = koinInject()): Boolean {
  val open by pages.shown.collectAsStateWithLifecycle()
  val tabs = pages.tabs ?: return open != null
  val all by tabs.tabs.collectAsStateWithLifecycle()
  val active by tabs.active.collectAsStateWithLifecycle()
  val page = open ?: return false
  return tagPageShown(page, active, all.firstOrNull { it.id == active }?.top)
}

fun Modifier.underTagPage(shown: Boolean, pages: TagPages): Modifier =
  if (!shown) {
    this
  } else {
    clearAndSetSemantics {}.onPreviewKeyEvent { event ->
      if (event.type == KeyEventType.KeyDown) {
        if (Shortcuts.Back.matches(event.key)) pages.back() else pages.takeKeys()
      }
      true
    }
  }

@Composable
fun TagPageHost(
  pages: TagPages = koinInject(),
  tabs: TabsController = koinInject(),
  snackbar: SnackbarHostState? = null,
  sheet: Boolean = !platformIsTouch,
  onKeysBack: () -> Unit = {},
) {
  val shownNow = rememberTagPageShown(pages)
  var wasShown by remember { mutableStateOf(false) }
  val keysBack by rememberUpdatedState(onKeysBack)
  LaunchedEffect(shownNow) {
    if (!shownNow && wasShown) keysBack()
    wasShown = shownNow
  }
  val stack by pages.stack.collectAsStateWithLifecycle()
  val lastOpen = remember { mutableStateOf(emptyList<OpenTagPage>()) }
  SideEffect { if (stack.isNotEmpty()) lastOpen.value = stack }
  val slide = remember { Animatable(1f) }
  LaunchedEffect(stack.isEmpty()) {
    if (stack.isNotEmpty()) {
      if (sheet) slide.animateTo(0f, tween(SHEET_IN_MS, easing = EmphasizedDecelerate)) else slide.snapTo(0f)
    } else {
      if (sheet && lastOpen.value.isNotEmpty()) slide.animateTo(1f, tween(SHEET_OUT_MS, easing = EmphasizedAccelerate))
      lastOpen.value = emptyList()
    }
  }
  val closing = sheet && stack.isEmpty() && lastOpen.value.isNotEmpty()
  val drawn = if (closing) lastOpen.value else stack
  if (drawn.isEmpty()) return
  val all by tabs.tabs.collectAsStateWithLifecycle()
  LaunchedEffect(stack, all) { stack.firstOrNull()?.item?.let { item -> if (all.none { it.id == item }) pages.close() } }
  Box(Modifier.fillMaxSize()) {
    if (sheet && (shownNow || closing)) {
      Box(
        Modifier
          .fillMaxSize()
          .testTag(TAG_PAGE_SCRIM)
          .graphicsLayer { alpha = SCRIM_ALPHA * (1f - slide.value) }
          .background(Color.Black)
          .pointerInput(closing) { if (!closing) detectTapGestures { pages.close() } },
      )
    }
    Box(
      if (sheet) {
        Modifier
          .align(Alignment.CenterEnd)
          .widthIn(max = SideSheetWidth)
          .fillMaxWidth()
          .fillMaxHeight()
          .graphicsLayer { translationX = slide.value * size.width }
          .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
      } else {
        Modifier.fillMaxSize()
      },
    ) {
      drawn.forEachIndexed { i, open ->
        val top = i == drawn.lastIndex
        key(i, open) {
          TagPage(
            open.tag,
            onClose = pages::back,
            shown = shownNow && top,
            leaving = closing && top,
            typed = open.typed,
            keyReturns = pages.keyReturns,
            snackbar = snackbar,
            color = if (sheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
            onOpenTag = pages::openLinked,
          )
        }
      }
    }
  }
}

internal sealed interface TagPageState {
  data object Loading : TagPageState

  data class Loaded(val page: TagAndWiki) : TagPageState

  data class Failed(val error: ApiError) : TagPageState {
    val unreachable: Boolean get() = error is ApiError.Network
  }
}

internal fun postsCount(count: Int): String = if (count == 1) "1 post" else "${compactCount(count)} posts"

internal fun booksCount(count: Int): String = if (count == 1) "1 book" else "${compactCount(count)} books"

@Composable
internal fun TagPage(
  seed: TagDto,
  onClose: () -> Unit,
  shown: Boolean = true,
  leaving: Boolean = false,
  typed: Boolean = true,
  keyReturns: Int = 0,
  snackbar: SnackbarHostState? = null,
  color: Color = MaterialTheme.colorScheme.surface,
  onOpenTag: (WikiTag) -> Unit = {},
  touch: Boolean = platformIsTouch,
  tags: TagsRepository = koinInject(),
  navigator: Navigator = koinInject(),
  reader: BookReader = koinInject(),
  session: AuthSession = koinInject(),
  actions: PostActions = koinInject(),
  blacklisting: TagBlacklisting = koinInject(),
) {
  var attempt by remember { mutableIntStateOf(0) }
  val state by produceState<TagPageState>(TagPageState.Loading, seed.searchName, attempt) {
    value = TagPageState.Loading
    value = try {
      TagPageState.Loaded(tags.page(seed.searchName))
    } catch (e: CancellationException) {
      throw e
    } catch (e: ApiError) {
      TagPageState.Failed(e)
    }
  }
  var retryWhenShown by remember { mutableStateOf(false) }
  RetryWhenBack {
    if ((state as? TagPageState.Failed)?.unreachable == true) {
      if (shown) attempt++ else retryWhenShown = true
    }
  }
  LaunchedEffect(shown) {
    if (shown && retryWhenShown) {
      retryWhenShown = false
      attempt++
    }
  }
  val loaded = (state as? TagPageState.Loaded)?.page
  val tag = loaded?.tag?.let { it.copy(name = seed.name, tagName = it.tagName ?: seed.tagName) } ?: seed
  val signedIn by session.isSignedIn.collectAsState()
  val overrides by actions.follows.overrides.collectAsState()
  val following = actions.follows.isFollowing(tag, overrides)
  val focus = remember { FocusRequester() }
  LaunchedEffect(shown, keyReturns) { if (shown) focus.requestFocus() }
  var backArmed by remember { mutableStateOf(false) }
  LaunchedEffect(shown) {
    backArmed = false
    if (shown) {
      withFrameNanos {}
      backArmed = true
    }
  }
  if (backArmed) PlatformBackHandler(enabled = true, onBack = onClose)
  if (shown) PlatformSystemBars(hidden = false, lightIcons = MaterialTheme.colorScheme.surface.luminance() < 0.5f)

  fun open(route: Route) {
    reader.close()
    navigator.goTo(route)
  }

  val posts = Route.Grid(SearchTags.searchForOpenedTag(tag.searchName))
  val books = Route.Books(listOf(tag.searchName))
  Surface(
    Modifier
      .then(
        when {
          shown -> Modifier
          leaving -> Modifier.clearAndSetSemantics {}
          else -> Modifier.clearAndSetSemantics {}.layout { measurable, constraints -> measurable.measure(constraints).let { layout(0, 0) {} } }
        },
      )
      .fillMaxSize()
      .semantics { paneTitle = tag.displayName }
      .focusProperties {
        onExit = {
          if (shown && (requestedFocusDirection == FocusDirection.Next || requestedFocusDirection == FocusDirection.Previous)) {
            cancelFocusChange()
          }
        }
      }
      .focusRequester(focus)
      .focusable(enabled = shown)
      .platformBackKeys(onClose),
    color = color,
  ) {
    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        TopRow(tag, signedIn, onClose, blacklisting)
        Column(
          Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 24.dp),
        ) {
          Header(tag, typed || loaded != null, following, onToggleFollow = { actions.toggleFollow(tag) }.takeIf { signedIn && tag.id != null })
          if (loaded == null || tag.postCount != 0) {
            OpenRow(grid_view, POSTS, tag.postCount?.let(::postsCount), onOpen = { open(posts) }, onOpenInNewTab = { navigator.openInNewTab(posts) })
          }
          if (loaded == null || tag.poolCount != 0) {
            OpenRow(menu_book, BOOKS, tag.poolCount?.let(::booksCount), onOpen = { open(books) }, onOpenInNewTab = { navigator.openInNewTab(books) })
          }
          when (val s = state) {
            TagPageState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
              AppLoadingIndicator()
            }
            is TagPageState.Failed -> ApiErrorState(
              s.error,
              onRetry = { attempt++ },
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              onSignIn = koinInject<SignInController>()::open,
            )
            is TagPageState.Loaded -> s.page.wiki?.body?.takeIf { it.isNotBlank() }?.let { body ->
              About(
                body,
                onTag = onOpenTag,
                onTagForLater = { navigator.openInNewTab(Route.Grid(SearchTags.searchForOpenedTag(it))) },
                touch = touch,
              )
            }
          }
        }
      }
      if (snackbar != null && shown) {
        AppSnackbarHost(
          snackbar,
          swipeable = platformIsTouch,
          if (platformIsTouch) {
            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars)
          } else {
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
          },
        )
      }
    }
  }
}

@Composable
private fun TopRow(tag: TagDto, signedIn: Boolean, onClose: () -> Unit, blacklisting: TagBlacklisting) {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  var menu by remember { mutableStateOf(false) }
  var confirming by remember { mutableStateOf(false) }
  Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
    ButtonHint("Back (${Shortcuts.Back.label})") { IconButton(onClick = onClose) { Icon(arrow_back, contentDescription = "Back") } }
    Text(TAG, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge)
    Box {
      ButtonHint("More") { IconButton(onClick = { menu = true }) { Icon(more_vert, contentDescription = "More") } }
      GroupedMenu(expanded = menu, onDismissRequest = { menu = false }) {
        MenuGroup(0, 1) {
          MenuAction(
            text = { Text(COPY_NAME) },
            onClick = {
              menu = false
              scope.launch { clipboard.setClipEntry(plainTextClip(tag.searchName)) }
            },
          )
          if (signedIn) {
            MenuAction(
              text = { Text(BLACKLIST) },
              onClick = {
                menu = false
                confirming = true
              },
            )
          }
        }
      }
    }
  }
  if (confirming) {
    BlacklistConfirmation(tag, blacklisting::usedRules, onDismiss = { confirming = false }, onConfirm = { blacklisting.blacklist(tag) })
  }
}

@Composable
private fun Header(tag: TagDto, typed: Boolean, following: Boolean, onToggleFollow: (() -> Unit)?) {
  val colors = MaterialTheme.colorScheme
  Column(
    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      Modifier.heightIn(min = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (typed) {
        Box(Modifier.size(10.dp).background(tag.category.color, CircleShape))
        Text(tag.category.name, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
      }
    }
    Text(
      tag.displayName,
      Modifier.semantics { heading() },
      style = MaterialTheme.typography.headlineSmall,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
    )
    if (onToggleFollow != null) {
      if (following) {
        OutlinedButton(onClick = onToggleFollow) { Text(FOLLOWING) }
      } else {
        FilledTonalButton(onClick = onToggleFollow) { Text(FOLLOW) }
      }
    }
  }
}

@Composable
private fun OpenRow(icon: ImageVector, label: String, count: String?, onOpen: () -> Unit, onOpenInNewTab: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .openOnClick(onOpen = onOpen, onOpenInNewTab = onOpenInNewTab)
      .semantics(mergeDescendants = true) { role = Role.Button }
      .padding(start = 24.dp, end = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant)
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    if (count != null) Text(count, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    Icon(chevron_right, contentDescription = null, tint = colors.onSurfaceVariant)
  }
}

@Composable
private fun About(body: String, onTag: (WikiTag) -> Unit, onTagForLater: (String) -> Unit, touch: Boolean = platformIsTouch) {
  val linkColor = MaterialTheme.colorScheme.primary
  val tagClicked by rememberUpdatedState(onTag)
  val forLater by rememberUpdatedState(onTagForLater)
  val text = remember(body, linkColor) { wikiText(body, linkColor) { tagClicked(it) } }
  if (text.isEmpty()) return
  var expanded by remember { mutableStateOf(false) }
  var overflows by remember { mutableStateOf(false) }
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val haptics = LocalHapticFeedback.current

  fun tagAt(at: Offset): String? {
    val laid = layout ?: return null
    val near = laid.getOffsetForPosition(at)
    val char = listOf(near, near - 1).firstOrNull { it in text.indices && laid.getBoundingBox(it).contains(at) } ?: return null
    return text.getLinkAnnotations(char, char + 1).firstNotNullOfOrNull { (it.item as? LinkAnnotation.Clickable)?.tag }
  }
  HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
  Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(ABOUT, Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      text,
      Modifier
        .pointerInput(text) {
          awaitPointerEventScope {
            var middleAt: Offset? = null
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              when (event.type) {
                PointerEventType.Press -> middleAt = event.changes.firstOrNull()?.position?.takeIf { event.buttons.isTertiaryPressed }
                PointerEventType.Release -> middleAt?.let { at ->
                  middleAt = null
                  tagAt(at)?.let(forLater)
                }
              }
            }
          }
        }
        .pointerInput(text, touch) {
          if (!touch) return@pointerInput
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tag = tagAt(down.position) ?: return@awaitEachGesture
            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
              while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
              }
              true
            }
            if (ended == null) {
              haptics.performHapticFeedback(HapticFeedbackType.Confirm)
              forLater(tag)
              do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
              } while (event.changes.any { it.pressed })
            }
          }
        },
      style = MaterialTheme.typography.bodyMedium,
      maxLines = if (expanded) Int.MAX_VALUE else WIKI_LINES,
      overflow = TextOverflow.Ellipsis,
      onTextLayout = {
        layout = it
        if (!expanded) overflows = it.hasVisualOverflow
      },
    )
    if (overflows || expanded) {
      TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) LESS else MORE) }
    }
  }
}

private const val WIKI_LINES = 4
internal const val TAG_PAGE_SCRIM = "tag-page-scrim"
internal const val TAG = "Tag"
internal const val POSTS = "Posts"
internal const val BOOKS = "Books"
internal const val FOLLOW = "Follow"
internal const val FOLLOWING = "Following"
private const val ABOUT = "About"
private const val MORE = "More"
private const val LESS = "Less"
private const val COPY_NAME = "Copy name"
private const val BLACKLIST = "Blacklist tag…"

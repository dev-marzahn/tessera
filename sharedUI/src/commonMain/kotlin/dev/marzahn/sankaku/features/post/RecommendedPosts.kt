package dev.marzahn.sankaku.features.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.data.PostOverride
import dev.marzahn.sankaku.data.PostStateStore
import dev.marzahn.sankaku.data.PostTitle
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.features.grid.Rating
import dev.marzahn.sankaku.features.grid.ratingLocked
import dev.marzahn.sankaku.images.PostImage
import dev.marzahn.sankaku.images.postImageRequest
import dev.marzahn.sankaku.shell.LocalItemVisibility
import dev.marzahn.sankaku.shell.LocalPostHandoff
import dev.marzahn.sankaku.shell.PostHandoff
import dev.marzahn.sankaku.shell.sharedPicture
import dev.marzahn.sankaku.tabs.LocalTabEntry
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.ui.components.AlreadyInTab
import dev.marzahn.sankaku.ui.components.FavouriteBadgeButton
import dev.marzahn.sankaku.ui.components.InTabChip
import dev.marzahn.sankaku.ui.components.rememberAlreadyInTab
import dev.marzahn.sankaku.ui.openOnClick
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs

@Composable
fun RecommendedPosts(
  postId: String,
  modifier: Modifier = Modifier,
  navigator: Navigator = koinInject(),
  repository: PostsRepository = koinInject(),
  tabs: TabsController = koinInject(),
) {
  PostStrip(SheetStrip.Recommended, postId, modifier, navigator = navigator, repository = repository, tabs = tabs) {
    StripPosts(repository.recommended(postId, it))
  }
}

data class StripPosts(val posts: List<PostDto>, val more: Route? = null)

enum class SheetStrip(val title: String, val failed: String, val label: String, val placeholders: Int) {
  Parent("Parent", "Couldn't load the parent post", "Parent post", 1),
  Children("Children", "Couldn't load the child posts", "Child post", 4),
  Recommended("Recommended", "Couldn't load recommendations", "Recommended post", 4),
}

@Composable
internal fun PostStrip(
  strip: SheetStrip,
  postId: String,
  modifier: Modifier = Modifier,
  navigator: Navigator = koinInject(),
  repository: PostsRepository = koinInject(),
  session: AuthSession = koinInject(),
  privacy: PrivacySettings = koinInject(),
  tabs: TabsController = koinInject(),
  store: PostStateStore = koinInject(),
  load: suspend (narrowedBy: List<String>) -> StripPosts,
) {
  val signedIn by session.isSignedIn.collectAsState()
  val gOnly by privacy.gOnly.collectAsState()
  val narrowedBy = if (ratingLocked(signedIn, gOnly)) listOf(Rating.G.tag) else emptyList()
  val loader by rememberUpdatedState(load)
  var onScreen by remember { mutableStateOf(false) }
  var attempt by remember { mutableIntStateOf(0) }
  var state by remember(postId, narrowedBy) { mutableStateOf<StripState>(StripState.Loading) }
  LaunchedEffect(postId, narrowedBy, attempt) {
    snapshotFlow { onScreen }.first { it }
    state = StripState.Loading
    state = try {
      StripState.Loaded(loader(narrowedBy))
    } catch (_: ApiError) {
      StripState.Failed
    }
  }
  val current = state
  if (current is StripState.Loaded && current.strip.posts.isEmpty()) return

  val row = remember(postId) { LazyListState() }
  val tabList by tabs.tabs.collectAsState()
  val openInTabs = remember(tabList) { tabs.openPosts(tabList) }
  val overrides by store.overrides.collectAsState()
  val alreadyInTab = rememberAlreadyInTab()
  DisposableEffect(Unit) { onDispose { onScreen = false } }
  Column(
    modifier.onGloballyPositioned { onScreen = it.boundsInWindow().let { b -> b.width > 0f && b.height > 0f } },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(strip.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    when (current) {
      StripState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          strip.failed,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { attempt++ }) { Text("Retry") }
      }
      StripState.Loading -> LazyRow(horizontalArrangement = Arrangement.spacedBy(THUMB_GAP), userScrollEnabled = false) {
        items(strip.placeholders) {
          Box(Modifier.size(THUMB_WIDTH, THUMB_HEIGHT).clip(THUMB_SHAPE).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
      }
      is StripState.Loaded -> LazyRow(
        state = row,
        horizontalArrangement = Arrangement.spacedBy(THUMB_GAP),
        modifier = Modifier.wheelScrollsSideways(row),
      ) {
        items(current.strip.posts, key = { it.id }) { post ->
          val openInTab = post.id in openInTabs
          PostThumb(
            post,
            strip = strip,
            favourited = favouritedIn(post, overrides),
            openInTab = openInTab,
            asOne = opensAsOne(post, openInTab, gOnly),
            alreadyInTab = alreadyInTab,
            navigator = navigator,
            repository = repository,
            tabs = tabs,
          )
        }
        current.strip.more?.let { more ->
          item(key = MORE_KEY) { MoreTile(more, navigator) }
        }
      }
    }
  }
}

internal fun favouritedIn(post: PostDto, overrides: Map<String, PostOverride>): Boolean =
  overrides[post.id]?.isFavourited ?: post.isFavorited

internal fun opensAsOne(post: PostDto, openInTab: Boolean, gOnly: Boolean): Boolean =
  !openInTab && post.fileUrl != null && !(gOnly && Rating.ofCode(post.rating) != Rating.G)

@Composable
private fun PostThumb(
  post: PostDto,
  strip: SheetStrip,
  favourited: Boolean,
  openInTab: Boolean,
  asOne: Boolean,
  alreadyInTab: AlreadyInTab,
  navigator: Navigator,
  repository: PostsRepository,
  tabs: TabsController,
) {
  val context = LocalPlatformContext.current
  val name = PostTitle.of(post)
  val route = Route.Post(post.id, post.md5, emptyList(), name, post.rating)
  val label = (name ?: strip.label).let { if (favourited) "$it, favourited" else it }
  val visibility = LocalItemVisibility.current
  val handoff = LocalPostHandoff.current?.takeIf { asOne && visibility != null }
  val from = LocalTabEntry.current?.tab?.id
  val scope = rememberCoroutineScope()
  val sharing = from != null && handoff?.sharing(from, post.id, strip) == true
  Box(
    Modifier
      .sharedPicture(post.id, visibility, enabled = sharing, slow = true, clip = THUMB_SHAPE)
      .size(THUMB_WIDTH, THUMB_HEIGHT)
      .clip(THUMB_SHAPE)
      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
      .semantics { contentDescription = label }
      .openOnClick(
        onOpen = {
          if (handoff == null || from == null) {
            navigator.goTo(route)
          } else {
            scope.launch {
              handoff.open(from, post.id, active = { tabs.active.value }, strip = strip) {
                repository.hold(post)
                navigator.goTo(route)
              }
            }
          }
        },
        onOpenInNewTab = {
          val existing = navigator.openInNewTab(route)
          if (existing != null) alreadyInTab.show(post.id, existing)
        },
        inTab = openInTab,
      ),
  ) {
    AsyncImage(
      model = postImageRequest(context, PostImage.of(post, Variant.Preview)),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    if (openInTab) InTabChip(Modifier.align(Alignment.BottomStart).padding(6.dp), alreadyInTab.of(post.id))
    PostMediaBadge(post,Modifier.align(Alignment.TopStart).padding(6.dp))
    FavouriteBadgeButton(
      favourited = favourited,
      expanded = false,
      onToggle = {},
      modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clearAndSetSemantics {},
    )
  }
}

@Composable
private fun MoreTile(more: Route, navigator: Navigator) {
  Box(
    Modifier
      .size(THUMB_WIDTH, THUMB_HEIGHT)
      .clip(THUMB_SHAPE)
      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
      .openOnClick({ navigator.goTo(more) }, { navigator.openInNewTab(more) }),
    contentAlignment = Alignment.Center,
  ) {
    Text("All", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
  }
}

private fun Modifier.wheelScrollsSideways(row: LazyListState): Modifier = pointerInput(row) {
  val step = WHEEL_STEP.toPx()
  awaitPointerEventScope {
    while (true) {
      val event = awaitPointerEvent()
      if (event.type != PointerEventType.Scroll || event.keyboardModifiers.isShiftPressed) continue
      val change = event.changes.firstOrNull() ?: continue
      val delta = change.scrollDelta
      if (delta.y == 0f || abs(delta.x) >= abs(delta.y)) continue
      if (row.dispatchRawDelta(delta.y * step) != 0f) change.consume()
    }
  }
}

private sealed interface StripState {
  data object Loading : StripState
  data object Failed : StripState
  data class Loaded(val strip: StripPosts) : StripState
}

private val THUMB_WIDTH = 72.dp
private val THUMB_HEIGHT = 96.dp
private val THUMB_GAP = 8.dp
private val THUMB_SHAPE = RoundedCornerShape(10.dp)
private const val MORE_KEY = "more"

private val WHEEL_STEP = 52.dp

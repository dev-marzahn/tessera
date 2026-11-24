package dev.marzahn.sankaku.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.isBooks
import dev.marzahn.sankaku.data.BookSearch
import dev.marzahn.sankaku.searchTags
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.data.RecentSearches
import dev.marzahn.sankaku.data.TagsRepository
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.features.grid.GridCursorRequest
import dev.marzahn.sankaku.features.grid.FullScreenSearch
import dev.marzahn.sankaku.features.grid.SearchPanel
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.components.FullScreenSearchState
import dev.marzahn.sankaku.ui.components.effectsSpec
import dev.marzahn.sankaku.ui.components.spatialSpec
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
internal fun AuthSession.signedInName(): String? =
  (state.collectAsStateWithLifecycle().value as? AuthState.SignedIn)?.user?.name

val LocalOpenSearch = staticCompositionLocalOf<() -> Unit> { {} }

internal fun opensSearch(next: List<String>, searched: List<String>, current: Route?): Boolean =
  next != searched || current is Route.Post || current is Route.BookSeries

internal fun searchRoute(next: List<String>, current: Route?): Route =
  if (current?.isBooks == true) Route.Books(BookSearch.submitted(next, current.searchTags)) else Route.Grid(next)

private const val CURSOR_REQUEST_FRAMES = 5

@Composable
fun SearchOverlay(
  open: Boolean,
  current: Route?,
  anchor: Rect,
  fullScreen: FullScreenSearchState,
  onClose: () -> Unit,
  navigator: Navigator = koinInject(),
  cursorRequest: GridCursorRequest = koinInject(),
  session: AuthSession = koinInject(),
  privacy: PrivacySettings = koinInject(),
  tags: TagsRepository = koinInject(),
  recent: RecentSearches = koinInject(),
  signIn: SignInController = koinInject(),
  tabs: TabsController = koinInject(),
) {
  val gOnly by privacy.gOnly.collectAsStateWithLifecycle()
  val queue = tabs.tabs.collectAsStateWithLifecycle()
  val positions by remember(tabs, queue) { derivedStateOf { tabs.queuePositions(queue.value) } }
  val inQueue = { tag: String -> positions(Route.Grid(listOf(tag))) }
  val onSignIn = { signIn.open() }
  val onTurnOffGOnly = if (privacy.gOnlyForced) null else ({ privacy.setGOnly(false) })
  var origin by remember { mutableStateOf(Offset.Zero) }
  val scope = rememberCoroutineScope()
  val signedIn by session.isSignedIn.collectAsStateWithLifecycle()
  val me = session.signedInName()
  val known by tags.known.collectAsStateWithLifecycle()
  LaunchedEffect(open) { if (open) tags.lookUp(recent.searches.value.flatten()) }
  val searched = current?.searchTags.orEmpty()
  if (platformIsTouch) {
    FullScreenSearch(
      state = fullScreen,
      open = open,
      initial = searched,
      signedIn = signedIn,
      safeOnly = gOnly,
      recent = recent,
      suggest = tags::suggest,
      known = known,
      me = me,
      onSignIn = onSignIn,
      onTurnOffGOnly = onTurnOffGOnly,
      inQueue = inQueue,
      onSubmit = { next ->
        onClose()
        if (opensSearch(next, searched, current)) navigator.goToTyped(searchRoute(next, current))
      },
      onClose = onClose,
    )
    return
  }
  val scrimAlpha by animateFloatAsState(if (open) 1f else 0f, effectsSpec())
  val scrimDim = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.55f else 0.40f
  Box(Modifier.fillMaxSize().onPlaced { origin = it.positionInRoot() }) {
    if (scrimAlpha > 0f) {
      Box(
        Modifier
          .fillMaxSize()
          .graphicsLayer { alpha = scrimAlpha }
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimDim))
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
      )
    }
    val width = with(LocalDensity.current) { anchor.width.toDp() }
    AnimatedVisibility(
      visible = open,
      enter = fadeIn(effectsSpec()) + expandVertically(spatialSpec(), expandFrom = Alignment.Top),
      exit = fadeOut(effectsSpec()) + shrinkVertically(spatialSpec(), shrinkTowards = Alignment.Top),
      modifier = Modifier
        .offset { IntOffset((anchor.left - origin.x).roundToInt(), (anchor.top - origin.y).roundToInt()) }
        .width(width)
        .widthIn(min = 320.dp),
    ) {
      SearchPanel(
        initial = searched,
        signedIn = signedIn,
        safeOnly = gOnly,
        recent = recent,
        suggest = tags::suggest,
        known = known,
        me = me,
        onSignIn = onSignIn,
        onTurnOffGOnly = onTurnOffGOnly,
        inQueue = inQueue,
        onSubmit = { next ->
          onClose()
          if (opensSearch(next, searched, current)) {
            cursorRequest.request()
            navigator.goToTyped(searchRoute(next, current))
            scope.launch {
              repeat(CURSOR_REQUEST_FRAMES) { withFrameNanos {} }
              cursorRequest.lapse()
            }
          }
        },
        onClose = onClose,
      )
    }
  }
}

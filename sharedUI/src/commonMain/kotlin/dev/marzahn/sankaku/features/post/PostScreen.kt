package dev.marzahn.sankaku.features.post

import dev.marzahn.sankaku.shell.RetryWhenBack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedVisibilityScope
import dev.marzahn.sankaku.shell.LocalHandoffEntering
import dev.marzahn.sankaku.shell.LocalUnderQueuePage
import dev.marzahn.sankaku.shell.SharedTransitions
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.data.PostOverride
import dev.marzahn.sankaku.data.PostStateStore
import dev.marzahn.sankaku.data.PostTitle
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.merge
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.features.grid.PostViewer
import dev.marzahn.sankaku.features.grid.PhoneViewer
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.tabs.QueueBar
import dev.marzahn.sankaku.tabs.LocalOpenTabList
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.features.grid.PostOrigin
import dev.marzahn.sankaku.tabs.Ahead
import dev.marzahn.sankaku.tabs.ClosedTabUndo
import dev.marzahn.sankaku.tabs.LATER_HINT_POSTS
import dev.marzahn.sankaku.tabs.LocalTabEntry
import dev.marzahn.sankaku.tabs.TabState
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.tabs.UpNextState
import dev.marzahn.sankaku.tabs.kindLabel
import dev.marzahn.sankaku.tabs.queueSwipe
import dev.marzahn.sankaku.tabs.tabTitle
import dev.marzahn.sankaku.features.grid.ViewerMode
import dev.marzahn.sankaku.tabs.DescribeTab
import dev.marzahn.sankaku.tabs.TabContents
import dev.marzahn.sankaku.ui.components.AppLoadingIndicator
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import dev.marzahn.sankaku.ui.components.ApiErrorState
import dev.marzahn.sankaku.ui.components.KeyFocusHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

sealed interface PostUiState {
  data object Loading : PostUiState

  data class Loaded(val post: PostDto) : PostUiState

  data class Failed(val error: ApiError) : PostUiState {
    val unreachable: Boolean get() = error is ApiError.Network
  }
}

class PostViewModel(
  private val id: String,
  private val repository: PostsRepository,
  store: PostStateStore,
  session: AuthSession,
) : ViewModel() {
  private val loaded = MutableStateFlow(repository.heldPost(id)?.let(PostUiState::Loaded) ?: PostUiState.Loading)

  val state: StateFlow<PostUiState> = combine(loaded, store.overrides, ::withOverrides)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), withOverrides(loaded.value, store.overrides.value))

  private fun withOverrides(state: PostUiState, overrides: Map<String, PostOverride>) =
    if (state is PostUiState.Loaded) PostUiState.Loaded(state.post.merge(overrides[state.post.id])) else state

  init {
    if (loaded.value !is PostUiState.Loaded) load()
    viewModelScope.launch { session.isSignedIn.drop(1).collect { if (loaded.value is PostUiState.Failed) load() } }
  }

  fun load() {
    loaded.value = PostUiState.Loading
    viewModelScope.launch {
      loaded.value = try {
        PostUiState.Loaded(repository.cachedPost(id) ?: repository.fullPost(id))
      } catch (e: ApiError) {
        PostUiState.Failed(e)
      }
    }
  }
}

internal fun postOrigin(tab: TabState, route: Route.Post, tabs: TabsController, navigator: Navigator): PostOrigin? {
  val label = postOriginLabel(tab, route, tabs) ?: return null
  val source = tabs.sourceOf(tab)
  return PostOrigin(label) { if (source != null) tabs.jumpTo(source.id) else navigator.goTo(Route.Grid(route.originTags)) }
}

internal fun postOriginLabel(tab: TabState, route: Route.Post, tabs: TabsController): String? {
  tabs.sourceOf(tab)?.let { source -> return kindLabel(tab, source) }
  if (route.originTags.isEmpty()) return null
  return "From ${tabTitle(Route.Grid(route.originTags), names = tabs.tagNames)}"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PostScreen(
  route: Route.Post,
  navigator: Navigator = koinInject(),
  actions: PostActions = koinInject(),
  tabs: TabsController = koinInject(),
  undo: ClosedTabUndo = koinInject(),
  upNext: UpNextState = koinInject(),
  session: AuthSession = koinInject(),
  signIn: SignInController = koinInject(),
  touch: Boolean = platformIsTouch,
) {
  val viewModel = koinViewModel<PostViewModel>(key = "post-${route.id}") { parametersOf(route.id) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val signedIn by session.isSignedIn.collectAsStateWithLifecycle()
  RetryWhenBack { if ((viewModel.state.value as? PostUiState.Failed)?.unreachable == true) viewModel.load() }
  val loadedPost = (state as? PostUiState.Loaded)?.post
  val name = loadedPost?.let(PostTitle::of)
  if (route.label == null && loadedPost != null) LaunchedEffect(route.id, name) { tabs.loadedTitle(route, name) }
  DescribeTab(route) { TabContents.post(route.id, route.md5 ?: (viewModel.state.value as? PostUiState.Loaded)?.post?.md5) }
  val tab = LocalTabEntry.current?.tab
  val keptHere = tab?.takeIf { it.kind.isKept && it.backStack.size == 1 }
  val tabList by tabs.tabs.collectAsStateWithLifecycle()
  val origin = remember(tab, tabList, route) { tab?.let { postOrigin(it, route, tabs, navigator) } }
  Surface(Modifier.fillMaxSize()) {
    when (val s = state) {
      is PostUiState.Loaded -> SharedTransitions {
        val underPage = (LocalHandoffEntering.current ?: LocalUnderQueuePage.current)?.takeIf { touch }
        if (touch) {
          val shown = remember { MutableTransitionState(true) }
          val phone: @Composable AnimatedVisibilityScope.() -> Unit = {
            PhoneViewer(
              index = 0,
              postAt = { s.post },
              loadedCount = 1,
              hasMore = false,
              sharedId = s.post.id.takeIf { underPage != null },
              visibilityScope = this,
              onShow = {},
              onClose = navigator::goBack,
              mode = ViewerMode.Tab,
              searchTags = route.originTags,
              origin = origin,
              onDragClose = tab?.let { { undo.dragClose(it.id, upNext) } },
              queueFrom = tab?.id,
              onDrop = keptHere?.let { { undo.dragClose(it.id, upNext) } },
              laterHint = keptHere != null && tabs.laterStreak < LATER_HINT_POSTS && upNext.ahead() != Ahead.Nothing,
            )
          }
          if (underPage != null) {
            underPage.phone()
          } else {
            AnimatedVisibility(shown, enter = EnterTransition.None, exit = ExitTransition.None) { phone() }
          }
          return@SharedTransitions
        }
        AnimatedVisibility(remember { MutableTransitionState(true) }, enter = EnterTransition.None, exit = ExitTransition.None) {
          PostViewer(
            index = 0,
            postAt = { s.post },
            loadedCount = 1,
            hasMore = false,
            sharedId = null,
            visibilityScope = this,
            onStep = {},
            onClose = navigator::goBack,
            mode = ViewerMode.Tab,
            searchTags = route.originTags,
          )
        }
      }
      else -> Box(
        Modifier
          .fillMaxSize()
          .postShortcuts(post = { null }, actions = actions, onBack = navigator::goBack)
          .queueSwipe(enabled = true, from = tab?.id),
        contentAlignment = Alignment.Center,
      ) {
        KeyFocusHolder()
        if (s is PostUiState.Failed) {
          ApiErrorState(s.error, onRetry = viewModel::load, onSignIn = signIn::open, signedIn = signedIn)
        } else {
          AppLoadingIndicator()
        }
        if (touch) {
          QueueBar(
            onOpenQueue = LocalOpenTabList.current,
            bottom = BottomRowLayout.WindowRoom,
            modifier = Modifier.align(Alignment.BottomCenter),
          )
        }
      }
    }
  }
}

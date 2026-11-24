package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.GridSettings
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.features.grid.gridCellRatio
import dev.marzahn.sankaku.features.grid.gridCellRequest
import dev.marzahn.sankaku.features.grid.searchedTags
import dev.marzahn.sankaku.features.post.decodedInMemory
import dev.marzahn.sankaku.features.post.firstShownRequests
import dev.marzahn.sankaku.features.grid.gridMinCellWidth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun PreloadNeighbours(
  shown: TabState,
  neighbours: List<TabState>,
  repository: PostsRepository = koinInject(),
  imageLoader: ImageLoader = koinInject(),
  session: AuthSession = koinInject(),
  privacy: PrivacySettings = koinInject(),
  grid: GridSettings = koinInject(),
): Map<String, LoadedNeighbour> {
  val context = LocalPlatformContext.current
  val window = LocalWindowInfo.current.containerSize
  val minCellDp = gridMinCellWidth(grid)
  val minCellWidth = with(LocalDensity.current) { minCellDp.roundToPx() }
  val auth by session.state.collectAsStateWithLifecycle()
  val gOnly by privacy.gOnly.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  val loads = remember { mutableMapOf<Preload, Job>() }
  val ready = remember { mutableStateMapOf<String, LoadedNeighbour>() }
  fun preloadOf(tab: TabState, place: (Route.Grid) -> GridPlace? = tab::placeFor): Preload? = when (val top = tab.top) {
    is Route.Post -> Preload.Post(top.id)
    is Route.Grid -> if (auth is AuthState.Restoring) {
      null
    } else {
      Preload.Grid(searchedTags(top.tags, auth is AuthState.SignedIn, gOnly), place(top))
    }
    is Route.Books, is Route.BookSeries, is Route.Book, null -> null
  }
  val near = neighbours.mapNotNull { preloadOf(it) }.distinct()
  val keep = near + listOfNotNull(preloadOf(shown) { Snapshot.withoutReadObservation { shown.placeFor(it) } })
  LaunchedEffect(near, keep, window, minCellWidth) {
    loads.keys.filter { it !in keep }.forEach { loads.remove(it)?.cancel() }
    ready.keys.filter { Preload.Post(it) !in keep }.forEach { ready.remove(it) }
    if (window.width <= 0 || window.height <= 0) return@LaunchedEffect
    for (preload in near) {
      if (preload in loads) continue
      loads[preload] = when (preload) {
        is Preload.Post -> scope.preloadPost(preload.id, repository, imageLoader, context, window.width, window.height) {
          ready[preload.id] = it
        }
        is Preload.Grid -> scope.launch {
          val posts = try {
            repository.gridStart(preload.tags, preload.place)
          } catch (_: ApiError) {
            return@launch
          }
          val shown = preload.place?.let { place -> posts.dropWhile { it.id != place.post }.ifEmpty { null } } ?: posts
          firstScreen(shown, window.width, window.height, minCellWidth)
            .map { launch { imageLoader.execute(gridCellRequest(context, it)) } }
            .joinAll()
        }
      }
    }
  }
  return ready
}

private sealed interface Preload {
  data class Post(val id: String) : Preload

  data class Grid(val tags: List<String>, val place: GridPlace?) : Preload
}

private fun CoroutineScope.preloadPost(
  id: String,
  repository: PostsRepository,
  imageLoader: ImageLoader,
  context: PlatformContext,
  width: Int,
  height: Int,
  onReady: (LoadedNeighbour) -> Unit,
): Job = launch {
  val post = repository.cachedPost(id) ?: try {
    repository.fullPost(id)
  } catch (_: ApiError) {
    return@launch
  }
  firstShownRequests(context, post, width, height).map { request ->
    launch {
      imageLoader.execute(request)
      decodedInMemory(imageLoader, post.md5)?.let { onReady(LoadedNeighbour(post, it)) }
    }
  }.joinAll()
}

internal fun firstScreen(posts: List<PostDto>, windowWidth: Int, windowHeight: Int, minCellWidth: Int): List<PostDto> {
  if (windowWidth <= 0 || windowHeight <= 0) return emptyList()
  val lanes = (windowWidth / minCellWidth.coerceAtLeast(1)).coerceAtLeast(1)
  val laneWidth = windowWidth.toFloat() / lanes
  val heights = FloatArray(lanes)
  val shown = mutableListOf<PostDto>()
  for (post in posts) {
    if (post.redirectToSignup) continue
    val lane = heights.indices.minBy { heights[it] }
    if (heights[lane] >= windowHeight) break
    heights[lane] += laneWidth / gridCellRatio(post)
    shown += post
  }
  return shown
}

class LoadedNeighbour(val post: PostDto, val picture: Image)

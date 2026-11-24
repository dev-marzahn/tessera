package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.data.SearchTags
import dev.marzahn.sankaku.features.grid.Rating
import dev.marzahn.sankaku.features.grid.searchedTags
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.queue.SourceKey
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.images.PostImage
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class SourcePreview(
  val thumbs: List<QueueThumb>,
  val about: Int? = null,
  val loaded: Int = 0,
  val ended: Boolean = false,
  val g: Boolean = false,
) {
  val foundNothing: Boolean get() = ended && loaded == 0

  val size: SourceSize get() = about?.let(SourceSize::About) ?: SourceSize.Counted(loaded, more = !ended)

  fun isFor(searched: List<String>): Boolean = g == isGSearch(searched)

  override fun toString() = "SourcePreview(thumbs=${thumbs.size}, about=$about, loaded=$loaded, ended=$ended, g=$g)"

  companion object {
    const val SIZE = 9

    fun of(posts: List<PostDto>, searched: List<String>, more: Boolean): SourcePreview? =
      ofPages(listOf(PreviewPage.of(posts, searched, more)), searched)

    fun ofPages(pages: List<PreviewPage>, searched: List<String>): SourcePreview? {
      val last = pages.lastOrNull() ?: return null
      val loaded = pages.sumOf { it.shown }
      if (loaded == 0 && last.more) return null
      return SourcePreview(
        thumbs = pages.flatMap { it.thumbs }.take(SIZE),
        about = pages.firstNotNullOfOrNull { it.about },
        loaded = loaded,
        ended = !last.more,
        g = isGSearch(searched),
      )
    }

    internal fun aboutOf(posts: List<PostDto>, searched: List<String>): Int? {
      val tag = oneTag(searched) ?: return null
      return posts.firstNotNullOfOrNull { post -> post.tags.firstOrNull { it.searchName.equals(tag, ignoreCase = true) }?.postCount }
    }
  }
}

class PreviewPage(val thumbs: List<QueueThumb>, val shown: Int, val about: Int?, val more: Boolean) {
  companion object {
    fun of(posts: List<PostDto>, searched: List<String>, more: Boolean): PreviewPage {
      val shown = posts.filterNot { it.redirectToSignup }
      return PreviewPage(
        thumbs = shown.take(SourcePreview.SIZE).map { QueueThumb(it.md5, Rating.ofCode(it.rating)?.code) },
        shown = shown.size,
        about = SourcePreview.aboutOf(shown, searched),
        more = more,
      )
    }
  }
}

sealed interface SourceSize {
  data class About(val posts: Int) : SourceSize

  data class Counted(val posts: Int, val more: Boolean) : SourceSize
}

data class SourceProgress(val position: Int?, val size: SourceSize?)

internal fun oneTag(searched: List<String>): String? {
  val words = searched.map { it.trim() }.filter { it.isNotEmpty() && !it.removePrefix("-").startsWith("order:", ignoreCase = true) }
  val tag = words.singleOrNull() ?: return null
  return tag.takeUnless { it.startsWith("-") || SearchTags.isFilter(it) }
}

private fun isGSearch(searched: List<String>): Boolean = searched.any { Rating.of(it) == setOf(Rating.G) }

fun hasPreview(route: Route?): Boolean =
  route is Route.Grid && QueueKind.ofGrid(route.tags) == QueueKind.Source && SourceKey.of(route.tags) != null

fun isNewSource(tab: TabState): Boolean = hasPreview(tab.top) && !tab.topIsTouchedGrid

fun sourceProgress(tab: TabState): SourceProgress? {
  if (!hasPreview(tab.top)) return null
  return SourceProgress(position = tab.place?.index?.plus(1), size = tab.preview?.size)
}

class SourcePreviews(
  private val repository: PostsRepository,
  private val session: AuthSession,
  private val privacy: PrivacySettings,
) {
  private val lock = Mutex()

  private val loading = mutableMapOf<Pair<TabId, List<String>>, CompletableDeferred<Boolean>>()

  private val pictures = mutableStateMapOf<String, PostImage>()

  fun shown(tab: TabState, gOnly: Boolean): SourcePreview? {
    val route = tab.top as? Route.Grid ?: return null
    val auth = session.state.value.takeIf { it !is AuthState.Restoring } ?: return null
    return tab.preview?.takeIf { it.isFor(searchedTags(route.tags, auth is AuthState.SignedIn, gOnly)) }
  }

  fun picture(thumb: QueueThumb): PostImage = pictures[thumb.md5] ?: thumb.image

  suspend fun cardPosts(tab: TabState, searched: List<String>): List<PostDto>? {
    val route = tab.top as? Route.Grid ?: return null
    if (!hasPreview(route)) return null
    val page = try {
      repository.firstPage(searched)
    } catch (_: ApiError) {
      return null
    }
    val more = page.meta.next != null
    if (tab.preview?.isFor(searched) != true) SourcePreview.of(page.data, searched, more)?.let { tab.reportPreview(route, it) }
    return page.data.filterNot { it.redirectToSignup }
  }

  suspend fun newestUpload(searched: List<String>): Instant? {
    val byDate = searched.filterNot { it.removePrefix("-").startsWith("order:", ignoreCase = true) }
    val page = try {
      repository.firstPage(byDate)
    } catch (_: ApiError) {
      return null
    }
    return page.data.firstOrNull { !it.redirectToSignup }?.createdAt
  }

  suspend fun load(tab: TabState) {
    val route = tab.top as? Route.Grid ?: return
    if (!hasPreview(route)) return
    val auth = session.state.first { it !is AuthState.Restoring }
    val searched = searchedTags(route.tags, auth is AuthState.SignedIn, privacy.gOnly.value)
    val key = tab.id to searched
    while (tab.preview?.isFor(searched) != true) {
      val (waitFor, mine) = lock.withLock {
        loading[key]?.let { it to false } ?: (CompletableDeferred<Boolean>().also { loading[key] = it } to true)
      }
      if (!mine) {
        if (waitFor.await()) return else continue
      }
      var tried = false
      try {
        val page = repository.firstPage(searched)
        page.data.filterNot { it.redirectToSignup }.take(SourcePreview.SIZE).forEach { pictures[it.md5] = PostImage.of(it, Variant.Preview) }
        SourcePreview.of(page.data, searched, more = page.meta.next != null)?.let { tab.reportPreview(route, it) }
        tried = true
      } catch (_: ApiError) {
        tried = true
      } finally {
        withContext(NonCancellable) { lock.withLock { loading.remove(key) } }
        waitFor.complete(tried)
      }
      return
    }
  }
}

internal const val CARD_POSTS = 15

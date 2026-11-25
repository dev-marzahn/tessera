package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.images.PostImage

fun interface TabDescription {
  fun contents(): TabContents

  companion object {
    fun grid(loaded: List<PostDto>, firstShown: Int) = TabDescription { TabContents.grid(loaded, firstShown) }
  }
}

class TabContents(
  val preview: List<PostImage>,
  val images: List<PostImage>,
  val posts: Int,
) : TabDescription {
  override fun contents(): TabContents = this

  companion object {
    private const val GRID_PREVIEW_COUNT = 6

    fun grid(loaded: List<PostDto>, firstShown: Int): TabContents = TabContents(
      preview = shownFrom(loaded, firstShown, GRID_PREVIEW_COUNT).map { PostImage.of(it, Variant.Preview) },
      images = loaded.flatMap { post -> Variant.entries.map { PostImage.of(post, it) } },
      posts = loaded.size,
    )

    fun post(id: String, md5: String?): TabContents {
      if (md5 == null) return TabContents(emptyList(), emptyList(), 1)
      return TabContents(
        preview = listOf(PostImage(id, md5, Variant.Preview, null)),
        images = Variant.entries.map { PostImage(id, md5, it, null) },
        posts = 1,
      )
    }
  }
}

class Described internal constructor(internal val route: Route, private val shown: () -> TabDescription) {
  internal fun contents(): TabContents = shown().contents()

  internal fun lastShown(): Described {
    val last = shown()
    return Described(route) { last }
  }
}

class TabEntry(val tab: TabState, val contentKey: Any)

val LocalTabEntry = staticCompositionLocalOf<TabEntry?> { null }

@Composable
fun DescribeTab(route: Route, shown: () -> TabDescription) {
  val entry = LocalTabEntry.current ?: return
  val latest by rememberUpdatedState(shown)
  DisposableEffect(entry.tab, entry.contentKey, route) {
    val registered = entry.tab.describe(entry.contentKey, route) { latest() }
    onDispose { entry.tab.keepLastShown(entry.contentKey, registered) }
  }
}

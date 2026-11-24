package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.images.PostImage

class TabContents(
  val preview: List<PostImage>,
  val images: List<PostImage>,
  val posts: Int,
) {
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

class TabEntry(val tab: TabState, val contentKey: Any)

val LocalTabEntry = staticCompositionLocalOf<TabEntry?> { null }

@Composable
fun DescribeTab(route: Route, describe: () -> TabContents) {
  val entry = LocalTabEntry.current ?: return
  SideEffect { entry.tab.describe(entry.contentKey, route, describe) }
}

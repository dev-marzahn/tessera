package dev.marzahn.sankaku.features.settings

import coil3.ImageLoader
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.ReadingProgress
import dev.marzahn.sankaku.data.RecentSearches
import dev.marzahn.sankaku.data.TagsRepository
import dev.marzahn.sankaku.features.post.clearVideoCache
import dev.marzahn.sankaku.queue.FeedMarksStore
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.tabs.TabStateStore
import dev.marzahn.sankaku.tabs.TabsController

class ClearEverything(
  private val tabs: TabsController,
  private val tabStore: TabStateStore,
  private val recent: RecentSearches,
  private val knownTags: TagsRepository,
  private val posts: PostsRepository,
  private val images: ImageLoader,
  private val seen: SeenStore,
  private val readings: ReadingProgress,
  private val feedMarks: FeedMarksStore,
  private val clearVideos: suspend () -> Unit = ::clearVideoCache,
  private val today: TodayStore? = null,
) {
  suspend fun run() {
    ClearSavedTabs(tabs, tabStore).run()
    seen.clear()
    feedMarks.clear()
    today?.clear()
    readings.clear()
    recent.clear()
    knownTags.forget()
    posts.clearCaches()
    ClearCache(images, clearVideos).run()
  }
}

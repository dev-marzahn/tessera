package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.chevron_right
import com.google.fonts.icons.group
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.features.grid.feedPostsOf
import dev.marzahn.sankaku.features.grid.searchedTags
import dev.marzahn.sankaku.queue.FeedHeading
import dev.marzahn.sankaku.queue.FeedMarks
import dev.marzahn.sankaku.queue.FeedMarksStore
import dev.marzahn.sankaku.queue.FeedPost
import dev.marzahn.sankaku.queue.FeedReading
import dev.marzahn.sankaku.ui.components.TonalButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant

internal data class FollowingNews(val new: Int?, val more: Boolean = false, val left: Int? = null, val hasGap: Boolean = left != null) {
  val catchUpAt: FeedCatchUp.At?
    get() = when {
      new == null -> null
      new == 0 && hasGap -> FeedCatchUp.At.Gap
      else -> FeedCatchUp.At.Top
    }

  companion object {
    fun of(marks: FeedMarks?): FollowingNews = FollowingNews(new = null, left = marks?.gap?.left, hasGap = marks?.gap != null)

    fun of(marks: FeedMarks?, posts: List<FeedPost>, moreAfter: Boolean, now: Instant): FollowingNews {
      if (marks == null) return of(null)
      return when (val top = FeedReading.headings(Feed.Following, posts, atTop = true, marks, now).firstOrNull { it.before == 0 }?.heading) {
        is FeedHeading.New -> FollowingNews(top.count, more = top.more && moreAfter, left = top.gapLeft, hasGap = top.hasGap)
        is FeedHeading.UpToDate -> FollowingNews(0, left = top.gapLeft, hasGap = top.hasGap)
        else -> FollowingNews(0, left = marks.gap?.left, hasGap = marks.gap != null)
      }
    }
  }
}

internal fun followingWords(news: FollowingNews, kept: Int): List<String> {
  val parts = mutableListOf<String>()
  when (val new = news.new) {
    null -> Unit
    0 -> parts += "caught up"
    else -> parts += "${grouped(new)}${if (news.more) "+" else ""} new"
  }
  when {
    news.left != null && news.left > 0 -> parts += "${grouped(news.left)} left from last time"
    news.left == null && news.hasGap -> parts += "more left from last time"
  }
  if (kept > 0) parts += "${grouped(kept)} kept"
  if (parts.isEmpty()) parts += "Feed"
  return parts.mapIndexed { at, part -> if (at == 0) part.replaceFirstChar { it.uppercase() } else part }
}

class FollowingNewsSource(
  private val repository: PostsRepository,
  private val marks: FeedMarksStore,
  private val session: AuthSession,
  private val privacy: PrivacySettings,
  private val clock: Clock = Clock.System,
) {
  internal fun known(): FollowingNews = FollowingNews.of(marks.get(Feed.Following))

  internal suspend fun load(feed: TabState): FollowingNews {
    val shown = marks.get(Feed.Following)
    val route = feed.top as? Route.Grid ?: return FollowingNews.of(shown)
    val auth = session.state.first { it !is AuthState.Restoring }
    val searched = searchedTags(route.tags, auth is AuthState.SignedIn, privacy.gOnly.value)
    val page = try {
      repository.firstPage(searched)
    } catch (_: ApiError) {
      return FollowingNews.of(shown)
    }
    return FollowingNews.of(shown, feedPostsOf(page.data) { null }, moreAfter = page.meta.next != null, clock.now())
  }
}

class FeedCatchUp {
  enum class At { Top, Gap }

  class Request(val tab: TabId, val at: At)

  private val _request = MutableStateFlow<Request?>(null)

  val request: StateFlow<Request?> = _request.asStateFlow()

  fun ask(tab: TabId, at: At) {
    _request.value = Request(tab, at)
  }

  fun take(request: Request) {
    _request.compareAndSet(request, null)
  }
}

@Composable
internal fun FollowingLine(news: FollowingNews, kept: Int, onCatchUp: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val words = followingWords(news, kept)
  val newWords = words.first().takeIf { news.new != null && news.new > 0 }
  Row(
    modifier
      .padding(horizontal = 12.dp, vertical = 4.dp)
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(colors.surfaceContainer)
      .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
      .testTag(FOLLOWING_LINE_TAG),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(group, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(20.dp))
    Column(
      Modifier.weight(1f).clearAndSetSemantics { contentDescription = "Following, ${words.joinToString(", ")}" },
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(Feed.Following.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
      Text(
        buildAnnotatedString {
          words.forEachIndexed { at, part ->
            if (at > 0) append(" · ")
            if (part == newWords) withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium)) { append(part) } else append(part)
          }
        },
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        maxLines = rowTextLines(),
        overflow = TextOverflow.Ellipsis,
      )
    }
    TonalButton("Catch up", chevron_right, onCatchUp)
  }
}

internal const val FOLLOWING_LINE_TAG = "following line"

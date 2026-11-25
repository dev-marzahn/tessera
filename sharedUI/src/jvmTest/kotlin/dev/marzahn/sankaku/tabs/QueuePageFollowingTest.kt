package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.AuthApi
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.api.PostsApi
import dev.marzahn.sankaku.api.RateLimiter
import dev.marzahn.sankaku.api.createAuthClient
import dev.marzahn.sankaku.api.createMainClient
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PostStateStore
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.log.RequestLog
import dev.marzahn.sankaku.net.ConnectivityState
import dev.marzahn.sankaku.queue.FeedGap
import dev.marzahn.sankaku.queue.FeedMark
import dev.marzahn.sankaku.queue.FeedMarks
import dev.marzahn.sankaku.queue.FeedMarksStore
import dev.marzahn.sankaku.queue.QueueKind
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class QueuePageFollowingTest {
  private var now = Instant.parse("2026-11-15T17:00:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val seen = SeenStore(InMemoryTextFile(), clock)
  private val today = TodayStore(InMemoryTextFile(), clock, { TimeZone.UTC })
  private val privacy = PrivacySettings(InMemoryTextFile())
  private val tabs = TabsController(clock = clock, today = today)
  private val marks = FeedMarksStore(InMemoryTextFile(), clock)
  private val catchUp = FeedCatchUp()
  private var dismissed = 0

  private val engine = MockEngine { request ->
    if (request.url.encodedPath != "/v2/posts/keyset") error("unexpected request")
    val posts = listOf("n3" to "2026-11-15T16:00:00Z", "n2" to "2026-11-15T15:00:00Z", "n1" to "2026-11-15T14:00:00Z", "f3" to "2026-11-14T10:00:00Z")
      .joinToString(",") { (id, at) -> """{"id":"$id","md5":"m$id","rating":"s","status":"active","created_at":{"s":${Instant.parse(at).epochSeconds}},"tags":[]}""" }
    respond(
      """{"meta":{"next":"c2","prev":null},"data":[$posts]}""",
      HttpStatusCode.OK,
      headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
  }
  private val scope = CoroutineScope(SupervisorJob())

  @AfterTest
  fun tearDown() {
    scope.cancel()
  }

  private fun news(): FollowingNewsSource {
    val log = RequestLog()
    val limiter = RateLimiter(limits = emptyMap())
    val connectivity = ConnectivityState()
    val session = AuthSession(AuthApi(createAuthClient(engine, log, limiter, connectivity)), InMemoryTokenStore())
    runBlocking { session.restore() }
    val repository = PostsRepository(PostsApi(createMainClient(engine, log, limiter, connectivity, session)), PostStateStore(), session, scope)
    return FollowingNewsSource(repository, marks, session, privacy, clock)
  }

  private fun ComposeUiTest.show(following: FollowingNewsSource? = null, onSort: (() -> Unit)? = null) {
    setContent {
      SankakuTheme {
        Box(Modifier.requiredSize(412.dp, 915.dp)) {
          PhoneQueuePage(
            onDismiss = { dismissed++ }, controller = tabs, posts = TabPosts.None, privacy = privacy, seen = seen,
            interacted = { true }, onInteract = {},
            today = today, onSort = onSort, following = following, catchUp = catchUp,
          )
        }
      }
    }
    waitForIdle()
  }

  private fun keepFromFollowing(id: String) {
    tabs.activate(feed().id)
    tabs.openInBlock(listOf(Route.Post(id, md5 = "m$id", originTags = emptyList(), label = "Post $id", rating = "s")))
  }

  private fun feed() = tabs.tabs.value.single { it.kind == QueueKind.Feed }

  @Test
  fun signedInTheFollowingLineSaysWhatsNewLeftAndKeptAndCatchUpShowsTheFeedWhereItStands() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("artist_x")), background = true)
    tabs.pinFeeds(listOf(Feed.Following))
    keepFromFollowing("k1")
    keepFromFollowing("k2")
    tabs.open(Route.Grid(listOf("artist_y")), background = true)
    tabs.activate(tabs.tabs.value.first().id)
    marks.set(
      Feed.Following,
      FeedMarks(
        top = FeedMark("f3", Instant.parse("2026-11-14T10:00:00Z").epochSeconds),
        gap = FeedGap(FeedMark("f9", 100), FeedMark("f1", 50), left = 30, since = 0),
        at = 0,
      ),
    )
    val order = tabs.tabs.value.map { it.id }
    show(news())

    waitUntil(timeoutMillis = 5_000) {
      runCatching { onNodeWithContentDescription("Following, 3 new, 30 left from last time, 2 kept").assertExists() }.isSuccess
    }
    onNodeWithText("3 new · 30 left from last time · 2 kept", useUnmergedTree = true).assertExists()

    onNodeWithText("Catch up").performClick()
    waitForIdle()
    assertEquals(feed().id, tabs.active.value)
    assertEquals(order, tabs.tabs.value.map { it.id })
    assertEquals(feed().id, catchUp.request.value?.tab)
    assertEquals(FeedCatchUp.At.Top, catchUp.request.value?.at)
    assertEquals(1, dismissed)
  }

  @Test
  fun signedOutThereIsNoFollowingLine() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("artist_x")), background = true)
    show(news())

    onNodeWithTag(FOLLOWING_LINE_TAG).assertDoesNotExist()
  }

  @Test
  fun openedScrolledItSaysHowManyRowsAreAboveAndATapGoesToTheTop() = runComposeUiTest {
    (1..30).forEach { tabs.open(Route.Grid(listOf("tag_$it")), background = true) }
    tabs.activate(tabs.tabs.value[20].id)
    show()

    onNodeWithText("18 more above").assertExists()
    onNodeWithTag(MORE_ABOVE_TAG).performClick()
    waitForIdle()
    onNodeWithTag(MORE_ABOVE_TAG).assertDoesNotExist()
    onNodeWithContentDescription("Item 1 of 31").assertExists()
  }

  @Test
  fun openedAtTheTopThereIsNoCue() = runComposeUiTest {
    (1..30).forEach { tabs.open(Route.Grid(listOf("tag_$it")), background = true) }
    tabs.activate(tabs.tabs.value[1].id)
    show()

    onNodeWithTag(MORE_ABOVE_TAG).assertDoesNotExist()
  }

  @Test
  fun sortClearViewedAndContinueSitOnOneLineAtTheBottom() = runComposeUiTest {
    val route = Route.Grid(listOf("artist_new"))
    tabs.open(route, background = true)
    tabs.tabs.value.last().reportPreview(route, SourcePreview((1..9).map { QueueThumb("mn$it", "s") }, about = 80, loaded = 30))
    tabs.openInBlock(listOf(Route.Post("p1", md5 = "mp1", originTags = emptyList(), label = "Post p1", rating = "s")))
    now += 1.minutes
    seen.record("p1", "mp1")
    show(onSort = {})

    val actions = onNodeWithTag(QUEUE_ACTIONS_TAG).getBoundsInRoot()
    val sort = onNodeWithContentDescription("Sort 1 new").getBoundsInRoot()
    val clear = onNodeWithContentDescription("Clear viewed", substring = true).getBoundsInRoot()
    val proceed = onNodeWithContentDescription("Continue", substring = true).getBoundsInRoot()
    val chips = onNodeWithText("All").getBoundsInRoot()
    listOf(clear, proceed).forEach { assertEquals((sort.top + sort.bottom) / 2, (it.top + it.bottom) / 2) }
    assertTrue(sort.right <= clear.left && clear.right <= proceed.left, "$sort, $clear, $proceed")
    assertTrue(actions.top > 915.dp / 2 && actions.top > chips.bottom, "$actions")
  }

  @Test
  fun aCurrentItemLastInTheListOpensClearOfTheActions() = runComposeUiTest {
    (1..30).forEach { tabs.open(Route.Grid(listOf("tag_$it")), background = true) }
    tabs.activate(tabs.tabs.value.last().id)
    show(onSort = {})

    val row = onNodeWithContentDescription("Item 31 of 31").getBoundsInRoot()
    val actions = onNodeWithTag(QUEUE_ACTIONS_TAG).getBoundsInRoot()
    assertTrue(row.bottom <= actions.top, "the current row $row is clear of $actions")
  }

  @Test
  fun theCurrentItemIsNotAlsoNew() = runComposeUiTest {
    val fresh = Route.Grid(listOf("artist_new"))
    val current = Route.Grid(listOf("artist_shown"))
    listOf(fresh, current).forEach { route ->
      tabs.open(route, background = true)
      tabs.tabs.value.last().reportPreview(route, SourcePreview((1..9).map { QueueThumb("m${route.tags[0]}$it", "s") }, about = 80, loaded = 30))
    }
    tabs.activate(tabs.tabs.value.last().id)
    show()

    assertTrue(isNewSource(tabs.activeTab))
    assertEquals(1, onAllNodesWithTag(NEW_LABEL_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
  }
}

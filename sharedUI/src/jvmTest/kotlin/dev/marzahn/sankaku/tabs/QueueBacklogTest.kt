package dev.marzahn.sankaku.tabs

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.AuthApi
import dev.marzahn.sankaku.api.PostsApi
import dev.marzahn.sankaku.api.RateLimiter
import dev.marzahn.sankaku.api.createAuthClient
import dev.marzahn.sankaku.api.createMainClient
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.GridPresets
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PostStateStore
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.log.RequestLog
import dev.marzahn.sankaku.net.ConnectivityState
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.ui.Messages
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class QueueBacklogTest {
  private var now = Instant.parse("2026-11-14T12:00:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val seen = SeenStore(InMemoryTextFile(), clock)
  private val today = TodayStore(InMemoryTextFile(), clock, { TimeZone.UTC })
  private val privacy = PrivacySettings(InMemoryTextFile())
  private var tabs = TabsController(clock = clock, today = today)

  private val pageRequests: MutableList<String> = CopyOnWriteArrayList()
  private val engine = MockEngine { request ->
    if (request.url.encodedPath != "/v2/posts/keyset") error("unexpected request")
    val tags = request.url.parameters["tags"].orEmpty()
    pageRequests += tags
    val search = tags.split(' ').first { !it.startsWith("rating:") }
    val posts = (1..30).joinToString(",") { n -> """{"id":"$search-$n","md5":"m$search-$n","rating":"s","status":"active","tags":[]}""" }
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

  private fun previews(): SourcePreviews {
    val log = RequestLog()
    val limiter = RateLimiter(limits = emptyMap())
    val connectivity = ConnectivityState()
    val session = AuthSession(AuthApi(createAuthClient(engine, log, limiter, connectivity)), InMemoryTokenStore())
    runBlocking { session.restore() }
    val repository = PostsRepository(PostsApi(createMainClient(engine, log, limiter, connectivity, session)), PostStateStore(), session, scope)
    return SourcePreviews(repository, session, privacy)
  }

  private fun ComposeUiTest.show(previews: SourcePreviews? = null) {
    setContent {
      SankakuTheme {
        QueuePage(
          onDismiss = {}, controller = tabs, posts = TabPosts.None, privacy = privacy, seen = seen,
          clearViewed = ClearViewed(tabs, seen, Messages()), interacted = { true }, onInteract = {},
          previews = previews, today = today,
        )
      }
    }
    waitForIdle()
  }

  private fun thumbs(count: Int) = (1..count).map { QueueThumb("m$it", "s") }

  private fun source(search: String, preview: SourcePreview?, index: Int? = null): TabState {
    val route = Route.Grid(search.split(' '))
    tabs.open(route, background = true)
    val tab = tabs.tabs.value.last { it.top == route }
    preview?.let { tab.reportPreview(route, it) }
    if (index != null) tab.reportPlace(route, GridPlace("p$index", index = index))
    return tab
  }

  private fun keep(id: String) = tabs.openInBlock(listOf(Route.Post(id, md5 = "m$id", originTags = emptyList(), label = "Post $id", rating = "s")))

  @Test
  fun aNewSourcesRowHasNewAndItsFirstFivePosts() = runComposeUiTest {
    source("artist_x", SourcePreview(thumbs(9), about = 1900, loaded = 30))
    show()

    onNodeWithTag(NEW_LABEL_TAG, useUnmergedTree = true).assertExists()
    onNodeWithText("Newest · about 1,900 posts", useUnmergedTree = true).assertExists()
    val strip = onNodeWithTag(STRIP_TAG, useUnmergedTree = true).getBoundsInRoot()
    assertEquals(52.dp * 5 + 3.dp * 4, strip.right - strip.left)
    onNodeWithTag(PROGRESS_TAG, useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun aNewSourceWhoseGridHasShownHasOnlyItsStrip() = runComposeUiTest {
    val shown = Route.Grid(listOf("artist_x"))
    tabs.open(shown, background = false)
    tabs.tabs.value.last { it.top == shown }.apply {
      reportPreview(shown, SourcePreview(thumbs(9), about = 1900, loaded = 30))
      reportMosaic(shown, QueueMosaic(thumbs(4), posts = 30))
    }
    val other = source("artist_y", SourcePreview(thumbs(9), about = 1900, loaded = 30))
    show()

    val current = tabs.tabs.value.last { it.top == shown }
    assertTrue(isNewSource(current), "opened, not scrolled: still new")
    assertTrue(current.mosaic != null, "with a mosaic")
    assertEquals(1, onAllNodesWithTag(NEW_LABEL_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    assertEquals(2, onAllNodesWithTag(STRIP_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    onNodeWithTag(mosaicThumbTag(0), useUnmergedTree = true).assertDoesNotExist()
    val lines = onAllNodesWithText("about 1,900 posts", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
    assertEquals(2, lines.size)
    assertEquals(lines[1].boundsInRoot.left, lines[0].boundsInRoot.left)
    assertEquals(
      onAllNodesWithText("about 1,900 posts", substring = true, useUnmergedTree = true)[0].getBoundsInRoot().left,
      onNodeWithText(current.title, useUnmergedTree = true).getBoundsInRoot().left,
    )
  }

  @Test
  fun anEmptySourceSaysNoPostsWithNoLoadingTiles() = runComposeUiTest {
    source("artist_x", SourcePreview(emptyList(), ended = true))
    show()

    onNodeWithTag(NEW_LABEL_TAG, useUnmergedTree = true).assertExists()
    onNodeWithText("Newest · no posts", useUnmergedTree = true).assertExists()
    onNodeWithTag(STRIP_TAG, useUnmergedTree = true).assertDoesNotExist()
    onNodeWithTag(PROGRESS_TAG, useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun aStartedSourceSaysHowFarThroughItIsWithABar() = runComposeUiTest {
    source("artist_x", SourcePreview(thumbs(9), about = 240, loaded = 60), index = 37)
    show()

    onNodeWithText("Newest · 38 of about 240", useUnmergedTree = true).assertExists()
    onNodeWithTag(NEW_LABEL_TAG, useUnmergedTree = true).assertDoesNotExist()
    onNodeWithTag(STRIP_TAG, useUnmergedTree = true).assertDoesNotExist()
    val bar = onNodeWithTag(PROGRESS_TAG, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
    assertEquals(38f / 240, bar.current, 0.001f)
  }

  @Test
  fun aMultiTagSourceCountsWhatsLoaded() = runComposeUiTest {
    source("landscape sky", SourcePreview(thumbs(9), loaded = 400, ended = false), index = 119)
    show()

    onNodeWithText("Newest · 120 of 400+").assertExists()
  }

  @Test
  fun keptPostsFoldIntoOneLineThatOpensAndClosesWithTalkBacksActions() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("artist_x")), background = false)
    keep("p1")
    keep("p2")
    keep("p3")
    now += 1.minutes
    seen.record("p2", "mp2")
    show()

    val fold = onNodeWithContentDescription("1 seen · 2 not seen")
    onNodeWithText("Post p1").assertDoesNotExist()
    assertTrue(fold.fetchSemanticsNode().config.getOrNull(SemanticsActions.Expand) != null)
    assertTrue(fold.fetchSemanticsNode().config.getOrNull(SemanticsActions.Collapse) == null)

    fold.performSemanticsAction(SemanticsActions.Expand)
    waitForIdle()
    onNodeWithText("Post p1").assertExists()
    onNodeWithText("Post p3").assertExists()
    assertTrue(fold.fetchSemanticsNode().config.getOrNull(SemanticsActions.Collapse) != null)

    fold.performClick()
    waitForIdle()
    onNodeWithText("Post p1").assertDoesNotExist()
  }

  @Test
  fun anOpenFoldsKeptPostsMoveAsBeforeAndFindListsThemFolded() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("artist_x")), background = false)
    keep("p1")
    keep("p2")
    show()

    onNodeWithContentDescription("2 not seen").performSemanticsAction(SemanticsActions.Expand)
    waitForIdle()
    val row = onNodeWithText("Post p1", useUnmergedTree = true).fetchSemanticsNode()
    val parent = generateSequence(row) { it.parent }.first { it.config.getOrNull(SemanticsActions.CustomActions) != null }
    parent.config[SemanticsActions.CustomActions].first { it.label == "Move down" }.action()
    waitForIdle()
    assertEquals(listOf("Post p2", "Post p1"), tabs.tabs.value.filter { it.kind.isKept }.map { it.title })

    onNodeWithContentDescription("2 not seen").performSemanticsAction(SemanticsActions.Collapse)
    onNodeWithContentDescription("Find in queue").performClick()
    waitForIdle()
    onNode(hasSetTextAction()).performTextInput("p2")
    waitForIdle()
    onNodeWithText("Post p2").assertExists()
  }

  @Test
  fun theCurrentKeptPostsBlockOpensWithThePage() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("artist_x")), background = false)
    keep("p1")
    keep("p2")
    tabs.activate(tabs.tabs.value.last().id)
    show()

    onNodeWithText("Post p2").assertExists()
    onNodeWithContentDescription("2 not seen").fetchSemanticsNode().config[SemanticsActions.Collapse]
  }

  @Test
  fun theChipsListOnlyTheNewOrTheStartedSources() = runComposeUiTest {
    val fresh = source("artist_new", SourcePreview(thumbs(9), about = 85, loaded = 30))
    val started = source("artist_started", SourcePreview(thumbs(9), about = 240, loaded = 60), index = 37)
    show()

    onNodeWithText("New · 1").performClick()
    waitForIdle()
    onNodeWithText(fresh.title).assertExists()
    onNodeWithText(started.title).assertDoesNotExist()
    onNodeWithText("Browse").assertDoesNotExist()

    onNodeWithText("Started · 1").performClick()
    waitForIdle()
    onNodeWithText(fresh.title).assertDoesNotExist()
    onNodeWithText(started.title).assertExists()

    onNodeWithText("All").performClick()
    waitForIdle()
    onNodeWithText(fresh.title).assertExists()
    onNodeWithText(started.title).assertExists()
    onNodeWithText("Browse").assertExists()
  }

  @Test
  fun todaysCountsFollowAFindACloseASeenPostAndAFavourite() = runComposeUiTest {
    show()
    onNodeWithContentDescription("0 found today").assertExists()

    runOnIdle { tabs.open(Route.Grid(listOf("artist_x")), background = true) }
    onNodeWithContentDescription("1 found today").assertExists()
    assertEquals(0, today.counts().done)

    runOnIdle { tabs.close(tabs.tabs.value.last().id) }
    assertEquals(1, today.counts().done)
    onNodeWithContentDescription("0 kept posts gone through today").assertExists()

    runOnIdle { seen.record("p1", "m1") }
    onNodeWithContentDescription("1 post seen today").assertExists()

    runOnIdle { today.liked("p1", on = true) }
    onNodeWithContentDescription("1 liked today").assertExists()

    runOnIdle { tabs.reopen(tabs.closed.value.last()) }
    assertEquals(0, today.counts().done)
    onNodeWithContentDescription("1 found today").assertExists()
  }

  @Test
  fun goneThroughCountsKeptPostsDroppedOrSeenToday() = runComposeUiTest {
    keep("p1")
    keep("p2")
    keep("p3")
    show()
    onNodeWithContentDescription("0 kept posts gone through today").assertExists()
    onNodeWithText("gone through", useUnmergedTree = true).assertExists()
    onNodeWithText("seen", useUnmergedTree = true).assertExists()

    val p1 = tabs.tabs.value.first { (it.top as? Route.Post)?.id == "p1" }
    runOnIdle { tabs.close(p1.id) }
    onNodeWithContentDescription("1 kept post gone through today").assertExists()

    now += 1.minutes
    runOnIdle { seen.record("p2", "mp2") }
    onNodeWithContentDescription("2 kept posts gone through today").assertExists()

    runOnIdle { tabs.reopen(tabs.closed.value.last()) }
    onNodeWithContentDescription("1 kept post gone through today").assertExists()

    val p2 = tabs.tabs.value.first { (it.top as? Route.Post)?.id == "p2" }
    runOnIdle { tabs.close(p2.id) }
    onNodeWithContentDescription("1 kept post gone through today").assertExists()
  }

  @Test
  fun clearViewedCountsOnlyWhatWasSeenToday() {
    keep("p1")
    now += 1.minutes
    seen.record("p1", "mp1")
    now += 1.days
    keep("p2")
    now += 1.minutes
    seen.record("p2", "mp2")

    tabs.clearViewed(seen)

    assertEquals(1, today.counts().dropped)
  }

  @Test
  fun favouritesIsNeitherFoundNorDone() = runComposeUiTest {
    show()
    val favourites = Route.Grid(GridPresets.favourites("someone"))
    runOnIdle { tabs.search(favourites) }
    assertEquals(favourites, tabs.activeTab.top)
    onNodeWithContentDescription("0 found today").assertExists()

    runOnIdle { tabs.open(Route.Grid(listOf("artist_x")), background = true) }
    onNodeWithContentDescription("1 found today").assertExists()

    runOnIdle { tabs.close(tabs.activeTab.id) }
    assertEquals(0, today.counts().done)

    runOnIdle { tabs.open(favourites, background = false) }
    assertEquals(favourites, tabs.activeTab.top)
    onNodeWithContentDescription("1 found today").assertExists()
    assertEquals(0, today.counts().done)
  }

  @Test
  fun likedIsNotAButton() = runComposeUiTest {
    show()
    runOnIdle { today.liked("p1", on = true) }

    onNodeWithContentDescription("1 liked today").assertHasNoClickAction()
  }

  @Test
  fun aQueueOfThreeHundredSourcesAsksOnlyForTheRowsOnScreen() = runComposeUiTest {
    val saved = """{"version":2,"session":"s1","activeIndex":0,"tabs":[""" + (1..300).joinToString(",") { n ->
      """{"id":"t$n","routes":[{"type":"dev.marzahn.sankaku.Route.Grid","tags":["artist_$n"]}],"kind":"source","touched":1000,"addedIn":"s1"}"""
    } + "]}"
    tabs = TabsController(FileTabStateStore(InMemoryTextFile(saved)), clock = clock, today = today)
    val previews = previews()
    assertEquals(emptyList(), pageRequests)

    show(previews)
    waitUntil(timeoutMillis = 5_000) { tabs.tabs.value[0].preview != null }
    waitForIdle()

    val asked = pageRequests.toSet()
    assertTrue(asked.size in 1..12, "asked for ${asked.size}")
    assertTrue(asked.all { it.removePrefix("rating:s artist_").toInt() <= 12 }, "$asked")
    assertEquals(0, today.counts().found)
  }
}

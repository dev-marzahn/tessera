package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.AuthApi
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
import dev.marzahn.sankaku.queue.SourceKey
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.ui.UserGestures
import dev.marzahn.sankaku.ui.components.UNDO
import dev.marzahn.sankaku.ui.observeGestures
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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import androidx.compose.ui.test.assertHasClickAction
import dev.marzahn.sankaku.api.dto.TagDto
import dev.marzahn.sankaku.api.dto.TagType
import kotlinx.coroutines.Dispatchers

private val UPRIGHT = DpSize(412.dp, 760.dp)

private const val NOW_SECONDS = 1_794_657_600L

@OptIn(ExperimentalTestApi::class)
class SortDeckPageTest {
  @Volatile private var requests = 0

  @Volatile private var lastTags = ""

  @Volatile private var pages: Map<String, List<String>> = emptyMap()

  @Volatile private var pagePosts = 9
  private val engine = MockEngine { request ->
    if (request.url.encodedPath.contains("posts")) requests++
    val tags = request.url.parameters["tags"].orEmpty()
    lastTags = tags
    val search = tags.split(' ').first { !it.startsWith("rating:") }
    val ids = pages[search] ?: (1..pagePosts).map { "$search-$it" }
    val posts = ids.withIndex().joinToString(",") { (i, id) ->
      val n = i + 1
      """{"id":"$id","md5":"m$id","rating":"s","status":"active",""" +
        """"created_at":{"s":${NOW_SECONDS - (n + 1) * 86_400L}},"tags":[""" +
        """{"name":"$search","tagName":"$search","type":1,"post_count":1900}]}"""
    }
    respond(
      """{"meta":{"next":"c2","prev":null},"data":[$posts]}""",
      HttpStatusCode.OK,
      headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
  }
  private val scope = CoroutineScope(SupervisorJob())
  private val log = RequestLog()
  private val limiter = RateLimiter(limits = emptyMap())
  private val connectivity = ConnectivityState()
  private val session = AuthSession(AuthApi(createAuthClient(engine, log, limiter, connectivity)), InMemoryTokenStore())
  private val repository = PostsRepository(PostsApi(createMainClient(engine, log, limiter, connectivity, session)), PostStateStore(), session, scope)
  private val privacy = PrivacySettings(InMemoryTextFile())
  private val previews = SourcePreviews(repository, session, privacy)

  private val tabs = TabsController().apply {
    for (tag in listOf("artist_a", "artist_b", "artist_c")) open(Route.Grid(listOf(tag)), background = true)
  }
  private val gestures = UserGestures()
  private val undo = ClosedTabUndo(tabs, gestures = gestures)
  private val deck = SortDeck(tabs, undo)
  private var wentIn = 0
  private var toQueue = 0

  init {
    runBlocking { session.restore() }
  }

  @AfterTest
  fun tearDown() {
    scope.cancel()
  }

  private val today = TodayStore(InMemoryTextFile()).apply {
    added(SourceKey.of(listOf("artist_x"))!!)
    added(SourceKey.of(listOf("artist_y"))!!)
    closed(SourceKey.of(listOf("artist_x"))!!)
  }

  private fun ComposeUiTest.show(followInstead: FollowInstead? = null, window: DpSize = UPRIGHT, fontScale: Float = 1f) {
    runOnIdle { deck.start() }
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = fontScale)) {
        SankakuTheme {
          EndUndoAtNextAction(undo, tabs)
          Box(Modifier.size(window).observeGestures(gestures)) { Page(followInstead) }
        }
      }
    }
  }

  @Composable
  private fun Page(followInstead: FollowInstead?) {
    SortDeckPage(
      onGoIn = { wentIn++ },
      onToQueue = { toQueue++ },
      deck = deck,
      previews = previews,
      undo = undo,
      privacy = privacy,
      session = session,
      today = today,
      followInstead = followInstead,
      clock = object : Clock {
        override fun now() = Instant.fromEpochSeconds(NOW_SECONDS)
      },
    )
  }

  private val unfollowed = mutableListOf<String>()
  private val followInstead = FollowInstead(
    tabs,
    CoroutineScope(Dispatchers.Unconfined),
    account = { "a1" },
    lookUp = { TagDto(name = it, tagName = it, type = TagType.Artist.id, id = "id-$it") },
    isFollowing = { false },
    follow = { dev.marzahn.sankaku.features.post.FollowOutcome.Followed },
    unfollow = { unfollowed += it.name },
  )

  @Test
  fun anArtistsCardOffersFollowInsteadAndUndoTakesItBack() = runComposeUiTest {
    show(followInstead)
    val name = followInsteadName(runOnIdle { deck.card!!.title })

    onNodeWithText("Newest · 9+ posts · newest 2 days ago").assertIsDisplayed()
    onNodeWithText(FOLLOW_INSTEAD_NOTE).assertIsDisplayed()
    onNodeWithContentDescription(name).assertIsDisplayed().assertHasClickAction()
    val card = onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
    val actions = card.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
    assertEquals(listOf(GO_IN, LATER, DONE, name), actions.map { it.label })

    onNodeWithContentDescription(name).performClick()
    runOnIdle {
      assertEquals(tag(1), deck.card?.top)
      assertEquals(SortDeck.Tally(followed = 1), deck.tally)
    }

    onNodeWithText(dev.marzahn.sankaku.ui.components.UNDO).performClick()
    runOnIdle {
      assertEquals(tag(0), deck.card?.top)
      assertEquals(listOf("artist_a"), unfollowed)
    }

    runOnIdle {
      onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNode().config
        .getOrNull(SemanticsActions.CustomActions).orEmpty().single { it.label == name }.action()
    }
    onNodeWithContentDescription(STOP_SORTING).performClick()
    onNodeWithText(FOLLOWED_INSTEAD).assertIsDisplayed()
  }

  @Test
  fun aPopularSourcesNewestComesFromItsNewestPage() = runBlocking {
    val newest = previews.newestUpload(listOf("artist_a", "order:popularity", "rating:s"))

    assertFalse("order:" in lastTags)
    assertEquals(Instant.fromEpochSeconds(NOW_SECONDS - 2 * 86_400L), newest)
  }

  @Test
  fun aCardWithoutFollowInsteadSaysNoNewest() = runComposeUiTest {
    show()

    onNodeWithText("Newest · 9+ posts").assertIsDisplayed()
    onNodeWithText(FOLLOW_INSTEAD).assertDoesNotExist()
  }

  private fun tag(n: Int) = Route.Grid(listOf("artist_${'a' + n}"))

  @Test
  fun theCardBehindPeeksOutOnlyItsEdgeThoughTallerThanTheFront() = runComposeUiTest {
    val onlyB = FollowInstead(
      tabs,
      CoroutineScope(Dispatchers.Unconfined),
      account = { "a1" },
      lookUp = { TagDto(name = it, tagName = it, type = (if (it == "artist_b") TagType.Artist else TagType.General).id, id = "id-$it") },
      isFollowing = { false },
      follow = { dev.marzahn.sankaku.features.post.FollowOutcome.Followed },
      unfollow = {},
    )
    show(onlyB)
    waitUntil { runOnIdle { deck.nextCard?.let(onlyB::offer) != null } }
    onNodeWithText(FOLLOW_INSTEAD).assertDoesNotExist()

    val front = onNodeWithTag(SORT_CARD).getUnclippedBoundsInRoot()
    val behind = onNodeWithTag(SORT_CARD_BEHIND, useUnmergedTree = true).getUnclippedBoundsInRoot()
    assertEquals(front.height, behind.height)
    assertEquals(front.bottom + 10.dp, behind.bottom)
  }

  @Test
  fun aCardShowsItsPlaceItsTitleAndItsSize() = runComposeUiTest {
    show()

    onNodeWithText("1 of 3").assertIsDisplayed()
    onNodeWithText("Newest · 9+ posts").assertIsDisplayed()
    onNodeWithContentDescription("Post 9").assertIsDisplayed()
  }

  @Test
  fun theButtonsAnswerTheCard() = runComposeUiTest {
    show()

    onNodeWithText(LATER).performClick()
    runOnIdle { assertEquals(tag(1), deck.card?.top) }
    assertEquals(tag(0), tabs.tabs.value.last().top)
    onNodeWithText("2 of 3").assertIsDisplayed()

    onNodeWithText(DONE).performClick()
    runOnIdle {
      assertEquals(tag(2), deck.card?.top)
      assertEquals(1, tabs.closed.value.size)
    }
    onNodeWithText("Artist B closed · in Recently closed").assertIsDisplayed()
    onNodeWithText(UNDO).performClick()
    runOnIdle { assertEquals(tag(1), deck.card?.top) }

    onNodeWithText(GO_IN).performClick()
    runOnIdle {
      assertEquals(1, wentIn)
      assertFalse(deck.shown)
      assertEquals(tabs.activeTab.top, tag(1))
    }
  }

  @Test
  fun aCardGoneIntoSaysSoAndOffersBackIn() = runComposeUiTest {
    show()
    onNodeWithTag(SORT_WENT_IN).assertDoesNotExist()

    onNodeWithText(GO_IN).performClick()
    runOnIdle { deck.backToDeck() }

    onNodeWithTag(SORT_WENT_IN).assertIsDisplayed().assertTextContains("You went in", substring = true)
    onNodeWithText(GO_IN).assertDoesNotExist()
    onNodeWithText(BACK_IN).assertIsDisplayed()
    val actions = onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNode().config
      .getOrNull(SemanticsActions.CustomActions).orEmpty()
    assertEquals(listOf(BACK_IN, LATER, DONE), actions.map { it.label })
    onNodeWithText(LATER).performClick()
    onNodeWithText(GO_IN).assertIsDisplayed()
    onNodeWithTag(SORT_WENT_IN).assertDoesNotExist()
  }

  @Test
  fun aFindsRunEndsOnItsTallyAndGoesBack() = runComposeUiTest {
    val from = runOnIdle { tabs.tabs.value.first() }
    runOnIdle { deck.startFinds(tabs.tabs.value.drop(1), next = null, then = from.id, foundOn = "Scenery") }
    setContent { SankakuTheme { Box(Modifier.size(UPRIGHT)) { Page(null) } } }

    onNodeWithText(LATER).performClick()
    onNodeWithText(DONE).performClick()
    onNodeWithText(LATER).performClick()

    onNodeWithTag(SORT_TALLY).assertIsDisplayed()
    onNodeWithText("found on Scenery").assertIsDisplayed()
    onNodeWithText("Back to ${from.title}").performClick()
    runOnIdle {
      assertFalse(deck.running)
      assertEquals(1, wentIn)
      assertEquals(from.id, tabs.active.value)
    }
  }

  @Test
  fun aSwipeLeftIsLaterAndASwipeRightDoesNothing() = runComposeUiTest {
    show()

    onNodeWithTag(SORT_CARD).performTouchInput { swipeRight() }
    runOnIdle { assertEquals(tag(0), deck.card?.top) }

    onNodeWithTag(SORT_CARD).performTouchInput { swipeLeft() }
    runOnIdle {
      assertEquals(tag(1), deck.card?.top)
      assertEquals(1, deck.tally.later)
    }
    onNodeWithText("Artist A is last in the queue, still new").assertIsDisplayed()
    onNodeWithText(dev.marzahn.sankaku.ui.components.UNDO).performClick()
    runOnIdle {
      assertEquals(tag(0), deck.card?.top)
      assertEquals(listOf(tag(0), tag(1), tag(2)), tabs.tabs.value.map { it.top }.filter { it in listOf(tag(0), tag(1), tag(2)) })
      assertEquals(0, deck.tally.later)
    }
    onNodeWithText("Artist A is last in the queue, still new").assertDoesNotExist()
  }

  @Test
  fun eachAnswersUndoWorksAMinuteOn() = runComposeUiTest {
    show(followInstead)
    val answers = listOf<Pair<String, () -> Unit>>(
      DONE to { onNodeWithText(DONE).performClick() },
      LATER to { onNodeWithText(LATER).performClick() },
      FOLLOW_INSTEAD to { onNodeWithContentDescription(followInsteadName(runOnIdle { deck.card!!.title })).performClick() },
    )
    for ((name, answer) in answers) {
      answer()
      runOnIdle { assertEquals(tag(1), deck.card?.top, name) }
      mainClock.advanceTimeBy(60_000)
      onNodeWithText(UNDO).assertIsDisplayed().performClick()
      runOnIdle {
        assertEquals(tag(0), deck.card?.top, name)
        assertEquals(0, deck.tally.sorted, name)
      }
      onNodeWithText(UNDO).assertDoesNotExist()
    }
    assertEquals(listOf("artist_a"), unfollowed)
  }

  @Test
  fun theNextActionEndsAnAnswersLine() = runComposeUiTest {
    show(followInstead)
    onNodeWithText(DONE).performClick()
    mainClock.advanceTimeBy(60_000)
    onNodeWithText(UNDO).assertIsDisplayed()
    onNodeWithText(SORT).performClick()
    onNodeWithText(UNDO).assertDoesNotExist()
    runOnIdle { assertEquals(tag(1), deck.card?.top) }

    onNodeWithText(LATER).performClick()
    onNodeWithText(UNDO).assertIsDisplayed()
    onNodeWithTag(SORT_CARD).performTouchInput { swipeRight() }
    onNodeWithText(UNDO).assertDoesNotExist()
    runOnIdle { assertEquals(tag(2), deck.card?.top) }

    onNodeWithContentDescription(followInsteadName(runOnIdle { deck.card!!.title })).performClick()
    onNodeWithText(UNDO).assertIsDisplayed()
    onNodeWithText(SORT).performClick()
    onNodeWithText(UNDO).assertDoesNotExist()
    runOnIdle { assertEquals(SortDeck.Tally(later = 1, done = 1, followed = 1), deck.tally) }
  }

  @Test
  fun talkBackHasGoInLaterAndDoneOnTheCard() = runComposeUiTest {
    show()

    val card = onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
    val actions = card.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
    assertEquals(listOf(GO_IN, LATER, DONE), actions.map { it.label })

    runOnIdle { actions.single { it.label == DONE }.action() }
    runOnIdle {
      assertEquals(tag(1), deck.card?.top)
      assertEquals(1, deck.tally.done)
    }
  }

  @Test
  fun stoppingShowsTheTallyAndKeepSortingOrToTheQueue() = runComposeUiTest {
    show()
    onNodeWithText(LATER).performClick()
    onNodeWithText(DONE).performClick()

    onNodeWithContentDescription(STOP_SORTING).performClick()

    onNodeWithTag(SORT_TALLY).assertIsDisplayed()
    onNodeWithText("2 sorted").assertIsDisplayed()
    onNode(hasText(DONE) and hasText("1")).assertIsDisplayed()
    onNode(hasText(LATER) and hasText("1")).assertIsDisplayed()
    onNodeWithText(FOLLOWED_INSTEAD).assertDoesNotExist()
    onNode(hasText(STILL_NEW) and hasText("2")).assertIsDisplayed()
    onNodeWithText(QUEUE_TODAY).assertIsDisplayed()
    onNodeWithText("+2 found · 1 done").assertIsDisplayed()
    onNodeWithText(sortMoreLabel(1)).performClick()
    onNodeWithText("3 of 3").assertIsDisplayed()

    onNodeWithContentDescription(STOP_SORTING).performClick()
    onNodeWithText(TO_THE_QUEUE).performClick()
    runOnIdle {
      assertEquals(1, toQueue)
      assertFalse(deck.running)
    }
  }

  @Test
  fun aRunEndedWithLatersSaysTheyAreStillNew() = runComposeUiTest {
    show()
    repeat(3) { onNodeWithText(LATER).performClick() }

    onNodeWithTag(SORT_TALLY).assertIsDisplayed()
    onNodeWithText("3 sorted").assertIsDisplayed()
    onNode(hasText(STILL_NEW) and hasText("3")).assertIsDisplayed()
    onNodeWithText(NO_NEW_LEFT).assertDoesNotExist()
    onNodeWithText(" more", substring = true).assertDoesNotExist()
    runOnIdle { assertEquals(3, newSources(tabs.tabs.value).size) }
  }

  @Test
  fun aRunEndedWithDonesSaysNoneAreLeft() = runComposeUiTest {
    show()
    repeat(3) { onNodeWithText(DONE).performClick() }

    onNodeWithText("No new sources left").assertIsDisplayed()
  }

  @Test
  fun postsFromAnotherSearchAreNotShownAndLoadAgain() = runBlocking {
    deck.start()
    val tab = deck.card!!
    val g = listOf("artist_a", "rating:s")
    deck.loadPosts(tab, g, previews)
    assertTrue(deck.postsFor(tab, g) is CardPosts.Loaded)

    val other = listOf("artist_a")
    assertEquals(null, deck.postsFor(tab, other))
    deck.loadPosts(tab, other, previews)
    assertTrue(deck.postsFor(tab, other) is CardPosts.Loaded)
    assertEquals(null, deck.postsFor(tab, g))
  }

  private fun shownIds(tab: TabState, tag: String, count: Int = SourcePreview.SIZE): List<String> =
    (deck.shownPosts(tab, listOf(tag), count) as CardPosts.Loaded).posts.map { it.id }

  private val shared = (1..9).map { "shared-$it" }

  private fun runWithPages(vararg byTag: Pair<String, List<String>>): List<TabState> = runBlocking {
    pages = byTag.toMap()
    deck.start()
    newSources(tabs.tabs.value).onEach { deck.loadPosts(it, (it.top as Route.Grid).tags, previews) }
  }

  @Test
  fun theNextCardSkipsThePostsTheCardBeforeItShows() {
    val (a, b) = runWithPages("artist_a" to shared + "a-10", "artist_b" to shared + (10..20).map { "b-$it" })

    assertEquals(shared, shownIds(a, "artist_a"))
    val behind = shownIds(b, "artist_b")
    assertEquals((10..18).map { "b-$it" }, behind)

    deck.later()
    assertEquals(b, deck.card)
    assertEquals(behind, shownIds(b, "artist_b"))
  }

  @Test
  fun theCardBehindWaitsForTheCardInFront() = runBlocking {
    val b10 = (10..18).map { "b-$it" }
    pages = mapOf("artist_a" to shared, "artist_b" to shared + b10)
    deck.start()
    val a = deck.card!!
    val b = deck.nextCard!!
    deck.loadPosts(b, listOf("artist_b"), previews)

    assertEquals(null, deck.shownPosts(b, listOf("artist_b"), SourcePreview.SIZE))
    deck.loadPosts(a, listOf("artist_a"), previews)
    assertEquals(b10, shownIds(b, "artist_b"))
  }

  @Test
  fun aTallerCardSkipsAllTheCardBeforeShows() {
    val twelve = (1..12).map { "shared-$it" }
    val (a, b) = runWithPages("artist_a" to twelve, "artist_b" to twelve + (13..30).map { "b-$it" })

    assertEquals(twelve, shownIds(a, "artist_a", count = 12))
    assertEquals((13..24).map { "b-$it" }, shownIds(b, "artist_b", count = 12))
  }

  @Test
  fun aCardWithFewOthersShowsThoseWithoutRepeats() {
    val (_, b) = runWithPages("artist_a" to shared, "artist_b" to shared.take(5) + listOf("b-1", "b-2"))

    assertEquals(listOf("b-1", "b-2"), shownIds(b, "artist_b"))
  }

  @Test
  fun aCardSkipsWhatTheCardBeforeShowsNotItsWholePage() {
    val b10 = (10..18).map { "b-$it" }
    val (_, b, c) = runWithPages("artist_a" to shared, "artist_b" to shared + b10, "artist_c" to b10.take(5) + shared)

    assertEquals(b10, shownIds(b, "artist_b"))
    assertEquals(shared, shownIds(c, "artist_c"))
  }

  @Test
  fun aCardPassedOverIsNotTheCardBefore() {
    val (_, b, c) = runWithPages("artist_a" to shared, "artist_c" to shared + (10..18).map { "c-$it" })
    tabs.close(b.id)

    deck.later()

    assertEquals(c, deck.card)
    assertEquals((10..18).map { "c-$it" }, shownIds(c, "artist_c"))
  }

  @Test
  fun anUndoneDoneKeepsTheCardAfterItSkippingItsPosts() {
    val b10 = (10..18).map { "b-$it" }
    val (_, b) = runWithPages("artist_a" to shared, "artist_b" to shared + b10)
    deck.done()
    deck.undoDone()

    val a = deck.card!!
    assertEquals(shared, shownIds(a, "artist_a"))
    assertEquals(b, deck.nextCard)
    assertEquals(b10, shownIds(b, "artist_b"))
  }

  @Test
  fun aCardWithNoOthersShowsItsOwn() {
    val (_, b) = runWithPages("artist_a" to shared, "artist_b" to shared.take(4))

    assertEquals(shared.take(4), shownIds(b, "artist_b"))
  }

  @Test
  fun noCardLoadsWhileTheSessionRestores() = runComposeUiTest {
    val restoring = AuthSession(AuthApi(createAuthClient(engine, log, limiter, connectivity)), InMemoryTokenStore())
    runOnIdle { deck.start() }
    setContent {
      SankakuTheme {
        SortDeckPage(onGoIn = {}, onToQueue = {}, deck = deck, previews = previews, undo = undo, privacy = privacy, session = restoring)
      }
    }

    waitForIdle()
    assertEquals(0, requests)
    onNodeWithContentDescription("Post 1").assertDoesNotExist()
  }

  @Test
  fun aPostOfTheCardIsLookedAtAloneAndATapReturns() = runComposeUiTest {
    show()

    onNodeWithContentDescription("Post 1").performClick()
    onNodeWithTag(SORT_LOOK).assertIsDisplayed()
    onNodeWithTag(SORT_LOOK).performClick()

    onNodeWithTag(SORT_LOOK).assertDoesNotExist()
    assertTrue(deck.card != null)
  }

  private val landscape = DpSize(915.dp, 364.dp)

  private fun ComposeUiTest.assertLandscapeLayout(scale: Float) {
    show(followInstead, window = landscape, fontScale = scale)

    for (n in 1..9) {
      val post = onNodeWithContentDescription("Post $n").assertIsDisplayed().getUnclippedBoundsInRoot()
      assertTrue(post.width >= 80.dp && post.height >= 80.dp, "post $n at ${scale}x: ${post.width} × ${post.height}")
    }
    onNodeWithText(runOnIdle { deck.card!!.title }).assertIsDisplayed()

    fun assertButtonsReachable(labels: List<String>) {
      val card = onNodeWithTag(SORT_CARD).getUnclippedBoundsInRoot()
      for (label in labels) {
        val button = onNodeWithText(label).assertIsDisplayed().assertHasClickAction().getUnclippedBoundsInRoot()
        assertTrue(button.top >= 0.dp && button.bottom <= landscape.height && button.right <= landscape.width, "$label at ${scale}x: $button")
        assertTrue(button.height >= 40.dp, "$label at ${scale}x is ${button.height} tall")
        assertTrue(button.left >= card.right, "$label at ${scale}x: $button, card $card")
      }
    }
    assertButtonsReachable(listOf(GO_IN, LATER, DONE))
    onNodeWithText(DONE).performClick()
    fun assertUndoOnThePage() {
      val undo = onNodeWithText(dev.marzahn.sankaku.ui.components.UNDO).assertIsDisplayed().assertHasClickAction().getUnclippedBoundsInRoot()
      assertTrue(undo.top >= 0.dp && undo.bottom <= landscape.height && undo.right <= landscape.width, "Undo at ${scale}x: $undo")
      assertTrue(undo.height >= 40.dp, "Undo at ${scale}x is ${undo.height} tall")
    }
    assertButtonsReachable(listOf(GO_IN, LATER, DONE))
    assertUndoOnThePage()
    onNodeWithText(LATER).performClick()
    assertButtonsReachable(listOf(GO_IN, LATER, DONE))
    assertUndoOnThePage()
  }

  @Test
  fun inLandscapeThePostsStayLargeAndTheButtonsReachable() = runComposeUiTest { assertLandscapeLayout(1f) }

  @Test
  fun inLandscapeAtTwiceTheTextThePostsStayLargeAndTheButtonsReachable() = runComposeUiTest { assertLandscapeLayout(2f) }

  @Test
  fun inLandscapeASwipeLeftIsStillLater() = runComposeUiTest {
    show(window = landscape)

    onNodeWithTag(SORT_CARD).performTouchInput { swipeLeft() }
    runOnIdle { assertEquals(tag(1), deck.card?.top) }
  }

  @Test
  fun aNearlySquareWindowKeepsTheButtonsUnderTheCard() = runComposeUiTest {
    show(window = DpSize(520.dp, 480.dp))

    val card = onNodeWithTag(SORT_CARD).getUnclippedBoundsInRoot()
    assertTrue(onNodeWithText(GO_IN).getUnclippedBoundsInRoot().top >= card.bottom)
  }

  @Test
  fun uprightTheButtonsStayUnderTheCard() = runComposeUiTest {
    show()

    val card = onNodeWithTag(SORT_CARD).getUnclippedBoundsInRoot()
    val post = onNodeWithContentDescription("Post 1").getUnclippedBoundsInRoot()
    assertTrue(post.width >= 100.dp, "post: ${post.width}")
    val buttons = listOf(DONE, LATER, GO_IN).map { onNodeWithText(it).getUnclippedBoundsInRoot() }
    buttons.forEach { assertTrue(it.top >= card.bottom, "$it under $card") }
    assertEquals(1, buttons.map { it.top }.distinct().size)
  }

  @Test
  fun onATallPhoneMoreRowsOfPostsFillTheCard() = runDesktopComposeUiTest(width = TALL.width.value.toInt(), height = TALL.height.value.toInt()) {
    pagePosts = 15
    show(window = TALL)
    awaitPost(12)
    onNodeWithContentDescription("Post 12").assertIsDisplayed()
    val shown = (1..15).count { onAllNodesWithContentDescription("Post $it").fetchSemanticsNodes().isNotEmpty() }
    assertEquals(0, shown % 3, "whole rows: $shown")

    val card = onNodeWithTag(SORT_CARD).getUnclippedBoundsInRoot()
    val buttons = listOf(DONE, LATER, GO_IN).map { onNodeWithText(it).getUnclippedBoundsInRoot() }
    buttons.forEach { assertTrue(it.top >= card.bottom && TALL.height - it.bottom <= 20.dp, "$it under $card") }
    val gap = buttons.first().top - card.bottom
    assertTrue(gap <= 28.dp, "gap: $gap")
    val title = onNodeWithText(runOnIdle { deck.card!!.title }).getUnclippedBoundsInRoot()
    for (n in 1..shown) {
      val post = onNodeWithContentDescription("Post $n").getUnclippedBoundsInRoot()
      assertTrue(post.width >= 115.dp && post.height >= post.width - 1.dp && post.height <= post.width * 4 / 3 + 1.dp, "post $n: $post")
      assertTrue(post.bottom <= title.top, "post $n: $post, title $title")
    }
  }

  @Test
  fun aShorterPhoneKeepsThreeSquareRows() = runComposeUiTest {
    pagePosts = 15
    show(window = DpSize(411.dp, 683.dp))
    awaitPost(9)
    onNodeWithContentDescription("Post 9").assertIsDisplayed()

    onNodeWithContentDescription("Post 10").assertDoesNotExist()
    val first = onNodeWithContentDescription("Post 1").getUnclippedBoundsInRoot()
    assertNear(first.width, first.height)
    val card = onNodeWithTag(SORT_CARD).getUnclippedBoundsInRoot()
    val last = onNodeWithContentDescription("Post 9").getUnclippedBoundsInRoot()
    assertNear(card.width - 24.dp, last.right - first.left)
    assertNear(last.right - first.left, last.bottom - first.top)
  }

  private fun ComposeUiTest.awaitPost(n: Int) =
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Post $n").fetchSemanticsNodes().isNotEmpty() }

  private fun assertNear(expected: Dp, actual: Dp) = assertTrue(abs((expected - actual).value) <= 1f, "$actual, not $expected")

  @Test
  fun anUprightGridTakesTheRowsThatFit() {
    assertEquals(CardGrid(3, 360.dp, 360.dp), uprightGrid(360.dp, 400.dp, loaded = 15))
    assertEquals(CardGrid(3, 300.dp, 300.dp), uprightGrid(360.dp, 300.dp, loaded = 15))
    assertEquals(CardGrid(4, 356.dp, 500.dp), uprightGrid(356.dp, 500.dp, loaded = null))
    val five = uprightGrid(356.dp, 900.dp, loaded = 15)
    assertEquals(5, five.rows)
    assertTrue(five.height <= 116.dp * 4 / 3 * 5 + 16.dp + 0.01.dp, "five: $five")
    assertEquals(4, uprightGrid(356.dp, 700.dp, loaded = 12).rows)
    for (loaded in listOf(11, 9, 0)) {
      val three = uprightGrid(356.dp, 700.dp, loaded)
      assertEquals(3, three.rows, "$loaded posts")
      assertEquals(356.dp, three.width)
      assertNear(116.dp * 4 / 3 * 3 + 8.dp, three.height)
    }
  }

  @Test
  fun onATallPhoneNinePostsGrowTaller() = runDesktopComposeUiTest(width = TALL.width.value.toInt(), height = TALL.height.value.toInt()) {
    show(window = TALL)
    awaitPost(9)
    onNodeWithContentDescription("Post 9").assertIsDisplayed()

    onNodeWithContentDescription("Post 10").assertDoesNotExist()
    val post = onNodeWithContentDescription("Post 1").getUnclippedBoundsInRoot()
    assertTrue(post.height > post.width + 20.dp && post.height <= post.width * 4 / 3 + 1.dp, "post: $post")
  }
}

private val TALL = DpSize(427.dp, 876.dp)

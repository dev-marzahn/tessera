package dev.marzahn.sankaku.features.post

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.data.merge
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import dev.marzahn.sankaku.api.AuthApi
import dev.marzahn.sankaku.api.PostsApi
import dev.marzahn.sankaku.api.RateLimiter
import dev.marzahn.sankaku.api.TagsApi
import dev.marzahn.sankaku.api.createAuthClient
import dev.marzahn.sankaku.api.createMainClient
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.UserSummary
import dev.marzahn.sankaku.api.LOGIN_BASE
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.auth.SignInResult
import dev.marzahn.sankaku.auth.StoredTokens
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.OutboxSender
import dev.marzahn.sankaku.data.PendingChange
import dev.marzahn.sankaku.data.PostStateStore
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.data.PostsRepository
import dev.marzahn.sankaku.data.TagFollows
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.log.RequestLog
import dev.marzahn.sankaku.net.ConnectivityState
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class PostActionsTest {
  private val now = Instant.parse("2026-10-31T12:00:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val scope = CoroutineScope(SupervisorJob())
  private val exchangedTokens = """
    { "success": true, "token_type": "Bearer", "access_token": "new-access", "access_token_ttl": 604800,
      "refresh_token": "new-refresh", "refresh_token_ttl": 7776000,
      "current_user": { "id": "42", "name": "tester", "avatar_url": null, "mfa_method": 0 } }
  """.trimIndent()
  private val loginOk = """
    { "success": true, "token_type": "Bearer", "access_token": "login-token", "refresh_token": "login-refresh",
      "current_user": { "id": 42, "name": "tester" } }
  """.trimIndent()
  private val post = PostDto(id = "p1", md5 = "0123456789abcdef0123456789abcdef", rating = "s", status = "active")

  private inner class Rig(
    signedIn: Boolean,
    status: HttpStatusCode = HttpStatusCode.OK,
    serverFavourited: Boolean = false,
    refusals: Flow<OutboxSender.Refusal> = emptyFlow(),
    nextAction: suspend () -> Unit = { awaitCancellation() },
  ) {
    var requests = 0
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val fullPost =
      """{ "id": "p1", "md5": "${post.md5}", "rating": "s", "status": "active", "is_favorited": $serverFavourited }"""
    private val engine = MockEngine { request ->
      when {
        request.url.host == Url(LOGIN_BASE).host -> respond(loginOk, HttpStatusCode.OK, json)
        request.url.encodedPath.endsWith("/sso/token-exchange") -> respond(exchangedTokens, HttpStatusCode.OK, json)
        request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/posts/p1") ->
          respond(fullPost, HttpStatusCode.OK, json)
        else -> {
          requests++
          respond("{}", status, json)
        }
      }
    }
    private val log = RequestLog(clock)
    private val limiter = RateLimiter(limits = emptyMap())
    private val connectivity = ConnectivityState()
    private val tokens = if (signedIn) {
      StoredTokens("access", "refresh", now + 7.days, now + 90.days, UserSummary(id = "42", name = "tester", avatarUrl = null))
    } else {
      null
    }
    val session = AuthSession(AuthApi(createAuthClient(engine, log, limiter, connectivity), clock), InMemoryTokenStore(tokens), clock)
    private val client = createMainClient(engine, log, limiter, connectivity, session)

    init {
      runBlocking { session.restore() }
    }

    val store = PostStateStore()
    val signIn = SignInController()
    val messages = Messages()
    val repository = PostsRepository(PostsApi(client), store, session, scope)
    val seen = SeenStore(InMemoryTextFile(), clock)
    val tabs = TabsController(clock = clock)
    val actions = PostActions(
      repository,
      session,
      signIn,
      messages,
      scope,
      TagFollows(TagsApi(client)),
      actedOn(seen, tabs),
      refusals,
      store,
      nextAction,
    )

    suspend fun settled(favourited: Boolean) = withTimeout(5_000) {
      store.overrides.first { it[post.id]?.let { o -> o.isFavourited == favourited && !o.inFlight } == true }
    }
  }

  private val favourited = post.copy(isFavorited = true, favCount = 3)

  @Test
  fun unfavouritingOutsideTheGridSaysSoWithUndo() = runBlocking {
    val rig = Rig(signedIn = true)
    val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.messages.messages.first() } }

    assertTrue(rig.actions.toggleFavourite(favourited))

    val line = message.await()
    assertEquals("Unfavourited", line.text)
    assertEquals("Undo", line.actionLabel)
    assertFalse(line.isError)
    assertNotNull(line.until)
    assertEquals(false, rig.settled(favourited = false).getValue(post.id).isFavourited)
  }

  @Test
  fun undoFavouritesThePostAgain() = runBlocking {
    val rig = Rig(signedIn = true)
    val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.messages.messages.first() } }
    rig.actions.toggleFavourite(favourited)
    rig.settled(favourited = false)
    val hearts = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.actions.hearts.first() } }

    message.await().onAction()

    assertEquals(FavouriteHeart(post.id, true), hearts.await())
    assertEquals(3, rig.settled(favourited = true).getValue(post.id).favCount)
    assertEquals(2, rig.requests)
  }

  @Test
  fun undoOfAPostFavouritedAgainMeanwhileChangesNothing() = runBlocking {
    val rig = Rig(signedIn = true)
    val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.messages.messages.first() } }
    rig.actions.toggleFavourite(favourited)
    rig.settled(favourited = false)
    rig.actions.toggleFavourite(favourited.merge(rig.store.overrides.value[post.id]))
    rig.settled(favourited = true)

    message.await().onAction()
    delay(100)

    assertEquals(true, rig.store.overrides.value[post.id]?.isFavourited)
    assertEquals(2, rig.requests)
  }

  @Test
  fun theLineEndsAtTheNextAction() = runBlocking {
    val next = CompletableDeferred<Unit>()
    val rig = Rig(signedIn = true, nextAction = { next.await() })
    val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.messages.messages.first() } }
    rig.actions.toggleFavourite(favourited)
    val ended = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { message.await().until!!() } }
    rig.settled(favourited = false)
    delay(50)
    assertFalse(ended.isCompleted)

    next.complete(Unit)

    ended.await()
  }

  @Test
  fun theLineEndsWhenThePostIsFavouritedAgainAnotherWay() = runBlocking {
    val rig = Rig(signedIn = true)
    val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.messages.messages.first() } }
    rig.actions.toggleFavourite(favourited)
    val ended = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { message.await().until!!() } }
    rig.settled(favourited = false)
    delay(50)
    assertFalse(ended.isCompleted)

    rig.actions.toggleFavourite(favourited.merge(rig.store.overrides.value[post.id]))

    ended.await()
  }

  @Test
  fun theLineEndsWhenTheViewerShowsAnotherPost() = runBlocking {
    val rig = Rig(signedIn = true)
    rig.actions.showing(post.id)
    val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.messages.messages.first() } }
    rig.actions.toggleFavourite(favourited)
    val ended = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { message.await().until!!() } }
    rig.settled(favourited = false)
    rig.actions.showing(post.id)
    delay(50)
    assertFalse(ended.isCompleted)

    rig.actions.showing("p2")

    ended.await()
  }

  @Test
  fun aRefusedUnfavouriteEndsTheLine() = runBlocking {
    val rig = Rig(signedIn = true, status = HttpStatusCode.NotFound)
    val ended = CompletableDeferred<Unit>()
    val messages = async(start = CoroutineStart.UNDISPATCHED) {
      withTimeout(5_000) {
        rig.messages.messages.take(2).onEach { if (it.until != null) launch { it.until!!(); ended.complete(Unit) } }.toList()
      }
    }
    rig.actions.toggleFavourite(favourited)

    val (line, error) = messages.await()
    withTimeout(5_000) { ended.await() }

    assertEquals("Unfavourited", line.text)
    assertTrue(error.isError)
    assertEquals(true, rig.settled(favourited = true).getValue(post.id).isFavourited)
  }

  @Test
  fun favouritingStaysSilent() = runBlocking {
    val rig = Rig(signedIn = true)
    val messages = mutableListOf<Message>()
    val collecting = launch(start = CoroutineStart.UNDISPATCHED) { rig.messages.messages.collect { messages += it } }

    rig.actions.toggleFavourite(post)
    rig.actions.addFavourite(favourited)
    rig.settled(favourited = true)
    delay(50)

    collecting.cancel()
    assertEquals(emptyList(), messages)
  }

  @Test
  fun unfavouritingFromTheGridSaysNothing() = runBlocking {
    val rig = Rig(signedIn = true)
    val messages = mutableListOf<Message>()
    val collecting = launch(start = CoroutineStart.UNDISPATCHED) { rig.messages.messages.collect { messages += it } }

    rig.actions.toggleFavourite(favourited, fromGrid = true)
    rig.settled(favourited = false)
    delay(50)

    collecting.cancel()
    assertEquals(emptyList(), messages)
  }

  @Test
  fun aWaitingFavouriteSankakuRefusedLaterIsAnsweredAsAFailedToggle() = runBlocking {
    val refusals = MutableSharedFlow<OutboxSender.Refusal>()
    val rig = Rig(signedIn = true, refusals = refusals)
    val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.messages.messages.first() } }
    val rejection = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.actions.rejections.first() } }
    refusals.subscriptionCount.first { it > 0 }

    refusals.emit(OutboxSender.Refusal(PendingChange.Favourite(post.id, true, 1, false, 0), ApiError.Http(404, "")))

    assertEquals(Rejection.Favourite(post.id), rejection.await())
    assertEquals("Couldn't favourite. Sankaku couldn't find it. It may have been deleted.", message.await().text)
    assertTrue(message.await().isError)
  }

  @Test
  fun aHeartInTheViewerMakesThePostSeen() {
    val rig = Rig(signedIn = true)

    rig.actions.toggleFavourite(post)

    assertTrue(rig.seen.isSeen(post.id))
  }

  @Test
  fun aVoteMakesThePostSeen() {
    val rig = Rig(signedIn = true)

    rig.actions.vote(post, 4)

    assertTrue(rig.seen.isSeen(post.id))
  }

  @Test
  fun aTagActionMakesThePostSeen() {
    val rig = Rig(signedIn = true)

    rig.actions.actedOn(post)

    assertTrue(rig.seen.isSeen(post.id, post.md5))
  }

  @Test
  fun aHeartFromTheGridDoesNotMakeThePostSeen() {
    val rig = Rig(signedIn = true)

    rig.actions.toggleFavourite(post, viaDoubleTap = true, fromGrid = true)
    rig.actions.toggleFavourite(post, viaDoubleTap = false, fromGrid = true)

    assertFalse(rig.seen.isSeen(post.id, post.md5))
    assertEquals(0, rig.seen.size)
  }

  @AfterTest
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun aToggleInTheViewerGivesTheHeartOfTheStateItMade() = runBlocking {
    val rig = Rig(signedIn = true)
    val hearts = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.actions.hearts.take(2).toList() } }

    rig.actions.toggleFavourite(post)
    rig.actions.toggleFavourite(post.copy(isFavorited = true))

    assertEquals(listOf(FavouriteHeart(post.id, true), FavouriteHeart(post.id, false)), hearts.await())
  }

  @Test
  fun aRollbackGivesTheHeartOfTheStateItWentBackTo() = runBlocking {
    val rig = Rig(signedIn = true, status = HttpStatusCode.InternalServerError)
    val hearts = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.actions.hearts.take(2).toList() } }

    rig.actions.toggleFavourite(post)

    assertEquals(listOf(FavouriteHeart(post.id, true), FavouriteHeart(post.id, false)), hearts.await())
  }

  @Test
  fun aViewerPlaysOnlyItsShownPostsHearts() = runBlocking {
    val all = MutableSharedFlow<FavouriteHeart>(extraBufferCapacity = 8)
    var shown = "a"
    val played = mutableListOf<Boolean>()
    val collecting = launch(start = CoroutineStart.UNDISPATCHED) { all.forShown { shown }.collect { played += it } }

    suspend fun heart(postId: String, favourited: Boolean) {
      all.emit(FavouriteHeart(postId, favourited))
      delay(20)
    }

    heart("a", true)
    shown = "b"
    heart("a", false)
    heart("b", true)

    collecting.cancel()
    assertEquals(listOf(true, true), played)
  }

  @Test
  fun noHeartSignedOutOrFromTheGrid() = runBlocking {
    val rig = Rig(signedIn = false)
    val hearts = mutableListOf<FavouriteHeart>()
    val collecting = launch(start = CoroutineStart.UNDISPATCHED) { rig.actions.hearts.collect { hearts += it } }

    rig.actions.toggleFavourite(post)
    rig.session.signIn("tester", "password")
    rig.signIn.succeeded()
    withTimeout(5_000) { rig.store.overrides.first { it[post.id]?.let { o -> o.isFavourited == true && !o.inFlight } == true } }
    rig.actions.toggleFavourite(post.copy(isFavorited = true), fromGrid = true)
    delay(100)

    collecting.cancel()
    assertEquals(emptyList(), hearts)
  }

  @Test
  fun signedOutItOpensSignInAndSaysItDidNotToggle() {
    val rig = Rig(signedIn = false)

    assertFalse(rig.actions.toggleFavourite(post))

    assertTrue(rig.signIn.isOpen.value)
    assertEquals(0, rig.requests)
  }

  @Test
  fun signedOutTheFavouriteLandsOnceSignedIn() = runBlocking {
    val rig = Rig(signedIn = false)

    rig.actions.toggleFavourite(post)
    assertEquals("to favourite", rig.signIn.reason.value)
    assertEquals(SignInResult.Ok, rig.session.signIn("tester", "password"))
    rig.signIn.succeeded()

    val settled = withTimeout(5_000) {
      rig.store.overrides.first { all -> all[post.id]?.let { it.isFavourited != null && !it.inFlight } == true }
    }
    assertEquals(true, settled.getValue(post.id).isFavourited)
    assertEquals(1, rig.requests)
  }

  @Test
  fun signedOutAFavouriteThatFailsAfterSignInRollsBack() = runBlocking {
    val rig = Rig(signedIn = false, status = HttpStatusCode.InternalServerError)
    val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.messages.messages.first() } }
    val rejection = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.actions.rejections.first() } }

    rig.actions.toggleFavourite(post)
    rig.session.signIn("tester", "password")
    rig.signIn.succeeded()

    assertTrue(message.await().let { it.isError && it.text.startsWith("Couldn't favourite") })
    assertEquals(Rejection.Favourite(post.id), rejection.await())
    assertEquals(false, rig.store.overrides.value[post.id]?.isFavourited)
  }

  @Test
  fun signedOutAVoteLandsOnceSignedIn() = runBlocking {
    val rig = Rig(signedIn = false)

    rig.actions.vote(post, 4)
    assertEquals("to vote", rig.signIn.reason.value)
    rig.session.signIn("tester", "password")
    rig.signIn.succeeded()

    val settled = withTimeout(5_000) {
      rig.store.overrides.first { all -> all[post.id]?.let { it.vote != null && !it.voteInFlight } == true }
    }
    assertEquals(4, settled.getValue(post.id).vote?.userVote)
    assertEquals(1, rig.requests)
  }

  @Test
  fun closingSignInDropsTheFavourite() = runBlocking {
    val rig = Rig(signedIn = false)

    rig.actions.toggleFavourite(post)
    rig.signIn.close()
    rig.session.signIn("tester", "password")
    val running = scope.coroutineContext.job.children.count()
    rig.signIn.succeeded()

    assertEquals(running, scope.coroutineContext.job.children.count())
    assertEquals(0, rig.requests)
    assertNull(rig.store.overrides.value[post.id])
  }

  @Test
  fun signedOutAFavouriteTheAccountAlreadyHasIsNotSentAgain() = runBlocking {
    val rig = Rig(signedIn = false, serverFavourited = true)

    rig.actions.toggleFavourite(post)
    rig.session.signIn("tester", "password")
    val running = scope.coroutineContext.job.children.count()
    rig.signIn.succeeded()

    withTimeout(5_000) { while (scope.coroutineContext.job.children.count() > running) delay(10) }
    assertEquals(0, rig.requests)
    assertNull(rig.store.overrides.value[post.id]?.isFavourited)
  }

  @Test
  fun signedInItTogglesAndSaysSo() = runBlocking {
    val rig = Rig(signedIn = true)

    assertTrue(rig.actions.toggleFavourite(post))

    assertFalse(rig.signIn.isOpen.value)
    val settled = withTimeout(5_000) { rig.store.overrides.first { it[post.id]?.inFlight == false } }
    assertEquals(true, settled.getValue(post.id).isFavourited)
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun settingTheRatingsProgressVotesThroughTheUsualPath() = runComposeUiTest {
    val rig = Rig(signedIn = true)
    val voted = scope.async(start = CoroutineStart.UNDISPATCHED) {
      withTimeout(5_000) { rig.store.overrides.first { it[post.id]?.vote?.userVote == 3 } }
    }
    setContent {
      val overrides by rig.store.overrides.collectAsState()
      SankakuTheme {
        val shown = post.merge(overrides[post.id])
        PostStats(shown, onToggleFavourite = {}, onVote = { rig.actions.vote(shown, it) }, modifier = Modifier.width(320.dp))
      }
    }

    onNodeWithContentDescription("No votes").performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }

    assertEquals(3, runBlocking { voted.await() }.getValue(post.id).vote?.userVote)
    val settled = runBlocking { withTimeout(5_000) { rig.store.overrides.first { it[post.id]?.voteInFlight == false } } }
    assertEquals(3, settled.getValue(post.id).vote?.userVote)
    assertEquals(1, rig.requests)
    waitForIdle()
    onNodeWithContentDescription("Average 3.0 from 1 vote")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Your vote, 3"))
  }

  @Test
  fun aLongPressFavouritesWithTheHeart() = runBlocking {
    val rig = Rig(signedIn = true)
    val heart = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.actions.hearts.first() } }

    assertTrue(rig.actions.addFavourite(post))

    assertEquals(FavouriteHeart(post.id, true), heart.await())
    assertTrue(rig.seen.isSeen(post.id))
    val settled = withTimeout(5_000) { rig.store.overrides.first { it[post.id]?.inFlight == false } }
    assertEquals(true, settled.getValue(post.id).isFavourited)
    assertEquals(1, rig.requests)
  }

  @Test
  fun aLongPressOnAFavouriteNeverUnfavourites() = runBlocking {
    val rig = Rig(signedIn = true)
    val heart = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.actions.hearts.first() } }

    assertTrue(rig.actions.addFavourite(post.copy(isFavorited = true)))

    assertEquals(FavouriteHeart(post.id, true), heart.await())
    delay(100)
    assertEquals(0, rig.requests)
    assertNull(rig.store.overrides.value[post.id])
  }

  @Test
  fun aLongPressThatFailsRollsBackAndShakesTheHeart() = runBlocking {
    val rig = Rig(signedIn = true, status = HttpStatusCode.InternalServerError)
    val hearts = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.actions.hearts.take(2).toList() } }
    val rejection = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.actions.rejections.first() } }

    rig.actions.addFavourite(post)

    assertEquals(Rejection.Favourite(post.id), rejection.await())
    assertEquals(listOf(FavouriteHeart(post.id, true), FavouriteHeart(post.id, false)), hearts.await())
    assertEquals(false, rig.store.overrides.value[post.id]?.isFavourited)
  }

  @Test
  fun signedOutALongPressOpensSignIn() {
    val rig = Rig(signedIn = false)

    assertFalse(rig.actions.addFavourite(post))

    assertTrue(rig.signIn.isOpen.value)
    assertEquals("to favourite", rig.signIn.reason.value)
    assertEquals(0, rig.requests)
  }

  @Test
  fun aFailedToggleShowsTheErrorAndRollsBack() = runBlocking {
    val rig = Rig(signedIn = true, status = HttpStatusCode.InternalServerError)
    val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.messages.messages.first() } }
    val rejection = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { rig.actions.rejections.first() } }

    assertTrue(rig.actions.toggleFavourite(post))

    assertTrue(message.await().let { it.isError && it.text.startsWith("Couldn't favourite") })
    assertEquals(Rejection.Favourite(post.id), rejection.await())
    assertEquals(false, rig.store.overrides.value[post.id]?.isFavourited)
  }
}

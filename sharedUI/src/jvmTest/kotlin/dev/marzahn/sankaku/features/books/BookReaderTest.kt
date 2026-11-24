package dev.marzahn.sankaku.features.books

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import dev.marzahn.sankaku.tabs.COUNT_PILL
import dev.marzahn.sankaku.tabs.PREVIOUS_ITEM
import dev.marzahn.sankaku.data.AccessibilitySettings
import dev.marzahn.sankaku.tabs.LocalOpenTabList
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.marzahn.sankaku.features.post.PlayerPreferences
import dev.marzahn.sankaku.features.post.FakeVideoPlayers
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import dev.marzahn.sankaku.ui.components.BurstFavourited
import dev.marzahn.sankaku.ui.components.FAVOURITE_BURST_TAG
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import coil3.ImageLoader
import com.github.panpf.zoomimage.util.setMainThreadChecker
import coil3.PlatformContext
import dev.marzahn.sankaku.ACCESSIBILITY_FILE
import dev.marzahn.sankaku.CACHE_DIR
import dev.marzahn.sankaku.DebugFlags
import dev.marzahn.sankaku.FIRST_LAUNCH_FILE
import dev.marzahn.sankaku.KNOWN_TAGS_FILE
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.PLAYER_FILE
import dev.marzahn.sankaku.PRIVACY_FILE
import dev.marzahn.sankaku.QUEUE_FILE
import dev.marzahn.sankaku.CACHE_FILE
import dev.marzahn.sankaku.GRID_FILE
import dev.marzahn.sankaku.OUTBOX_FILE
import dev.marzahn.sankaku.READING_FILE
import dev.marzahn.sankaku.REOPEN_HINT_FILE
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.SEEN_FILE
import dev.marzahn.sankaku.TIPS_FILE
import dev.marzahn.sankaku.UPDATES_FILE
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.auth.TokenStorageStatus
import dev.marzahn.sankaku.auth.TokenStore
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.TextFile
import dev.marzahn.sankaku.di.authModule
import dev.marzahn.sankaku.di.booksModule
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.di.shellModule
import org.koin.core.context.GlobalContext
import dev.marzahn.sankaku.di.sharedModule
import dev.marzahn.sankaku.features.post.DirectorySaveFolder
import dev.marzahn.sankaku.features.post.SaveFolder
import dev.marzahn.sankaku.features.tag.TagPages
import dev.marzahn.sankaku.tabs.FileTabStateStore
import dev.marzahn.sankaku.tabs.TabId
import dev.marzahn.sankaku.tabs.TabStateStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import okio.Path.Companion.toOkioPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.tabs.bookSeenId
import kotlin.test.assertFalse
import dev.marzahn.sankaku.api.dto.UserSummary
import dev.marzahn.sankaku.auth.StoredTokens
import io.ktor.http.HttpMethod
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.SkikoComposeUiTest
import java.util.Collections
import dev.marzahn.sankaku.net.ConnectivityState
import kotlinx.io.IOException
import org.koin.mp.KoinPlatform
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BookReaderTest {
  private val opened = mutableListOf<Route>()
  private var closed = 0
  private val navigator = object : Navigator {
    override fun goTo(route: Route) {
      opened += route
    }

    override fun goBack() = Unit

    override fun replaceTop(route: Route) = Unit

    override fun openInNewTab(route: Route): Int? = null
  }

  private val favouriteCalls = Collections.synchronizedList(mutableListOf<HttpMethod>())

  private fun startFixtureKoin(gOnly: Boolean, signedIn: Boolean = false) {
    setMainThreadChecker { true }
    val cache = Files.createTempDirectory("book-reader").toFile()
    val now = Clock.System.now()
    val tokens = if (signedIn) StoredTokens("access", "refresh", now + 7.days, now + 90.days, UserSummary(id = "42", name = "tester", avatarUrl = null)) else null
    val platform = module {
      single<TokenStore> { InMemoryTokenStore(tokens) }
      single { TokenStorageStatus.InMemory }
      single { DebugFlags(safeOnly = gOnly) }
      single(CACHE_DIR) { cache.toOkioPath() }
      listOf(KNOWN_TAGS_FILE, PLAYER_FILE, REOPEN_HINT_FILE, PRIVACY_FILE, ACCESSIBILITY_FILE, QUEUE_FILE, GRID_FILE, CACHE_FILE, TIPS_FILE, SEEN_FILE, UPDATES_FILE, FIRST_LAUNCH_FILE, READING_FILE, OUTBOX_FILE)
        .forEach { name -> single<TextFile>(name) { InMemoryTextFile() } }
      single<TabStateStore> { FileTabStateStore(InMemoryTextFile()) }
      single<SaveFolder> { DirectorySaveFolder(cache, opener = {}) }
      single<PlatformContext> { PlatformContext.INSTANCE }
    }
    val fixtures = module {
      single<HttpClientEngine> {
        MockEngine { request ->
          if (bookUnreachable && request.url.encodedPath == "/pools/$BOOK") throw IOException("unreachable")
          val body = when (request.url.encodedPath) {
            "/pools/$BOOK" -> bookJson
            "/pools/$BOOK/favorite" -> {
              favouriteCalls += request.method
              """{ "success": true, "fav_count": ${if (request.method == HttpMethod.Post) 8 else 7} }"""
            }
            "/graphql" -> SERIES_JSON
            else -> """{ "meta": {}, "data": [] }"""
          }
          respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
      }
      single<ImageLoader> { ImageLoader.Builder(PlatformContext.INSTANCE).build() }
      single { PlayerPreferences(get(PLAYER_FILE)) }
    }
    startKoin { modules(sharedModule, platform, booksModule, authModule, shellModule, fixtures) }
  }

  @AfterTest
  fun stop() = stopKoin()

  private fun ComposeUiTest.showBook(
    gOnly: Boolean = false,
    series: SheetSeries? = null,
    json: String = BOOK_JSON,
    firstPage: String = "Page 1 of 4",
    tab: TabId? = null,
    signedIn: Boolean = false,
    touch: Boolean = false,
    koin: Boolean = true,
    await: Boolean = true,
    under: @Composable () -> Unit = {},
  ) {
    bookJson = json
    if (koin) startFixtureKoin(gOnly, signedIn)
    setContent {
      Box {
        CompositionLocalProvider(LocalOpenTabList provides { queueOpened++ }) {
          BookReaderScreen(
            OpenBook(BOOK, "Fixture"),
            series = series,
            tab = tab,
            touch = touch,
            onClose = { closed++ },
            navigator = navigator,
            videoPlayback = players.playback,
          )
        }
        under()
      }
    }
    if (!await) return
    waitUntil(timeoutMillis = 5_000) {
      onAllNodesWithContentDescription(firstPage, substring = true).fetchSemanticsNodes().isNotEmpty() ||
        onAllNodesWithText(NO_PAGES).fetchSemanticsNodes().isNotEmpty()
    }
    waitForIdle()
  }

  private var bookJson = BOOK_JSON
  @Volatile private var bookUnreachable = false
  private var queueOpened = 0
  private val players = FakeVideoPlayers()

  @Test
  fun aVideoPagePlaysMutedOnScreenAndStopsOutOfView() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(json = VIDEO_BOOK_JSON, firstPage = "Page 1 of 2")
    waitUntil(timeoutMillis = 5_000) { players.all.any { it.opened > 0 } }
    val player = players.all.single()
    assertTrue(player.isPlaying, "plays in place on screen")
    assertEquals(0f, player.volume, "muted")

    repeat(3) {
      onRoot().performKeyInput { pressKey(Key.Spacebar) }
      mainClock.advanceTimeBy(1_000)
      waitForIdle()
    }
    assertTrue(onAllNodesWithContentDescription("Page 1 of 2").fetchSemanticsNodes().isEmpty(), "scrolled out of view")
    assertTrue(!player.isPlaying || player.released, "paused or released out of view")
    assertEquals(1, players.all.size, "no other player")
  }

  @Test
  fun aBookIsSeenOnceItWasShownAndLeft() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    bookJson = BOOK_JSON
    startFixtureKoin(gOnly = false)
    var open by mutableStateOf(true)
    setContent {
      if (open) BookReaderScreen(OpenBook(BOOK, "Fixture"), onClose = { open = false }, navigator = navigator, videoPlayback = players.playback)
    }
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Page 1 of 4", substring = true).fetchSemanticsNodes().isNotEmpty() }
    val seen = GlobalContext.get().get<SeenStore>()
    assertFalse(seen.isSeen(bookSeenId(BOOK)), "not while it's shown")

    onNodeWithContentDescription("Close").performClick()
    waitForIdle()

    assertTrue(seen.isSeen(bookSeenId(BOOK)), "seen once left")
    assertFalse(seen.isSeen(BOOK))
  }

  @Test
  fun aBookThatCouldNotBeReachedLoadsWhenTheNetworkIsBack() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    bookUnreachable = true
    showBook(await = false)
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
    bookUnreachable = false
    runOnIdle { KoinPlatform.getKoin().get<ConnectivityState>().requestRetry() }
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Page 1 of 4").fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun aBookWithNoPagesSaysSo() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(json = EMPTY_BOOK_JSON)
    onNodeWithText(NO_PAGES).assertExists()
    onNodeWithContentDescription("Close").assertExists()
  }

  private fun ComposeUiTest.chromeShown() = onAllNodesWithContentDescription("Close").fetchSemanticsNodes().isNotEmpty()

  @Test
  fun pagesFillTheColumnWidthInSequenceOrderWithNoGaps() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    val first = onNodeWithContentDescription("Page 1 of 4").getBoundsInRoot()
    val second = onNodeWithContentDescription("Page 2 of 4").getBoundsInRoot()
    assertEquals(first.width, first.height)
    assertEquals(first.width, second.width)
    assertEquals(first.bottom, second.top, "no gap")
    assertTrue(first.width <= MAX_STRIP_WIDTH, "at most ${MAX_STRIP_WIDTH}: ${first.width}")
    onNodeWithText("page 1 / 4").assertExists()
    onNodeWithText("Fixture book").assertExists()
  }

  @Test
  fun aTapTogglesTheChromeWhichOpensShownAndHidesOnScroll() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    assertTrue(chromeShown(), "opens shown")
    onRoot().performMouseInput { scroll(3f) }
    waitForIdle()
    assertTrue(!chromeShown(), "hidden by the first scroll")
    onRoot().performMouseInput { click(Offset(width / 2f, height / 2f)) }
    mainClock.advanceTimeBy(500)
    waitForIdle()
    assertTrue(chromeShown(), "a tap shows it again")
  }

  @Test
  fun gOnlyShowsAPageOfAnotherRatingAsABand() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(gOnly = true)
    onNodeWithText("Hidden by G only").assertExists()
    assertEquals(1, onAllNodesWithText("Hidden by G only").fetchSemanticsNodes().size)
    onNodeWithContentDescription("Page 2 of 4, hidden by g only").assertExists()
  }

  @Test
  fun aPinchZoomsTheWholeStripWithoutScrollingIt() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    val before = shownRect("Page 1 of 4")
    val secondBefore = shownRect("Page 2 of 4")
    onRoot().performTouchInput {
      val c = center
      pinch(c - Offset(20f, 20f), c - Offset(200f, 200f), c + Offset(20f, 20f), c + Offset(200f, 200f))
    }
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
    val after = shownRect("Page 1 of 4")
    val second = shownRect("Page 2 of 4")
    assertTrue(after.width > before.width * 1.5f, "zoomed: ${before.width} → ${after.width}")
    assertEquals(after.width, second.width, 0.5f, "the strip zooms as a whole")
    assertEquals(after.bottom, second.top, 0.5f, "still no gap")
    val zoom = after.width / before.width
    assertEquals(secondBefore.top * zoom + (after.top - before.top * zoom), second.top, 1f)
  }

  private fun ComposeUiTest.shownRect(description: String): Rect {
    val coordinates = onNodeWithContentDescription(description).fetchSemanticsNode().layoutInfo.coordinates
    val size = coordinates.size
    return Rect(coordinates.localToRoot(Offset.Zero), coordinates.localToRoot(Offset(size.width.toFloat(), size.height.toFloat())))
  }

  @Test
  fun theReaderKeepsTheKeysWhenAScreenUnderItTakesFocus() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(under = {
      val focus = remember { FocusRequester() }
      LaunchedEffect(Unit) { focus.requestFocus() }
      Box(Modifier.size(1.dp).focusRequester(focus).focusable())
    })
    onRoot().performKeyInput { pressKey(Key.Escape) }
    waitForIdle()
    assertEquals(1, closed)
  }

  @Test
  fun sAndWScrollALineAndSpaceAScreen() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    fun top() = shownRect("Page 2 of 4").top
    val start = top()
    fun press(key: Key) {
      onRoot().performKeyInput { pressKey(key) }
      mainClock.advanceTimeBy(1_000)
      waitForIdle()
    }
    press(Key.S)
    press(Key.S)
    val line = (start - top()) / 2
    assertTrue(line > 50f, "two presses of S go two lines: ${start - top()}")
    press(Key.W)
    assertEquals(start - line, top(), 1f)
    press(Key.W)
    assertEquals(start, top(), 1f)
    press(Key.Spacebar)
    assertEquals(start - HEIGHT * 0.85f * density.density, top(), 2f)
  }

  @Test
  fun escAndCloseCloseTheReader() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    onRoot().performKeyInput { pressKey(Key.Escape) }
    waitForIdle()
    assertEquals(1, closed)
    onNodeWithContentDescription("Close").performClick()
    waitForIdle()
    assertEquals(2, closed)
  }

  @Test
  fun moreOpensTheCurrentPageAsAPost() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    onNodeWithContentDescription("More").performClick()
    waitForIdle()
    onNodeWithText("Open this page as a post").performClick()
    waitForIdle()
    assertEquals(listOf<Route>(Route.Post("p0", "md5-p0", emptyList(), rating = "s")), opened)
    assertEquals(1, closed)
  }

  @Test
  fun rOpensTheMenuWithItsItemFocusedAndSpaceChoosesIt() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    onRoot().performMouseInput { scroll(3f) }
    waitForIdle()
    onRoot().performKeyInput { pressKey(Key.R) }
    waitForIdle()
    onNodeWithText("Open this page as a post").assertIsFocused()
    onNodeWithText("Open this page as a post").performKeyInput { pressKey(Key.Spacebar) }
    waitForIdle()
    assertEquals(1, opened.size)
  }

  private fun ComposeUiTest.tapDetails() {
    onNodeWithContentDescription(BOOK_SHEET).performClick()
    waitForIdle()
  }

  private fun ComposeUiTest.sheetShown() = onAllNodesWithText("A fixture description").fetchSemanticsNodes().isNotEmpty()

  @Test
  fun theTopRowsDetailsButtonOpensTheBookSheet() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    assertTrue(!sheetShown())

    tapDetails()

    assertTrue(sheetShown(), "the description")
    onNodeWithText("Fixture Artist").assertExists()
    onNodeWithText("Comedy").assertExists()
    onNodeWithText("4 pages").assertExists()
    assertTrue(onAllNodesWithText("Series").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun aTapAtTheBottomTogglesTheChromeRatherThanOpeningTheSheet() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    onRoot().performMouseInput { click(Offset(width / 2f, height - 20f)) }
    mainClock.advanceTimeBy(500)
    waitForIdle()

    assertTrue(!sheetShown(), "no sheet")
    assertTrue(!chromeShown(), "the tap hid the chrome")
  }

  @Test
  fun onTouchTheCountPillOpensTheQueuePage() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(tab = TabId("kept"), touch = true)

    onNodeWithTag(COUNT_PILL).performClick()
    waitForIdle()

    assertEquals(1, queueOpened)
    assertEquals(0, closed, "a kept book stays")
  }

  @Test
  fun overTheWindowTheCountPillClosesTheReaderForTheQueuePage() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(touch = true)

    onNodeWithTag(COUNT_PILL).performClick()
    waitForIdle()

    assertEquals(1, queueOpened)
    assertEquals(1, closed)
  }

  @Test
  fun overTheWindowThePillOffersNoQueueButtons() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    startFixtureKoinWithButtons()
    showBook(touch = true, koin = false)
    assertTrue(onAllNodesWithContentDescription("Close", substring = true).fetchSemanticsNodes().size == 1, "only the top row's ✕")
    assertTrue(onAllNodesWithContentDescription(PREVIOUS_ITEM, substring = true).fetchSemanticsNodes().isEmpty(), "no ‹")
    onNodeWithTag(COUNT_PILL).performClick()
    waitForIdle()
    assertEquals(1, queueOpened)
  }

  @Test
  fun inAKeptBooksTabThePillKeepsItsQueueButtons() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    startFixtureKoinWithButtons()
    showBook(touch = true, tab = TabId("kept"), koin = false)
    assertTrue(onAllNodesWithContentDescription("Close", substring = true).fetchSemanticsNodes().size > 1, "the pill's ✕ too")
  }

  private fun startFixtureKoinWithButtons() {
    startFixtureKoin(gOnly = false)
    GlobalContext.get().get<AccessibilitySettings>().setShowQueueButtons(true)
  }

  @Test
  fun theCountPillHidesWithTheChrome() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(touch = true)
    onNodeWithTag(COUNT_PILL).assertExists()
    onRoot().performMouseInput { scroll(3f) }
    waitForIdle()
    assertTrue(onAllNodesWithTag(COUNT_PILL).fetchSemanticsNodes().isEmpty(), "hidden with the chrome")
  }

  @Test
  fun onDesktopThereIsNoCountPill() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    assertTrue(onAllNodesWithTag(COUNT_PILL).fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun aTagInTheSheetOpensItsPageOverTheReader() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    tapDetails()

    onNodeWithText("Fixture Artist").performClick()
    waitForIdle()

    assertEquals("fixture_artist", GlobalContext.get().get<TagPages>().shown.value?.tag?.searchName)
    assertTrue(opened.isEmpty(), "no search yet")
    assertEquals(0, closed)
    assertTrue(!sheetShown(), "the sheet closed")
  }

  @Test
  fun aKeptBookStaysInTheQueueWhenItsSheetOpensASearch() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(series = SheetSeries("s1", listed = true), tab = TabId("kept"))
    tapDetails()
    onNodeWithText("Fixture series").performClick()
    waitForIdle()

    assertEquals(Route.BookSeries("s1", "Fixture series"), opened.last())
    assertEquals(0, closed)
  }

  @Test
  fun theSeriesItWasOpenedFromOpensItsBooks() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    val route = Route.BookSeries("s1", "Fixture series")
    showBook(series = SheetSeries("s1", listed = false))
    tapDetails()

    onNodeWithText("Book 2 of 6").assertExists()
    onNodeWithText("Fixture series").performClick()
    waitForIdle()

    assertEquals(listOf<Route>(route), opened)
    assertEquals(1, closed)
  }

  @Test
  fun signedOutTheHeartAsksToSignIn() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    onNodeWithContentDescription("Favourite").performClick()
    waitForIdle()

    val signIn = GlobalContext.get().get<SignInController>()
    assertTrue(signIn.isOpen.value)
    assertEquals("to favourite", signIn.reason.value)
  }

  @Test
  fun signedOutFAsksToSignInAsTheHeartDoes() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    onRoot().performKeyInput { pressKey(Key.F) }
    waitForIdle()

    val signIn = GlobalContext.get().get<SignInController>()
    assertTrue(signIn.isOpen.value)
    assertEquals("to favourite", signIn.reason.value)
    assertTrue(favouriteCalls.isEmpty())
  }

  @Test
  fun fFavouritesAndUnfavouritesTheBook() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(signedIn = true)

    onRoot().performKeyInput { pressKey(Key.F) }
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Unfavourite").fetchSemanticsNodes().isNotEmpty() }
    waitUntil(timeoutMillis = 5_000) { favouriteCalls.size == 1 }
    assertEquals(listOf(HttpMethod.Post), favouriteCalls.toList())

    onRoot().performKeyInput { pressKey(Key.F) }
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Favourite").fetchSemanticsNodes().isNotEmpty() }
    waitUntil(timeoutMillis = 5_000) { favouriteCalls.size == 2 }
    assertEquals(listOf(HttpMethod.Post, HttpMethod.Delete), favouriteCalls.toList())
    assertEquals(0, closed)
  }

  private fun ComposeUiTest.burstShown(favourited: Boolean? = null): Boolean {
    val heart = hasTestTag(FAVOURITE_BURST_TAG).let { tag ->
      favourited?.let { tag and SemanticsMatcher.expectValue(BurstFavourited, it) } ?: tag
    }
    return onAllNodes(heart, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
  }

  private fun ComposeUiTest.awaitBurst(favourited: Boolean) {
    val deadline = System.currentTimeMillis() + 5_000
    while (!burstShown(favourited)) {
      check(System.currentTimeMillis() < deadline) { "no ${if (favourited) "filled" else "broken"} heart" }
      mainClock.advanceTimeByFrame()
      Thread.sleep(5)
    }
  }

  @Test
  fun fPlaysTheFilledHeartThenTheBrokenOne() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(signedIn = true)
    assertFalse(burstShown())
    mainClock.autoAdvance = false

    onRoot().performKeyInput { pressKey(Key.F) }
    awaitBurst(favourited = true)
    val heart = onNode(hasTestTag(FAVOURITE_BURST_TAG), useUnmergedTree = true).getBoundsInRoot()
    assertEquals(WIDTH.dp / 2, (heart.left + heart.right) / 2)
    assertEquals(HEIGHT.dp / 2, (heart.top + heart.bottom) / 2)

    onRoot().performKeyInput { pressKey(Key.F) }
    awaitBurst(favourited = false)
    mainClock.advanceTimeBy(3_000)
    assertFalse(burstShown())
  }

  @Test
  fun theTopRowsHeartPlaysTheBurstToo() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(signedIn = true)
    mainClock.autoAdvance = false

    onNodeWithContentDescription("Favourite").performClick()

    awaitBurst(favourited = true)
  }

  @Test
  fun signedOutFPlaysNoHeart() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook()
    mainClock.autoAdvance = false

    onRoot().performKeyInput { pressKey(Key.F) }
    repeat(10) {
      mainClock.advanceTimeByFrame()
      Thread.sleep(5)
      assertFalse(burstShown())
    }
    val signIn = GlobalContext.get().get<SignInController>()
    assertTrue(signIn.isOpen.value)
    assertEquals("to favourite", signIn.reason.value)
  }

  @OptIn(InternalComposeUiApi::class)
  @Test
  fun aHeldFTogglesOnce() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(signedIn = true)
    val scene = (this as SkikoComposeUiTest).scene
    listOf(KeyEventType.KeyDown, KeyEventType.KeyDown, KeyEventType.KeyDown, KeyEventType.KeyUp).forEach { type ->
      runOnIdle { scene.sendKeyEvent(KeyEvent(Key.F, type)) }
    }
    waitUntil(timeoutMillis = 5_000) { favouriteCalls.size == 1 }
    Thread.sleep(200)
    waitForIdle()

    assertEquals(listOf(HttpMethod.Post), favouriteCalls.toList())
    onNodeWithContentDescription("Unfavourite").assertExists()
  }

  @OptIn(InternalComposeUiApi::class)
  @Test
  fun anFWhoseReleaseWentElsewhereLeavesTheNextFNew() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    val elsewhere = FocusRequester()
    showBook(signedIn = true, under = { Box(Modifier.size(1.dp).focusRequester(elsewhere).focusable()) })
    val scene = (this as SkikoComposeUiTest).scene
    runOnIdle { scene.sendKeyEvent(KeyEvent(Key.F, KeyEventType.KeyDown)) }
    waitUntil(timeoutMillis = 5_000) { favouriteCalls.size == 1 }
    runOnIdle { elsewhere.requestFocus() }
    waitForIdle()
    runOnIdle { scene.sendKeyEvent(KeyEvent(Key.F, KeyEventType.KeyDown)) }

    waitUntil(timeoutMillis = 5_000) { favouriteCalls.size == 2 }
    assertEquals(listOf(HttpMethod.Post, HttpMethod.Delete), favouriteCalls.toList())
  }

  @Test
  fun cmdFIsNotTheReadersFavourite() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showBook(signedIn = true)
    onRoot().performKeyInput {
      keyDown(Key.MetaLeft)
      pressKey(Key.F)
      keyUp(Key.MetaLeft)
    }
    waitForIdle()
    Thread.sleep(200)

    assertTrue(favouriteCalls.isEmpty())
    onNodeWithContentDescription("Favourite").assertExists()
  }

  internal companion object {
    const val BOOK = "fixturebook"

    const val WIDTH = 600
    const val HEIGHT = 1000

    fun pageJson(id: String, sequence: Int, rating: String, height: Int, type: String = "image/png") = """
      { "id": "$id", "md5": "md5-$id", "rating": "$rating", "status": "active", "sequence": $sequence,
        "file_type": "$type", "width": 1000, "height": $height,
        "sample_url": "https://fixture.invalid/$id.${if (type == "video/mp4") "mp4" else "png"}" }
    """.trimIndent()

    val VIDEO_BOOK_JSON = """
      { "id": "$BOOK", "name_en": "Fixture book", "rating": "s", "post_count": 2,
        "posts": [
          ${pageJson("v0", 0, "s", 1000, type = "video/mp4")},
          ${pageJson("p1", 1, "s", 6000)}
        ] }
    """.trimIndent()

    val EMPTY_BOOK_JSON = """{ "id": "$BOOK", "name_en": "Fixture book", "rating": "s", "status": "pending", "posts": [] }"""

    val SERIES_JSON = """
      { "data": { "series": { "id": "s1", "name_en": "Fixture series", "pool_count": 6,
        "books": [ { "id": "first", "sequence": 1 }, { "id": "$BOOK", "sequence": 2 } ] } } }
    """.trimIndent()

    val BOOK_JSON = """
      { "id": "$BOOK", "name_en": "Fixture book", "rating": "s", "post_count": 5, "fav_count": 7,
        "description_en": "A fixture description",
        "artist_tags": [{ "name": "Fixture Artist", "tagName": "fixture_artist", "type": 1 }],
        "genre_tags": [{ "name": "Comedy", "tagName": "comedy", "type": 0 }],
        "posts": [
          ${pageJson("p1", 1, "e", 2000)},
          ${pageJson("p0", 0, "s", 1000)},
          ${pageJson("p2", 2, "s", 1000)},
          ${pageJson("p3", 3, "s", 1000)}
        ] }
    """.trimIndent()
  }
}

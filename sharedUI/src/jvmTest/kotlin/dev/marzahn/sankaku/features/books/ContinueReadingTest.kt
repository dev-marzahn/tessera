package dev.marzahn.sankaku.features.books

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import coil3.ImageLoader
import coil3.PlatformContext
import com.github.panpf.zoomimage.util.setMainThreadChecker
import dev.marzahn.sankaku.ACCESSIBILITY_FILE
import dev.marzahn.sankaku.CACHE_DIR
import dev.marzahn.sankaku.DebugFlags
import dev.marzahn.sankaku.FIRST_LAUNCH_FILE
import dev.marzahn.sankaku.CACHE_FILE
import dev.marzahn.sankaku.GRID_FILE
import dev.marzahn.sankaku.KNOWN_TAGS_FILE
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.OUTBOX_FILE
import dev.marzahn.sankaku.PLAYER_FILE
import dev.marzahn.sankaku.PRIVACY_FILE
import dev.marzahn.sankaku.QUEUE_FILE
import dev.marzahn.sankaku.READING_FILE
import dev.marzahn.sankaku.REOPEN_HINT_FILE
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.SEEN_FILE
import dev.marzahn.sankaku.TIPS_FILE
import dev.marzahn.sankaku.UPDATES_FILE
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.api.dto.UserSummary
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.auth.StoredTokens
import dev.marzahn.sankaku.auth.TokenStorageStatus
import dev.marzahn.sankaku.auth.TokenStore
import dev.marzahn.sankaku.data.BookPlace
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.ReadingProgress
import dev.marzahn.sankaku.data.SavedReading
import dev.marzahn.sankaku.data.TextFile
import dev.marzahn.sankaku.di.authModule
import dev.marzahn.sankaku.di.booksModule
import dev.marzahn.sankaku.di.sharedModule
import dev.marzahn.sankaku.features.post.PlayerPreferences
import dev.marzahn.sankaku.tabs.FileTabStateStore
import dev.marzahn.sankaku.tabs.TabStateStore
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.ui.Messages
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.koin.mp.KoinPlatform
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalTestApi::class)
class ContinueReadingTest {
  private val reader = BookReader { _, _ -> }

  private val listed = CopyOnWriteArrayList<String>()

  private fun startFixtureKoin(signedIn: Boolean) {
    setMainThreadChecker { true }
    val cache = Files.createTempDirectory("continue-reading").toFile()
    val now = Clock.System.now()
    val tokens = StoredTokens("access", "refresh", now + 7.days, now + 90.days, UserSummary(id = "42", name = "tester", avatarUrl = null))
    val platform = module {
      single<TokenStore> { InMemoryTokenStore(tokens.takeIf { signedIn }) }
      single { TokenStorageStatus.InMemory }
      single { DebugFlags() }
      single(CACHE_DIR) { cache.toOkioPath() }
      listOf(KNOWN_TAGS_FILE, PLAYER_FILE, REOPEN_HINT_FILE, PRIVACY_FILE, ACCESSIBILITY_FILE, QUEUE_FILE, GRID_FILE, CACHE_FILE, TIPS_FILE, SEEN_FILE, UPDATES_FILE, FIRST_LAUNCH_FILE, READING_FILE, OUTBOX_FILE)
        .forEach { name -> single<TextFile>(name) { InMemoryTextFile() } }
      single<TabStateStore> { FileTabStateStore(InMemoryTextFile()) }
    }
    val fixtures = module {
      single<HttpClientEngine> {
        MockEngine { request ->
          val path = request.url.encodedPath
          val tags = request.url.parameters["tags"].orEmpty()
          if (path == "/poolseriesv2") listed += tags
          val firstPage = (request.url.parameters["page"] ?: "1") == "1"
          val body = when {
            request.method == HttpMethod.Put && path.endsWith("/reading") -> """{ "success": true }"""
            path == "/poolseriesv2" && "order:reading" in tags -> READING_JSON
            path == "/poolseriesv2" -> if (firstPage) LIST_JSON else EMPTY_LIST_JSON
            path.startsWith("/pools/") -> bookJson(path.removePrefix("/pools/"))
            else -> """{ "meta": {}, "data": [] }"""
          }
          respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
      }
      single<ImageLoader> { ImageLoader.Builder(PlatformContext.INSTANCE).build() }
      single { PlayerPreferences(get(PLAYER_FILE)) }
      single { TabsController() }
      single<Navigator> {
        object : Navigator {
          override fun goTo(route: Route) = Unit

          override fun goBack() = Unit

          override fun replaceTop(route: Route) = Unit

          override fun openInNewTab(route: Route): Int? = null
        }
      }
    }
    startKoin { modules(sharedModule, platform, booksModule, authModule, fixtures, module { single { Messages() } }) }
  }

  @AfterTest
  fun stop() = stopKoin()

  private fun ComposeUiTest.showList(route: Route.Books = Route.Books(), signedIn: Boolean = true) {
    startFixtureKoin(signedIn)
    setContent {
      Box {
        BookListScreen(route, reader = reader)
        BookReaderPage(reader)
      }
    }
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(BOOK_ROW_TAG).fetchSemanticsNodes().size >= 3 }
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
  }

  private fun ComposeUiTest.shelf(): List<String> =
    onAllNodesWithTag(SHELF_BOOK_TAG).fetchSemanticsNodes().map { node ->
      node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }.first { it.startsWith("Reading ") }
    }

  @Test
  fun theShelfListsTheStartedUnfinishedBooksInSankakusOrder() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showList()
    waitUntil(timeoutMillis = 5_000) { shelf().isNotEmpty() }
    assertEquals(listOf("Reading one", "Reading three"), shelf())
    assertTrue(onAllNodesWithText(CONTINUE_READING).fetchSemanticsNodes().isNotEmpty())
    assertTrue(onAllNodesWithText("Page 2 / 3", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    assertTrue("order:reading" in listed, "$listed")
  }

  @Test
  fun aBookOnTheShelfOpensAtItsSavedPage() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showList()
    waitUntil(timeoutMillis = 5_000) { shelf().isNotEmpty() }
    onAllNodesWithTag(SHELF_BOOK_TAG)[0].performClick()
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Page 2 of 3").fetchSemanticsNodes().isNotEmpty() }
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
    assertEquals("r1", reader.shown.value?.id)
    val shown = onAllNodesWithContentDescription("Page 2 of 3").fetchSemanticsNodes().map { it.boundsInRoot }.minBy { abs(it.left) }
    assertEquals(0f, shown.top, 0.5f)
  }

  @Test
  fun placesSavedWhileTheReaderIsOpenAskForTheShelfOnceItCloses() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showList()
    waitUntil(timeoutMillis = 5_000) { shelf().isNotEmpty() }
    onAllNodesWithTag(SHELF_BOOK_TAG)[0].performClick()
    waitUntil(timeoutMillis = 5_000) { reader.session.value != null }
    waitForIdle()
    fun asks() = listed.count { "order:reading" in it }
    val before = asks()
    val progress = KoinPlatform.getKoin().get<ReadingProgress>()

    repeat(3) { page ->
      val landed = progress.landed.value
      progress.save("r1", BookPlace(sequence = 0, page = page + 1, pages = 3))
      waitUntil(timeoutMillis = 5_000) { progress.landed.value > landed }
      waitForIdle()
    }
    assertEquals(before, asks(), "nothing asked while the reader covers the shelf")

    runOnIdle { reader.close() }
    waitUntil(timeoutMillis = 5_000) { asks() > before }
    waitForIdle()
    assertEquals(before + 1, asks(), "asked once when the reader closes")
  }

  @Test
  fun theShelfScrollsAwayWithTheRowsAndAWheelBringsItBack() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showList()
    waitUntil(timeoutMillis = 5_000) { shelf().isNotEmpty() }
    fun shelfTop() = onAllNodesWithTag(SHELF_TAG).fetchSemanticsNodes().single().boundsInRoot.top
    val shown = shelfTop()
    onRoot().performMouseInput {
      moveTo(center)
      scroll(30f)
    }
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
    assertTrue(shelfTop() < shown - 50f, "the shelf scrolled away: ${shelfTop()} from $shown")
    onRoot().performMouseInput {
      moveTo(center)
      scroll(-2f)
    }
    mainClock.advanceTimeBy(2_000)
    waitForIdle()
    assertEquals(shown, shelfTop(), 0.5f)
  }

  @Test
  fun aSignOutWhileTheShelfIsntShownForgetsItsBooks() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showList()
    waitUntil(timeoutMillis = 5_000) { shelf().isNotEmpty() }
    val koin = KoinPlatform.getKoin()
    val shelfModel = ContinueReadingViewModel(koin.get(), koin.get(), koin.get(), koin.get())
    runOnIdle { shelfModel.shown(true) }
    waitUntil(timeoutMillis = 5_000) { shelfModel.books.value.isNotEmpty() }
    runOnIdle { shelfModel.shown(false) }
    runBlocking { koin.get<AuthSession>().signOut() }
    waitUntil(timeoutMillis = 5_000) { shelfModel.books.value.isEmpty() }
  }

  @Test
  fun signedOutThereIsNoShelf() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showList(signedIn = false)
    assertEquals(emptyList(), shelf())
    assertTrue(listed.none { "order:reading" in it }, "asked for signed out: $listed")
  }

  @Test
  fun aSearchHasNoShelf() = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
    showList(Route.Books(listOf("maid")))
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
    assertEquals(emptyList(), shelf())
    assertTrue(listed.none { "order:reading" in it }, "$listed")
  }

  @Test
  fun theShelfLeavesOutWhatIsntStartedOrIsFinished() {
    val started = BookDto(id = "a")
    val read = BookDto(id = "b")
    val never = BookDto(id = "c")
    val places = mapOf(
      "a" to SavedReading(sequence = 1, page = 2, pages = 3, finished = false),
      "b" to SavedReading(sequence = 2, page = 3, pages = 3, finished = true),
    )
    assertEquals(listOf("a"), shelfBooks(listOf(read, started, never)) { book -> places[book.id] }.map { it.id })
  }

  private companion object {
    const val WIDTH = 600
    const val HEIGHT = 800

    fun bookItem(n: Int) = """{ "id": "b$n", "name_en": "Book $n", "rating": "s", "status": "active", "post_count": 2 }"""

    val LIST_JSON = """{ "series": { "data": [] }, "pools": { "meta": {}, "data": [ ${(1..4).joinToString { bookItem(it) }} ] } }"""
    const val EMPTY_LIST_JSON = """{ "series": { "data": [] }, "pools": { "meta": {}, "data": [] } }"""

    fun readingItem(n: Int, word: String, page: Int, percent: Int) = """
      { "id": "r$n", "name_en": "Reading $word", "rating": "s", "status": "active", "post_count": 3,
        "reading": { "current_page": $page, "sequence": ${page - 1}, "percent": $percent } }
    """.trimIndent()

    val READING_JSON = """{ "series": { "data": [] }, "pools": { "meta": {}, "data": [
      ${readingItem(1, "one", 2, 67)}, ${readingItem(2, "two", 3, 100)}, ${readingItem(3, "three", 1, 33)}
    ] } }"""

    fun pageJson(id: String, sequence: Int) = """
      { "id": "$id", "md5": "md5-$id", "rating": "s", "status": "active", "sequence": $sequence,
        "file_type": "image/png", "width": 1000, "height": 1000, "sample_url": "https://fixture.invalid/$id.png" }
    """.trimIndent()

    fun bookJson(id: String): String = """
      { "id": "$id", "name_en": "Reading", "rating": "s", "post_count": 3,
        "reading": { "current_page": 2, "sequence": 1, "percent": 67 },
        "posts": [ ${pageJson("$id-0", 0)}, ${pageJson("$id-1", 1)}, ${pageJson("$id-2", 2)} ] }
    """.trimIndent()
  }
}

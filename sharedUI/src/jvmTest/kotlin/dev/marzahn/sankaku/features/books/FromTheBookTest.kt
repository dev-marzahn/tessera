package dev.marzahn.sankaku.features.books

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import dev.marzahn.sankaku.ACCESSIBILITY_FILE
import dev.marzahn.sankaku.CACHE_DIR
import dev.marzahn.sankaku.DebugFlags
import dev.marzahn.sankaku.FIRST_LAUNCH_FILE
import dev.marzahn.sankaku.KNOWN_TAGS_FILE
import dev.marzahn.sankaku.PLAYER_FILE
import dev.marzahn.sankaku.PRIVACY_FILE
import dev.marzahn.sankaku.QUEUE_FILE
import dev.marzahn.sankaku.CACHE_FILE
import dev.marzahn.sankaku.GRID_FILE
import dev.marzahn.sankaku.REOPEN_HINT_FILE
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.OUTBOX_FILE
import dev.marzahn.sankaku.READING_FILE
import dev.marzahn.sankaku.SEEN_FILE
import dev.marzahn.sankaku.TIPS_FILE
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.UPDATES_FILE
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.auth.TokenStorageStatus
import dev.marzahn.sankaku.auth.TokenStore
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.TextFile
import dev.marzahn.sankaku.di.authModule
import dev.marzahn.sankaku.di.booksModule
import dev.marzahn.sankaku.di.gridModule
import dev.marzahn.sankaku.di.postModule
import dev.marzahn.sankaku.di.sharedModule
import dev.marzahn.sankaku.di.shellModule
import dev.marzahn.sankaku.features.post.DirectorySaveFolder
import dev.marzahn.sankaku.features.post.PostDetails
import dev.marzahn.sankaku.features.post.SaveFolder
import dev.marzahn.sankaku.tabs.FileTabStateStore
import dev.marzahn.sankaku.tabs.TabStateStore
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import okio.Path.Companion.toOkioPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FromTheBookTest {
  private val tabs = TabsController().apply { open(Route.Grid(listOf("scenery")), background = false) }
  private val navigator = TabsNavigator(tabs)
  private val booksAsked: MutableList<String> = Collections.synchronizedList(mutableListOf())
  private val opened = mutableListOf<String>()

  private val page = PostDto(id = "4012", md5 = "fixture-md5", rating = "s", status = "active", inVisiblePool = true)

  @BeforeTest
  fun startFixtureKoin() {
    val cache = Files.createTempDirectory("from-the-book").toFile()
    val platform = module {
      single<TokenStore> { InMemoryTokenStore() }
      single { TokenStorageStatus.InMemory }
      single { DebugFlags() }
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
          val path = request.url.encodedPath
          if (path.startsWith("/posts/")) return@MockEngine respondError(HttpStatusCode.NotFound)
          val body = when {
            path.startsWith("/post/") && path.endsWith("/pools") -> {
              booksAsked += path.removePrefix("/post/").removeSuffix("/pools")
              """[ { "id": 11, "name_en": "Explicit edition", "rating": "e" },
                { "id": 12, "name_en": "The book", "rating": "s" } ]"""
            }
            path == "/v2/posts" -> "[]"
            else -> """{ "meta": { "next": null }, "data": [] }"""
          }
          respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
      }
      single<ImageLoader> { ImageLoader.Builder(PlatformContext.INSTANCE).build() }
      single<BookOpener> { BookOpener { book -> opened += "${book.id} ${book.title}" } }
    }
    startKoin { modules(sharedModule, platform, shellModule, authModule, gridModule, postModule, booksModule, fixtures) }
  }

  @AfterTest
  fun stop() = stopKoin()

  private fun ComposeUiTest.awaitRow() = waitUntil(timeoutMillis = 5_000) {
    onAllNodesWithContentDescription(ROW).fetchSemanticsNodes().isNotEmpty()
  }

  private fun ComposeUiTest.settle() {
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Rating").fetchSemanticsNodes().isNotEmpty() }
    waitForIdle()
    Thread.sleep(300)
    waitForIdle()
  }

  @Test
  fun aBooksPageShowsItsBookAndAClickOpensIt() = runDesktopComposeUiTest {
    setContent { SankakuTheme { PostDetails(page, navigator = navigator, tabs = tabs) } }
    awaitRow()

    assertEquals(listOf("4012"), booksAsked.toList(), "one request, with the post's id")
    onNodeWithContentDescription(ROW).performClick()
    assertEquals(listOf("12 The book"), opened)
    onNodeWithContentDescription(ROW).performMouseInput {
      moveTo(center)
      press(MouseButton.Tertiary)
      release(MouseButton.Tertiary)
    }
    waitForIdle()
    assertEquals(listOf("12 The book"), opened)
    assertEquals(Route.Book("12", "The book", cover = null, rating = "s"), tabs.tabs.value.last().top)
  }

  @Test
  fun aPostInNoBookShowsNothingAndAsksNothing() = runDesktopComposeUiTest {
    setContent { SankakuTheme { PostDetails(page.copy(inVisiblePool = false), navigator = navigator, tabs = tabs) } }
    settle()
    assertTrue(onAllNodesWithText("From the book").fetchSemanticsNodes().isEmpty())
    assertTrue(booksAsked.isEmpty())
  }

  @Test
  fun aDesktopIdFindsItsBookToo() = runDesktopComposeUiTest {
    setContent { SankakuTheme { PostDetails(page.copy(id = "aB3dE5gH7jK"), navigator = navigator, tabs = tabs) } }
    awaitRow()
    assertEquals(listOf("aB3dE5gH7jK"), booksAsked.toList())
  }

  @Test
  fun closedDetailsAskNothingAndOpenedOnesAskOncePerPost() = runDesktopComposeUiTest {
    var open by mutableStateOf(false)
    var shown by mutableStateOf(page)
    setContent {
      SankakuTheme {
        Box(if (open) Modifier else Modifier.height(0.dp)) { PostDetails(shown, navigator = navigator, tabs = tabs) }
      }
    }
    waitForIdle()
    Thread.sleep(300)
    assertTrue(booksAsked.isEmpty(), "a sheet at peek or a closed panel asks nothing")

    open = true
    awaitRow()
    shown = page.copy(id = "4013")
    waitUntil(timeoutMillis = 5_000) { booksAsked.size == 2 }
    shown = page
    waitForIdle()
    onNodeWithContentDescription(ROW).assertExists()
    assertEquals(listOf("4012", "4013"), booksAsked.toList())
  }

  private companion object {
    const val ROW = "From the book The book"
  }
}

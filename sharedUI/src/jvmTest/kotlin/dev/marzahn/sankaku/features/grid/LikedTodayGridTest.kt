package dev.marzahn.sankaku.features.grid

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import coil3.PlatformContext
import dev.marzahn.sankaku.ACCESSIBILITY_FILE
import dev.marzahn.sankaku.ActiveTab
import dev.marzahn.sankaku.CACHE_DIR
import dev.marzahn.sankaku.DebugFlags
import dev.marzahn.sankaku.FIRST_LAUNCH_FILE
import dev.marzahn.sankaku.CACHE_FILE
import dev.marzahn.sankaku.GRID_FILE
import dev.marzahn.sankaku.KNOWN_TAGS_FILE
import dev.marzahn.sankaku.OUTBOX_FILE
import dev.marzahn.sankaku.PLAYER_FILE
import dev.marzahn.sankaku.PRIVACY_FILE
import dev.marzahn.sankaku.QUEUE_FILE
import dev.marzahn.sankaku.READING_FILE
import dev.marzahn.sankaku.REOPEN_HINT_FILE
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.SEEN_FILE
import dev.marzahn.sankaku.TIPS_FILE
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.UPDATES_FILE
import dev.marzahn.sankaku.api.dto.UserSummary
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.auth.StoredTokens
import dev.marzahn.sankaku.auth.TokenStorageStatus
import dev.marzahn.sankaku.auth.TokenStore
import dev.marzahn.sankaku.data.GridPresets
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.TextFile
import dev.marzahn.sankaku.di.authModule
import dev.marzahn.sankaku.di.booksModule
import dev.marzahn.sankaku.di.gridModule
import dev.marzahn.sankaku.di.postModule
import dev.marzahn.sankaku.di.sharedModule
import dev.marzahn.sankaku.di.shellModule
import dev.marzahn.sankaku.features.post.DirectorySaveFolder
import dev.marzahn.sankaku.features.post.SaveFolder
import dev.marzahn.sankaku.queue.TodayStore
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import okio.Path.Companion.toOkioPath
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalTestApi::class)
class LikedTodayGridTest {
  private val ids = List(20) { "f$it" }
  private val likedHere = listOf("f0", "f1", "f2", "f4")
  private val favourites = GridPresets.favourites("tester")

  private fun page() = ids.joinToString(",", """{"meta":{"next":null,"prev":null},"data":[""", "]}") { id ->
    """{"id":"$id","md5":"md5-$id","rating":"s","status":"active","file_type":"image/png","width":300,"height":300,""" +
      """"preview_width":300,"preview_height":300}"""
  }

  private fun startFixtureKoin() {
    val cache = Files.createTempDirectory("liked-today").toFile().apply { deleteOnExit() }
    val platform = module {
      single<TokenStore> {
        val at = Clock.System.now()
        InMemoryTokenStore(StoredTokens("access", "refresh", at + 7.days, at + 90.days, UserSummary(id = "42", name = "tester", avatarUrl = null)))
      }
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
          if (request.url.host == "sankakuapi.com" && request.url.encodedPath == "/v2/posts/keyset") {
            respond(page(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
          } else {
            respondError(HttpStatusCode.NotFound)
          }
        }
      }
    }
    startKoin { modules(sharedModule, platform, shellModule, authModule, gridModule, postModule, booksModule, fixtures) }
  }

  @AfterTest
  fun stop() = stopKoin()

  private fun favouritesGrid(tags: List<String> = favourites, test: ComposeUiTest.() -> Unit) {
    startFixtureKoin()
    val koin = GlobalContext.get()
    runBlocking { koin.get<AuthSession>().restore() }
    val today = koin.get<TodayStore>()
    likedHere.forEach { today.liked(it, on = true) }
    val tabs = koin.get<TabsController>()
    val route = Route.Grid(tags)
    tabs.open(route, background = false)
    tabs.tabs.value.filter { it.top != route }.forEach { tabs.close(it.id) }
    val navigator = TabsNavigator(tabs)
    runDesktopComposeUiTest(width = 1200, height = 2000, effectContext = StandardTestDispatcher()) {
      setContent {
        SankakuTheme {
          val activeId = tabs.active.collectAsState().value
          val active = tabs.tabs.collectAsState().value.first { it.id == activeId }
          ActiveTab(active, tabs, navigator, rememberSaveableStateHolder()) { key ->
            NavEntry<NavKey>(key) { if (key is Route.Grid) GridScreen(key) }
          }
        }
      }
      waitUntil(timeoutMillis = 5_000) { tagged(gridCellTag(0)).isNotEmpty() }
      test()
    }
  }

  private fun ComposeUiTest.tagged(prefix: String) =
    onAllNodes(SemanticsMatcher("tag starts with $prefix") {
      SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag].startsWith(prefix)
    }).fetchSemanticsNodes()

  @Test
  fun todayIsOverTheTopAndEarlierWhereTodaysFavouritesEnd() = favouritesGrid {
    val heading = onNodeWithTag("feed-heading-liked-today").assertIsDisplayed().getBoundsInRoot()
    onNodeWithText("Today · 4").assertIsDisplayed()
    val earlier = onNodeWithTag("feed-heading-earlier").assertIsDisplayed().getBoundsInRoot()
    val todays = (0..4).map { onNodeWithTag(gridCellTag(it)).getBoundsInRoot() }
    val older = (5..9).map { onNodeWithTag(gridCellTag(it)).getBoundsInRoot() }

    assertTrue(todays.all { heading.bottom <= it.top }, "Today is over every post")
    assertTrue(heading.right - heading.left > (todays[0].right - todays[0].left) * 3, "full-span")
    assertTrue(todays.all { it.bottom <= earlier.top }, "Earlier is below the last post favourited here today")
    assertTrue(older.all { earlier.bottom <= it.top }, "and above the posts favourited before")
    assertTrue(earlier.right - earlier.left > (todays[0].right - todays[0].left) * 3, "full-span")
  }

  @Test
  fun aNarrowerSearchOfTheFavouritesHasBothWithoutTheCount() = favouritesGrid(favourites + "scenery") {
    onNodeWithText("Today").assertIsDisplayed()
    onNodeWithTag("feed-heading-earlier").assertIsDisplayed()
  }

  @Test
  fun favouritesInAnotherOrderHasNeither() = favouritesGrid(listOf("order:popularity") + favourites) {
    assertTrue(tagged("feed-heading-").isEmpty())
    onNodeWithTag(gridCellTag(0)).assertIsDisplayed()
  }

  @Test
  fun unfavouritingTheLastOfTodaysLikesRemovesBothHeadings() = favouritesGrid {
    val today = GlobalContext.get().get<TodayStore>()
    runOnIdle { likedHere.drop(1).forEach { today.liked(it, on = false) } }
    waitForIdle()
    onNodeWithText("Today · 1").assertIsDisplayed()
    onNodeWithTag("feed-heading-earlier").assertIsDisplayed()

    runOnIdle { today.liked(likedHere.first(), on = false) }
    waitForIdle()

    assertTrue(tagged("feed-heading-").isEmpty(), "neither heading with nothing liked today")
    onNodeWithTag(gridCellTag(0)).assertIsDisplayed()

    runOnIdle { today.liked(likedHere.first(), on = true) }
    waitForIdle()
    onNodeWithText("Today · 1").assertIsDisplayed()
    onNodeWithTag("feed-heading-earlier").assertIsDisplayed()
  }
}

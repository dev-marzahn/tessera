package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import dev.marzahn.sankaku.ui.Tips
import dev.marzahn.sankaku.ui.components.TipLayer
import dev.marzahn.sankaku.ui.components.tipGestures
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import dev.marzahn.sankaku.ACCESSIBILITY_FILE
import dev.marzahn.sankaku.ActiveTab
import dev.marzahn.sankaku.CACHE_DIR
import dev.marzahn.sankaku.DebugFlags
import dev.marzahn.sankaku.FIRST_LAUNCH_FILE
import dev.marzahn.sankaku.CACHE_FILE
import dev.marzahn.sankaku.GRID_FILE
import dev.marzahn.sankaku.KNOWN_TAGS_FILE
import dev.marzahn.sankaku.PLAYER_FILE
import dev.marzahn.sankaku.PRIVACY_FILE
import dev.marzahn.sankaku.QUEUE_FILE
import dev.marzahn.sankaku.OUTBOX_FILE
import dev.marzahn.sankaku.READING_FILE
import dev.marzahn.sankaku.REOPEN_HINT_FILE
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.SEEN_FILE
import dev.marzahn.sankaku.TIPS_FILE
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.UPDATES_FILE
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.auth.StoredTokens
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
import dev.marzahn.sankaku.features.post.LocalVideoPlayback
import dev.marzahn.sankaku.features.post.SaveFolder
import dev.marzahn.sankaku.features.post.VideoPlayback
import dev.marzahn.sankaku.tabs.FileTabStateStore
import dev.marzahn.sankaku.tabs.TabStateStore
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import dev.marzahn.sankaku.images.PostImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import kotlin.random.Random
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalTestApi::class)
internal class GridScreenFixture(
  private val types: Map<Int, String> = emptyMap(),
  private val player: VideoPlayback? = null,
  private val images: Boolean = false,
  private val pictures: Map<Int, Int> = emptyMap(),
  private val locked: Set<Int> = emptySet(),
  private val tokens: StoredTokens? = null,
  secondPageAtStart: SecondPage = SecondPage.None,
) {
  val grid = Route.Grid(listOf("fixture_tag"))
  private var collecting: Job? = null

  val messages = mutableListOf<String>()

  private fun url(path: String) = "https://v.sankakucomplex.com/data/$path?e=${(Clock.System.now() + 1.hours).epochSeconds}"

  enum class SecondPage { None, Hold, Fail, SearchTimeout, Serve }

  @Volatile
  var secondPage: SecondPage = secondPageAtStart

  private val held = CompletableDeferred<Unit>()

  fun releaseSecondPage() {
    held.complete(Unit)
  }

  private fun page(numbers: IntRange, more: Boolean): String {
    val posts = numbers.joinToString(",") { n ->
      if (n in locked) {
        return@joinToString """{"id":"fixture-$n","md5":"fixture-md5-$n","rating":"s","status":"active","redirect_to_signup":true}"""
      }
      val type = types[n] ?: "image/png"
      val ext = type.substringAfter('/')
      """{"id":"fixture-$n","md5":"fixture-md5-$n","rating":"s","status":"active","file_type":"$type",""" +
        """"width":300,"height":400,"preview_width":300,"preview_height":400,"preview_url":"${url("preview/fixture-$n.png")}",""" +
        """"sample_url":"${url("sample/fixture-$n.$ext")}",""" +
        """"tags":[{"name":"Fixture $n","tagName":"fixture_$n","type":0}],"tag_names":["fixture_$n"]}"""
    }
    val next = if (more) "\"fixture-cursor-2\"" else "null"
    return """{"meta":{"next":$next,"prev":null},"data":[$posts]}"""
  }

  fun start() {
    val cache = Files.createTempDirectory("grid-screen-fixture").toFile()
    val platform = module {
      single<TokenStore> { InMemoryTokenStore(tokens) }
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
          val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
          if (request.url.host == "sankakuapi.com" && request.url.encodedPath == "/v2/posts/keyset") {
            if (request.url.parameters["next"] == null) {
              respond(page(1..6, more = secondPage != SecondPage.None), HttpStatusCode.OK, json)
            } else {
              if (secondPage == SecondPage.Hold) held.await()
              if (secondPage == SecondPage.Fail) {
                respondError(HttpStatusCode.InternalServerError)
              } else if (secondPage == SecondPage.SearchTimeout) {
                respond("""{"success":false,"code":"snackbar__account_search-query-timeout"}""", HttpStatusCode.RequestTimeout, json)
              } else {
                respond(page(7..8, more = false), HttpStatusCode.OK, json)
              }
            }
          } else {
            respondError(HttpStatusCode.NotFound)
          }
        }
      }
      single<ImageLoader> {
        ImageLoader.Builder(PlatformContext.INSTANCE).apply {
          if (images) {
            components {
              add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                  val n = (chain.request.data as? PostImage)?.postId?.removePrefix("fixture-")?.toIntOrNull()
                  val seed = n?.let { pictures[it] }
                  val bitmap = if (seed == null) {
                    Bitmap().apply { allocN32Pixels(30, 40); erase(0xFF2E7D32.toInt()) }
                  } else {
                    picture(seed)
                  }
                  return SuccessResult(bitmap.asImage(), chain.request, DataSource.MEMORY)
                }
              })
            }
          }
        }.build()
      }
    }
    startKoin { modules(sharedModule, platform, shellModule, authModule, gridModule, postModule, booksModule, fixtures) }
    SingletonImageLoader.setUnsafe(GlobalContext.get().get<ImageLoader>())
    collecting = CoroutineScope(Dispatchers.Unconfined).launch {
      GlobalContext.get().get<Messages>().messages.collect { messages += it.text }
    }
  }

  private fun picture(seed: Int): Bitmap {
    val random = Random(seed)
    val bitmap = Bitmap().apply { allocN32Pixels(90, 120) }
    val canvas = Canvas(bitmap)
    canvas.clear(0xFF101010.toInt())
    repeat(8) {
      val x = random.nextInt(70).toFloat()
      val y = random.nextInt(100).toFloat()
      val paint = Paint().apply { color = 0xFF000000.toInt() or random.nextInt(0xFFFFFF) }
      canvas.drawRect(Rect.makeXYWH(x, y, 10f + random.nextInt(40), 10f + random.nextInt(50)), paint)
    }
    return bitmap
  }

  fun stop() {
    collecting?.cancel()
    SingletonImageLoader.reset()
    stopKoin()
  }

  val tabs: TabsController get() = GlobalContext.get().get()

  fun ComposeUiTest.showGrid() {
    tabs.open(grid, background = false)
    tabs.tabs.value.filter { it.top != grid }.forEach { tabs.close(it.id) }
    val navigator = TabsNavigator(tabs)
    setContent {
      SankakuTheme {
        val grid = @Composable {
          val activeId by tabs.active.collectAsState()
          val active = tabs.tabs.collectAsState().value.first { it.id == activeId }
          ActiveTab(active, tabs, navigator, rememberSaveableStateHolder()) { key ->
            NavEntry<NavKey>(key) { if (key is Route.Grid) GridScreen(key) }
          }
        }
        val fake = player
        val tips = GlobalContext.get().get<Tips>()
        Box(Modifier.fillMaxSize().tipGestures(tips)) {
          if (fake == null) grid() else CompositionLocalProvider(LocalVideoPlayback provides { fake }) { grid() }
          TipLayer(tips)
        }
      }
    }
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Fixture 1", substring = true).fetchSemanticsNodes().isNotEmpty() }
    waitForIdle()
  }

  @OptIn(InternalComposeUiApi::class)
  fun ComposeUiTest.press(key: Key, downs: Int = 1, shift: Boolean = false, alt: Boolean = false) {
    val scene = (this as SkikoComposeUiTest).scene
    repeat(downs) {
      runOnIdle { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown, isAltPressed = alt, isShiftPressed = shift)) }
    }
    runOnIdle { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp, isAltPressed = alt, isShiftPressed = shift)) }
    waitForIdle()
  }

  fun ComposeUiTest.cursorPost(): Int? =
    onAllNodesWithContentDescription("Fixture", substring = true)
      .fetchSemanticsNodes()
      .firstOrNull { it.config.getOrNull(SemanticsProperties.Selected) == true }
      ?.config?.get(SemanticsProperties.ContentDescription)?.first()
      ?.let { Regex("Fixture (\\d+)").find(it)?.groupValues?.get(1)?.toInt() }

  fun ComposeUiTest.cursorTo(n: Int) {
    repeat(12) {
      if (cursorPost() == n) return
      press(Key.D)
    }
    check(cursorPost() == n) { "the cursor didn't reach post $n" }
  }

  fun ComposeUiTest.viewerOpen(): Boolean = onAllNodesWithText("of 6", substring = true).fetchSemanticsNodes().isNotEmpty()

  fun ComposeUiTest.viewerPost(): Int = (1..8).single { n -> onAllNodesWithText("Fixture $n").fetchSemanticsNodes().isNotEmpty() }

  fun queue(): List<String> =
    tabs.tabs.value.map { (it.top as? Route.Post)?.id ?: (it.top as Route.Grid).tags.joinToString(" ") }
}

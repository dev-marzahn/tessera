package dev.marzahn.sankaku.demo

import androidx.paging.testing.asSnapshot
import dev.marzahn.sankaku.APP_DATA_DIR
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.SEEN_FILE
import dev.marzahn.sankaku.api.API_HOST
import dev.marzahn.sankaku.api.IMAGE_HOSTS
import dev.marzahn.sankaku.api.PostsApi
import dev.marzahn.sankaku.api.TagsApi
import dev.marzahn.sankaku.api.UsersApi
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.SignInResult
import dev.marzahn.sankaku.auth.TokenStore
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PostTitle
import dev.marzahn.sankaku.data.TextFile
import dev.marzahn.sankaku.data.TextFiles
import dev.marzahn.sankaku.desktopPlatformModule
import dev.marzahn.sankaku.di.AUTH_CLIENT
import dev.marzahn.sankaku.di.GITHUB_CLIENT
import dev.marzahn.sankaku.di.MAIN_CLIENT
import dev.marzahn.sankaku.di.SEEN_FILE_NAME
import dev.marzahn.sankaku.di.appModules
import dev.marzahn.sankaku.features.grid.GridViewModel
import dev.marzahn.sankaku.features.post.SaveFolder
import dev.marzahn.sankaku.log.CrashReports
import dev.marzahn.sankaku.tabs.TabsController
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class DemoLaunchTest {
  private var keyringOpened = false

  private fun launch(): Koin {
    val platform = desktopPlatformModule(CrashReports(InMemoryTextFile())) {
      keyringOpened = true
      null
    }
    return startKoin { modules(appModules(platform, desktopDemoModules())) }.koin
  }

  private fun Koin.stop() {
    get<CoroutineScope>().cancel()
    stopKoin()
  }

  @AfterTest
  fun stopAnyLaunch() {
    KoinPlatform.getKoinOrNull()?.stop()
  }

  @Test
  fun everyRequestIsAnsweredInsideTheApp() = runBlocking {
    val koin = launch()
    val engine = koin.get<HttpClientEngine>()
    assertIs<MockEngine>(engine)
    for (client in listOf(MAIN_CLIENT, AUTH_CLIENT, GITHUB_CLIENT)) assertSame(engine, koin.get<HttpClient>(client).engine)

    assertEquals(SignInResult.Ok, koin.get<AuthSession>().signIn("demo", "demo"))
    assertEquals("Demo", koin.get<UsersApi>().me().name)
    koin.get<PostsApi>().favourite("demo-animated-gif")
    assertTrue(koin.get<PostsApi>().keyset(listOf("animated")).data.single().isFavorited)
    assertTrue(koin.get<TagsApi>().autocomplete("land").any { it.tagName == "landscape" })
    assertRefused { koin.get<HttpClient>(GITHUB_CLIENT).get("https://api.github.com/repos/x/y/releases/latest") }
    assertRefused { koin.get<HttpClient>(MAIN_CLIENT).get("https://example.com/") }

    val answered = engine.requestHistory.map { it.url.host }.toSet()
    assertEquals(setOf(API_HOST, "login.sankakucomplex.com"), answered - IMAGE_HOSTS)
  }

  @Test
  fun nothingIsStoredAndNoKeyringIsOpened() = runBlocking {
    val koin = launch()
    koin.get<AuthSession>().signIn("demo", "demo")
    koin.get<PostsApi>().favourite("demo-portrait-image")
    koin.get<SaveFolder>().save("demo-portrait-image.jpg", "image/jpeg") { sink ->
      val bytes = Buffer().apply { writeString("saved") }
      sink.write(bytes, bytes.size)
    }
    koin.get<TextFile>(SEEN_FILE).write("""{"seen":["demo-portrait-image"]}""")

    assertEquals("""{"seen":["demo-portrait-image"]}""", koin.get<TextFiles>().named(SEEN_FILE_NAME).read(), "the queue export reads the demo's files")
    assertTrue(APP_DATA_DIR.listFiles().isNullOrEmpty(), "the data folder holds ${APP_DATA_DIR.list()?.toList()}")
    assertFalse(keyringOpened)
  }

  @Test
  fun everyLaunchStartsOnTheSameGrid() = runBlocking {
    repeat(2) {
      val koin = launch()
      val start = koin.get<TabsController>().tabs.value.single().backStack.single()
      assertIs<Route.Grid>(start)
      val grid = koin.get<GridViewModel> { parametersOf(start.tags, null) }
      assertEquals(GRID_ORDER, grid.posts.asSnapshot().map { it.id })
      koin.stop()
    }
  }

  @Test
  fun aSecondLaunchForgetsTheFirst() = runBlocking {
    launch().apply {
      get<AuthSession>().signIn("demo", "demo")
      get<PostsApi>().favourite(GRID_ORDER.first())
      get<TagsApi>().follow("demo-tag-video", "video")
      stop()
    }
    val koin = launch()
    assertNull(koin.get<TokenStore>().load())
    val posts = koin.get<PostsApi>().keyset(emptyList()).data
    assertTrue(posts.none { it.isFavorited })
    assertTrue(posts.flatMap { it.tags }.none { it.isFollowing })
  }

  @Test
  fun eachMediaTypeHasItsPlaceAndVideosPlayFromTheScratchFolder() = runBlocking {
    val koin = launch()
    val posts = koin.get<PostsApi>().keyset(emptyList()).data
    val kinds = posts.take(6).map { it.fileType to (it.fileWidth!! > it.fileHeight!!) }
    val expected = listOf(
      "image/jpeg" to false,
      "image/jpeg" to true,
      "image/gif" to false,
      "video/mp4" to false,
      "video/mp4" to true,
      "image/jpeg" to false,
    )
    assertEquals(expected, kinds)
    assertEquals(44, posts[5].tags.size)
    assertEquals(posts.map { it.id.removePrefix("demo-").replace('-', ' ') }, posts.map { PostTitle.of(it) })
    val video = posts[4]
    assertTrue(video.sampleUrl!!.startsWith("file:"), "the player opens ${video.sampleUrl}")
    val saved = koin.get<HttpClient>(MAIN_CLIENT).get(video.fileUrl!!).readRawBytes()
    assertTrue(saved.size > 100_000, "saving gets the whole video")
  }

  private suspend fun assertRefused(request: suspend () -> Unit) {
    try {
      request()
    } catch (e: Exception) {
      if (generateSequence<Throwable>(e) { it.cause }.any { it is DemoNoNetworkException }) return
      throw e
    }
    fail("the request was answered")
  }

  private companion object {
    val GRID_ORDER = listOf(
      "demo-portrait-image", "demo-landscape-image", "demo-animated-gif", "demo-portrait-video", "demo-landscape-video",
      "demo-many-tags", "demo-filler-1", "demo-filler-2", "demo-filler-3", "demo-filler-4", "demo-filler-5",
      "demo-filler-6",
    )
  }
}

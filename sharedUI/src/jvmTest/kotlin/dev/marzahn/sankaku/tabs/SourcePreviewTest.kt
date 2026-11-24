package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.AuthApi
import dev.marzahn.sankaku.api.PostsApi
import dev.marzahn.sankaku.api.RateLimiter
import dev.marzahn.sankaku.api.createAuthClient
import dev.marzahn.sankaku.api.createMainClient
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.TagDto
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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourcePreviewTest {
  private val artist = Route.Grid(listOf("artist_x"))

  private val pageRequests: MutableList<String> = CopyOnWriteArrayList()
  private var pageHasMore = true
  private val engine = MockEngine { request ->
    if (request.url.encodedPath != "/v2/posts/keyset") error("unexpected request")
    val tags = request.url.parameters["tags"].orEmpty()
    pageRequests += tags
    val search = tags.split(' ').first { !it.startsWith("rating:") }
    val posts = (1..30).joinToString(",") { n ->
      """{"id":"$search-$n","md5":"m$search-$n","rating":"s","status":"active","tags":[""" +
        """{"name":"$search","tagName":"$search","type":1,"post_count":240}]}"""
    }
    val next = if (pageHasMore) "\"c2\"" else "null"
    respond(
      """{"meta":{"next":$next,"prev":null},"data":[$posts]}""",
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

  init {
    runBlocking { session.restore() }
  }

  @AfterTest
  fun tearDown() {
    scope.cancel()
  }

  private fun post(n: Int, vararg tags: Pair<String, Int?>) = PostDto(
    id = "p$n",
    md5 = "m$n",
    rating = if (n % 2 == 0) "s" else "e",
    status = "active",
    tags = tags.map { (name, count) -> TagDto(name = name, tagName = name, postCount = count) },
  )

  private fun page(count: Int, vararg tags: Pair<String, Int?>) = (1..count).map { post(it, *tags) }

  private fun sourceJson(id: String, tag: String) =
    """{"id":"$id","routes":[{"type":"dev.marzahn.sankaku.Route.Grid","tags":["$tag"]}],"kind":"source","touched":1000,"addedIn":"s1"}"""

  private fun savedQueue(sources: Int) =
    """{"version":2,"session":"s1","activeIndex":0,"tabs":[""" + (1..sources).joinToString(",") { sourceJson("t$it", "artist_$it") } + "]}"

  @Test
  fun thePreviewAndSizeAreSavedAndLoadedWithTheItem() {
    val file = InMemoryTextFile(savedQueue(1).replace("artist_1", "artist_x"))
    val tabs = TabsController(FileTabStateStore(file))
    val tab = tabs.activeTab
    val preview = SourcePreview.of(page(30, "artist_x" to 240), listOf("artist_x"), more = true)!!
    tab.reportPreview(artist, preview)
    tab.reportPlace(artist, GridPlace("p38", "c2", 2, index = 37))
    FileTabStateStore(file).save(tabs.saved())

    val restored = TabsController(FileTabStateStore(file)).activeTab

    assertEquals(preview, restored.preview)
    assertEquals(9, restored.preview!!.thumbs.size)
    assertEquals(listOf("m1", "m2"), restored.preview!!.thumbs.take(2).map { it.md5 })
    assertEquals(listOf("e", "s"), restored.preview!!.thumbs.take(2).map { it.rating })
    assertEquals(SourceProgress(position = 38, size = SourceSize.About(240)), sourceProgress(restored))
    val text = file.read()!!
    assertTrue("\"about\":240" in text)
    assertFalse("\"ended\"" in text)
    assertFalse("\"g\"" in text)
  }

  @Test
  fun aFileWithoutAPreviewReadsAndWritesBackWithout() {
    val file = InMemoryTextFile(savedQueue(1))
    val tabs = TabsController(FileTabStateStore(file))

    assertNull(tabs.activeTab.preview)
    assertEquals(SourceProgress(position = null, size = null), sourceProgress(tabs.activeTab))
    FileTabStateStore(file).save(tabs.saved())
    assertFalse("preview" in file.read()!!)
  }

  @Test
  fun aOneTagSearchIsAboutItsTagsCountFromAnyPostThatCarriesIt() {
    val posts = page(3, "copyright_y" to 9000) + post(4, "artist_x" to 240)

    assertEquals(SourceSize.About(240), SourcePreview.of(posts, listOf("artist_x"), more = true)!!.size)
    assertEquals(SourceSize.About(240), SourcePreview.of(posts, listOf("order:popularity", "artist_x"), more = true)!!.size)
    assertEquals(SourceSize.Counted(3, more = true), SourcePreview.of(page(3, "copyright_y" to 9000), listOf("artist_x"), more = true)!!.size)
  }

  @Test
  fun aMultiTagSearchCountsWhatsLoaded() {
    val posts = page(30, "artist_x" to 240, "blue_hair" to 90_000)

    assertEquals(SourceSize.Counted(30, more = true), SourcePreview.of(posts, listOf("artist_x", "blue_hair"), more = true)!!.size)
    assertEquals(SourceSize.Counted(30, more = false), SourcePreview.of(posts, listOf("artist_x", "blue_hair"), more = false)!!.size)
    assertEquals(SourceSize.Counted(30, more = true), SourcePreview.of(posts, listOf("-artist_x"), more = true)!!.size)
    assertEquals(SourceSize.Counted(30, more = true), SourcePreview.of(posts, listOf("fav:someone"), more = true)!!.size)
    assertEquals(SourceSize.Counted(30, more = true), SourcePreview.of(posts, listOf("rating:s", "artist_x"), more = true)!!.size)
  }

  @Test
  fun aFeedBrowsePopularAndTopHaveNone() {
    val none = listOf(GridPresets.following, GridPresets.forYou, emptyList(), GridPresets.popular, GridPresets.top)
    for (tags in none) {
      val tab = TabState(TabId("x"), listOf(Route.Grid(tags)))
      assertFalse(hasPreview(tab.top), "$tags")
      assertNull(sourceProgress(tab), "$tags")
      assertFalse(isNewSource(tab), "$tags")
      runBlocking { previews.load(tab) }
      assertNull(tab.preview, "$tags")
    }
    assertTrue(hasPreview(artist))
    assertTrue(hasPreview(Route.Grid(listOf("order:popularity", "artist_x"))))
    assertFalse(hasPreview(Route.Books(listOf("artist_x"))))
    assertEquals(emptyList(), pageRequests)
  }

  @Test
  fun aSourceIsNewUntilItsShownAndStaysNewWhenMoved() {
    val tabs = TabsController(FileTabStateStore(InMemoryTextFile(savedQueue(3))))
    val second = tabs.tabs.value[1]
    assertTrue(tabs.tabs.value.all(::isNewSource))

    tabs.move(1, 2)
    assertTrue(isNewSource(second))

    second.markTouched(second.top as Route.Grid)
    assertFalse(isNewSource(second))
    val third = tabs.tabs.value[1]
    third.reportPlace(third.top as Route.Grid, GridPlace("p5"))
    assertFalse(isNewSource(third))
  }

  @Test
  fun aQueueOfThreeHundredSourcesAsksForNoPreviewAtLaunch() = runBlocking {
    val tabs = TabsController(FileTabStateStore(InMemoryTextFile(savedQueue(300))), scope)
    delay(200)

    assertEquals(300, tabs.tabs.value.size)
    assertEquals(emptyList(), pageRequests)
    assertTrue(tabs.tabs.value.all { it.preview == null })
  }

  @Test
  fun aSourceAskedAboutPaysOneRequestOnce() = runBlocking {
    val tabs = TabsController(FileTabStateStore(InMemoryTextFile(savedQueue(300))), scope)
    val asked = tabs.tabs.value[41]

    listOf(async { previews.load(asked) }, async { previews.load(asked) }).awaitAll()
    previews.load(asked)

    assertEquals(listOf("rating:s artist_42"), pageRequests)
    val preview = assertNotNull(asked.preview)
    assertEquals(SourcePreview.SIZE, preview.thumbs.size)
    assertTrue(preview.g)
    assertEquals(SourceSize.Counted(30, more = true), preview.size)
    assertTrue(tabs.tabs.value.filter { it != asked }.all { it.preview == null })
  }

  @Test
  fun aPreviewThatComesAfterTheTabMovedOnIsNotKept() {
    val tab = TabState(TabId("x"), listOf(artist))
    val other = Route.Grid(listOf("artist_y"))
    tab.backStack.add(other)
    val forOther = SourcePreview.of(page(3), listOf("artist_y"), more = false)!!
    tab.reportPreview(other, forOther)

    tab.reportPreview(artist, SourcePreview.of(page(30), listOf("artist_x"), more = true)!!)

    assertEquals(forOther, tab.preview)
  }

  @Test
  fun aPreviewFromTheOtherSearchIsAskedForAgain() {
    val tab = TabState(TabId("x"), listOf(artist))
    tab.reportPreview(artist, SourcePreview.of(page(30), listOf("artist_x"), more = false)!!)
    assertFalse(tab.preview!!.g)

    pageHasMore = false
    runBlocking { previews.load(tab) }

    assertEquals(listOf("rating:s artist_x"), pageRequests)
    assertTrue(tab.preview!!.g)
    assertEquals(SourceSize.Counted(30, more = false), tab.preview!!.size)
  }
}

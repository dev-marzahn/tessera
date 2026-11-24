package dev.marzahn.sankaku.features.post

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import coil3.ImageLoader
import coil3.PlatformContext
import dev.marzahn.sankaku.ACCESSIBILITY_FILE
import dev.marzahn.sankaku.CACHE_DIR
import dev.marzahn.sankaku.CACHE_FILE
import dev.marzahn.sankaku.DebugFlags
import dev.marzahn.sankaku.FIRST_LAUNCH_FILE
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
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.TagDto
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
import dev.marzahn.sankaku.tabs.FileTabStateStore
import dev.marzahn.sankaku.tabs.TabStateStore
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.components.LATER
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import okio.Path.Companion.toOkioPath
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OpenTagForLaterTest {
  private val grid = Route.Grid(listOf("scenery"))
  private val sky = Route.Grid(listOf("order:popularity", "sky"))
  private val tabs = TabsController().apply {
    open(grid, background = false)
    activeTab.markTouched(grid)
  }
  private val gridTab = tabs.activeTab
  private val shown = mutableListOf<Message>()

  private val post = PostDto(
    id = "fixture-post",
    md5 = "fixture-md5",
    rating = "s",
    status = "active",
    fileType = "image/png",
    tags = listOf(TagDto(name = "Scenery", tagName = "scenery"), TagDto(name = "Sky", tagName = "sky")),
  )

  @BeforeTest
  fun startFixtureKoin() {
    val cache = Files.createTempDirectory("open-tag-for-later").toFile()
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
      single<HttpClientEngine> { MockEngine { respondError(HttpStatusCode.NotFound) } }
      single<ImageLoader> { ImageLoader.Builder(PlatformContext.INSTANCE).build() }
    }
    startKoin { modules(sharedModule, platform, shellModule, authModule, gridModule, postModule, booksModule, fixtures) }
  }

  @AfterTest
  fun stop() = stopKoin()

  private fun ComposeUiTest.showPost(touch: Boolean = true) {
    setContent {
      SankakuTheme {
        val messages = koinInject<Messages>()
        LaunchedEffect(messages) { messages.messages.collect { shown += it } }
        PostDetails(post, searchTags = grid.tags, navigator = TabsNavigator(tabs, messages), tabs = tabs, touch = touch)
      }
    }
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Couldn't load recommendations").fetchSemanticsNodes().isNotEmpty() }
    waitForIdle()
  }

  @Test
  fun oneTapOnTheClockOpensTheTagForLaterAndSaysWhereItWent() = runDesktopComposeUiTest {
    showPost()
    val before = tabs.tabs.value.size
    onNodeWithContentDescription("Open Sky for later").performClick()
    waitForIdle()

    assertEquals(before + 1, tabs.tabs.value.size, "one item added")
    assertEquals(gridTab.id, tabs.activeTab.id, "the post stays shown")
    val opened = tabs.tabs.value.single { it.top == sky }
    assertEquals(grid.tags, opened.foundOn?.search, "found on the grid")
    val line = shown.single()
    val position = tabs.tabs.value.size
    assertEquals("Sky · later, item $position · from Scenery", line.text)
    assertEquals(OPEN_NOW, line.actionLabel)
    onNodeWithContentDescription("Go to Sky, item $position").assertExists()

    line.onAction()
    assertEquals(opened.id, tabs.activeTab.id, "Open now shows it")
    assertEquals(before + 1, tabs.tabs.value.size, "and adds nothing")
  }

  @Test
  fun aTapOnTheNameStillOpensItNow() = runDesktopComposeUiTest {
    showPost()
    onNodeWithText("Sky").performTouchInput { click(percentOffset(0.15f, 0.5f)) }
    waitForIdle()

    assertEquals(sky, tabs.activeTab.top)
    assertTrue(shown.isEmpty(), "no line")
  }

  @Test
  fun aHoldGivesTheSheetWithItsRowsLaterSpeltOut() = runDesktopComposeUiTest {
    showPost()
    onNodeWithText("Sky").performTouchInput { longClick(percentOffset(0.15f, 0.5f)) }
    waitForIdle()

    assertTrue(onAllNodesWithText(LATER).fetchSemanticsNodes().size >= 2, "a Later on each search row")
    onNodeWithContentDescription(forLaterLabel("Search only this tag")).performClick()
    waitForIdle()
    assertEquals(gridTab.id, tabs.activeTab.id)
    assertTrue(tabs.tabs.value.any { it.top == sky }, "opened for later")
  }

  @Test
  fun aTagAlreadyInTheQueueShowsItsMarkAndATapGoesThere() = runDesktopComposeUiTest {
    tabs.open(sky, background = true)
    val queued = tabs.tabs.value.single { it.top == sky }
    showPost()

    onNodeWithContentDescription("Open Sky for later").assertDoesNotExist()
    onNodeWithContentDescription("Go to Sky, item ${tabs.tabs.value.size}").performClick()
    waitForIdle()
    assertEquals(queued.id, tabs.activeTab.id)
  }

  @Test
  fun anIncludedTagWhoseSearchIsQueuedHasNoSegment() = runDesktopComposeUiTest {
    showPost()
    onNodeWithContentDescription("Open Scenery for later").assertDoesNotExist()
    onNodeWithContentDescription("Go to Scenery", substring = true).assertDoesNotExist()
  }

  @Test
  fun desktopHasNoClock() = runDesktopComposeUiTest {
    showPost(touch = false)
    onNodeWithContentDescription("Open Sky for later").assertDoesNotExist()
  }

  @Test
  fun theLineLeavesOutAnOriginItHasNot() {
    assertEquals("Pink flower · later, item 31 · from Scenery", openedForLaterText("Pink flower", 31, "Scenery"))
    assertEquals("Pink flower · later, item 31", openedForLaterText("Pink flower", 31, null))
  }
}

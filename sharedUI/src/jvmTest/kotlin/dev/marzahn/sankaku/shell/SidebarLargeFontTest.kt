package dev.marzahn.sankaku.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import coil3.ImageLoader
import coil3.PlatformContext
import dev.marzahn.sankaku.ACCESSIBILITY_FILE
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
import dev.marzahn.sankaku.SEEN_FILE
import dev.marzahn.sankaku.TIPS_FILE
import dev.marzahn.sankaku.UPDATES_FILE
import dev.marzahn.sankaku.auth.InMemoryTokenStore
import dev.marzahn.sankaku.auth.TokenStorageStatus
import dev.marzahn.sankaku.auth.TokenStore
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.TextFile
import dev.marzahn.sankaku.di.authModule
import dev.marzahn.sankaku.di.sharedModule
import dev.marzahn.sankaku.di.shellModule
import dev.marzahn.sankaku.tabs.BOOKS_TITLE
import dev.marzahn.sankaku.tabs.FileTabStateStore
import dev.marzahn.sankaku.tabs.SETTINGS_TITLE
import dev.marzahn.sankaku.tabs.TabStateStore
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import okio.Path.Companion.toOkioPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SidebarLargeFontTest {
  @BeforeTest
  fun startFixtureKoin() {
    val cache = Files.createTempDirectory("sidebar-large-font").toFile()
    val platform = module {
      single<TokenStore> { InMemoryTokenStore() }
      single { TokenStorageStatus.InMemory }
      single { DebugFlags() }
      single(CACHE_DIR) { cache.toOkioPath() }
      listOf(KNOWN_TAGS_FILE, PLAYER_FILE, REOPEN_HINT_FILE, PRIVACY_FILE, ACCESSIBILITY_FILE, QUEUE_FILE, GRID_FILE, CACHE_FILE, TIPS_FILE, SEEN_FILE, UPDATES_FILE, FIRST_LAUNCH_FILE, READING_FILE, OUTBOX_FILE)
        .forEach { name -> single<TextFile>(name) { InMemoryTextFile() } }
      single<TabStateStore> { FileTabStateStore(InMemoryTextFile()) }
      single<PlatformContext> { PlatformContext.INSTANCE }
    }
    val fixtures = module {
      single<HttpClientEngine> { MockEngine { respondError(HttpStatusCode.NotFound) } }
      single<ImageLoader> { ImageLoader.Builder(PlatformContext.INSTANCE).build() }
    }
    startKoin { modules(sharedModule, platform, shellModule, authModule, fixtures) }
  }

  @AfterTest
  fun stop() = stopKoin()

  @Test
  fun theEntriesScrollAndSettingsAndSignInStayPinned() = runDesktopComposeUiTest(width = 360, height = 420) {
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
        SankakuTheme { Sidebar(open = true, onClose = {}) }
      }
    }
    waitForIdle()

    val settings = onNodeWithText(SETTINGS_TITLE).getUnclippedBoundsInRoot()
    assertTrue(settings.height >= 40.dp, "$settings")
    val signIn = onNodeWithText("Sign in", substring = true).getUnclippedBoundsInRoot()
    listOf(settings, signIn).forEach { assertTrue(it.top >= 0.dp && it.bottom <= 420.dp, "off screen: $it") }

    val books = onNodeWithText(BOOKS_TITLE)
    books.performScrollTo()
    waitForIdle()
    val shown = books.getUnclippedBoundsInRoot()
    assertTrue(shown.top >= 0.dp && shown.bottom <= settings.top, "books $shown, settings $settings")
  }

  @Test
  fun theMemoryReadoutShowsUnderSignIn() = runDesktopComposeUiTest(width = 360, height = 720) {
    setContent { SankakuTheme { Sidebar(open = true, onClose = {}, readMemory = { 300L * 1024 * 1024 }) } }
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Memory usage 300.0 MB").fetchSemanticsNodes().isNotEmpty() }

    val memory = onNodeWithText("Memory usage 300.0 MB").getUnclippedBoundsInRoot()
    val signIn = onNodeWithText("Sign in", substring = true).getUnclippedBoundsInRoot()
    assertTrue(memory.top >= signIn.bottom && memory.bottom <= 720.dp, "memory $memory, sign in $signIn")
  }
}

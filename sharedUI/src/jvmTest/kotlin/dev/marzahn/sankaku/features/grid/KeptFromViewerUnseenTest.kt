package dev.marzahn.sankaku.features.grid

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.features.post.keepingFrom
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.Viewing
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.tabs.keptLeft
import org.koin.core.context.GlobalContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class KeptFromViewerUnseenTest {
  private val fixture = GridScreenFixture(images = true)

  @BeforeTest
  fun start() = fixture.start()

  @AfterTest
  fun stop() = fixture.stop()

  @Test
  fun aPostKeptWithEInTheViewerIsUnseen() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      cursorTo(1)
      press(Key.Spacebar)
      press(Key.D)
      assertEquals(2, viewerPost())
      val seen = GlobalContext.get().get<SeenStore>()
      assertTrue(seen.isSeen("fixture-1"), "the viewer never fully showed a post")

      press(Key.E)
      assertEquals("Kept in tab 2", messages.last())
      press(Key.Escape)
      waitForIdle()
      assertFalse(viewerOpen(), "Esc didn't close the viewer")

      val kept = tabs.tabs.value.single { (it.top as? Route.Post)?.id == "fixture-2" }
      assertFalse(kept.seenSinceKept(seen), "a post kept from the viewer is born seen")
      assertEquals(emptyList(), tabs.viewedKeptPosts(seen))
      assertEquals(1, keptLeft(tabs.tabs.value) { it.seenSinceKept(seen) })

      press(Key.Spacebar)
      assertEquals(2, viewerPost())
      press(Key.E)
      assertEquals("Already in tab 2", messages.last())
    }
  }
}

class PhoneKeepUnseenTest {
  private var now = Instant.parse("2026-11-13T12:00:00Z")
  private val clock = object : Clock {
    override fun now() = now.also { now += 1.seconds }
  }
  private val seen = SeenStore(InMemoryTextFile(), clock)
  private val tabs = TabsController(clock = clock)
  private val navigator = TabsNavigator(tabs)
  private val grid = Route.Grid(listOf("sky"))
  private val post = PostDto(id = "p1", md5 = "aa11", rating = "s", status = "active")

  @Test
  fun keepForLaterFromThePhoneViewerIsUnseen() {
    tabs.open(grid, background = false)
    tabs.tabs.value.filter { it.top != grid }.forEach { tabs.close(it.id) }
    val viewing = Viewing(seen)
    val keep = viewing.keepingFrom { shown: PostDto -> keepPost(shown, grid.tags, navigator, tabs, seen) }
    viewing.current(post.id, post.md5)
    viewing.loaded(post.id)

    keep(post)
    viewing.leave()

    val kept = tabs.tabs.value.single { (it.top as? Route.Post)?.id == "p1" }
    assertFalse(kept.seenSinceKept(seen))
    assertEquals(emptyList(), tabs.viewedKeptPosts(seen))
    assertEquals(1, keptLeft(tabs.tabs.value) { it.seenSinceKept(seen) })
  }
}

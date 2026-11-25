package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipeLeft
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.features.post.FakeVideoPlayers
import dev.marzahn.sankaku.features.post.LocalVideoPlayback
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class VideoPlayerReleaseTest {
  private val players = FakeVideoPlayers()

  private val fixture = GridScreenFixture(types = mapOf(2 to "video/mp4", 3 to "video/mp4", 4 to "video/mp4"), playback = players.playback)

  @BeforeTest
  fun start() = fixture.start()

  @AfterTest
  fun stop() = fixture.stop()

  @Test
  fun steppingThroughVideosKeepsOnePlayerAndClosingTheViewerReleasesIt() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      cursorTo(2)
      press(Key.Spacebar)
      for (video in 2..4) {
        assertEquals(video, viewerPost())
        assertEquals(1, players.live.size, "players alive on video $video")
        press(Key.D)
      }
      assertEquals(5, viewerPost())
      assertEquals(emptyList(), players.live, "a player outlived its video")

      press(Key.A)
      assertEquals(1, players.live.size)
      press(Key.Escape)
      assertFalse(viewerOpen())
      assertEquals(emptyList(), players.live, "a player outlived the closed viewer")
    }
  }

  @Test
  fun closingTheQueueItemShowingAVideoReleasesItsPlayer() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      runOnIdle { tabs.open(Route.Grid(listOf("other_tag")), background = true) }
      cursorTo(2)
      press(Key.Spacebar)
      assertEquals(1, players.live.size)

      runOnIdle { tabs.close(tabs.active.value) }
      waitForIdle()
      assertEquals(emptyList(), players.live, "the closed queue item's player is still alive")
    }
  }

  @Test
  fun swipingThePhoneViewerKeepsOnlyTheSettledPagesPlayer() = runDesktopComposeUiTest(width = 400, height = 800) {
    val videos = (1..3).map { n ->
      PostDto(id = "v$n", md5 = "md5-$n", rating = "s", status = "active", fileType = "video/mp4", sampleUrl = "https://v.sankakucomplex.com/data/v$n.mp4")
    }
    var shown by mutableStateOf(true)
    setContent {
      SankakuTheme {
        CompositionLocalProvider(LocalVideoPlayback provides players.playback) {
          SharedTransitionLayout {
            AnimatedVisibility(shown) {
              PhoneViewer(
                index = 0,
                postAt = { videos.getOrNull(it) },
                loadedCount = videos.size,
                hasMore = false,
                sharedId = null,
                visibilityScope = this,
                onShow = {},
                onClose = {},
              )
            }
          }
        }
      }
    }
    waitUntil(timeoutMillis = 5_000) { players.live.isNotEmpty() }
    assertEquals(1, players.live.size, "the neighbouring video already holds a player")
    val first = players.live.single()

    onRoot().performTouchInput { swipeLeft() }
    waitForIdle()
    assertTrue(first.released, "the swiped-past video still holds its player")
    assertEquals(1, players.live.size, "players alive after one swipe")

    shown = false
    waitForIdle()
    assertEquals(emptyList(), players.live, "a player outlived the closed viewer")
  }
}

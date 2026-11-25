package dev.marzahn.sankaku.features.post

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class TapSeekTest {
  private fun playingVideo() = FakeVideoPlayback(playing = true, position = 0.5f, hasMedia = true, firstFrameShown = true, duration = 100.0)

  private val player = playingVideo()
  private val keys = VideoKeys(PlayerPreferences(InMemoryTextFile())).also { it.register(player) }
  private var taps = 0

  private val seeked get() = (player.position * player.duration - 50.0).toInt()

  private var shownKeys by mutableStateOf<VideoKeys?>(keys)

  private fun ComposeUiTest.start() {
    setContent {
      Box(Modifier.size(400.dp, 300.dp).testTag("picture").videoTaps({ shownKeys }, key = Unit, onTap = { taps++ }, onLongPress = null))
    }
  }

  @Test
  fun aDoubleTapBeforeThePageSettlesDoesNothing() = runComposeUiTest {
    shownKeys = null
    start()
    picture().performTouchInput { doubleClick() }
    mainClock.advanceTimeBy(1_000)
    waitForIdle()

    assertEquals(0, seeked)
    assertEquals(0, taps)
  }

  @Test
  fun aDoubleTapOnceThePageHasSettledSeeks() = runComposeUiTest {
    shownKeys = null
    start()
    runOnIdle { shownKeys = keys }
    picture().performTouchInput { doubleClick(centerRight - Offset(10f, 0f)) }
    waitForIdle()

    assertEquals(5, seeked)
    assertEquals(0, taps)
  }

  private fun ComposeUiTest.picture() = onNodeWithTag("picture")

  private fun ComposeUiTest.tap(at: (androidx.compose.ui.test.TouchInjectionScope) -> Offset) {
    picture().performTouchInput { click(at(this)) }
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
  }

  @Test
  fun aDoubleTapSeeksAndTouchesNoChrome() = runComposeUiTest {
    start()
    picture().performTouchInput { doubleClick(centerRight - Offset(10f, 0f)) }
    waitForIdle()

    assertEquals(5, seeked)
    assertEquals(0, taps)
    assertNotNull(keys.tapSeek)
  }

  @Test
  fun aTapWhileTheSeekShowsSeeksAgainInsteadOfHidingTheChrome() = runComposeUiTest {
    start()
    picture().performTouchInput { doubleClick(centerRight - Offset(10f, 0f)) }
    waitForIdle()
    tap { it.centerRight - Offset(10f, 0f) }

    assertEquals(10, seeked)
    assertEquals(0, taps)
  }

  @Test
  fun aSecondDoubleTapWhileTheSeekShowsSeeksTwice() = runComposeUiTest {
    start()
    picture().performTouchInput { doubleClick(centerLeft + Offset(10f, 0f)) }
    waitForIdle()
    picture().performTouchInput { doubleClick(centerLeft + Offset(10f, 0f)) }
    waitForIdle()

    assertEquals(-15, seeked)
    assertEquals(0, taps)
  }

  @Test
  fun aTapOnceTheSeekHasFadedTogglesTheChromeAgain() = runComposeUiTest {
    start()
    picture().performTouchInput { doubleClick(centerRight - Offset(10f, 0f)) }
    waitForIdle()
    runOnIdle { keys.tapSeekShown(keys.tapSeek!!) }
    assertNull(keys.tapSeek)

    tap { it.center }

    assertEquals(5, seeked)
    assertEquals(1, taps)
  }

  @Test
  fun aFadedSeekLeavesALaterOnesRippleShowing() {
    keys.seekFromTap(SeekDirection.Forward)
    val first = keys.tapSeek!!
    keys.seekFromTap(SeekDirection.Forward)

    keys.tapSeekShown(first)

    assertNotNull(keys.tapSeek)
  }

  @Test
  fun anotherVideoStartsWithNoSeekShowing() {
    keys.seekFromTap(SeekDirection.Back)

    keys.register(playingVideo())

    assertNull(keys.tapSeek)
  }
}

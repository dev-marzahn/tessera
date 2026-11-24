package dev.marzahn.sankaku.ui.components

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FavouriteBurstsTest {
  private val hearts = MutableSharedFlow<Boolean>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private fun ComposeUiTest.show() {
    setContent { SankakuTheme { FavouriteBursts(hearts) } }
    waitForIdle()
    mainClock.autoAdvance = false
  }

  private fun ComposeUiTest.burst() = onNodeWithTag(FAVOURITE_BURST_TAG, useUnmergedTree = true)

  private fun shows(favourited: Boolean) = SemanticsMatcher.expectValue(BurstFavourited, favourited)

  @Test
  fun aFavouriteShowsTheFilledHeartThenGoes() = runComposeUiTest {
    show()
    burst().assertDoesNotExist()

    hearts.tryEmit(true)
    repeat(2) { mainClock.advanceTimeByFrame() }
    burst().assertExists().assert(shows(true))

    mainClock.advanceTimeBy(3_000)
    burst().assertDoesNotExist()
  }

  @Test
  fun anUnfavouriteShowsTheBrokenHeart() = runComposeUiTest {
    show()

    hearts.tryEmit(false)
    repeat(2) { mainClock.advanceTimeByFrame() }

    burst().assert(shows(false))
  }

  @Test
  fun aSecondToggleReplacesTheHeart() = runComposeUiTest {
    show()

    hearts.tryEmit(true)
    repeat(2) { mainClock.advanceTimeByFrame() }
    hearts.tryEmit(false)
    repeat(2) { mainClock.advanceTimeByFrame() }

    assertEquals(1, onAllNodesWithTag(FAVOURITE_BURST_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    burst().assert(shows(false))
  }

  @Test
  fun withAnimationsOffNothingPlays() = runComposeUiTest(effectContext = AnimationsOff) {
    show()

    hearts.tryEmit(true)
    repeat(4) {
      mainClock.advanceTimeByFrame()
      burst().assertDoesNotExist()
    }
  }

  private object AnimationsOff : MotionDurationScale {
    override val scaleFactor = 0f
  }
}

package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipe
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.tabs.COUNT_PILL
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import org.koin.core.context.GlobalContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BarPlaceTapTest {
  private val fixture = GridScreenFixture()
  private val post = PostDto(id = "p1", md5 = "aa11", rating = "s", status = "active", fileType = "image/png", fileWidth = 300, fileHeight = 400)
  private var shown by mutableStateOf(false)

  @BeforeTest
  fun start() = fixture.start()

  @AfterTest
  fun stop() = fixture.stop()

  private val signIn: SignInController get() = GlobalContext.get().get()

  private fun ComposeUiTest.showViewer() {
    setContent {
      SankakuTheme {
        SharedTransitionLayout {
          AnimatedVisibility(shown, enter = EnterTransition.None, exit = ExitTransition.None) {
            PhoneViewer(
              index = 0,
              postAt = { post },
              loadedCount = 1,
              hasMore = false,
              sharedId = null,
              visibilityScope = this,
              onShow = {},
              onClose = { shown = false },
            )
          }
        }
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    shown = true
  }

  private fun ComposeUiTest.gridBarPlace() = Offset(WIDTH / 2f, HEIGHT - BottomRowLayout.CentreLine.value * density.density)

  private fun ComposeUiTest.voteRow(x: Float) = Offset(x, gridBarPlace().y)

  private fun ComposeUiTest.pillCentreY(): Float = onNodeWithTag(COUNT_PILL).fetchSemanticsNode().boundsInRoot.center.y

  private fun ComposeUiTest.handleTop(): Float = onNodeWithContentDescription("Details").fetchSemanticsNode().boundsInRoot.top

  private fun ComposeUiTest.tap(at: Offset) {
    onRoot().performTouchInput { click(at) }
  }

  private fun ComposeUiTest.openUntilDocked() {
    var last = Float.NaN
    repeat(MAX_OPENING_FRAMES) {
      mainClock.advanceTimeByFrame()
      val y = pillCentreY()
      if (y == last && y < gridBarPlace().y - 1f) return
      last = y
    }
    error("the bar never docked")
  }

  @Test
  fun aTapOnTheGridBarsPlaceAsTheViewerOpensNeverReachesTheVotes() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
    showViewer()
    var framesAway = 0
    repeat(OPENING_FRAMES) {
      mainClock.advanceTimeByFrame()
      tap(gridBarPlace())
      if (pillCentreY() < gridBarPlace().y - 1f) framesAway++
    }
    assertTrue(framesAway > 0, "the bar never left the grid's place")
    assertFalse(signIn.isOpen.value, "a tap meant for the bar reached the vote control")
  }

  @Test
  fun aSecondFingerOnTheVotesRightAfterTheBarDocksIsIgnoredToo() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
    showViewer()
    openUntilDocked()
    onRoot().performTouchInput {
      down(0, voteRow(60f))
      down(1, voteRow(200f))
      up(1)
      up(0)
    }
    mainClock.advanceTimeByFrame()
    assertFalse(signIn.isOpen.value, "a second finger reached the vote control inside the grace")
  }

  @Test
  fun aSwipeUpFromTheVotesRightAfterTheBarDocksStillMovesTheSheet() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
    showViewer()
    openUntilDocked()
    val before = handleTop()
    onRoot().performTouchInput { swipe(voteRow(200f), voteRow(200f) - Offset(0f, 300f), durationMillis = 200) }
    repeat(10) { mainClock.advanceTimeByFrame() }
    assertTrue(handleTop() < before - 50f, "the sheet didn't follow a drag from the vote row")
    assertFalse(signIn.isOpen.value, "the drag reached the vote control")
  }

  @Test
  fun onceTheGraceHasPassedTheVotesTakeTaps() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
    showViewer()
    openUntilDocked()
    mainClock.advanceTimeBy(BAR_MOVE_GRACE.inWholeMilliseconds)
    repeat(FRAMES_TO_TAKE_EFFECT) { mainClock.advanceTimeByFrame() }
    tap(gridBarPlace())
    mainClock.advanceTimeByFrame()
    assertTrue(signIn.isOpen.value, "the vote control stays deaf after the grace")
  }

  private companion object {
    const val WIDTH = 412
    const val HEIGHT = 915
    const val OPENING_FRAMES = 30
    const val MAX_OPENING_FRAMES = 120
    const val FRAMES_TO_TAKE_EFFECT = 3
  }
}

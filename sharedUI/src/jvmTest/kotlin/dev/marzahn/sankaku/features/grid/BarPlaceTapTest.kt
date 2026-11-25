package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Transition
import androidx.compose.runtime.CompositionLocalProvider
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
import dev.marzahn.sankaku.features.post.FakeVideoPlayers
import dev.marzahn.sankaku.features.post.LocalVideoPlayback
import dev.marzahn.sankaku.tabs.COUNT_PILL
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import org.koin.core.context.GlobalContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BarPlaceTapTest {
  private val fixture = GridScreenFixture()
  private val picture = PostDto(id = "p1", md5 = "aa11", rating = "s", status = "active", fileType = "image/png", fileWidth = 300, fileHeight = 400)
  private val video = PostDto(
    id = "v1", md5 = "bb22", rating = "s", status = "active", fileType = "video/mp4", fileWidth = 400, fileHeight = 300,
    sampleUrl = "https://v.sankakucomplex.com/data/v1.mp4",
  )
  private val players = FakeVideoPlayers()
  private var shown by mutableStateOf(false)
  private var opening: Transition<EnterExitState>? = null

  @BeforeTest
  fun start() = fixture.start()

  @AfterTest
  fun stop() = fixture.stop()

  private val signIn: SignInController get() = GlobalContext.get().get()

  private fun ComposeUiTest.showViewer(post: PostDto = picture) {
    setContent {
      SankakuTheme {
        CompositionLocalProvider(LocalVideoPlayback provides players.playback) {
          SharedTransitionLayout {
            AnimatedVisibility(shown, enter = EnterTransition.None, exit = ExitTransition.None) {
              opening = transition
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
    }
    waitForIdle()
    mainClock.autoAdvance = false
    shown = true
  }

  private fun stillOpening(): Boolean = opening?.let { it.currentState != it.targetState } == true

  private fun ComposeUiTest.gridBarPlace(): Offset {
    val window = onRoot().fetchSemanticsNode().size
    return Offset(window.width / 2f, window.height - BottomRowLayout.CentreLine.value * density.density)
  }

  private fun ComposeUiTest.voteRow(x: Float) = Offset(x, gridBarPlace().y)

  private fun ComposeUiTest.pillCentreY(): Float = onNodeWithTag(COUNT_PILL).fetchSemanticsNode().boundsInRoot.center.y

  private fun ComposeUiTest.handleTop(): Float = onNodeWithContentDescription("Details").fetchSemanticsNode().boundsInRoot.top

  private fun ComposeUiTest.positionPillTop(): Float =
    onNodeWithContentDescription(positionDescription(0, 1, hasMore = false)).fetchSemanticsNode().boundsInRoot.top

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

  private fun ComposeUiTest.untilTheSheetRests() {
    var last = Float.NaN
    var stillFrames = 0
    repeat(MAX_SETTLE_FRAMES) {
      mainClock.advanceTimeByFrame()
      val top = handleTop()
      stillFrames = if (top == last) stillFrames + 1 else 0
      if (stillFrames == REST_FRAMES) return
      last = top
    }
    error("the sheet never came to rest")
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

  @Test
  fun aDragUpFromTheGridBarsPlaceAsTheViewerOpensFollowsTheFingerAndSettlesAtHalf() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
    showViewer()
    mainClock.advanceTimeByFrame()
    val peekHandle = handleTop()
    val start = gridBarPlace()
    var slop = 0f
    var finger = start
    var firstMove: Pair<Float, Float>? = null
    onRoot().performTouchInput {
      slop = viewConfiguration.touchSlop
      down(start)
    }
    repeat(DRAG_FRAMES) { frame ->
      finger -= Offset(0f, STEP)
      onRoot().performTouchInput { moveTo(finger) }
      mainClock.advanceTimeByFrame()
      val travel = start.y - finger.y
      val lifted = peekHandle - handleTop()
      val moved = firstMove
      if (moved == null) {
        if (travel <= slop) return@repeat
        assertTrue(lifted > 0f, "frame $frame: the finger is past the touch slop and the sheet hasn't moved")
        assertTrue(stillOpening(), "the open had settled before the sheet moved")
        firstMove = travel to lifted
      } else {
        val (travelThen, liftedThen) = moved
        val lag = (travel - travelThen) - (lifted - liftedThen)
        assertTrue(lag <= 1f, "frame $frame: the sheet lags the finger by $lag px")
      }
    }
    assertTrue(pillCentreY() < handleTop(), "the bar never docked during the drag, so the drag didn't run past it")
    onRoot().performTouchInput { up() }
    untilTheSheetRests()
    val half = sheetStops(peekTop = peekHandle, height = HEIGHT.toFloat()).half
    assertEquals(peekHandle - half, handleTop(), 1f, "the sheet didn't settle at the half stop")
  }

  @Test
  fun aDragFromTheBarStopsFollowingOnceTheViewerStartsClosing() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
    showViewer()
    mainClock.advanceTimeByFrame()
    var finger = gridBarPlace()
    onRoot().performTouchInput { down(finger) }
    repeat(LIFT_FRAMES * 2) {
      finger -= Offset(0f, STEP)
      onRoot().performTouchInput { moveTo(finger) }
      mainClock.advanceTimeByFrame()
    }
    shown = false
    mainClock.advanceTimeByFrame()
    val handleAtClose = handleTop()
    repeat(LIFT_FRAMES * 2) {
      finger -= Offset(0f, STEP)
      onRoot().performTouchInput { moveTo(finger) }
      mainClock.advanceTimeByFrame()
      assertTrue(handleTop() >= handleAtClose - 1f, "frame $it: the sheet still follows the finger while the viewer closes")
    }
    onRoot().performTouchInput { up() }
  }

  @Test
  fun aSheetLiftedAndDroppedBackAsTheViewerOpensKeepsTheBarDocking() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
    showViewer()
    mainClock.advanceTimeByFrame()
    val peekHandle = handleTop()
    val start = gridBarPlace()
    var finger = start
    onRoot().performTouchInput { down(start) }
    repeat(LIFT_FRAMES) {
      finger -= Offset(0f, STEP)
      onRoot().performTouchInput { moveTo(finger) }
      mainClock.advanceTimeByFrame()
    }
    onRoot().performTouchInput { moveTo(start) }
    mainClock.advanceTimeByFrame()
    assertEquals(peekHandle, handleTop(), 1f, "the sheet isn't back at peek")
    var pill = pillCentreY()
    repeat(DROPPED_FRAMES) { frame ->
      mainClock.advanceTimeByFrame()
      assertTrue(pillCentreY() <= pill, "frame $frame: the bar headed back to the grid's place once the sheet was back at peek")
      pill = pillCentreY()
    }
    assertTrue(stillOpening(), "the open had settled, so it would have docked the bar anyway")
    onRoot().performTouchInput { up() }
  }

  @Test
  fun aTapOnTheGridBarsPlaceAsALandscapeVideoOpensLeavesTheControlsShown() = runDesktopComposeUiTest(LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT) {
    showViewer(video)
    mainClock.advanceTimeByFrame()
    assertTrue(stillOpening(), "the open had settled before the tap")
    tap(gridBarPlace())
    tap(gridBarPlace().copy(x = BAR_ROW_SIDE))
    repeat(OPENING_FRAMES) { mainClock.advanceTimeByFrame() }
    assertTrue(positionPillTop() >= 0f, "the tap hid the controls")
  }

  @Test
  fun aDragDownBesideTheFlyingBarAsALandscapeVideoOpensClosesTheViewer() = runDesktopComposeUiTest(LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT) {
    showViewer(video)
    mainClock.advanceTimeByFrame()
    var finger = gridBarPlace().copy(x = BAR_ROW_SIDE)
    onRoot().performTouchInput { down(finger) }
    repeat(FLICK_FRAMES) {
      finger += Offset(0f, FLICK_STEP)
      onRoot().performTouchInput { moveTo(finger) }
      mainClock.advanceTimeByFrame()
    }
    assertTrue(stillOpening(), "the open had settled before the drag")
    onRoot().performTouchInput { up() }
    repeat(OPENING_FRAMES) { mainClock.advanceTimeByFrame() }
    assertFalse(shown, "a drag down on the picture beside the flying bar didn't close the viewer")
  }

  private companion object {
    const val WIDTH = 412
    const val HEIGHT = 915
    const val LANDSCAPE_WIDTH = 915
    const val LANDSCAPE_HEIGHT = 412
    const val OPENING_FRAMES = 30
    const val MAX_OPENING_FRAMES = 120
    const val MAX_SETTLE_FRAMES = 120
    const val REST_FRAMES = 5
    const val FRAMES_TO_TAKE_EFFECT = 3
    const val STEP = 10f
    const val DRAG_FRAMES = 30
    const val LIFT_FRAMES = 3
    const val BAR_ROW_SIDE = 80f
    const val FLICK_STEP = 30f
    const val FLICK_FRAMES = 6
    const val DROPPED_FRAMES = 6
  }
}

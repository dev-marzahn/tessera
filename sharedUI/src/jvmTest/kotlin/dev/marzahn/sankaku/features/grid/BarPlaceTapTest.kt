package dev.marzahn.sankaku.features.grid

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarPlaceTapTest {
  @Test
  fun aTapOnTheGridBarsPlaceAsTheViewerOpensNeverReachesTheVotes() = phoneViewerTest {
    showViewer()
    var framesAway = 0
    repeat(OPENING_FRAMES) {
      frames(1)
      tap(gridBarPlace())
      if (!barAtTheGridsPlace()) framesAway++
    }
    assertTrue(framesAway > 0, "the bar never left the grid's place")
    assertFalse(signIn.isOpen.value, "a tap meant for the bar reached the vote control")
  }

  @Test
  fun aSecondFingerOnTheVotesRightAfterTheBarDocksIsIgnoredToo() = phoneViewerTest {
    showViewer()
    openUntilDocked()
    val first = press(voteRow(60f))
    val second = press(voteRow(200f))
    second.lift()
    first.lift()
    frames(1)
    assertFalse(signIn.isOpen.value, "a second finger reached the vote control inside the grace")
  }

  @Test
  fun aSwipeUpFromTheVotesRightAfterTheBarDocksStillMovesTheSheet() = phoneViewerTest {
    showViewer()
    openUntilDocked()
    val before = handleTop()
    val finger = press(voteRow(200f))
    repeat(SWIPE_MOVES) { finger.moveBy(Offset(0f, -SWIPE_STEP)) }
    finger.lift()
    frames(10)
    assertTrue(handleTop() < before - 50f, "the sheet didn't follow a drag from the vote row")
    assertFalse(signIn.isOpen.value, "the drag reached the vote control")
  }

  @Test
  fun onceTheGraceHasPassedTheVotesTakeTaps() = phoneViewerTest {
    showViewer()
    settleTheOpen()
    tap(gridBarPlace())
    frames(1)
    assertTrue(signIn.isOpen.value, "the vote control stays deaf after the grace")
  }

  @Test
  fun aDragUpFromTheGridBarsPlaceAsTheViewerOpensFollowsTheFingerAndSettlesAtHalf() = phoneViewerTest {
    showViewer()
    frames(1)
    val peekHandle = handleTop()
    val start = gridBarPlace()
    val slop = touchSlop()
    val finger = press(start)
    var firstMove: ScreenFrame? = null
    repeat(DRAG_FRAMES) { move ->
      val frame = finger.moveBy(Offset(0f, -STEP))
      val travel = start.y - frame.finger.y
      val moved = firstMove
      if (moved == null) {
        if (travel <= slop) return@repeat
        assertTrue(frame.handleTop < peekHandle, "move $move: the finger is past the touch slop and the sheet hasn't moved")
        assertTrue(stillOpening(), "the open had settled before the sheet moved")
        firstMove = frame
      } else {
        val lag = (moved.finger.y - frame.finger.y) - (moved.handleTop - frame.handleTop)
        assertTrue(lag <= 1f, "move $move: the sheet lags the finger by $lag px")
      }
    }
    assertTrue(pillCentreY() < handleTop(), "the bar never docked during the drag, so the drag didn't run past it")
    finger.lift()
    untilTheSheetRests()
    val half = sheetStops(peekTop = peekHandle, height = PhoneWindow.Portrait.height.toFloat()).half
    assertEquals(peekHandle - half, handleTop(), 1f, "the sheet didn't settle at the half stop")
  }

  @Test
  fun aTapOnTheGridBarsPlaceAsALandscapeVideoOpensLeavesTheControlsShown() = phoneViewerTest(PhoneWindow.Landscape) {
    showViewer(ViewerPosts.landscapeVideo)
    frames(1)
    assertTrue(stillOpening(), "the open had settled before the tap")
    tap(gridBarPlace())
    tap(gridBarPlace().copy(x = BAR_ROW_SIDE))
    frames(OPENING_FRAMES)
    assertTrue(positionPillTop() >= 0f, "the tap hid the controls")
  }

  @Test
  fun aDragDownBesideTheFlyingBarAsALandscapeVideoOpensClosesTheViewer() = phoneViewerTest(PhoneWindow.Landscape) {
    showViewer(ViewerPosts.landscapeVideo)
    frames(1)
    val finger = press(gridBarPlace().copy(x = BAR_ROW_SIDE))
    repeat(FLICK_FRAMES) { finger.moveBy(Offset(0f, FLICK_STEP)) }
    assertTrue(stillOpening(), "the open had settled before the drag")
    finger.lift()
    frames(OPENING_FRAMES)
    assertFalse(shown, "a drag down on the picture beside the flying bar didn't close the viewer")
  }

  private companion object {
    const val OPENING_FRAMES = 30
    const val STEP = 10f
    const val DRAG_FRAMES = 30
    const val SWIPE_STEP = 20f
    const val SWIPE_MOVES = 15
    const val BAR_ROW_SIDE = 80f
    const val FLICK_STEP = 30f
    const val FLICK_FRAMES = 6
  }
}

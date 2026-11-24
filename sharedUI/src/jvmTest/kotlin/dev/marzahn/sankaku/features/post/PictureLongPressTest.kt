package dev.marzahn.sankaku.features.post

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.compose.ZoomState
import com.github.panpf.zoomimage.compose.rememberZoomState
import com.github.panpf.zoomimage.util.setMainThreadChecker
import com.github.panpf.zoomimage.zoom.ScalesCalculator
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.marzahn.sankaku.features.grid.favouriteWithHaptic
import dev.marzahn.sankaku.tabs.queueSwipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class PictureLongPressTest {
  private var taps = 0
  private var longPresses = 0
  private var seeks = 0
  private var dragged = 0f

  private val haptics = mutableListOf<HapticFeedbackType>()
  private val recordingHaptics = object : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
      haptics += hapticFeedbackType
    }
  }
  private val favourite = { favouriteWithHaptic(recordingHaptics) { longPresses++ } }
  private lateinit var pager: PagerState
  private lateinit var zoomState: ZoomState

  private fun ComposeUiTest.start(zoomable: Boolean) {
    if (zoomable) setMainThreadChecker { true }
    setContent {
      pager = rememberPagerState { 2 }
      Box(
        Modifier
          .size(400.dp, 800.dp)
          .draggable(rememberDraggableState { dragged += it }, Orientation.Vertical),
      ) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
          val tag = Modifier.testTag("picture$page")
          if (zoomable) {
            zoomState = rememberZoomState()
            LaunchedEffect(zoomState) { zoomState.zoomable.setScalesCalculator(ScalesCalculator.fixed(2.5f)) }
            ZoomImage(
              painter = remember { BitmapPainter(ImageBitmap(400, 800)) },
              contentDescription = null,
              modifier = tag.fillMaxSize(),
              zoomState = zoomState,
              onTap = { taps++ },
              onLongPress = { favourite() },
            )
          } else {
            Box(
              tag.fillMaxSize().pictureTaps(
                key = Unit,
                onTap = { taps++ },
                onLongPress = favourite,
                onDoubleTap = { _, _ -> seeks++ },
              ),
            )
          }
        }
      }
    }
    waitForIdle()
  }

  private fun ComposeUiTest.picture() = onNodeWithTag("picture${pager.currentPage}")

  private fun assertCounts(taps: Int = 0, longPresses: Int = 0, seeks: Int = 0) {
    assertEquals(taps, this.taps, "taps")
    assertEquals(longPresses, this.longPresses, "long-presses")
    assertEquals(seeks, this.seeks, "seeks")
  }

  private fun ComposeUiTest.assertHapticAsTheLongPressCompletes() {
    var timeout = 0L
    picture().performTouchInput {
      timeout = viewConfiguration.longPressTimeoutMillis
      down(center)
    }
    mainClock.advanceTimeBy(timeout + 50)
    waitForIdle()
    assertEquals(listOf(HapticFeedbackType.Confirm), haptics, "haptic with the finger still down")
    assertCounts(longPresses = 1)
    picture().performTouchInput { up() }
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
    assertEquals(listOf(HapticFeedbackType.Confirm), haptics, "nothing more on the release")
    assertCounts(longPresses = 1)
  }

  @Test
  fun aVideosLongPressHapticComesAsItCompletes() = runComposeUiTest {
    start(zoomable = false)
    assertHapticAsTheLongPressCompletes()
  }

  @Test
  fun anImagesLongPressHapticComesAsItCompletes() = runComposeUiTest {
    start(zoomable = true)
    assertHapticAsTheLongPressCompletes()
  }

  @Test
  fun aLongPressOnAVideoFavouritesAndIsNoTap() = runComposeUiTest {
    start(zoomable = false)
    picture().performTouchInput { longClick() }
    waitForIdle()
    assertCounts(longPresses = 1)
  }

  @Test
  fun aTapOnAVideoStillTogglesTheChrome() = runComposeUiTest {
    start(zoomable = false)
    picture().performTouchInput { click() }
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
    assertCounts(taps = 1)
  }

  @Test
  fun aDoubleTapOnAVideoStillSeeks() = runComposeUiTest {
    start(zoomable = false)
    picture().performTouchInput { doubleClick() }
    waitForIdle()
    assertCounts(seeks = 1)
  }

  @Test
  fun theSwipeBetweenPostsIsNoLongPressEvenSlow() = runComposeUiTest {
    start(zoomable = false)
    picture().performTouchInput { swipeLeft(durationMillis = 1_000) }
    waitForIdle()
    assertEquals(1, pager.currentPage)
    assertCounts()
  }

  @Test
  fun theSheetsDragAndTheClosesDragAreNoLongPress() = runComposeUiTest {
    start(zoomable = false)
    picture().performTouchInput { swipeUp(durationMillis = 1_000) }
    waitForIdle()
    assertTrue(dragged < 0f)
    dragged = 0f
    picture().performTouchInput { swipeDown(durationMillis = 1_000) }
    waitForIdle()
    assertTrue(dragged > 0f)
    assertCounts()
  }

  @Test
  fun aLonePostsQueueSwipeIsNoLongPress() = runComposeUiTest {
    setContent {
      Box(
        Modifier
          .size(400.dp, 800.dp)
          .queueSwipe(enabled = true, from = null, claimSideways = true)
          .testTag("lone")
          .pictureTaps(key = Unit, onTap = { taps++ }, onLongPress = { longPresses++ }, onDoubleTap = { _, _ -> seeks++ }),
      )
    }
    onNodeWithTag("lone").performTouchInput { swipeLeft(durationMillis = 1_000) }
    waitForIdle()
    assertCounts()
    onNodeWithTag("lone").performTouchInput { longClick() }
    waitForIdle()
    assertCounts(longPresses = 1)
  }

  @Test
  fun aLongPressOnAnImageFavouritesWithoutZooming() = runComposeUiTest {
    start(zoomable = true)
    picture().performTouchInput { longClick() }
    waitForIdle()
    assertCounts(longPresses = 1)
    assertEquals(zoomState.zoomable.minScale, zoomState.zoomable.transform.scaleX)
  }

  @Test
  fun aDoubleTapOnAnImageStillZoomsAndALongPressZoomedStillFavourites() = runComposeUiTest {
    start(zoomable = true)
    picture().performTouchInput { doubleClick() }
    waitForIdle()
    assertTrue(zoomState.zoomable.transform.scaleX > zoomState.zoomable.minScale * 2f)
    assertCounts()

    picture().performTouchInput { longClick() }
    waitForIdle()
    assertCounts(longPresses = 1)
  }

  @Test
  fun aTapOnAnImageStillTogglesTheChrome() = runComposeUiTest {
    start(zoomable = true)
    picture().performTouchInput { click() }
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
    assertCounts(taps = 1)
  }

  @Test
  fun theSwipeBetweenImagesIsNoLongPress() = runComposeUiTest {
    start(zoomable = true)
    picture().performTouchInput { swipeLeft(durationMillis = 1_000) }
    waitForIdle()
    assertEquals(1, pager.currentPage)
    assertCounts()
  }
}

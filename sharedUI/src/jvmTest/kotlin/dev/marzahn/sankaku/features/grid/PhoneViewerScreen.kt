package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.toSize
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.features.post.FakeVideoPlayers
import dev.marzahn.sankaku.features.post.LocalVideoPlayback
import dev.marzahn.sankaku.features.post.voteState
import dev.marzahn.sankaku.tabs.COUNT_PILL
import dev.marzahn.sankaku.tabs.LocalOpenTabList
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.math.abs
import org.koin.core.context.GlobalContext

internal object ViewerPosts {
  val picture = PostDto(id = "p1", md5 = "aa11", rating = "s", status = "active", fileType = "image/png", fileWidth = 300, fileHeight = 400)
  val nextPicture = picture.copy(id = "p2", md5 = "cc33")
  val unzoomablePicture = picture.copy(id = "g1", md5 = "dd44", fileType = "image/gif")
  val landscapeVideo = PostDto(
    id = "v1", md5 = "bb22", rating = "s", status = "active", fileType = "video/mp4", fileWidth = 400, fileHeight = 300,
    sampleUrl = "https://v.sankakucomplex.com/data/v1.mp4",
  )
}

internal enum class PhoneWindow(val width: Int, val height: Int) {
  Portrait(412, 915),
  Landscape(915, 412),
}

@OptIn(ExperimentalTestApi::class)
internal fun <T> phoneViewerTest(window: PhoneWindow = PhoneWindow.Portrait, block: PhoneViewerScreen.() -> T): T {
  val fixture = GridScreenFixture()
  fixture.start()
  try {
    val results = mutableListOf<T>()
    runDesktopComposeUiTest(window.width, window.height) { results += PhoneViewerScreen(this).block() }
    return results.single()
  } finally {
    fixture.stop()
  }
}

internal class ScreenFrame(val finger: Offset, val handleTop: Float, val pillY: Float, val lift: Float, val stop: SheetStop)

@OptIn(ExperimentalTestApi::class)
internal class PhoneViewerScreen(private val test: ComposeUiTest) {
  private val players = FakeVideoPlayers()
  private val handleMatcher = hasContentDescription("Details") and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)
  private val voteControl = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, voteState(userVote = null))
  private var opening: Transition<EnterExitState>? = null
  private var loadedCount = 0
  private var nextPointer = 0
  private var aParentTookOver = false
  var shown by mutableStateOf(false)
    private set
  var shownIndex by mutableIntStateOf(0)
    private set
  var queueOpened = 0
    private set

  val signIn: SignInController get() = GlobalContext.get().get()

  fun showViewer(vararg posts: PostDto = arrayOf(ViewerPosts.picture)) {
    loadedCount = posts.size
    test.setContent {
      SankakuTheme {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { giveTheGestureToAParentWhenItTakesOver() }) {
          CompositionLocalProvider(LocalVideoPlayback provides players.playback, LocalOpenTabList provides { queueOpened++ }) {
            SharedTransitionLayout {
              AnimatedVisibility(shown, enter = EnterTransition.None, exit = ExitTransition.None) {
                opening = transition
                PhoneViewer(
                  index = shownIndex,
                  postAt = { posts.getOrNull(it) },
                  loadedCount = posts.size,
                  hasMore = false,
                  sharedId = null,
                  visibilityScope = this,
                  onShow = { shownIndex = it },
                  onClose = { shown = false },
                )
              }
            }
          }
        }
      }
    }
    test.waitForIdle()
    test.mainClock.autoAdvance = false
    shown = true
  }

  private suspend fun PointerInputScope.giveTheGestureToAParentWhenItTakesOver() = awaitPointerEventScope {
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      if (aParentTookOver) event.changes.forEach { it.consume() }
    }
  }

  fun startClosing() {
    shown = false
  }

  fun reopen() {
    shown = true
  }

  fun stillOpening(): Boolean = opening?.let { it.currentState != it.targetState } == true

  fun frames(count: Int) = repeat(count) { test.mainClock.advanceTimeByFrame() }

  fun openUntilDocked() {
    test.mainClock.advanceTimeByFrame()
    repeat(MAX_OPENING_FRAMES) { if (barDockedOverAFrame()) return }
    error("the bar never docked")
  }

  fun barDockedOverAFrame(): Boolean {
    val offset = barOffsetFromTheHandle()
    test.mainClock.advanceTimeByFrame()
    return !barAtTheGridsPlace() && abs(barOffsetFromTheHandle() - offset) < DOCKED_DRIFT
  }

  private fun barOffsetFromTheHandle(): Float = pillCentreY() - handleTop()

  fun waitOutTheBarGrace() {
    test.mainClock.advanceTimeBy(BAR_MOVE_GRACE.inWholeMilliseconds)
    frames(FRAMES_TO_TAKE_EFFECT)
  }

  fun settleTheOpen() {
    openUntilDocked()
    repeat(MAX_OPENING_FRAMES) {
      if (!stillOpening()) return waitOutTheBarGrace()
      test.mainClock.advanceTimeByFrame()
    }
    error("the open never settled")
  }

  fun openToPeek() {
    frames(1)
    settleTheOpen()
  }

  fun openToHalf() {
    openToPeek()
    tap(handle().center)
    untilTheSheetRests()
  }

  fun untilTheSheetReaches(stop: SheetStop) {
    repeat(MAX_SETTLE_FRAMES) {
      test.mainClock.advanceTimeByFrame()
      if (abs(sheetLift() - shownStops().at(stop)) < 1f) return
    }
    error("the sheet never reached $stop")
  }

  fun untilTheSheetRests() {
    var last = Float.NaN
    var stillFrames = 0
    repeat(MAX_SETTLE_FRAMES) {
      test.mainClock.advanceTimeByFrame()
      val lift = sheetLift()
      stillFrames = if (lift == last) stillFrames + 1 else 0
      if (stillFrames == REST_FRAMES) return
      last = lift
    }
    error("the sheet never came to rest")
  }

  fun centreX(): Float = test.onRoot().fetchSemanticsNode().size.width / 2f

  fun gridBarPlace(): Offset {
    val window = test.onRoot().fetchSemanticsNode().size
    return Offset(window.width / 2f, window.height - BottomRowLayout.CentreLine.value * test.density.density)
  }

  fun voteRow(x: Float) = Offset(x, gridBarPlace().y)

  fun bar(): Rect {
    val pill = test.onNodeWithTag(COUNT_PILL).fetchSemanticsNode()
    return Rect(pill.positionInRoot, pill.size.toSize())
  }

  fun barDrawn(): Boolean = !test.onNodeWithTag(COUNT_PILL).fetchSemanticsNode().boundsInRoot.isEmpty

  fun pillCentreY(): Float = bar().center.y

  fun barAtTheGridsPlace(): Boolean = abs(pillCentreY() - gridBarPlace().y) < 1f

  private fun handleNode() = test.onNode(handleMatcher).fetchSemanticsNode()

  fun handle(): Rect = handleNode().boundsInRoot

  fun handleTop(): Float = handle().top

  fun sheetStop(): SheetStop {
    val spoken = handleNode().config.getOrNull(SemanticsProperties.StateDescription)
    return SheetStop.entries.first { it.spoken == spoken }
  }

  private fun sheetRange() =
    test.onNode(hasAnyDescendant(handleMatcher) and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
      .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

  fun sheetLift(): Float = sheetRange().value()

  fun shownStops(): SheetStops =
    sheetStops(peekTop = sheetRange().maxValue(), height = test.onRoot().fetchSemanticsNode().size.height.toFloat())

  fun sheetStopLifts(): List<Float> = shownStops().all

  fun votes(): Rect = test.onNode(voteControl).fetchSemanticsNode().boundsInRoot

  fun pictureClearOfItsRetryButton(): Offset = Offset(centreX() / 2, handleTop() / 2)

  fun positionPillTop(): Float =
    test.onNodeWithContentDescription(positionDescription(shownIndex, loadedCount, hasMore = false)).fetchSemanticsNode().boundsInRoot.top

  fun touchSlop(): Float {
    var slop = 0f
    test.onRoot().performTouchInput { slop = viewConfiguration.touchSlop }
    return slop
  }

  fun tap(at: Offset) {
    test.onRoot().performTouchInput { click(at) }
  }

  fun press(at: Offset): Finger = Finger(nextPointer++, at)

  private fun frame(finger: Offset) = ScreenFrame(finger, handleTop(), pillCentreY(), sheetLift(), sheetStop())

  inner class Finger internal constructor(private val pointer: Int, start: Offset) {
    var at = start
      private set

    init {
      test.onRoot().performTouchInput { down(pointer, start) }
    }

    fun moveBy(by: Offset): ScreenFrame {
      at += by
      test.onRoot().performTouchInput { moveTo(pointer, at, delayMillis = 0) }
      return hold()
    }

    fun moveTo(to: Offset): ScreenFrame = moveBy(to - at)

    fun hold(): ScreenFrame {
      test.mainClock.advanceTimeByFrame()
      return frame(at)
    }

    fun lift() {
      test.onRoot().performTouchInput { up(pointer) }
    }

    fun cancel() {
      aParentTookOver = true
      lift()
      test.mainClock.advanceTimeByFrame()
      aParentTookOver = false
    }
  }

  private companion object {
    const val MAX_OPENING_FRAMES = 120
    const val MAX_SETTLE_FRAMES = 120
    const val REST_FRAMES = 5
    const val FRAMES_TO_TAKE_EFFECT = 3
    const val DOCKED_DRIFT = 0.1f
  }
}

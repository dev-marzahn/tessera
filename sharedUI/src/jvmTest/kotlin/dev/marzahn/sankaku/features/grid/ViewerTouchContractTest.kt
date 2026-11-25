package dev.marzahn.sankaku.features.grid

import androidx.compose.ui.geometry.Offset
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.features.grid.Drag.CancelledFlickDown
import dev.marzahn.sankaku.features.grid.Drag.CancelledFlickUp
import dev.marzahn.sankaku.features.grid.Drag.DragDown
import dev.marzahn.sankaku.features.grid.Drag.DragUp
import dev.marzahn.sankaku.features.grid.Drag.FlickDown
import dev.marzahn.sankaku.features.grid.Drag.FlickUp
import dev.marzahn.sankaku.features.grid.Drag.LiftAndDropBack
import dev.marzahn.sankaku.features.grid.Drag.Swipe
import dev.marzahn.sankaku.features.grid.DragOutcome.Closes
import dev.marzahn.sankaku.features.grid.DragOutcome.FlingsToNextStop
import dev.marzahn.sankaku.features.grid.DragOutcome.FollowsFinger
import dev.marzahn.sankaku.features.grid.DragOutcome.FollowsFingerAsTheBarDocks
import dev.marzahn.sankaku.features.grid.DragOutcome.HoldsStill
import dev.marzahn.sankaku.features.grid.DragOutcome.KeepsDocking
import dev.marzahn.sankaku.features.grid.DragOutcome.NoFling
import dev.marzahn.sankaku.features.grid.DragOutcome.SwipesToNext
import dev.marzahn.sankaku.features.grid.Phase.ClosingAtHalf
import dev.marzahn.sankaku.features.grid.Phase.ClosingWhileOpening
import dev.marzahn.sankaku.features.grid.Phase.Half
import dev.marzahn.sankaku.features.grid.Phase.Opening
import dev.marzahn.sankaku.features.grid.Phase.OpeningAtHalf
import dev.marzahn.sankaku.features.grid.Phase.OpeningDocked
import dev.marzahn.sankaku.features.grid.Phase.Peek
import dev.marzahn.sankaku.features.grid.Place.DockedBar
import dev.marzahn.sankaku.features.grid.Place.FlyingBar
import dev.marzahn.sankaku.features.grid.Place.Handle
import dev.marzahn.sankaku.features.grid.Place.Picture
import dev.marzahn.sankaku.features.grid.Place.SheetContent
import dev.marzahn.sankaku.features.grid.Place.StatsRow
import dev.marzahn.sankaku.features.grid.Scene.LandscapeVideo
import dev.marzahn.sankaku.features.grid.TapOutcome.HidesChrome
import dev.marzahn.sankaku.features.grid.TapOutcome.ReachesBar
import dev.marzahn.sankaku.features.grid.TapOutcome.ReachesNothing
import dev.marzahn.sankaku.features.grid.TapOutcome.ReachesVotes
import dev.marzahn.sankaku.features.grid.TapOutcome.StepsSheet
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewerTouchContractTest {
  @Test
  fun everyTouchDoesWhatTheContractSays() {
    val broken = CONTRACT.mapNotNull { touch -> runCatching(touch::check).exceptionOrNull()?.let { "$touch: ${it.message}" } }
    assertTrue(broken.isEmpty(), "${broken.size} of ${CONTRACT.size} touches broke the contract:\n" + broken.joinToString("\n"))
  }

  @Test
  fun everyPlaceOnScreenIsTouchedInEveryPhase() {
    for (place in Place.entries) for (phase in Phase.entries) {
      if (place.absenceIn(phase) != null) continue
      assertTrue(CONTRACT.any { it.place == place && it.phase == phase }, "$place while $phase has no touch in the contract")
    }
  }
}

private val CONTRACT = listOf(
  tap(Picture, Opening, HidesChrome),
  drag(Picture, Opening, DragUp, FollowsFinger),
  drag(Picture, Opening, DragDown, Closes),
  drag(Picture, Opening, Swipe, SwipesToNext),
  drag(Picture, Opening, LiftAndDropBack, KeepsDocking),
  tap(FlyingBar, Opening, ReachesBar),
  tap(FlyingBar, Opening, ReachesBar, LandscapeVideo),
  drag(FlyingBar, Opening, DragUp, FollowsFingerAsTheBarDocks),
  drag(FlyingBar, Opening, DragDown, HoldsStill),
  drag(FlyingBar, Opening, DragDown, Closes, LandscapeVideo),
  drag(FlyingBar, Opening, FlickUp, FlingsToNextStop),
  drag(FlyingBar, Opening, CancelledFlickUp, NoFling),
  drag(FlyingBar, Opening, LiftAndDropBack, KeepsDocking),
  tap(Handle, Opening, StepsSheet),
  drag(Handle, Opening, DragUp, FollowsFinger),

  tap(Picture, OpeningDocked, HidesChrome),
  drag(Picture, OpeningDocked, DragDown, Closes),
  tap(DockedBar, OpeningDocked, ReachesBar),
  drag(DockedBar, OpeningDocked, DragUp, FollowsFinger),
  drag(DockedBar, OpeningDocked, DragDown, Closes),
  tap(StatsRow, OpeningDocked, ReachesNothing),
  drag(StatsRow, OpeningDocked, DragUp, FollowsFinger),
  tap(Handle, OpeningDocked, StepsSheet),

  drag(Picture, OpeningAtHalf, DragDown, FollowsFinger),
  drag(DockedBar, OpeningAtHalf, DragDown, FollowsFinger),
  drag(StatsRow, OpeningAtHalf, DragDown, FollowsFinger),
  tap(Handle, OpeningAtHalf, StepsSheet),
  drag(SheetContent, OpeningAtHalf, DragDown, FollowsFinger),

  tap(Picture, Peek, HidesChrome),
  tap(Picture, Peek, HidesChrome, LandscapeVideo),
  drag(Picture, Peek, DragUp, FollowsFinger),
  drag(Picture, Peek, DragDown, Closes),
  drag(Picture, Peek, DragDown, Closes, LandscapeVideo),
  drag(Picture, Peek, Swipe, SwipesToNext),
  drag(Picture, Peek, FlickUp, FlingsToNextStop),
  drag(Picture, Peek, FlickDown, Closes),
  drag(Picture, Peek, CancelledFlickUp, NoFling),
  drag(Picture, Peek, CancelledFlickDown, NoFling),
  tap(DockedBar, Peek, ReachesBar),
  drag(DockedBar, Peek, DragUp, FollowsFinger),
  drag(DockedBar, Peek, DragDown, Closes),
  drag(DockedBar, Peek, DragDown, Closes, LandscapeVideo),
  drag(DockedBar, Peek, FlickUp, FlingsToNextStop),
  tap(StatsRow, Peek, ReachesVotes),
  drag(StatsRow, Peek, DragUp, FollowsFinger),
  drag(StatsRow, Peek, DragDown, HoldsStill),
  drag(StatsRow, Peek, CancelledFlickUp, NoFling),
  tap(Handle, Peek, StepsSheet),
  drag(Handle, Peek, DragUp, FollowsFinger),

  tap(Picture, Half, HidesChrome),
  drag(Picture, Half, DragUp, FollowsFinger),
  drag(Picture, Half, DragDown, FollowsFinger),
  drag(Picture, Half, Swipe, SwipesToNext),
  tap(DockedBar, Half, ReachesBar),
  drag(DockedBar, Half, DragUp, FollowsFinger),
  drag(DockedBar, Half, DragDown, FollowsFinger),
  tap(StatsRow, Half, ReachesVotes),
  drag(StatsRow, Half, DragDown, FollowsFinger),
  tap(Handle, Half, StepsSheet),
  drag(Handle, Half, DragDown, FollowsFinger),
  drag(SheetContent, Half, DragUp, FollowsFinger),
  drag(SheetContent, Half, DragDown, FollowsFinger),
  drag(SheetContent, Half, FlickDown, FlingsToNextStop),
  drag(SheetContent, Half, CancelledFlickDown, NoFling),

  closing(Picture, ClosingWhileOpening),
  closing(FlyingBar, ClosingWhileOpening),
  closing(Handle, ClosingWhileOpening),
  closing(Picture, ClosingAtHalf),
  closing(DockedBar, ClosingAtHalf),
  closing(StatsRow, ClosingAtHalf),
  closing(Handle, ClosingAtHalf),
  closing(SheetContent, ClosingAtHalf),
)

private fun tap(place: Place, phase: Phase, outcome: TapOutcome, scene: Scene = Scene.Portrait): Touch = TapTouch(scene, place, phase, outcome)

private fun drag(place: Place, phase: Phase, drag: Drag, outcome: DragOutcome, scene: Scene = Scene.Portrait): Touch =
  DragTouch(scene, place, phase, drag, outcome)

private fun closing(place: Place, phase: Phase, scene: Scene = Scene.Portrait): Touch = ClosingTouch(scene, place, phase)

private enum class Scene(val window: PhoneWindow, val posts: List<PostDto>, val label: String) {
  Portrait(PhoneWindow.Portrait, listOf(ViewerPosts.unzoomablePicture, ViewerPosts.nextPicture), ""),
  LandscapeVideo(PhoneWindow.Landscape, listOf(ViewerPosts.landscapeVideo, ViewerPosts.nextPicture), " (landscape video)"),
}

private enum class Phase(val stop: SheetStop, val reach: PhoneViewerScreen.() -> Unit, val closesMidGesture: Boolean = false) {
  Opening(SheetStop.Peek, PhoneViewerScreen::atTheOpensFirstFrame),
  OpeningDocked(SheetStop.Peek, PhoneViewerScreen::dockedWhileOpening),
  OpeningAtHalf(SheetStop.Half, PhoneViewerScreen::atHalfWhileOpening),
  Peek(SheetStop.Peek, PhoneViewerScreen::settledAtPeek),
  Half(SheetStop.Half, PhoneViewerScreen::settledAtHalf),
  ClosingWhileOpening(SheetStop.Peek, PhoneViewerScreen::atTheOpensFirstFrame, closesMidGesture = true),
  ClosingAtHalf(SheetStop.Half, PhoneViewerScreen::settledAtHalf, closesMidGesture = true),
}

private fun PhoneViewerScreen.atTheOpensFirstFrame() {
  frames(1)
  assertTrue(stillOpening(), "phase not reached: the open had settled")
}

private fun PhoneViewerScreen.dockedWhileOpening() {
  frames(1)
  openUntilDocked()
  assertTrue(stillOpening(), "phase not reached: the open settled before the bar docked")
}

private fun PhoneViewerScreen.atHalfWhileOpening() {
  frames(1)
  tap(handle().center)
  untilTheSheetReaches(SheetStop.Half)
  openUntilDocked()
  assertTrue(stillOpening(), "phase not reached: the open settled before the sheet reached half and the bar docked")
  assertEquals(SheetStop.Half, sheetStop(), "phase not reached: the sheet isn't at half")
}

private fun PhoneViewerScreen.settledAtPeek() {
  openToPeek()
  assertFalse(stillOpening(), "phase not reached: the open is still running")
  assertEquals(SheetStop.Peek, sheetStop(), "phase not reached: the sheet isn't at peek")
}

private fun PhoneViewerScreen.settledAtHalf() {
  openToHalf()
  assertFalse(stillOpening(), "phase not reached: the open is still running")
  assertEquals(SheetStop.Half, sheetStop(), "phase not reached: the sheet isn't at half")
}

private const val THE_BAR_HAS_DOCKED = "the bar docks before the sheet can reach this phase"
private const val THE_BAR_IS_STILL_AT_THE_GRIDS_PLACE = "on the open's first frame the bar is still at the grid's place"
private const val ONLY_THE_HEADER_SHOWS = "at peek the sheet shows only its header"

private enum class Place(private val absences: Map<Phase, String>, val find: PhoneViewerScreen.() -> Offset) {
  Picture(emptyMap(), { pictureClearOfItsRetryButton() }),
  FlyingBar(
    mapOf(OpeningDocked to THE_BAR_HAS_DOCKED, OpeningAtHalf to THE_BAR_HAS_DOCKED, Peek to THE_BAR_HAS_DOCKED, Half to THE_BAR_HAS_DOCKED, ClosingAtHalf to THE_BAR_HAS_DOCKED),
    {
      assertTrue(barAtTheGridsPlace(), "place not reached: the bar has left the grid's place")
      bar().center
    },
  ),
  DockedBar(
    mapOf(Opening to THE_BAR_IS_STILL_AT_THE_GRIDS_PLACE, ClosingWhileOpening to THE_BAR_IS_STILL_AT_THE_GRIDS_PLACE),
    {
      assertTrue(barDockedOverAFrame(), "place not reached: the bar is still flying")
      bar().center
    },
  ),
  StatsRow(
    mapOf(Opening to THE_BAR_IS_STILL_AT_THE_GRIDS_PLACE, ClosingWhileOpening to THE_BAR_IS_STILL_AT_THE_GRIDS_PLACE),
    {
      val votes = votes().center
      assertFalse(bar().contains(votes), "place not reached: the bar lies over the votes")
      votes
    },
  ),
  Handle(emptyMap(), { handle().center }),
  SheetContent(
    mapOf(Opening to ONLY_THE_HEADER_SHOWS, OpeningDocked to ONLY_THE_HEADER_SHOWS, Peek to ONLY_THE_HEADER_SHOWS, ClosingWhileOpening to ONLY_THE_HEADER_SHOWS),
    { Offset(centreX(), handleTop() + SHEET_CONTENT_BELOW_HANDLE) },
  ),
  ;

  fun absenceIn(phase: Phase): String? = absences[phase]
}

private class Before(val stop: SheetStop, val lift: Float)

private sealed class Touch(val scene: Scene, val place: Place, val phase: Phase) {
  init {
    require(place.absenceIn(phase) == null) { "$place can't be touched while $phase: ${place.absenceIn(phase)}" }
  }

  fun check() = phoneViewerTest(scene.window) {
    showViewer(*scene.posts.toTypedArray())
    phase.reach(this)
    val at = place.find(this)
    run(at, Before(sheetStop(), sheetLift()))
  }

  protected abstract fun PhoneViewerScreen.run(at: Offset, before: Before)
}

private class TapTouch(scene: Scene, place: Place, phase: Phase, private val outcome: TapOutcome) : Touch(scene, place, phase) {
  init {
    require(!phase.closesMidGesture) { "a tap is over before closing could start: use closing($place, $phase)" }
  }

  override fun PhoneViewerScreen.run(at: Offset, before: Before) {
    tap(at)
    outcome.holds(this, before)
  }

  override fun toString() = "$place, $phase, Tap -> $outcome${scene.label}"
}

private class DragTouch(scene: Scene, place: Place, phase: Phase, private val drag: Drag, private val outcome: DragOutcome) :
  Touch(scene, place, phase) {
  init {
    require(!phase.closesMidGesture) { "a drag into closing only stops: use closing($place, $phase)" }
    require(outcome.fits(drag, phase)) { "$outcome can't follow $drag while $phase" }
  }

  override fun PhoneViewerScreen.run(at: Offset, before: Before) {
    val slop = touchSlop()
    val finger = press(at)
    val moves = drag.moves.map(finger::moveBy)
    val held = drag.ending.finish(finger)
    outcome.holds(this, before, drag, DragTrace(at, slop, moves, held))
  }

  override fun toString() = "$place, $phase, $drag -> $outcome${scene.label}"
}

private class ClosingTouch(scene: Scene, place: Place, phase: Phase) : Touch(scene, place, phase) {
  init {
    require(phase.closesMidGesture) { "$phase doesn't close: use drag($place, $phase, ...)" }
  }

  override fun PhoneViewerScreen.run(at: Offset, before: Before) {
    val finger = press(at)
    repeat(MOVES_BEFORE_CLOSING) { finger.moveBy(SLOW_UP) }
    startClosing()
    val atClose = finger.hold()
    assertTrue(abs(atClose.lift - before.lift) > 0.5f, "the sheet never followed the finger before closing began")
    repeat(MOVES_WHILE_CLOSING) { move ->
      val followed = finger.moveBy(SLOW_UP).lift - atClose.lift
      assertTrue(followed <= 1f, "move $move: the sheet still follows the finger $followed px after closing began")
    }
    finger.lift()
    reopen()
    untilTheSheetRests()
    val lift = sheetLift()
    assertTrue(sheetStopLifts().any { abs(it - lift) <= 1f }, "the sheet was left between stops, $lift px up")
  }

  override fun toString() = "$place, $phase, DragUp -> StopsWhenClosing${scene.label}"
}

private class DragTrace(val start: Offset, val slop: Float, val moves: List<ScreenFrame>, val held: List<ScreenFrame>)

private sealed interface Direction {
  val unit: Offset

  fun steps(count: Int, size: Float): List<Offset> = List(count) { unit * size }
}

private enum class Vertical(override val unit: Offset) : Direction {
  Up(Offset(0f, -1f)),
  Down(Offset(0f, 1f)),
}

private data object Sideways : Direction {
  override val unit = Offset(-1f, 0f)
}

private sealed interface Ending {
  fun finish(finger: PhoneViewerScreen.Finger): List<ScreenFrame>

  data object Lift : Ending {
    override fun finish(finger: PhoneViewerScreen.Finger): List<ScreenFrame> {
      finger.lift()
      return emptyList()
    }
  }

  data object Cancel : Ending {
    override fun finish(finger: PhoneViewerScreen.Finger): List<ScreenFrame> {
      finger.cancel()
      return emptyList()
    }
  }

  data class HoldThenLift(val frames: Int) : Ending {
    override fun finish(finger: PhoneViewerScreen.Finger): List<ScreenFrame> = List(frames) { finger.hold() }.also { finger.lift() }
  }
}

private enum class Drag(val direction: Direction, val moves: List<Offset>, val ending: Ending, val flick: Boolean = false) {
  DragUp(Vertical.Up, Vertical.Up.steps(60, SLOW_STEP), Ending.Lift),
  DragDown(Vertical.Down, Vertical.Down.steps(20, 2 * SLOW_STEP), Ending.Lift),
  Swipe(Sideways, Sideways.steps(10, SWIPE_STEP), Ending.Lift),
  FlickUp(Vertical.Up, Vertical.Up.steps(4, FAST_STEP), Ending.Lift, flick = true),
  FlickDown(Vertical.Down, Vertical.Down.steps(4, FAST_STEP), Ending.Lift, flick = true),
  CancelledFlickUp(Vertical.Up, Vertical.Up.steps(3, FAST_STEP), Ending.Cancel, flick = true),
  CancelledFlickDown(Vertical.Down, Vertical.Down.steps(3, FAST_STEP), Ending.Cancel, flick = true),
  LiftAndDropBack(Vertical.Up, Vertical.Up.steps(2, FAST_STEP / 2) + Vertical.Down.steps(1, FAST_STEP), Ending.HoldThenLift(6)),
}

private val SLOW_UP = Vertical.Up.unit * SLOW_STEP

private fun SheetStop.above(): SheetStop? = SheetStop.entries.getOrNull(ordinal + 1)

private fun SheetStop.below(): SheetStop? = SheetStop.entries.getOrNull(ordinal - 1)

private fun SheetStop.stepped(): SheetStop = above() ?: SheetStop.Peek

private fun SheetStop.next(direction: Direction): SheetStop? = when (direction) {
  Vertical.Up -> above()
  Vertical.Down -> below()
  Sideways -> null
}

private enum class TapOutcome(val holds: PhoneViewerScreen.(Before) -> Unit) {
  HidesChrome({
    frames(SETTLE_FRAMES)
    assertTrue(positionPillTop() < 0f, "the tap didn't reach the picture: the controls are still shown")
  }),
  ReachesVotes({
    frames(1)
    assertTrue(signIn.isOpen.value, "the tap didn't reach the vote control")
  }),
  ReachesBar({ before ->
    val drawnWhenTapped = barDrawn()
    frames(SETTLE_FRAMES)
    assertNothingElseTookTheTap(before)
    assertTrue(drawnWhenTapped, "the bar is clipped away where it was tapped, so it isn't drawn and lets taps through")
    assertEquals(1, queueOpened, "the tap didn't reach the bar")
  }),
  ReachesNothing({ before ->
    frames(SETTLE_FRAMES)
    assertNothingElseTookTheTap(before)
    assertEquals(0, queueOpened, "the tap opened the tab list")
  }),
  StepsSheet({ before ->
    untilTheSheetRests()
    assertEquals(before.stop.stepped(), sheetStop(), "the tap didn't step the sheet")
  }),
}

private fun PhoneViewerScreen.assertNothingElseTookTheTap(before: Before) {
  assertTrue(positionPillTop() >= 0f, "the tap reached the picture and hid the controls")
  assertFalse(signIn.isOpen.value, "the tap reached the vote control")
  assertEquals(before.stop, sheetStop(), "the tap moved the sheet")
}

private enum class DragOutcome(val fits: (Drag, Phase) -> Boolean, val holds: PhoneViewerScreen.(Before, Drag, DragTrace) -> Unit) {
  Closes({ drag, _ -> drag.direction == Vertical.Down && drag.ending == Ending.Lift }, { _, _, _ ->
    frames(SETTLE_FRAMES)
    assertFalse(shown, "the viewer stayed open")
  }),
  FollowsFinger({ drag, _ -> drag.direction is Vertical && drag.ending == Ending.Lift && !drag.flick }, { before, _, trace -> followsFinger(before, trace) }),
  FollowsFingerAsTheBarDocks({ drag, _ -> drag.direction == Vertical.Up && drag.ending == Ending.Lift && !drag.flick }, { before, _, trace ->
    followsFinger(before, trace)
    val riding = trace.moves.takeLast(RIDING_MOVES).map { it.handleTop - it.pillY }
    assertTrue(riding.max() - riding.min() <= 1f, "the bar never docked onto the sheet during the drag")
  }),
  HoldsStill({ drag, _ -> drag.direction is Vertical && drag.ending == Ending.Lift }, { before, _, trace ->
    for ((index, frame) in trace.moves.withIndex()) {
      assertEquals(before.lift, frame.lift, 1f, "move $index: the sheet moved")
    }
    frames(SETTLE_FRAMES)
    assertTrue(shown, "the viewer closed")
    assertEquals(0, shownIndex, "the viewer moved to another post")
  }),
  SwipesToNext({ drag, _ -> drag.direction == Sideways }, { _, _, _ ->
    frames(SETTLE_FRAMES)
    assertEquals(1, shownIndex, "the viewer didn't move to the next post")
  }),
  FlingsToNextStop({ drag, phase -> drag.flick && drag.ending == Ending.Lift && phase.stop.next(drag.direction) != null }, { before, drag, _ ->
    untilTheSheetRests()
    assertEquals(before.stop.next(drag.direction), sheetStop(), "the flick didn't carry the sheet to the next stop")
  }),
  NoFling({ drag, _ -> drag.flick && drag.ending == Ending.Cancel }, { before, _, _ ->
    frames(SETTLE_FRAMES)
    assertTrue(shown, "the cancelled pull closed the viewer")
    assertEquals(before.stop, sheetStop(), "the cancelled pull flung the sheet")
    assertEquals(before.lift, sheetLift(), 1f, "the cancelled pull left the sheet between stops")
  }),
  KeepsDocking({ drag, _ -> drag.ending is Ending.HoldThenLift }, { before, _, trace ->
    assertTrue(trace.moves.any { it.lift > before.lift + 1f }, "the sheet never lifted")
    assertEquals(before.lift, trace.held.first().lift, 1f, "the sheet isn't back at peek")
    for ((index, frame) in trace.held.withIndex().drop(1)) {
      assertTrue(frame.pillY <= trace.held[index - 1].pillY, "held frame $index: the bar headed back to the grid's place")
    }
    assertTrue(stillOpening(), "the open had settled, so it would have docked the bar anyway")
  }),
}

private fun PhoneViewerScreen.followsFinger(before: Before, trace: DragTrace) {
  val pastSlop = trace.moves.withIndex().dropWhile { abs(it.value.finger.y - trace.start.y) <= trace.slop }
  assertTrue(pastSlop.size >= 2, "the finger never got past the touch slop")
  val first = pastSlop.first().value
  assertTrue(abs(first.lift - before.lift) > 0.5f, "the finger is past the touch slop and the sheet hasn't moved")
  for ((index, frame) in pastSlop.drop(1)) {
    val off = (first.finger.y - frame.finger.y) - (frame.lift - first.lift)
    assertTrue(abs(off) <= 1f, "move $index: the sheet is $off px off the finger")
  }
  assertTrue(shown, "the viewer closed")
}

private const val SLOW_STEP = 5f
private const val FAST_STEP = 40f
private const val SWIPE_STEP = 30f
private const val MOVES_BEFORE_CLOSING = 4
private const val MOVES_WHILE_CLOSING = 6
private const val RIDING_MOVES = 10
private const val SETTLE_FRAMES = 60
private const val SHEET_CONTENT_BELOW_HANDLE = 200f

package dev.marzahn.sankaku.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MainTestClock
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal const val COMPOSE_TEST_FRAME_MILLIS = 16L

internal enum class TouchKind { Down, Move, Up }

internal enum class Consumption(val words: String) {
  Unconsumed("left unconsumed"),
  ByEndOfMainPass("consumed by the end of the Main pass"),
  AfterMainPass("consumed after the Main pass"),
}

internal data class TouchRecord(
  val frame: Int,
  val kind: TouchKind,
  val at: Offset,
  val onPath: Set<String>,
  val consumption: Consumption,
) {
  override fun toString() =
    "frame $frame: $kind at (${at.x.roundToInt()}, ${at.y.roundToInt()}), on the path ${onPath.sorted()}, ${consumption.words}"
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.rootForTest(): RootForTest =
  checkNotNull(onRoot().fetchSemanticsNode().root) { "the test's root semantics node has no RootForTest" }

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.setTracedContent(vararg named: Pair<String, SemanticsMatcher>, content: @Composable () -> Unit): TouchTrace {
  lateinit var trace: TouchTrace
  setContent { TouchProbeRoot({ event, pass -> trace.observe(event, pass) }, content) }
  mainClock.autoAdvance = false
  trace = TouchTrace(mainClock, rootForTest(), named.toMap())
  return trace
}

@Composable
private fun TouchProbeRoot(onPass: (PointerEvent, PointerEventPass) -> Unit, content: @Composable () -> Unit) {
  Layout(content, TouchProbe(onPass)) { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    layout(constraints.maxWidth, constraints.maxHeight) {
      placeables.forEach { it.place(0, 0) }
    }
  }
}

internal class TouchTrace(
  private val clock: MainTestClock,
  private val root: RootForTest,
  private val named: Map<String, SemanticsMatcher>,
) {
  private val hitPath = ComposeHitPath(root)
  private val seen = mutableListOf<SeenTouch>()
  private var touched: Touched? = null

  val records: List<TouchRecord>
    get() {
      val firstTouchMillis = seen.firstOrNull()?.touched?.atMillis ?: return emptyList()
      return seen.map { it.record(firstTouchMillis) }
    }

  fun assertRecords(claim: String, holds: (List<TouchRecord>) -> Boolean) {
    if (!holds(records)) throw AssertionError("$claim\n$this")
  }

  override fun toString() = records.joinToString("\n", prefix = "touch trace, frames counted from the first touch:\n")

  fun observe(event: PointerEvent, pass: PointerEventPass) {
    when (pass) {
      PointerEventPass.Initial -> {
        check(!clock.autoAdvance) { "the touch trace counts frames on a stopped clock: leave mainClock.autoAdvance false" }
        val change = event.changes.singleOrNull() ?: error("the touch trace follows one finger, and this event has ${event.changes.size}")
        touched = Touched(clock.currentTime, kindOf(event.type), change.position, namedOnPath(hitPath.nodes()), change)
      }
      PointerEventPass.Main -> {
        val current = checkNotNull(touched) { "a Main pass came without its Initial pass" }
        seen += SeenTouch(current, current.changeAfterDispatch.isConsumed)
      }
      PointerEventPass.Final -> Unit
    }
  }

  private fun namedOnPath(hitNodes: List<Modifier.Node>): Set<String> {
    val hitCoordinates = hitNodes.filter { it.isAttached }.map { it.requireLayoutCoordinates() }.toSet()
    val semantics = root.semanticsOwner.unmergedRootSemanticsNode.selfAndDescendants()
    return named.filterValues { matcher ->
      semantics.filter(matcher::matches).any { node -> node.layoutInfo.ownCoordinates().any(hitCoordinates::contains) }
    }.keys
  }
}

private fun kindOf(type: PointerEventType): TouchKind = when (type) {
  PointerEventType.Press -> TouchKind.Down
  PointerEventType.Move -> TouchKind.Move
  PointerEventType.Release -> TouchKind.Up
  else -> error("the touch trace follows touches, not $type events")
}

private class Touched(
  val atMillis: Long,
  val kind: TouchKind,
  val at: Offset,
  val onPath: Set<String>,
  val changeAfterDispatch: PointerInputChange,
)

private class SeenTouch(val touched: Touched, val consumedByEndOfMain: Boolean) {
  fun record(firstTouchMillis: Long) = TouchRecord(
    frame = ((touched.atMillis - firstTouchMillis) / COMPOSE_TEST_FRAME_MILLIS).toInt(),
    kind = touched.kind,
    at = touched.at,
    onPath = touched.onPath,
    consumption = when {
      consumedByEndOfMain -> Consumption.ByEndOfMainPass
      touched.changeAfterDispatch.isConsumed -> Consumption.AfterMainPass
      else -> Consumption.Unconsumed
    },
  )
}

private fun SemanticsNode.selfAndDescendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.selfAndDescendants() }

internal fun LayoutInfo.ownCoordinates(): List<LayoutCoordinates> = getModifierInfo().map { it.coordinates } + coordinates

private data class TouchProbe(val onPass: (PointerEvent, PointerEventPass) -> Unit) : ModifierNodeElement<TouchProbeNode>() {
  override fun create() = TouchProbeNode(onPass)

  override fun update(node: TouchProbeNode) {
    node.onPass = onPass
  }
}

private class TouchProbeNode(var onPass: (PointerEvent, PointerEventPass) -> Unit) : Modifier.Node(), PointerInputModifierNode {
  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) = onPass(pointerEvent, pass)

  override fun onCancelPointerInput() = Unit
}

package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class SheetStops(val half: Float, val full: Float) {
  val all: List<Float> get() = listOf(0f, half, full)

  fun at(stop: SheetStop): Float = when (stop) {
    SheetStop.Peek -> 0f
    SheetStop.Half -> half
    SheetStop.Full -> full
  }
}

internal enum class SheetStop(val spoken: String) {
  Peek("Collapsed"),
  Half("Half expanded"),
  Full("Expanded"),
}

internal fun settleLift(lift: Float, velocity: Float, stops: SheetStops, flingThreshold: Float): Float {
  val all = stops.all
  return when {
    velocity < -flingThreshold -> all.firstOrNull { it > lift + STOP_SLOP } ?: stops.full
    velocity > flingThreshold -> all.lastOrNull { it < lift - STOP_SLOP } ?: 0f
    else -> all.minBy { abs(it - lift) }
  }
}

internal fun sheetFractions(lift: Float, stops: SheetStops): Pair<Float, Float> {
  val half = if (stops.half > 0f) (lift / stops.half).coerceIn(0f, 1f) else 0f
  val span = stops.full - stops.half
  val full = if (span > 0f) ((lift - stops.half) / span).coerceIn(0f, 1f) else 0f
  return half to full
}

private const val STOP_SLOP = 1f

private const val QUIET_FRAMES = 2

@Stable
internal class ViewerSheetState(
  private val scope: CoroutineScope,
  private val spec: AnimationSpec<Float>,
  private val flingThreshold: Float,
  private val initial: SheetStop = SheetStop.Peek,
) {
  var measured by mutableStateOf(false)
    private set

  var lift by mutableFloatStateOf(0f)
    private set

  var stops by mutableStateOf(SheetStops(0f, 0f))
    private set

  private var job: Job? = null

  private var springTarget by mutableStateOf<Float?>(null)

  internal var touched = false

  private var stepped = false
  private var quiet: Job? = null

  val atPeek: Boolean get() = lift <= STOP_SLOP

  val headedForPeek: Boolean get() = atPeek || springTarget == 0f

  private val atStop: Boolean get() = stops.all.any { abs(it - lift) <= STOP_SLOP }

  private val heading: Float get() = springTarget ?: lift

  val stop: SheetStop by derivedStateOf {
    val all = stops.all
    SheetStop.entries[all.indices.minBy { abs(all[it] - heading) }]
  }

  private fun stopAbove(): Float? = stops.all.firstOrNull { it > heading + STOP_SLOP }

  private fun stopBelow(): Float? = stops.all.lastOrNull { it < heading - STOP_SLOP }

  fun update(new: SheetStops) {
    if (new == stops) return
    val first = !measured
    val index = stops.all.indexOfFirst { abs(it - lift) <= STOP_SLOP }
    measured = true
    stops = new
    if (job?.isActive != true) {
      lift = when {
        first -> new.at(initial)
        index >= 0 -> new.all[index]
        else -> lift.coerceIn(0f, new.full)
      }
    }
  }

  fun dragBy(dy: Float): Float {
    job?.cancel()
    springTarget = null
    val before = lift
    lift = (lift - dy).coerceIn(0f, stops.full)
    return before - lift
  }

  fun settle(velocity: Float) = animateTo(settleLift(lift, velocity, stops, flingThreshold), velocity)

  fun animateTo(target: Float, velocity: Float = 0f) {
    job?.cancel()
    springTarget = target
    job = scope.launch {
      animate(lift, target, -velocity, spec) { value, _ -> lift = value.coerceIn(0f, stops.full) }
      springTarget = null
    }
  }

  val canExpand: Boolean get() = stopAbove() != null

  val canCollapse: Boolean get() = stopBelow() != null

  fun expand(): Boolean = stopAbove()?.also { animateTo(it) } != null

  fun collapse(): Boolean = stopBelow()?.also { animateTo(it) } != null

  fun step() = animateTo(stopAbove() ?: 0f)

  private fun stepBy(move: () -> Boolean) {
    if (!stepped) stepped = move()
    quiet?.cancel()
    quiet = scope.launch {
      repeat(QUIET_FRAMES) { withFrameNanos {} }
      stepped = false
    }
  }

  val connection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      if (available.y < 0f && source == NestedScrollSource.UserInput && lift < stops.full) {
        if (touched) return Offset(0f, dragBy(available.y))
        stepBy(::expand)
        return Offset(0f, available.y)
      }
      return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
      if (available.y > 0f && source == NestedScrollSource.UserInput && lift > 0f) {
        if (touched) return Offset(0f, dragBy(available.y))
        stepBy(::collapse)
        return Offset(0f, available.y)
      }
      return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
      if (atStop) return Velocity.Zero
      settle(available.y)
      return available
    }
  }
}

@Composable
internal fun rememberViewerSheetState(
  scope: CoroutineScope,
  spec: AnimationSpec<Float>,
  flingThreshold: Float,
  postId: String?,
): ViewerSheetState {
  var saved by rememberSaveable(key = postId?.let(::viewerSheetKey)) { mutableStateOf(SheetStop.Peek.name) }
  val sheet = remember {
    ViewerSheetState(scope, spec, flingThreshold, initial = SheetStop.entries.firstOrNull { it.name == saved } ?: SheetStop.Peek)
  }
  LaunchedEffect(sheet) { snapshotFlow { sheet.stop.takeIf { sheet.measured } }.collect { if (it != null) saved = it.name } }
  return sheet
}

internal fun viewerSheetKey(id: String): String = "viewer-sheet-$id"

internal fun Modifier.viewerSheet(sheet: ViewerSheetState): Modifier = this
  .pointerInput(sheet) {
    awaitPointerEventScope {
      try {
        while (true) sheet.touched = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
      } finally {
        sheet.touched = false
      }
    }
  }
  .nestedScroll(sheet.connection)
  .semantics {
    verticalScrollAxisRange = ScrollAxisRange(value = { sheet.lift }, maxValue = { sheet.stops.full })
    scrollBy { _, y ->
      when {
        y > 0f -> sheet.expand()
        y < 0f -> sheet.collapse()
        else -> false
      }
    }
  }

internal fun sheetActions(sheet: ViewerSheetState, beforeExpand: () -> Unit = {}): List<CustomAccessibilityAction> =
  listOfNotNull(
    CustomAccessibilityAction(EXPAND_ACTION) {
      beforeExpand()
      sheet.expand()
    }.takeIf { sheet.canExpand },
    CustomAccessibilityAction(COLLAPSE_ACTION) { sheet.collapse() }.takeIf { sheet.canCollapse },
  )

private const val EXPAND_ACTION = "Expand details"
private const val COLLAPSE_ACTION = "Collapse details"

@Composable
internal fun SheetHandle(sheet: ViewerSheetState, fullFraction: () -> Float, modifier: Modifier = Modifier) {
  val stop = sheet.stop
  val actions = sheetActions(sheet)
  Box(
    modifier
      .fillMaxWidth()
      .height(16.dp)
      .clickable(
        interactionSource = null,
        indication = null,
        onClickLabel = if (stop == SheetStop.Full) "Collapse" else "Expand",
        role = Role.Button,
        onClick = sheet::step,
      )
      .semantics {
        contentDescription = "Details"
        stateDescription = stop.spoken
        customActions = actions
      },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier
        .graphicsLayer { alpha = 1f - fullFraction() }
        .size(width = 32.dp, height = 4.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
    )
  }
}

package dev.marzahn.sankaku.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.core.keyframes
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import com.google.fonts.icons.close
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.check
import com.google.fonts.icons.more_vert
import com.google.fonts.icons.undo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Modifier.rejected(count: Int, shape: Shape): Modifier = composed {
  val shake = remember { Animatable(0f) }
  var outlined by remember { mutableStateOf(false) }
  val haptics = LocalHapticFeedback.current
  val error = MaterialTheme.colorScheme.error
  val atStart = remember { count }
  LaunchedEffect(count) {
    if (count == atStart) return@LaunchedEffect
    haptics.performHapticFeedback(HapticFeedbackType.Reject)
    outlined = true
    launch {
      shake.snapTo(0f)
      shake.animateTo(
        0f,
        keyframes {
          durationMillis = SHAKE_MS
          6f at 50
          -6f at 130
          4f at 210
          -2f at 290
        },
      )
    }
    delay(OUTLINE_MS)
    outlined = false
  }
  graphicsLayer { translationX = shake.value.dp.toPx() }
    .drawWithContent {
      drawContent()
      if (outlined) {
        val width = 2.dp.toPx()
        drawOutline(shape.createOutline(size, layoutDirection, this), error, style = Stroke(width))
      }
    }
}

@Composable
fun Modifier.countdownRing(left: () -> Float, inset: Dp = 3.dp): Modifier {
  val color = LocalContentColor.current
  return drawWithContent {
    drawContent()
    val stroke = 3.dp.toPx()
    val radius = minOf(size.width, size.height) / 2 - inset.toPx()
    val topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius)
    val arc = Size(radius * 2, radius * 2)
    drawArc(color.copy(alpha = 0.18f), 0f, 360f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke))
    drawArc(color, -90f, 360f * left(), useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
  }
}

@Composable
fun LiveAnnouncement(text: String, modifier: Modifier = Modifier) {
  Box(
    modifier.size(1.dp).semantics {
      liveRegion = LiveRegionMode.Polite
      contentDescription = text
    },
  )
}

@Composable
fun CloseCorner(
  canReopen: Boolean,
  onClose: () -> Unit,
  onReopen: () -> Boolean,
  modifier: Modifier = Modifier,
  closeLabel: String = CLOSE_LABEL,
) {
  val haptics = LocalHapticFeedback.current
  val presses = remember { MutableInteractionSource() }
  val hold = rememberHold(presses, active = canReopen)
  val reopen = {
    haptics.performHapticFeedback(if (onReopen()) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
  }
  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    shadowElevation = FabElevation,
    modifier = modifier.size(BottomRowLayout.CornerButton),
  ) {
    Box(
      Modifier
        .combinedClickable(
          interactionSource = presses,
          indication = ripple(),
          role = Role.Button,
          hapticFeedbackEnabled = false,
          onLongClickLabel = if (canReopen) REOPEN_LABEL else null,
          onLongClick = reopen,
          onClick = onClose,
        )
        .semantics {
          contentDescription = closeLabel
          if (canReopen) customActions = listOf(CustomAccessibilityAction(REOPEN_LABEL) { onReopen() })
        }
        .then(if (hold.held) Modifier.countdownRing({ hold.progress }) else Modifier),
      contentAlignment = Alignment.Center,
    ) {
      Icon(if (hold.showsReopen) undo else close, contentDescription = null)
    }
  }
}

const val CLOSE_LABEL = "Close"

const val REOPEN_LABEL = "Reopen closed item"

@Stable
internal class Hold(private val pressed: () -> Boolean, private val fill: Animatable<Float, *>) {
  val progress: Float get() = fill.value

  val held: Boolean get() = pressed() && fill.value > 0f

  val showsReopen: Boolean get() = pressed() && fill.value >= HOLD_SHOWS_REOPEN
}

@Composable
internal fun rememberHold(presses: MutableInteractionSource, active: Boolean): Hold {
  val pressed by presses.collectIsPressedAsState()
  val fill = remember { Animatable(0f) }
  val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
  LaunchedEffect(pressed, active) {
    fill.snapTo(0f)
    if (pressed && active) fill.animateTo(1f, tween(timeout.toInt(), easing = LinearEasing))
  }
  return remember(fill) { Hold({ pressed }, fill) }
}

private const val HOLD_SHOWS_REOPEN = 0.25f

@Composable
internal fun ReopenBadge(modifier: Modifier = Modifier) {
  Badge(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    modifier = modifier.size(ReopenBadgeSize).clearAndSetSemantics {},
  ) {
    Icon(undo, contentDescription = null, modifier = Modifier.size(14.dp))
  }
}

private val ReopenBadgeSize = 22.dp

val REOPEN_TAB_LABEL = "Reopen closed tab (${Shortcuts.ReopenTab.label})"

@Composable
fun ReopenTabBadge(canReopen: Boolean, onReopen: () -> Unit, content: @Composable () -> Unit) {
  Box {
    content()
    if (canReopen) {
      Box(Modifier.align(Alignment.TopEnd)) {
        HoverHint(REOPEN_TAB_LABEL) {
          ReopenBadge(
            Modifier
              .semantics(mergeDescendants = true) { contentDescription = REOPEN_TAB_LABEL }
              .clip(CircleShape)
              .clickable(role = Role.Button, onClick = onReopen),
          )
        }
      }
    }
  }
}

internal val FabElevation = 6.dp

enum class MenuSaveState {
  Idle,

  Saving,

  Saved,
}

@Composable
fun MenuButton(state: MenuSaveState, onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
  Surface(onClick = onClick, shape = CircleShape, color = Color.Transparent, modifier = modifier.size(48.dp).semantics { contentDescription = description }) {
    Box(contentAlignment = Alignment.Center) {
      Icon(more_vert, contentDescription = null)
      if (state == MenuSaveState.Saving) CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 3.dp)
      if (state == MenuSaveState.Saved) SavedDot(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
    }
  }
}

@Composable
private fun SavedDot(modifier: Modifier) {
  Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary, modifier = modifier.size(14.dp)) {
    Icon(check, contentDescription = null, modifier = Modifier.padding(2.dp))
  }
}

@Composable
fun CornerActionChip(
  icon: ImageVector,
  label: String,
  error: Boolean,
  onClick: () -> Unit,
  left: () -> Float,
  interactionSource: MutableInteractionSource,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = if (error) colors.errorContainer else colors.tertiaryContainer,
    contentColor = if (error) colors.onErrorContainer else colors.onTertiaryContainer,
    interactionSource = interactionSource,
    modifier = modifier.size(48.dp).semantics { contentDescription = label },
  ) {
    Box(Modifier.countdownRing(left, inset = 2.dp), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
  }
}

private const val SHAKE_MS = 360
private const val OUTLINE_MS = 1_000L

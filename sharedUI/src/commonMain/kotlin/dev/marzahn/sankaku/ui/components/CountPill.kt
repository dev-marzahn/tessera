package dev.marzahn.sankaku.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.fonts.icons.chevron_left
import com.google.fonts.icons.chevron_right
import com.google.fonts.icons.undo
import com.google.fonts.icons.view_agenda

@Composable
fun CountPill(
  text: CountPillText,
  onOpen: () -> Unit,
  canReopen: Boolean,
  onReopen: () -> Boolean,
  actions: List<CustomAccessibilityAction>,
  modifier: Modifier = Modifier,
  arrows: Boolean = false,
  onPrevious: () -> Unit = {},
  onNext: () -> Unit = {},
  previousLabel: String = "",
  nextLabel: String = "",
  previousEnabled: Boolean = false,
  nextEnabled: Boolean = false,
  tip: @Composable BoxScope.() -> Unit = {},
) {
  val haptics = LocalHapticFeedback.current
  val presses = remember { MutableInteractionSource() }
  val hold = rememberHold(presses, active = canReopen)
  val reopen = {
    haptics.performHapticFeedback(if (onReopen()) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
  }
  val description = text.spoken + if (canReopen) ", closed items to reopen" else ""
  val allActions = actions + listOfNotNull(CustomAccessibilityAction(REOPEN_LABEL) { onReopen() }.takeIf { canReopen })
  val colors = MaterialTheme.colorScheme
  BadgedBox(badge = { if (canReopen) ReopenBadge() }, modifier = modifier) {
    Box(Modifier.height(BottomRowLayout.PieceTarget), contentAlignment = Alignment.Center) {
      Surface(shape = CircleShape, color = colors.primaryContainer, shadowElevation = FabElevation, modifier = Modifier.matchParentSize().padding(vertical = 4.dp)) {}
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(BottomRowLayout.PieceTarget)) {
        if (arrows) PillArrow(chevron_left, previousLabel, enabled = previousEnabled, onClick = onPrevious)
        Box(
          Modifier
            .weight(1f, fill = false)
            .fillMaxHeight()
            .widthIn(min = if (arrows) 48.dp else 64.dp)
            .combinedClickable(
              interactionSource = presses,
              indication = null,
              role = Role.Button,
              hapticFeedbackEnabled = false,
              onClickLabel = "Show queue",
              onLongClickLabel = if (canReopen) REOPEN_LABEL else null,
              onLongClick = reopen,
              onClick = onOpen,
            )
            .semantics {
              contentDescription = description
              customActions = allActions
            },
          contentAlignment = Alignment.Center,
        ) {
          val fill = colors.onPrimaryContainer.copy(alpha = 0.16f)
          Box(
            Modifier
              .matchParentSize()
              .padding(vertical = 4.dp)
              .clip(CircleShape)
              .indication(presses, ripple())
              .drawBehind { if (hold.held) drawRect(fill, size = Size(size.width * hold.progress, size.height)) },
          )
          Row(
            Modifier.padding(horizontal = if (arrows) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (!arrows || hold.showsReopen) {
              Icon(if (hold.showsReopen) undo else view_agenda, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
            if (text.source != null) {
              PillSource(text.source, text.short, Modifier.weight(1f, fill = false))
              Text("·", style = PillSourceStyle, color = colors.onPrimaryContainer.copy(alpha = 0.6f), maxLines = 1)
            }
            Text(text.figures, style = PillCountStyle, color = colors.onPrimaryContainer, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            if (text.later != null) {
              Text("·", style = PillSourceStyle, color = colors.onPrimaryContainer.copy(alpha = 0.6f), maxLines = 1)
              Text(text.later, style = PillCountStyle, color = colors.primary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
          }
          text.progress?.let { progress ->
            val track = colors.primary.copy(alpha = 0.2f)
            val bar = colors.primary
            Box(
              Modifier
                .matchParentSize()
                .padding(start = 14.dp, end = 14.dp, bottom = 9.dp)
                .drawBehind {
                  val height = PillBarHeight.toPx()
                  val top = size.height - height
                  drawRect(track, topLeft = Offset(0f, top), size = Size(size.width, height))
                  drawRect(bar, topLeft = Offset(0f, top), size = Size(size.width * progress, height))
                },
            )
          }
          tip()
        }
        if (arrows) PillArrow(chevron_right, nextLabel, enabled = nextEnabled, onClick = onNext)
      }
    }
  }
}

@Composable
private fun PillSource(name: String, short: ShortSource?, modifier: Modifier = Modifier) {
  val color = MaterialTheme.colorScheme.onPrimaryContainer
  Layout(
    contents = listOf(
      { Text(name, style = PillSourceStyle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      {
        if (short != null) {
          Row {
            Text(
              short.lead,
              style = PillSourceStyle,
              color = color,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            Text(" +${short.more}", style = PillSourceStyle, color = color, maxLines = 1, softWrap = false)
          }
        }
      },
    ),
    modifier = modifier,
  ) { (wholes, shorts), constraints ->
    val whole = wholes.single()
    val shortened = shorts.firstOrNull()?.takeIf { whole.maxIntrinsicWidth(constraints.maxHeight) > constraints.maxWidth }
    val placed = (shortened ?: whole).measure(constraints)
    layout(placed.width, placed.height) { placed.placeRelative(0, 0) }
  }
}

@Composable
private fun PillArrow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
  val presses = remember { MutableInteractionSource() }
  Box(
    Modifier
      .size(48.dp)
      .clickable(presses, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(40.dp).clip(CircleShape).indication(presses, ripple()))
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.alpha(if (enabled) 1f else 0.38f))
  }
}

private val PillCountStyle: TextStyle
  @Composable get() = TextStyle(
    fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
    fontSize = 15.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
  )

private val PillSourceStyle: TextStyle
  @Composable get() = TextStyle(fontFamily = MaterialTheme.typography.labelLarge.fontFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium)

private val PillBarHeight = 3.dp

data class CountPillText(
  val source: String?,
  val figures: String,
  val spoken: String,
  val later: String? = null,
  val progress: Float? = null,
  val short: ShortSource? = null,
)

data class ShortSource(val lead: String, val more: Int) {
  companion object {
    fun of(tags: List<String>, prefix: String = ""): ShortSource? =
      if (tags.size < 2) null else ShortSource(prefix + tags.first(), tags.size - 1)
  }
}

@Composable
fun PillWithLine(line: (@Composable () -> Unit)?, modifier: Modifier = Modifier, pill: @Composable () -> Unit) {
  Layout(
    contents = listOf(pill, { if (line != null) line() }),
    modifier = modifier,
  ) { (pills, lines), constraints ->
    val placedPill = pills.single().measure(constraints)
    val gap = 4.dp.roundToPx()
    val room = (constraints.maxWidth - 2 * BlockEndLineMargin.roundToPx()).coerceAtLeast(0)
    val placedLine = lines.firstOrNull()?.measure(Constraints(maxWidth = room))
    layout(placedPill.width, placedPill.height) {
      placedPill.placeRelative(0, 0)
      placedLine?.placeRelative((placedPill.width - placedLine.width) / 2, -placedLine.height - gap)
    }
  }
}

private val BlockEndLineMargin = 16.dp

@Composable
fun DoneWithChip(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, label: String = doneWithLabel(title)) {
  val colors = MaterialTheme.colorScheme
  Surface(
    onClick = onClose,
    shape = CircleShape,
    color = colors.inverseSurface,
    contentColor = colors.inverseOnSurface,
    shadowElevation = FabElevation,
    modifier = modifier.clearAndSetSemantics {
      contentDescription = label
      role = Role.Button
      onClick { onClose(); true }
    },
  ) {
    Row(
      Modifier.heightIn(min = 40.dp).padding(start = 14.dp, end = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        "Done with $title?",
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
      Surface(shape = CircleShape, color = colors.inversePrimary, contentColor = colors.inverseSurface) {
        Text(DONE_WITH_CLOSE, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
      }
    }
  }
}

const val DONE_WITH_CLOSE = "Close"

fun doneWithLabel(title: String): String = "$DONE_WITH_CLOSE $title"

@Composable
fun BlockEndLine(text: String, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Surface(
    shape = CircleShape,
    color = colors.surfaceContainerHighest,
    contentColor = colors.onSurface,
    shadowElevation = FabElevation,
    modifier = modifier.clearAndSetSemantics {},
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
  }
}

@Composable
fun UndoChip(
  visibleState: MutableTransitionState<Boolean>,
  onUndo: () -> Unit,
  label: String,
  interactionSource: MutableInteractionSource,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visibleState,
    modifier = modifier,
    enter = expandHorizontally(spatialSpec(), expandFrom = Alignment.End) + fadeIn(effectsSpec()),
    exit = shrinkHorizontally(spatialSpec(), shrinkTowards = Alignment.End) + fadeOut(effectsSpec()),
  ) {
    UndoChipBody(onUndo, label, interactionSource)
  }
}

@Composable
private fun UndoChipBody(onUndo: () -> Unit, label: String, interactionSource: MutableInteractionSource) {
  val colors = MaterialTheme.colorScheme
  Box(
    Modifier
      .size(BottomRowLayout.PieceTarget)
      .clickable(interactionSource, indication = null, role = Role.Button, onClick = onUndo)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = CircleShape,
      color = colors.inverseSurface,
      contentColor = colors.inversePrimary,
      shadowElevation = FabElevation,
      modifier = Modifier.clearAndSetSemantics {}.size(BottomRowLayout.PieceDrawn),
    ) {
      Box(
        Modifier
          .clip(CircleShape)
          .indication(interactionSource, ripple()),
        contentAlignment = Alignment.Center,
      ) {
        Icon(undo, contentDescription = null, modifier = Modifier.size(20.dp))
      }
    }
  }
}

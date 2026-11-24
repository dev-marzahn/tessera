package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.marzahn.sankaku.api.dto.TagType
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.theme.color
import dev.marzahn.sankaku.ui.theme.onColor
import dev.marzahn.sankaku.ui.theme.CountAlpha
import dev.marzahn.sankaku.ui.theme.HoverShade
import dev.marzahn.sankaku.ui.theme.Tinted
import dev.marzahn.sankaku.ui.theme.tinted

enum class TagChipState { Plain, Included, Excluded }

@Composable
fun TagChip(
  name: String,
  type: TagType?,
  count: String?,
  state: TagChipState,
  selected: Boolean,
  interactionSource: MutableInteractionSource,
  modifier: Modifier = Modifier,
  touch: Boolean = platformIsTouch,
  rejected: Int = 0,
  later: TagLater? = null,
  trailing: (@Composable (gap: Dp) -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  val panel = colors.surfaceContainerLowest
  fun solid(fill: Color, text: Color) = Tinted(fill, text, text.copy(alpha = CountAlpha))
  val chip = when (state) {
    TagChipState.Excluded -> solid(Color.Transparent, colors.error)
    TagChipState.Included -> solid(type?.color ?: colors.primary, type?.onColor ?: colors.onPrimary)
    TagChipState.Plain -> type?.tinted(panel) ?: solid(colors.surfaceContainerHigh, colors.onSurface)
  }
  val fill = chip.fill
  val text = chip.text
  val hovered by interactionSource.collectIsHoveredAsState()
  val pressed by interactionSource.collectIsPressedAsState()
  val laterInteractions = remember { MutableInteractionSource() }
  val laterPressed by laterInteractions.collectIsPressedAsState()
  val darken = hovered || (touch && pressed)
  val measures = if (touch) TouchMeasures else DesktopMeasures
  val ring = colors.primary
  Box(
    modifier
      .then(if (touch) Modifier.sizeIn(minWidth = TouchTarget, minHeight = TouchTarget) else Modifier)
      .zIndex(if (selected) 1f else 0f),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      Modifier
        .drawBehind {
          if (!selected) return@drawBehind
          val out = (RingGap + SelectionRing / 2).toPx()
          drawRoundRect(
            ring,
            topLeft = Offset(-out, -out),
            size = Size(size.width + 2 * out, size.height + 2 * out),
            cornerRadius = CornerRadius((ChipCorner + RingGap + SelectionRing / 2).toPx()),
            style = Stroke(SelectionRing.toPx()),
          )
        }
        .rejected(rejected, ChipShape)
        .heightIn(min = measures.height)
        .then(if (later != null) Modifier.height(IntrinsicSize.Min) else Modifier)
        .clip(ChipShape)
        .background(fill)
        .then(if (state == TagChipState.Excluded) Modifier.border(1.5.dp, colors.error, ChipShape) else Modifier)
        .drawWithContent {
          drawContent()
          if (darken) drawRect(Color.Black.copy(alpha = HoverShade))
        }
        .then(
          if (later != null) {
            Modifier.padding(start = measures.padding)
          } else {
            Modifier.padding(horizontal = measures.padding, vertical = 1.dp)
          },
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(measures.gap),
    ) {
      val sign = when (state) {
        TagChipState.Included -> "+"
        TagChipState.Excluded -> "−"
        TagChipState.Plain -> null
      }
      if (sign != null) Text(sign, color = text, fontSize = measures.name, fontWeight = FontWeight.Bold)
      Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
        Text(
          name,
          color = text,
          fontSize = measures.name,
          fontWeight = FontWeight.Medium,
          textDecoration = if (state == TagChipState.Excluded) TextDecoration.LineThrough else null,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        CompositionLocalProvider(LocalContentColor provides text) { trailing?.invoke(measures.gap) }
      }
      if (count != null) Text(count, color = chip.count, fontSize = measures.count)
      if (later != null) LaterSegment(later, text, laterPressed)
    }
    if (later != null) {
      Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
        Box(
          Modifier
            .width(LaterSegmentWidth + measures.gap)
            .fillMaxHeight()
            .clickable(laterInteractions, indication = null, role = Role.Button, onClick = later.onClick)
            .semantics { contentDescription = later.description },
        )
      }
    }
  }
}

@Composable
private fun LaterSegment(later: TagLater, text: Color, pressed: Boolean) {
  val colors = MaterialTheme.colorScheme
  val queued = later.state != TagLaterState.Open
  Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 6.dp).background(text.copy(alpha = HairlineAlpha)))
    Box(
      Modifier
        .width(LaterSegmentWidth - 1.dp)
        .fillMaxHeight()
        .then(if (queued) Modifier.background(colors.primaryContainer) else Modifier)
        .then(if (pressed) Modifier.background(Color.Black.copy(alpha = HoverShade)) else Modifier),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        later.state.icon,
        contentDescription = null,
        tint = if (queued) colors.onPrimaryContainer else text,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

private class ChipMeasures(val height: Dp, val padding: Dp, val gap: Dp, val name: TextUnit, val count: TextUnit)

private val DesktopMeasures = ChipMeasures(height = 26.dp, padding = 8.dp, gap = 5.dp, name = 12.5.sp, count = 11.sp)
private val TouchMeasures = ChipMeasures(height = 32.dp, padding = 10.dp, gap = 6.dp, name = 13.sp, count = 11.5.sp)
private val TouchTarget = 48.dp
private val ChipCorner = 8.dp
private val ChipShape = RoundedCornerShape(ChipCorner)
private val RingGap = 2.dp
private val SelectionRing = 2.dp
private val LaterSegmentWidth = 42.dp
private const val HairlineAlpha = 0.3f

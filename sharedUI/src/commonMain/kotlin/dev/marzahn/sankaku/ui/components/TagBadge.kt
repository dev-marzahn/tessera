package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.background
import dev.marzahn.sankaku.platformIsTouch
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.api.dto.TagType
import dev.marzahn.sankaku.ui.theme.color
import dev.marzahn.sankaku.ui.theme.HoverShade
import dev.marzahn.sankaku.ui.theme.onColor
import dev.marzahn.sankaku.ui.theme.tinted

val TagBadgeHeight = 28.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagBadge(
  label: AnnotatedString,
  type: TagType?,
  hint: String?,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  included: Boolean = false,
  interactionSource: MutableInteractionSource? = null,
  flagged: Boolean = false,
  trailing: (@Composable () -> Unit)? = null,
) {
  val excluded = label.startsWith("-")
  val colors = MaterialTheme.colorScheme
  val tint = if (flagged) null else type?.tinted(colors.surfaceContainerLowest)
  val container = if (flagged) colors.errorContainer else tint?.fill ?: colors.surfaceContainerHighest
  val content = if (flagged) colors.onErrorContainer else tint?.text ?: colors.onSurface
  val typed = type.takeIf { tint != null }
  val dot = typed?.color ?: colors.outlineVariant
  val badge = @Composable {
    val body = @Composable {
      Row(
        Modifier.heightIn(min = TagBadgeHeight).padding(start = 8.dp, end = if (trailing == null) 12.dp else 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(Modifier.size(14.dp).background(dot, CircleShape), contentAlignment = Alignment.Center) {
          val bar = typed?.onColor ?: content
          if (excluded || included) Box(Modifier.width(8.dp).height(2.dp).background(bar, CircleShape))
          if (included && !excluded) Box(Modifier.width(2.dp).height(8.dp).background(bar, CircleShape))
        }
        Text(
          if (excluded) label.subSequence(1, label.length) else label,
          style = MaterialTheme.typography.labelLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
      }
    }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val shade by animateFloatAsState(
      if ((onClick != null || interactionSource != null) && (hovered || pressed)) HoverShade else 0f,
      effectsSpec(),
    )
    val clicks = if (onClick != null) Modifier.clickable(interactions, indication = null, onClick = onClick) else Modifier
    Surface(
      shape = CircleShape,
      color = lerp(container, Color.Black, shade),
      contentColor = content,
      modifier = modifier.then(clicks),
    ) { body() }
  }
  HoverHint(hint) { badge() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoverHint(hint: String?, content: @Composable () -> Unit) {
  if (hint == null) {
    content()
  } else {
    TooltipBox(
      positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
      tooltip = { PlainTooltip { Text(hint) } },
      state = rememberTooltipState(),
    ) { content() }
  }
}

@Composable
fun ButtonHint(hint: String?, content: @Composable () -> Unit) = HoverHint(hint.takeUnless { platformIsTouch }, content)

@Composable
fun TagBadge(
  name: String,
  type: TagType?,
  hint: String?,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  included: Boolean = false,
  interactionSource: MutableInteractionSource? = null,
  flagged: Boolean = false,
  trailing: (@Composable () -> Unit)? = null,
) = TagBadge(AnnotatedString(name), type, hint, modifier, onClick, included, interactionSource, flagged, trailing)
